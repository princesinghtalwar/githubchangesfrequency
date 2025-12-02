package com.githubchangesfrequency.web;

import com.githubchangesfrequency.domain.RepositoryChange;
import com.githubchangesfrequency.service.GitChangeScannerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scan")
public class ScanController {

    private final GitChangeScannerService scanner;

    public ScanController(GitChangeScannerService scanner) {
        this.scanner = scanner;
    }

    @PostMapping
    public ResponseEntity<?> scanRepo(@RequestParam String repoUrl, @RequestParam String branch) {
        try {
            RepositoryChange result = scanner.scanRepository(repoUrl, branch);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Scan failed: " + e.getMessage());
        }
    }
}
