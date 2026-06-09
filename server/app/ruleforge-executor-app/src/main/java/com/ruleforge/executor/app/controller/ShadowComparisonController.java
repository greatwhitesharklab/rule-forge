package com.ruleforge.executor.app.controller;

import com.ruleforge.decision.dto.ShadowDivergenceStats;
import com.ruleforge.decision.entity.ShadowComparison;
import com.ruleforge.decision.service.IShadowComparisonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 陪跑对比内部 API（executor-app）
 * console-app 通过代理调用
 */
@Slf4j
@RestController
@RequestMapping("/api/shadow")
@RequiredArgsConstructor
public class ShadowComparisonController {

    private final IShadowComparisonService shadowComparisonService;

    @GetMapping("/comparison/{mainFlowLogId}")
    public ResponseEntity<?> getComparison(@PathVariable Long mainFlowLogId) {
        ShadowComparison comparison = shadowComparisonService.getByMainFlowLogId(mainFlowLogId);
        if (comparison == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(comparison);
    }

    @GetMapping("/comparisons")
    public ResponseEntity<?> listComparisons(
            @RequestParam String rulePackagePath,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<ShadowComparison> comparisons = shadowComparisonService.listByPackage(
                rulePackagePath, startTime, endTime, page, size);
        return ResponseEntity.ok(comparisons);
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(
            @RequestParam String rulePackagePath,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        ShadowDivergenceStats stats = shadowComparisonService.getDivergenceStats(
                rulePackagePath, startTime, endTime);
        return ResponseEntity.ok(stats);
    }
}
