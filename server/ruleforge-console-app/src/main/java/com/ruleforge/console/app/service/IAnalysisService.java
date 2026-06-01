package com.ruleforge.console.app.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 决策日志聚合分析服务
 */
public interface IAnalysisService {

    /**
     * 时间序列聚合 — 趋势图数据
     *
     * @return ECharts 友好格式 {timestamps, volume, successRate, rejectRate, avgLatency}
     */
    Map<String, Object> getFlowLogTimeSeries(Date startTime, Date endTime,
                                              String rulePackagePath, String flowId,
                                              Boolean isGray, String granularity);

    /**
     * 规则包/决策流汇总统计
     */
    List<Map<String, Object>> getPackageFlowSummary(Date startTime, Date endTime);

    /**
     * 拒绝码分布 Top-N
     */
    List<Map<String, Object>> getRejectDistribution(Date startTime, Date endTime,
                                                     String rulePackagePath, int limit);

    /**
     * 规则覆盖率分析 — 热/冷/死规则分类 + 频率分布
     */
    Map<String, Object> getRuleCoverageAnalysis(String rulePackagePath,
                                                 Date startTime, Date endTime);

    /**
     * 规则触发频率排名
     */
    List<Map<String, Object>> getRuleFireFrequency(Date startTime, Date endTime,
                                                    String rulePackagePath);

    /**
     * 偏差检测 — 历史基线 vs 当前窗口
     *
     * @return 异常列表 [{metric, baseline, current, sigmaDelta, severity, direction}]
     */
    List<Map<String, Object>> detectAnomalies(Date currentTime, int baselineDays,
                                               double sigmaThreshold, String rulePackagePath);

    /**
     * 所有规则包路径
     */
    List<String> listPackagePaths();
}
