package com.ruleforge.console.app.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruleforge.console.app.entity.AlertHistory;
import com.ruleforge.console.app.entity.AlertRule;
import com.ruleforge.console.app.entity.MetricsSnapshot;
import com.ruleforge.console.app.repository.data.MonitoringRepository;
import com.ruleforge.console.app.service.IAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringRepository monitoringRepository;
    private final IAlertService alertService;

    @GetMapping("/metrics")
    public ResponseEntity<?> queryMetrics(
            @RequestParam String metricName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date endTime,
            @RequestParam(required = false) String tags
    ) {
        List<MetricsSnapshot> snapshots = monitoringRepository.findMetricsByMetricName(metricName, startTime, endTime, tags);

        Map<String, Object> result = convertToEChartsFormat(snapshots);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/metrics/packages")
    public ResponseEntity<?> listPackages() {
        List<MetricsSnapshot> snapshots = monitoringRepository.findDistinctTags();

        List<String> packages = snapshots.stream()
                .map(MetricsSnapshot::getTags)
                .filter(Objects::nonNull)
                .map(tags -> {
                    try {
                        JSONObject json = JSON.parseObject(tags);
                        return json.getString("package");
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        return ResponseEntity.ok(packages);
    }

    @GetMapping("/alerts")
    public ResponseEntity<?> listAlertRules() {
        return ResponseEntity.ok(alertService.listAlertRules());
    }

    @PostMapping("/alerts")
    public ResponseEntity<?> createAlertRule(@RequestBody AlertRule rule) {
        return ResponseEntity.ok(alertService.saveAlertRule(rule));
    }

    @PutMapping("/alerts/{id}")
    public ResponseEntity<?> updateAlertRule(@PathVariable Long id, @RequestBody AlertRule rule) {
        rule.setId(id);
        return ResponseEntity.ok(alertService.updateAlertRule(rule));
    }

    @DeleteMapping("/alerts/{id}")
    public ResponseEntity<?> deleteAlertRule(@PathVariable Long id) {
        alertService.deleteAlertRule(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/alerts/history")
    public ResponseEntity<?> listAlertHistory(
            @RequestParam(required = false) Long alertRuleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date endTime
    ) {
        return ResponseEntity.ok(alertService.listAlertHistory(alertRuleId, startTime, endTime));
    }

    private Map<String, Object> convertToEChartsFormat(List<MetricsSnapshot> snapshots) {
        List<String> timestamps = new ArrayList<>();
        Map<String, List<Object>> series = new LinkedHashMap<>();
        series.put("p50", new ArrayList<>());
        series.put("p95", new ArrayList<>());
        series.put("p99", new ArrayList<>());
        series.put("count", new ArrayList<>());

        for (MetricsSnapshot s : snapshots) {
            timestamps.add(formatTime(s.getSnapshotTime()));
            series.get("p50").add(s.getP50Ms() != null ? s.getP50Ms() : 0);
            series.get("p95").add(s.getP95Ms() != null ? s.getP95Ms() : 0);
            series.get("p99").add(s.getP99Ms() != null ? s.getP99Ms() : 0);
            series.get("count").add(s.getCountVal() != null ? s.getCountVal() : 0);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamps", timestamps);
        result.put("series", series);
        return result;
    }

    private String formatTime(Date date) {
        if (date == null) return "";
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }
}
