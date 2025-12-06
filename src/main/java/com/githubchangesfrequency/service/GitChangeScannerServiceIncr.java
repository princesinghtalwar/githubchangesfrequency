package com.githubchangesfrequency.service;

import com.githubchangesfrequency.domain.RepositoryChange;
import com.githubchangesfrequency.domain.ScanCheckpoint;
import com.githubchangesfrequency.repository.RepositoryChangeRepository;
import com.githubchangesfrequency.repository.ScanCheckpointRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.Instant;
import java.util.Optional;

@Service
public class GitChangeScannerServiceIncr {

    private final RepositoryChangeRepository changeRepo;
    private final ScanCheckpointRepository checkpointRepo;
    private final String cloneBase;

    public GitChangeScannerServiceIncr(RepositoryChangeRepository changeRepo,
                                   ScanCheckpointRepository checkpointRepo,
                                   @Value("${git.local.cloneBase:${java.io.tmpdir}/git-clones}") String cloneBase) {
        this.changeRepo = changeRepo;
        this.checkpointRepo = checkpointRepo;
        this.cloneBase = cloneBase;
    }

    /**
     * Incremental scan: compute added/deleted lines for commits on branch that are newer than
     * checkpoint.lastProcessedCommit. If no checkpoint exists, performs a full scan.
     *
     * Returns saved RepositoryChange for this run (added/deleted/total for only the scanned commits).
     */
    @Transactional
    public RepositoryChange scanRepositoryIncremental(String repoUrl, String branch) throws Exception {
        // prepare local clone/fetch
        String dirName = repoUrl.replaceAll("[^a-zA-Z0-9._-]", "_");
        File repoDir = new File(cloneBase, dirName);

        Git git;
        if (repoDir.exists() && new File(repoDir, ".git").exists()) {
            git = Git.open(repoDir);
            try { git.fetch().setRemote("origin").call(); } catch (Exception ignored) {}
        } else {
            repoDir.mkdirs();
            git = Git.cloneRepository()
                     .setURI(repoUrl)
                     .setDirectory(repoDir)
                     .setCloneAllBranches(true)
                     .call();
        }

        try (Repository repository = git.getRepository()) {
            String resolvedRef = resolveBranchRef(repository, branch);
            if (resolvedRef == null) {
                throw new IllegalArgumentException("Branch not found: " + branch);
            }

            ObjectId tipId = repository.resolve(resolvedRef);
            if (tipId == null) {
                throw new IllegalArgumentException("Cannot resolve branch ref: " + resolvedRef);
            }

            // load checkpoint (if any)
            Optional<ScanCheckpoint> optCheckpoint = checkpointRepo.findByRepoUrlAndBranchName(repoUrl, branch);
            String lastProcessedSha = optCheckpoint.map(ScanCheckpoint::getLastProcessedCommit).orElse(null);

            long totalAdded = 0;
            long totalDeleted = 0;
            String newCheckpointSha = tipId.getName(); // update checkpoint to tip at end

            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit tipCommit = revWalk.parseCommit(tipId);
                revWalk.markStart(tipCommit);

                RevCommit lastProcessed = null;
                if (lastProcessedSha != null) {
                    try {
                        ObjectId lastId = repository.resolve(lastProcessedSha);
                        if (lastId != null) {
                            lastProcessed = revWalk.parseCommit(lastId);
                            // mark uninteresting will exclude that commit and its ancestors
                            revWalk.markUninteresting(lastProcessed);
                        } else {
                            // If the saved SHA cannot be resolved (e.g., remote history rewritten),
                            // treat as full scan. Optionally, you could reset checkpoint to null.
                            lastProcessed = null;
                        }
                    } catch (Exception e) {
                        // If parsing fails, treat as full scan
                        lastProcessed = null;
                    }
                }

                // If revWalk had no start marks due to unresolved tip, we already handled above.
                // Iterate new commits (commits reachable from tip but not reachable from lastProcessed)
                for (RevCommit commit : revWalk) {
                    // For safety: if commit equals lastProcessed (shouldn't happen because marked uninteresting),
                    // break. But revWalk.markUninteresting excludes it, so this loop contains only new commits.
                    if (lastProcessed != null && commit.equals(lastProcessed)) break;

                    // compute diff between parent(0) and commit (or empty tree for root)
                    if (commit.getParentCount() == 0) {
                        // root commit: diff against empty tree
                        try (ObjectReader reader = repository.newObjectReader();
                             DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                            diffFormatter.setRepository(repository);
                            diffFormatter.setDetectRenames(true);

                            AbstractTreeIterator emptyIter = new EmptyTreeIterator();
                            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                            newTreeIter.reset(reader, commit.getTree());

                            for (DiffEntry entry : diffFormatter.scan(emptyIter, newTreeIter)) {
                                FileHeader fh = diffFormatter.toFileHeader(entry);
                                EditList edits = fh.toEditList();
                                for (Edit e : edits) {
                                    totalAdded += Math.max(0, e.getEndB() - e.getBeginB());
                                    totalDeleted += Math.max(0, e.getEndA() - e.getBeginA());
                                }
                            }
                        }
                    } else {
                        RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
                        try (ObjectReader reader = repository.newObjectReader();
                             DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

                            diffFormatter.setRepository(repository);
                            diffFormatter.setDetectRenames(true);

                            CanonicalTreeParser parentTreeIter = new CanonicalTreeParser();
                            parentTreeIter.reset(reader, parent.getTree());

                            CanonicalTreeParser commitTreeIter = new CanonicalTreeParser();
                            commitTreeIter.reset(reader, commit.getTree());

                            for (DiffEntry entry : diffFormatter.scan(parentTreeIter, commitTreeIter)) {
                                FileHeader fh = diffFormatter.toFileHeader(entry);
                                EditList edits = fh.toEditList();
                                for (Edit e : edits) {
                                    totalAdded += Math.max(0, e.getEndB() - e.getBeginB());
                                    totalDeleted += Math.max(0, e.getEndA() - e.getBeginA());
                                }
                            }
                        }
                    }
                }
            }

            // Save a RepositoryChange record with the run totals
            RepositoryChange saved = new RepositoryChange(repoUrl, branch, totalAdded, totalDeleted, Instant.now());
            RepositoryChange persisted = changeRepo.save(saved);

            // Update or create checkpoint with tip commit
            Instant now = Instant.now();
            if (optCheckpoint.isPresent()) {
                ScanCheckpoint cp = optCheckpoint.get();
                cp.setLastProcessedCommit(newCheckpointSha);
                cp.setUpdatedAt(now);
                checkpointRepo.save(cp);
            } else {
                ScanCheckpoint cp = new ScanCheckpoint(repoUrl, branch, newCheckpointSha, now);
                checkpointRepo.save(cp);
            }

            return persisted;
        } finally {
            git.close();
        }
    }

    // resolveBranchRef() as in previous code sample (search local heads and remotes)
    private String resolveBranchRef(Repository repository, String branch) {
        try {
            String local = "refs/heads/" + branch;
            if (repository.findRef(local) != null) return local;

            String remote = "refs/remotes/origin/" + branch;
            if (repository.findRef(remote) != null) return remote;

            if (repository.findRef(branch) != null) return branch;

            for (Ref ref : repository.getRefDatabase().getRefs()) {
                if (ref.getName().endsWith("/" + branch)) return ref.getName();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
