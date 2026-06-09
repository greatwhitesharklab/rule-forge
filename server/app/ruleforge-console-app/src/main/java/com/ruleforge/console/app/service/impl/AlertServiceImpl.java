package com.ruleforge.console.app.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ruleforge.console.app.entity.AlertHistory;
import com.ruleforge.console.app.entity.AlertRule;
import com.ruleforge.console.app.entity.MetricsSnapshot;
import com.ruleforge.console.app.repository.data.MonitoringRepository;
import com.ruleforge.console.app.service.IAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements IAlertService {

    private final MonitoringRepository monitoringRepository;
    private final RestTemplate restTemplate;

    @Value("${ruleforge.monitoring.alert-webhook-timeout-ms:5000}")
    private int webhookTimeoutMs;

    @Override
    public void evaluateAlerts() {
        List<AlertRule> rules = monitoringRepository.findEnabledAlertRules();

        for (AlertRule rule : rules) {
            try {
                evaluateRule(rule);
            } catch (Exception e) {
                log.error("评估告警规则失败: ruleId={}, name={}", rule.getId(), rule.getName(), e);
            }
        }
    }

    @Override
    public List<AlertRule> listAlertRules() {
        return monitoringRepository.findAllAlertRules();
    }

    @Override
    public AlertRule saveAlertRule(AlertRule rule) {
        rule.setEnabled(true);
        rule.setCreatedAt(new Date());
        monitoringRepository.insertAlertRule(rule);
        return rule;
    }

    @Override
    public AlertRule updateAlertRule(AlertRule rule) {
        rule.setUpdatedAt(new Date());
        monitoringRepository.updateAlertRule(rule);
        return rule;
    }

    @Override
    public void deleteAlertRule(Long id) {
        monitoringRepository.deleteAlertRule(id);
    }

    @Override
    public List<AlertHistory> listAlertHistory(Long alertRuleId, Date startTime, Date endTime) {
        return monitoringRepository.findAlertHistory(alertRuleId, startTime, endTime);
    }

    private void evaluateRule(AlertRule rule) {
        if (isInCooldown(rule)) {
            return;
        }

        List<MetricsSnapshot> recentSnapshots = monitoringRepository.findLatestMetrics(
                rule.getMetricName(), rule.getDurationMin(), rule.getMetricTags());
        if (recentSnapshots.size() < rule.getDurationMin()) {
            return;
        }

        for (MetricsSnapshot snapshot : recentSnapshots) {
            double value = extractMetricValue(snapshot);
            if (!evaluateCondition(rule.getCondition(), value, rule.getThreshold())) {
                return;
            }
        }

        MetricsSnapshot latestSnapshot = recentSnapshots.get(0);
        double actualValue = extractMetricValue(latestSnapshot);
        fireAlert(rule, actualValue);
    }

    private boolean isInCooldown(AlertRule rule) {
        if (rule.getLastFiredAt() == null) {
            return false;
        }
        long cooldownMs = (long) rule.getCooldownMin() * 60 * 1000;
        return System.currentTimeMillis() - rule.getLastFiredAt().getTime() < cooldownMs;
    }

    private double extractMetricValue(MetricsSnapshot snapshot) {
        String metricType = snapshot.getMetricType();
        if ("TIMER".equals(metricType)) {
            if (snapshot.getP95Ms() != null) {
                return snapshot.getP95Ms();
            }
            if (snapshot.getMeanMs() != null) {
                return snapshot.getMeanMs();
            }
            return 0;
        } else if ("COUNTER".equals(metricType)) {
            return snapshot.getCountVal() != null ? snapshot.getCountVal() : 0;
        } else if ("GAUGE".equals(metricType)) {
            return snapshot.getGaugeVal() != null ? snapshot.getGaugeVal() : 0;
        }
        return 0;
    }

    private boolean evaluateCondition(String condition, double value, double threshold) {
        return switch (condition) {
            case "GT" -> value > threshold;
            case "GTE" -> value >= threshold;
            case "LT" -> value < threshold;
            case "LTE" -> value <= threshold;
            case "EQ" -> Double.compare(value, threshold) == 0;
            default -> false;
        };
    }

    private void fireAlert(AlertRule rule, double actualValue) {
        AlertHistory history = new AlertHistory();
        history.setAlertRuleId(rule.getId());
        history.setRuleName(rule.getName());
        history.setMetricName(rule.getMetricName());
        history.setActualValue(actualValue);
        history.setThreshold(rule.getThreshold());
        history.setWebhookUrl(rule.getWebhookUrl());
        history.setFiredAt(new Date());

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("alertName", rule.getName());
            payload.put("metricName", rule.getMetricName());
            payload.put("condition", rule.getCondition());
            payload.put("threshold", rule.getThreshold());
            payload.put("actualValue", actualValue);
            payload.put("firedAt", new Date());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (rule.getWebhookHeaders() != null && !rule.getWebhookHeaders().isEmpty()) {
                Map<String, String> customHeaders = JSON.parseObject(rule.getWebhookHeaders(), Map.class);
                customHeaders.forEach(headers::set);
            }

            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(payload), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    rule.getWebhookUrl(), HttpMethod.POST, entity, String.class
            );

            history.setWebhookStatus(response.getStatusCode().value());
            history.setWebhookResponse(truncate(response.getBody(), 1024));
            log.info("告警触发: rule={}, actualValue={}, webhookStatus={}",
                    rule.getName(), actualValue, response.getStatusCode());
        } catch (Exception e) {
            history.setWebhookStatus(-1);
            history.setWebhookResponse(truncate(e.getMessage(), 1024));
            log.warn("告警 Webhook 发送失败: rule={}, url={}", rule.getName(), rule.getWebhookUrl(), e);
        }

        monitoringRepository.insertAlertHistory(history);

        monitoringRepository.updateAlertRuleLastFired(rule.getId(), new Date());
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
