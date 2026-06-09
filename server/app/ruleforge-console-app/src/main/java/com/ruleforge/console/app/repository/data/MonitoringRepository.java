package com.ruleforge.console.app.repository.data;

import com.ruleforge.console.app.entity.AlertHistory;
import com.ruleforge.console.app.entity.AlertRule;
import com.ruleforge.console.app.entity.MetricsSnapshot;

import java.util.Date;
import java.util.List;

/**
 * Monitoring data access repository.
 * Encapsulates all DB operations for MetricsSnapshot, AlertRule, and AlertHistory.
 */
public interface MonitoringRepository {

    // ===== MetricsSnapshot =====

    /**
     * Query metrics by metric name, optionally filtered by time range and tags.
     * Ordered by snapshot_time asc.
     */
    List<MetricsSnapshot> findMetricsByMetricName(String metricName, Date startTime, Date endTime, String tags);

    /**
     * Select DISTINCT tags where tags is not null.
     */
    List<MetricsSnapshot> findDistinctTags();

    /**
     * Find latest metrics for a given metric name, limited to the specified count.
     * Ordered by snapshot_time desc.
     */
    List<MetricsSnapshot> findLatestMetrics(String metricName, int limit, String tags);

    /**
     * Insert a single metrics snapshot.
     */
    MetricsSnapshot insertSnapshot(MetricsSnapshot entity);

    // ===== AlertRule =====

    /**
     * Find all enabled alert rules.
     */
    List<AlertRule> findEnabledAlertRules();

    /**
     * Find all alert rules ordered by created_at desc.
     */
    List<AlertRule> findAllAlertRules();

    /**
     * Find an alert rule by its id.
     */
    AlertRule findAlertRuleById(Long id);

    /**
     * Insert a new alert rule.
     */
    AlertRule insertAlertRule(AlertRule entity);

    /**
     * Update an existing alert rule.
     */
    void updateAlertRule(AlertRule entity);

    /**
     * Update only the last_fired_at field for the given alert rule id.
     */
    void updateAlertRuleLastFired(Long id, Date lastFired);

    /**
     * Delete an alert rule by id.
     */
    void deleteAlertRule(Long id);

    // ===== AlertHistory =====

    /**
     * Find alert history, optionally filtered by alertRuleId and time range.
     * Ordered by fired_at desc.
     */
    List<AlertHistory> findAlertHistory(Long alertRuleId, Date startTime, Date endTime);

    /**
     * Insert an alert history record.
     */
    AlertHistory insertAlertHistory(AlertHistory entity);
}
