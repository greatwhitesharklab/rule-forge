package com.ruleforge.console.app.repository.data;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ruleforge.console.app.entity.AlertHistory;
import com.ruleforge.console.app.entity.AlertRule;
import com.ruleforge.console.app.entity.MetricsSnapshot;
import com.ruleforge.console.app.mapper.AlertHistoryMapper;
import com.ruleforge.console.app.mapper.AlertRuleMapper;
import com.ruleforge.console.app.mapper.MetricsSnapshotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonitoringRepositoryImpl implements MonitoringRepository {

    private final MetricsSnapshotMapper metricsSnapshotMapper;
    private final AlertRuleMapper alertRuleMapper;
    private final AlertHistoryMapper alertHistoryMapper;

    // ===== MetricsSnapshot =====

    @Override
    public List<MetricsSnapshot> findMetricsByMetricName(String metricName, Date startTime, Date endTime, String tags) {
        QueryWrapper<MetricsSnapshot> wrapper = new QueryWrapper<MetricsSnapshot>()
                .eq("metric_name", metricName)
                .orderByAsc("snapshot_time");

        if (startTime != null) {
            wrapper.ge("snapshot_time", startTime);
        }
        if (endTime != null) {
            wrapper.le("snapshot_time", endTime);
        }
        if (tags != null && !tags.isEmpty()) {
            wrapper.eq("tags", tags);
        }

        return metricsSnapshotMapper.selectList(wrapper);
    }

    @Override
    public List<MetricsSnapshot> findDistinctTags() {
        QueryWrapper<MetricsSnapshot> wrapper = new QueryWrapper<MetricsSnapshot>()
                .select("DISTINCT tags")
                .isNotNull("tags");
        return metricsSnapshotMapper.selectList(wrapper);
    }

    @Override
    public List<MetricsSnapshot> findLatestMetrics(String metricName, int limit, String tags) {
        QueryWrapper<MetricsSnapshot> wrapper = new QueryWrapper<MetricsSnapshot>()
                .eq("metric_name", metricName)
                .orderByDesc("snapshot_time")
                .last("LIMIT " + limit);

        if (tags != null && !tags.isEmpty()) {
            wrapper.eq("tags", tags);
        }

        return metricsSnapshotMapper.selectList(wrapper);
    }

    @Override
    public MetricsSnapshot insertSnapshot(MetricsSnapshot entity) {
        metricsSnapshotMapper.insert(entity);
        return entity;
    }

    // ===== AlertRule =====

    @Override
    public List<AlertRule> findEnabledAlertRules() {
        return alertRuleMapper.selectList(
                new QueryWrapper<AlertRule>().eq("enabled", 1)
        );
    }

    @Override
    public List<AlertRule> findAllAlertRules() {
        return alertRuleMapper.selectList(
                new QueryWrapper<AlertRule>().orderByDesc("created_at")
        );
    }

    @Override
    public AlertRule findAlertRuleById(Long id) {
        return alertRuleMapper.selectById(id);
    }

    @Override
    public AlertRule insertAlertRule(AlertRule entity) {
        alertRuleMapper.insert(entity);
        return entity;
    }

    @Override
    public void updateAlertRule(AlertRule entity) {
        alertRuleMapper.updateById(entity);
    }

    @Override
    public void updateAlertRuleLastFired(Long id, Date lastFired) {
        alertRuleMapper.update(null, new UpdateWrapper<AlertRule>()
                .eq("id", id)
                .set("last_fired_at", lastFired));
    }

    @Override
    public void deleteAlertRule(Long id) {
        alertRuleMapper.deleteById(id);
    }

    // ===== AlertHistory =====

    @Override
    public List<AlertHistory> findAlertHistory(Long alertRuleId, Date startTime, Date endTime) {
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

    @Override
    public AlertHistory insertAlertHistory(AlertHistory entity) {
        alertHistoryMapper.insert(entity);
        return entity;
    }
}
