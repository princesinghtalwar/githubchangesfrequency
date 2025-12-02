package com.githubchangesfrequency.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "repository_change")
public class RepositoryChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String repoUrl;
    private String branchName;
    private long linesAdded;
    private long linesDeleted;
    private long totalChanges;
    private Instant scannedAt;

    // constructors, getters, setters

    public RepositoryChange() {}

    public RepositoryChange(String repoUrl, String branchName, long linesAdded, long linesDeleted, Instant scannedAt) {
        this.repoUrl = repoUrl;
        this.branchName = branchName;
        this.linesAdded = linesAdded;
        this.linesDeleted = linesDeleted;
        this.totalChanges = linesAdded + linesDeleted;
        this.scannedAt = scannedAt;
    }

    // getters and setters...
    // (omit for brevity in this snippet; generate using IDE)
}
