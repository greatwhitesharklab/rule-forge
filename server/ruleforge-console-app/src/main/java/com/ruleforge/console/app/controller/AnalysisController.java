package com.ruleforge.console.app.controller;

import com.ruleforge.console.app.service.IAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 决策日志聚合分析 REST API
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforgeV2.root.path}/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final IAnalysisService analysisService;

    // ========== Feature 1: 决策日志聚合 ==========

    @GetMapping("/flow/timeseries")
    public ResponseEntity<?> getFlowTimeSeries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date endTime,
            @RequestParam(required = false) String rulePackagePath,
            @RequestParam(required = false) String flowId,
            @RequestParam(required = false) Boolean isGray,
            @RequestParam(defaultValue = "hourly") String granularity
    ) {
        Map<String, Object> result = analysisService.getFlowLogTimeSeries(
                startTime, endTime, rulePackagePath, flowId, isGray, granularity);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/flow/packages-summary")
    public ResponseEntity<?> getPackageFlowSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date endTime
    ) {
        List<Map<String, Object>> result = analysisService.getPackageFlowSummary(startTime, endTime);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/flow/reject-distribution")
    public ResponseEntity<?> getRejectDistribution(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date endTime,
            @RequestParam(required = false) String rulePackagePath,
            @RequestParam(defaultValue = "20") int limit
    ) {
        List<Map<String, Object>> result = analysisService.getRejectDistribution(
                startTime, endTime, rulePackagePath, limit);
        return ResponseEntity.ok(result);
    }

    // ========== Feature 2: 规则覆盖率 ==========

    @GetMapping("/rule/coverage")
    public ResponseEntity<?> getRuleCoverage(
            @RequestParam(required = false) String rulePackagePath,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date endTime
    ) {
        Map<String, Object> result = analysisService.getRuleCoverageAnalysis(
                rulePackagePath, startTime, endTime);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/rule/fire-frequency")
    public ResponseEntity<?> getRuleFireFrequency(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date endTime,
            @RequestParam(required = false) String rulePackagePath
    ) {
        List<Map<String, Object>> result = analysisService.getRuleFireFrequency(
                startTime, endTime, rulePackagePath);
        return ResponseEntity.ok(result);
    }

    // ========== Feature 3: 偏差检测 ==========

    @GetMapping("/anomaly/detect")
    public ResponseEntity<?> detectAnomalies(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date currentTime,
            @RequestParam(defaultValue = "7") int baselineDays,
            @RequestParam(defaultValue = "2.0") double sigmaThreshold,
            @RequestParam(required = false) String rulePackagePath
    ) {
        List<Map<String, Object>> result = analysisService.detectAnomalies(
                currentTime, baselineDays, sigmaThreshold, rulePackagePath);
        return ResponseEntity.ok(result);
    }

    // ========== 公共查询 ==========

    @GetMapping("/packages")
    public ResponseEntity<?> listPackages() {
        List<String> packages = analysisService.listPackagePaths();
        return ResponseEntity.ok(packages);
    }
}
