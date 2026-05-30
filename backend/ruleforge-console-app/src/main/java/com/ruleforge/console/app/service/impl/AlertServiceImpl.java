package com.ruleforge.console.app.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ruleforge.console.app.entity.AlertHistory;
import com.ruleforge.console.app.entity.AlertRule;
import com.ruleforge.console.app.entity.MetricsSnapshot;
import com.ruleforge.console.app.mapper.AlertHistoryMapper;
import com.ruleforge.console.app.mapper.AlertRuleMapper;
import com.ruleforge.console.app.mapper.MetricsSnapshotMapper;
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

    private final AlertRuleMapper alertRuleMapper;
    private final AlertHistoryMapper alertHistoryMapper;
    private final MetricsSnapshotMapper metricsSnapshotMapper;
    private final RestTemplate restTemplate;

    @Value("${ruleforge.monitoring.alert-webhook-timeout-ms:5000}")
    private int webhookTimeoutMs;

    @Override
    public void evaluateAlerts() {
        List<AlertRule> rules = alertRuleMapper.selectList(
                new QueryWrapper<AlertRule>().eq("enabled", 1)
        );

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
        return alertRuleMapper.selectList(new QueryWrapper<AlertRule>().orderByDesc("created_at"));
    }

    @Override
    public AlertRule saveAlertRule(AlertRule rule) {
        rule.setEnabled(true);
        rule.setCreatedAt(new Date());
        alertRuleMapper.insert(rule);
        return rule;
    }

    @Override
    public AlertRule updateAlertRule(AlertRule rule) {
        rule.setUpdatedAt(new Date());
        alertRuleMapper.updateById(rule);
        return rule;
    }

    @Override
    public void deleteAlertRule(Long id) {
        alertRuleMapper.deleteById(id);
    }

    @Override
    public List<AlertHistory> listAlertHistory(Long alertRuleId, Date startTime, Date endTime) {
        QueryWrapper<AlertHistory> wrapper = new QueryWrapper<AlertHistory>()
                .orderByDesc("fired_at");
        if (alertRuleId != null) {
            wrapper.eq("alert_rule_id", alertRuleId);
        }
        if (startTime != null) {
            wrapper.ge("fired_at", startTime);
        }
        if (endTime != null) {
            wrapper.le("fired_at", endTime);
        }
        return alertHistoryMapper.selectList(wrapper);
    }

    private void evaluateRule(AlertRule rule) {
        if (isInCooldown(rule)) {
            return;
        }

        List<MetricsSnapshot> recentSnapshots = queryRecentSnapshots(rule);
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

    private List<MetricsSnapshot> queryRecentSnapshots(AlertRule rule) {
        QueryWrapper<MetricsSnapshot> wrapper = new QueryWrapper<MetricsSnapshot>()
                .eq("metric_name", rule.getMetricName())
                .orderByDesc("snapshot_time")
                .last("LIMIT " + rule.getDurationMin());

        if (rule.getMetricTags() != null && !rule.getMetricTags().isEmpty()) {
            wrapper.eq("tags", rule.getMetricTags());
        }

        return metricsSnapshotMapper.selectList(wrapper);
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

        alertHistoryMapper.insert(history);

        alertRuleMapper.update(null, new UpdateWrapper<AlertRule>()
                .eq("id", rule.getId())
                .set("last_fired_at", new Date()));
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
