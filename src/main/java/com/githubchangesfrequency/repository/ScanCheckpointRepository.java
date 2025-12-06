package com.githubchangesfrequency.repository;


import com.githubchangesfrequency.domain.ScanCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScanCheckpointRepository extends JpaRepository<ScanCheckpoint, Long> {
    Optional<ScanCheckpoint> findByRepoUrlAndBranchName(String repoUrl, String branchName);
}
