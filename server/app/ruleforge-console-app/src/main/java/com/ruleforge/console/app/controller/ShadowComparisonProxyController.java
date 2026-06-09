package com.ruleforge.console.app.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 陪跑对比查询代理（console-app → executor-app）
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/shadow")
@RequiredArgsConstructor
public class ShadowComparisonProxyController {

    private final RestTemplate execRestTemplate;

    @Value("${ruleforge.exec.url}")
    private String execUrl;

    @GetMapping("/comparisons/{mainFlowLogId}")
    public ResponseEntity<?> getComparison(@PathVariable Long mainFlowLogId) {
        try {
            ResponseEntity<Map> response = execRestTemplate.getForEntity(
                    execUrl + "/api/shadow/comparison/" + mainFlowLogId, Map.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            log.error("查询陪跑对比失败: mainFlowLogId={}", mainFlowLogId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "查询陪跑对比失败"));
        }
    }

    @GetMapping("/comparisons")
    public ResponseEntity<?> listComparisons(
            @RequestParam String rulePackagePath,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            StringBuilder url = new StringBuilder(execUrl + "/api/shadow/comparisons?")
                    .append("rulePackagePath=").append(rulePackagePath)
                    .append("&page=").append(page)
                    .append("&size=").append(size);
            if (startTime != null) url.append("&startTime=").append(startTime);
            if (endTime != null) url.append("&endTime=").append(endTime);

            ResponseEntity<Map> response = execRestTemplate.getForEntity(url.toString(), Map.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            log.error("查询陪跑对比列表失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "查询陪跑对比列表失败"));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(
            @RequestParam String rulePackagePath,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        try {
            StringBuilder url = new StringBuilder(execUrl + "/api/shadow/stats?")
                    .append("rulePackagePath=").append(rulePackagePath);
            if (startTime != null) url.append("&startTime=").append(startTime);
            if (endTime != null) url.append("&endTime=").append(endTime);

            ResponseEntity<Map> response = execRestTemplate.getForEntity(url.toString(), Map.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            log.error("查询陪跑差异统计失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "查询陪跑差异统计失败"));
        }
    }
}
