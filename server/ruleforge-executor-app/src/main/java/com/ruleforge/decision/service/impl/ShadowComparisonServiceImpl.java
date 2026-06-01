package com.ruleforge.decision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.decision.dto.ShadowComparisonResult;
import com.ruleforge.decision.dto.ShadowDivergenceStats;
import com.ruleforge.decision.entity.*;
import com.ruleforge.decision.mapper.ShadowComparisonMapper;
import com.ruleforge.decision.repository.DecisionLogRepository;
import com.ruleforge.decision.service.IShadowComparisonService;
import com.ruleforge.decision.util.ComparisonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 陪跑结果对比服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowComparisonServiceImpl implements IShadowComparisonService {

    private final DecisionLogRepository decisionLogRepository;
    private final ShadowComparisonMapper shadowComparisonMapper;

    @Override
    public void compareAndSave(Long mainFlowLogId, Long shadowFlowLogId) {
        try {
            // 1. 加载主日志
            DecisionFlowLog mainLog = decisionLogRepository.findFlowLogById(mainFlowLogId);
            if (mainLog == null) {
                log.warn("陪跑对比跳过: 主日志不存在, mainFlowLogId={}", mainFlowLogId);
                return;
            }

            // 2. 加载陪跑日志
            ShadowFlowLog shadowLog = decisionLogRepository.findShadowFlowLogByMainFlowLogId(mainFlowLogId);
            if (shadowLog == null) {
                log.warn("陪跑对比跳过: 陪跑日志不存在, mainFlowLogId={}", mainFlowLogId);
                return;
            }

            // 3. 加载参数（output_params）
            DecisionFlowParams mainParams = decisionLogRepository.findFlowParamsByFlowLogId(mainFlowLogId);
            ShadowFlowParams shadowParams = decisionLogRepository.findShadowFlowParamsByFlowLogId(shadowLog.getId());

            // 4. 加载规则日志
            List<DecisionRuleLog> mainRules = decisionLogRepository.findRuleLogsByFlowLogId(mainFlowLogId);
            List<ShadowRuleLog> shadowRules = decisionLogRepository.findShadowRuleLogsByFlowLogId(shadowLog.getId());

            // 5. 执行对比
            ShadowComparison comparison = doCompare(mainLog, shadowLog, mainParams, shadowParams, mainRules, shadowRules);

            // 6. 保存对比记录
            shadowComparisonMapper.insert(comparison);
            log.info("陪跑对比完成: mainFlowLogId={}, shadowFlowLogId={}, hasDivergence={}, severity={}",
                    mainFlowLogId, shadowFlowLogId, comparison.getHasDivergence(), comparison.getDivergenceSeverity());

        } catch (Exception e) {
            log.error("陪跑对比失败: mainFlowLogId={}, shadowFlowLogId={}", mainFlowLogId, shadowFlowLogId, e);
        }
    }

    @Override
    public ShadowComparison getByMainFlowLogId(Long mainFlowLogId) {
        LambdaQueryWrapper<ShadowComparison> wrapper = new LambdaQueryWrapper<ShadowComparison>()
                .eq(ShadowComparison::getMainFlowLogId, mainFlowLogId)
                .orderByDesc(ShadowComparison::getId)
                .last("LIMIT 1");
        return shadowComparisonMapper.selectOne(wrapper);
    }

    @Override
    public List<ShadowComparison> listByPackage(String rulePackagePath, String startTime, String endTime, int page, int size) {
        LambdaQueryWrapper<ShadowComparison> wrapper = new LambdaQueryWrapper<ShadowComparison>()
                .eq(ShadowComparison::getRulePackagePath, rulePackagePath)
                .ge(startTime != null, ShadowComparison::getCreatedAt, startTime)
                .le(endTime != null, ShadowComparison::getCreatedAt, endTime)
                .orderByDesc(ShadowComparison::getId);

        // 简单分页
        wrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        return shadowComparisonMapper.selectList(wrapper);
    }

    @Override
    public ShadowDivergenceStats getDivergenceStats(String rulePackagePath, String startTime, String endTime) {
        LambdaQueryWrapper<ShadowComparison> wrapper = new LambdaQueryWrapper<ShadowComparison>()
                .eq(ShadowComparison::getRulePackagePath, rulePackagePath)
                .ge(startTime != null, ShadowComparison::getCreatedAt, startTime)
                .le(endTime != null, ShadowComparison::getCreatedAt, endTime);

        List<ShadowComparison> comparisons = shadowComparisonMapper.selectList(wrapper);

        ShadowDivergenceStats stats = new ShadowDivergenceStats();
        stats.setRulePackagePath(rulePackagePath);
        stats.setTotalComparisons(comparisons.size());

        int totalDivergent = 0;
        int statusDivergent = 0;
        int resultDivergent = 0;
        int highCount = 0;
        int mediumCount = 0;
        int lowCount = 0;

        for (ShadowComparison c : comparisons) {
            if (Boolean.TRUE.equals(c.getHasDivergence())) {
                totalDivergent++;
            }
            if (!Boolean.TRUE.equals(c.getStatusMatch())) {
                statusDivergent++;
            }
            if (!Boolean.TRUE.equals(c.getResultMatch())) {
                resultDivergent++;
            }
            String severity = c.getDivergenceSeverity();
            if ("HIGH".equals(severity)) highCount++;
            else if ("MEDIUM".equals(severity)) mediumCount++;
            else if ("LOW".equals(severity)) lowCount++;
        }

        stats.setTotalDivergent(totalDivergent);
        stats.setStatusDivergent(statusDivergent);
        stats.setResultDivergent(resultDivergent);
        stats.setHighSeverityCount(highCount);
        stats.setMediumSeverityCount(mediumCount);
        stats.setLowSeverityCount(lowCount);
        stats.setDivergenceRate(comparisons.isEmpty() ? 0 :
                Math.round((double) totalDivergent / comparisons.size() * 10000.0) / 100.0);

        return stats;
    }

    // ===== 内部对比方法 =====

    private ShadowComparison doCompare(DecisionFlowLog mainLog, ShadowFlowLog shadowLog,
                                       DecisionFlowParams mainParams, ShadowFlowParams shadowParams,
                                       List<DecisionRuleLog> mainRules, List<ShadowRuleLog> shadowRules) {
        ShadowComparison comparison = new ShadowComparison();
        comparison.setMainFlowLogId(mainLog.getId());
        comparison.setShadowFlowLogId(shadowLog.getId());
        comparison.setUserId(mainLog.getUserId());
        comparison.setOrderNo(mainLog.getOrderNo());
        comparison.setRulePackagePath(mainLog.getRulePackagePath());
        comparison.setMainTotalTimeMs(mainLog.getTotalTimeMs());
        comparison.setShadowTotalTimeMs(shadowLog.getTotalTimeMs());

        // 1. 执行状态对比
        boolean statusMatch = ComparisonUtils.nullSafeEquals(
                mainLog.getExecutionStatus(), shadowLog.getExecutionStatus());
        comparison.setStatusMatch(statusMatch);
        comparison.setMainExecutionStatus(mainLog.getExecutionStatus());
        comparison.setShadowExecutionStatus(shadowLog.getExecutionStatus());

        // 2. 决策结果对比
        boolean resultMatch = ComparisonUtils.nullSafeEquals(
                mainLog.getRejectCode(), shadowLog.getRejectCode());
        comparison.setResultMatch(resultMatch);
        comparison.setMainRejectCode(mainLog.getRejectCode());
        comparison.setShadowRejectCode(shadowLog.getRejectCode());

        // 3. 输出字段对比
        String outputDivergence = ComparisonUtils.compareOutputParams(
                mainParams != null ? mainParams.getOutputParams() : null,
                shadowParams != null ? shadowParams.getOutputParams() : null);
        comparison.setOutputDivergence(outputDivergence);
        boolean hasOutputDiv = ComparisonUtils.hasOutputDivergence(outputDivergence);

        // 4. 规则执行对比
        Set<String> mainRuleNames = mainRules.stream().map(DecisionRuleLog::getRuleName).collect(Collectors.toSet());
        Set<String> shadowRuleNames = shadowRules.stream().map(ShadowRuleLog::getRuleName).collect(Collectors.toSet());
        String ruleDivergence = ComparisonUtils.compareRules(mainRuleNames, shadowRuleNames);
        comparison.setRuleDivergence(ruleDivergence);
        boolean hasRuleDiv = ComparisonUtils.hasRuleDivergence(ruleDivergence);

        // 5. 判定严重度和是否有差异
        boolean hasDivergence = !statusMatch || !resultMatch || hasOutputDiv || hasRuleDiv;
        comparison.setHasDivergence(hasDivergence);
        comparison.setDivergenceSeverity(ComparisonUtils.calculateSeverity(statusMatch, resultMatch, hasOutputDiv, hasRuleDiv));

        return comparison;
    }
}
