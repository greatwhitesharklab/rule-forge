package com.ruleforge.decision.service;

import com.ruleforge.decision.dto.ShadowComparisonResult;
import com.ruleforge.decision.dto.ShadowDivergenceStats;
import com.ruleforge.decision.entity.ShadowComparison;

import java.util.List;

/**
 * 陪跑结果对比服务接口
 */
public interface IShadowComparisonService {

    /**
     * 自动对比主流程和陪跑流程结果，并保存对比记录
     * 在陪跑日志保存完成后自动调用
     *
     * @param mainFlowLogId   主决策流日志 ID
     * @param shadowFlowLogId 陪跑决策流日志 ID
     */
    void compareAndSave(Long mainFlowLogId, Long shadowFlowLogId);

    /**
     * 按主流程日志 ID 获取对比结果
     */
    ShadowComparison getByMainFlowLogId(Long mainFlowLogId);

    /**
     * 按规则包路径分页查询对比列表
     */
    List<ShadowComparison> listByPackage(String rulePackagePath, String startTime, String endTime, int page, int size);

    /**
     * 获取差异统计摘要
     */
    ShadowDivergenceStats getDivergenceStats(String rulePackagePath, String startTime, String endTime);
}
