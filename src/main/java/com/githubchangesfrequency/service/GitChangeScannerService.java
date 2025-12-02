package com.githubchangesfrequency.service;


import java.io.File;
import java.time.Instant;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.githubchangesfrequency.domain.RepositoryChange;
import com.githubchangesfrequency.repository.RepositoryChangeRepository;

@Service
public class GitChangeScannerService {

    private final RepositoryChangeRepository changeRepo;
    private final String cloneBase;

    public GitChangeScannerService(RepositoryChangeRepository changeRepo,
                                   @Value("${git.local.cloneBase:${java.io.tmpdir}/git-clones}") String cloneBase) {
        this.changeRepo = changeRepo;
        this.cloneBase = cloneBase;
    }

    /**
     * Clone or fetch repo and compute lines added/deleted on the given branch.
     * Returns saved RepositoryChange entity.
     */
    public RepositoryChange scanRepository(String repoUrl, String branch) throws Exception {
        // compute local path
        String dirName = repoUrl.replaceAll("[^a-zA-Z0-9._-]", "_");
        File repoDir = new File(cloneBase, dirName);

        Git git;
        if (repoDir.exists() && new File(repoDir, ".git").exists()) {
        	// open existing repo and fetch
        	
        	String username = "princesinghtalwar";
			String passwordOrToken = "***REMOVED***";
			Git.cloneRepository().setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, passwordOrToken));
            git = Git.open(repoDir);
            try {
                git.fetch().setRemote("origin").call();
            } catch (Exception e) {
                // fetch may fail for public readonly or different remote; ignore or log
            }
        } else {
            // clone
            repoDir.mkdirs();
            git = Git.cloneRepository()
                     .setURI(repoUrl)
                     .setDirectory(repoDir)
                     .setCloneAllBranches(true)
                     .call();
        }

        try (Repository repository = git.getRepository()) {
            // try to resolve branch ref. Prefer remote branch if local absent.
            String fullRef = resolveBranchRef(repository, branch);

            if (fullRef == null) {
                throw new IllegalArgumentException("Branch not found: " + branch);
            }

            ObjectId branchObjectId = repository.resolve(fullRef);
            if (branchObjectId == null) {
                throw new IllegalArgumentException("Cannot resolve branch object id for: " + fullRef);
            }

            long totalAdded = 0;
            long totalDeleted = 0;

            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit start = revWalk.parseCommit(branchObjectId);
                revWalk.markStart(start);

                for (RevCommit commit : revWalk) {
                    if (commit.getParentCount() == 0) {
                        // root commit: diff against empty tree
                        try (ObjectReader reader = repository.newObjectReader();
                             DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                            diffFormatter.setRepository(repository);
                            diffFormatter.setDetectRenames(true);
                            AbstractTreeIterator oldTreeIter = new EmptyTreeIterator();
                            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                            newTreeIter.reset(reader, commit.getTree());
                            // produce file diffs
                            for (DiffEntry entry : diffFormatter.scan(oldTreeIter, newTreeIter)) {
                                EditList edits = diffFormatter.toFileHeader(entry).toEditList();
                                for (Edit e : edits) {
                                    totalAdded += e.getEndB() - e.getBeginB();
                                    totalDeleted += e.getEndA() - e.getBeginA();
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

            RepositoryChange saved = new RepositoryChange(repoUrl, branch, totalAdded, totalDeleted, Instant.now());
            return changeRepo.save(saved);
        } finally {
            git.close();
        }
    }

    private String resolveBranchRef(Repository repository, String branch) {
        try {
            // check local refs/heads/{branch}
            String local = "refs/heads/" + branch;
            if (repository.findRef(local) != null) return local;

            // check remote origin
            String remote = "refs/remotes/origin/" + branch;
            if (repository.findRef(remote) != null) return remote;

            // try branch as provided (maybe already a ref)
            if (repository.findRef(branch) != null) return branch;

            // as fallback, try to resolve to any ref that ends with branch
            for (Ref ref : repository.getRefDatabase().getRefs()) {
                if (ref.getName().endsWith("/" + branch)) return ref.getName();
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}