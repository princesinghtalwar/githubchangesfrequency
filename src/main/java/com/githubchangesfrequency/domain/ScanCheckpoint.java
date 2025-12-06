package com.githubchangesfrequency.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "scan_checkpoint", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"repo_url", "branch_name"})
})
public class ScanCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repo_url", nullable = false, length = 1024)
    private String repoUrl;

    @Column(name = "branch_name", nullable = false, length = 255)
    private String branchName;

    @Column(name = "last_processed_commit", length = 64)
    private String lastProcessedCommit;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public ScanCheckpoint() {}

    public ScanCheckpoint(String repoUrl, String branchName, String lastProcessedCommit, Instant updatedAt) {
        this.repoUrl = repoUrl;
        this.branchName = branchName;
        this.lastProcessedCommit = lastProcessedCommit;
        this.updatedAt = updatedAt;
    }

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getRepoUrl() {
		return repoUrl;
	}

	public void setRepoUrl(String repoUrl) {
		this.repoUrl = repoUrl;
	}

	public String getBranchName() {
		return branchName;
	}

	public void setBranchName(String branchName) {
		this.branchName = branchName;
	}

	public String getLastProcessedCommit() {
		return lastProcessedCommit;
	}

	public void setLastProcessedCommit(String lastProcessedCommit) {
		this.lastProcessedCommit = lastProcessedCommit;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	@Override
	public String toString() {
		return "ScanCheckpoint [id=" + id + ", repoUrl=" + repoUrl + ", branchName=" + branchName
				+ ", lastProcessedCommit=" + lastProcessedCommit + ", updatedAt=" + updatedAt + "]";
	}

	
}
