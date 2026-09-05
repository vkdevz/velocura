package com.velocura.controller;

import com.velocura.dto.ClinicalBenchmarkReportDto;
import com.velocura.service.clinical.benchmark.ClinicalBenchmarkService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Controller exposing clinical benchmark metrics and formal audit white paper
 * for hospital procurement, CMO reviews, and regulatory audits.
 */
@RestController
@RequestMapping("/api/clinical/benchmark")
@CrossOrigin(origins = "*")
public class ClinicalBenchmarkController {

    private final ClinicalBenchmarkService benchmarkService;

    public ClinicalBenchmarkController(ClinicalBenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @GetMapping("/run")
    public ResponseEntity<ClinicalBenchmarkReportDto> runBenchmark() {
        ClinicalBenchmarkReportDto report = benchmarkService.runBenchmarkSuite();
        return ResponseEntity.ok(report);
    }

    @GetMapping("/latest")
    public ResponseEntity<ClinicalBenchmarkReportDto> getLatest() {
        ClinicalBenchmarkReportDto report = benchmarkService.getLatestReport();
        return ResponseEntity.ok(report);
    }

    @GetMapping(value = "/whitepaper", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public ResponseEntity<String> getWhitePaper() {
        String whitepaper = benchmarkService.generateClinicalAuditWhitePaper();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"velocura-clinical-whitepaper.md\"")
                .body(whitepaper);
    }
}
