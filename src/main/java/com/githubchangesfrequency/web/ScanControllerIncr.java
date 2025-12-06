package com.githubchangesfrequency.web;

import com.githubchangesfrequency.domain.RepositoryChange;
import com.githubchangesfrequency.service.GitChangeScannerService;
import com.githubchangesfrequency.service.GitChangeScannerServiceIncr;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scan")
public class ScanControllerIncr {

    private final GitChangeScannerServiceIncr scanner;

    public ScanControllerIncr(GitChangeScannerServiceIncr scanner) {
        this.scanner = scanner;
    }

    @PostMapping("/incremental")
    public ResponseEntity<?> incrementalScan(@RequestParam String repoUrl, @RequestParam String branch) {
        try {
            RepositoryChange result = scanner.scanRepositoryIncremental(repoUrl, branch);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Scan failed: " + e.getMessage());
        }
    }
}
