package com.ruleforge.console.app.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ruleforge.console.app.entity.MetricsSnapshot;
import com.ruleforge.console.app.repository.data.MonitoringRepository;
import com.ruleforge.console.app.service.IAlertService;
import com.ruleforge.console.app.service.IMetricsAggregationService;
import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsAggregationServiceImpl implements IMetricsAggregationService {

    private final MeterRegistry meterRegistry;
    private final MonitoringRepository monitoringRepository;
    private final IAlertService alertService;

    @Override
    @Scheduled(fixedRateString = "${ruleforge.monitoring.aggregation-interval-ms:60000}")
    public void aggregateAndSnapshot() {
        List<MetricsSnapshot> snapshots = new ArrayList<>();
        Date snapshotTime = new Date();

        for (Meter meter : meterRegistry.getMeters()) {
            if (meter instanceof Timer timer) {
                MetricsSnapshot snapshot = buildTimerSnapshot(timer, snapshotTime);
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            } else if (meter instanceof Counter counter) {
                MetricsSnapshot snapshot = buildCounterSnapshot(counter, snapshotTime);
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            } else if (meter instanceof Gauge gauge) {
                MetricsSnapshot snapshot = buildGaugeSnapshot(gauge, snapshotTime);
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            }
        }

        if (snapshots.isEmpty()) {
            return;
        }

        try {
            for (MetricsSnapshot snapshot : snapshots) {
                monitoringRepository.insertSnapshot(snapshot);
            }
            log.debug("聚合写入 {} 条指标快照", snapshots.size());
        } catch (Exception e) {
            log.error("写入指标快照失败", e);
            return;
        }

        // Reset counters/timers after snapshot so next aggregation window starts fresh.
        // Don't use meterRegistry.clear() as it destroys histogram percentile state.
        for (Meter meter : meterRegistry.getMeters()) {
            if (meter instanceof Counter counter) {
                meterRegistry.remove(counter);
            }
            if (meter instanceof Timer timer) {
                meterRegistry.remove(timer);
            }
        }

        try {
            alertService.evaluateAlerts();
        } catch (Exception e) {
            log.error("告警评估失败", e);
        }
    }

    private MetricsSnapshot buildTimerSnapshot(Timer timer, Date snapshotTime) {
        long count = timer.count();
        if (count == 0) {
            return null;
        }

        io.micrometer.core.instrument.distribution.HistogramSnapshot hist = timer.takeSnapshot();
        io.micrometer.core.instrument.distribution.ValueAtPercentile[] percentiles = hist.percentileValues();

        MetricsSnapshot snapshot = new MetricsSnapshot();
        snapshot.setMetricName(timer.getId().getName());
        snapshot.setMetricType("TIMER");
        snapshot.setTags(serializeTags(timer.getId()));
        snapshot.setSnapshotTime(snapshotTime);
        snapshot.setCountVal(count);
        snapshot.setTotalMs(hist.total(TimeUnit.MILLISECONDS));
        snapshot.setMeanMs(hist.mean(TimeUnit.MILLISECONDS));
        snapshot.setMaxMs((long) hist.max(TimeUnit.MILLISECONDS));

        // Extract percentiles from configured percentile values
        for (io.micrometer.core.instrument.distribution.ValueAtPercentile vap : percentiles) {
            double valueMs = vap.value(TimeUnit.MILLISECONDS);
            if (Math.abs(vap.percentile() - 0.5) < 0.001) snapshot.setP50Ms((long) valueMs);
            else if (Math.abs(vap.percentile() - 0.95) < 0.001) snapshot.setP95Ms((long) valueMs);
            else if (Math.abs(vap.percentile() - 0.99) < 0.001) snapshot.setP99Ms((long) valueMs);
        }

        // Fallback: if no percentiles configured, estimate from mean/max
        if (snapshot.getP50Ms() == null && count > 0) {
            long mean = (long) hist.mean(TimeUnit.MILLISECONDS);
            long max = (long) hist.max(TimeUnit.MILLISECONDS);
            snapshot.setP50Ms(mean);
            snapshot.setP95Ms((long) (mean + (max - mean) * 0.5));
            snapshot.setP99Ms(max);
        }
        return snapshot;
    }

    private MetricsSnapshot buildCounterSnapshot(Counter counter, Date snapshotTime) {
        double count = counter.count();
        if (count == 0) {
            return null;
        }

        MetricsSnapshot snapshot = new MetricsSnapshot();
        snapshot.setMetricName(counter.getId().getName());
        snapshot.setMetricType("COUNTER");
        snapshot.setTags(serializeTags(counter.getId()));
        snapshot.setSnapshotTime(snapshotTime);
        snapshot.setCountVal((long) count);
        return snapshot;
    }

    private MetricsSnapshot buildGaugeSnapshot(Gauge gauge, Date snapshotTime) {
        double value = gauge.value();

        MetricsSnapshot snapshot = new MetricsSnapshot();
        snapshot.setMetricName(gauge.getId().getName());
        snapshot.setMetricType("GAUGE");
        snapshot.setTags(serializeTags(gauge.getId()));
        snapshot.setSnapshotTime(snapshotTime);
        snapshot.setGaugeVal(value);
        return snapshot;
    }

    private String serializeTags(Meter.Id id) {
        List<Tag> tags = id.getTags();
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        Map<String, String> tagMap = new LinkedHashMap<>();
        for (Tag tag : tags) {
            tagMap.put(tag.getKey(), tag.getValue());
        }
        return JSON.toJSONString(tagMap);
    }
}
