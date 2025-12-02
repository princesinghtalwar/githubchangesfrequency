package com.githubchangesfrequency.repository;

import com.githubchangesfrequency.domain.RepositoryChange;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RepositoryChangeRepository extends JpaRepository<RepositoryChange, Long> {
    List<RepositoryChange> findByRepoUrlAndBranchNameOrderByScannedAtDesc(String repoUrl, String branchName);
}
