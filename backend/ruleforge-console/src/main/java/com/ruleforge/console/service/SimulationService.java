package com.ruleforge.console.service;

import java.util.List;
import java.util.Map;

/**
 * 规则仿真服务接口
 *
 * 加载历史决策日志 → 重放到指定规则版本 → 4 维度对比 → 存储结果。
 */
public interface SimulationService {

    /**
     * 启动仿真（异步）
     *
     * @return simulation run ID
     */
    Long startSimulation(String project, String packageId, String files, String flowId,
                         String startTime, String endTime, String createdBy);

    /**
     * 查询仿真进度
     */
    Map<String, Object> getSimulationProgress(Long runId);

    /**
     * 查询仿真的分页对比结果
     */
    List<Map<String, Object>> listSimulationResults(Long runId, int page, int size);

    /**
     * 查询仿真的聚合统计
     */
    Map<String, Object> getSimulationStats(String rulePackagePath, String startTime, String endTime);

    /**
     * 查询历史仿真记录
     */
    List<Map<String, Object>> listSimulationRuns(String rulePackagePath, int page, int size);
}
