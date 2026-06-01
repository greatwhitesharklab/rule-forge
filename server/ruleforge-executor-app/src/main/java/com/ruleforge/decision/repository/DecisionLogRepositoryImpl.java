package com.ruleforge.decision.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.decision.entity.DecisionFlowLog;
import com.ruleforge.decision.entity.DecisionFlowParams;
import com.ruleforge.decision.entity.DecisionMessageLog;
import com.ruleforge.decision.entity.DecisionNodeLog;
import com.ruleforge.decision.entity.DecisionRuleLog;
import com.ruleforge.decision.entity.ShadowFlowLog;
import com.ruleforge.decision.entity.ShadowFlowParams;
import com.ruleforge.decision.entity.ShadowMessageLog;
import com.ruleforge.decision.entity.ShadowNodeLog;
import com.ruleforge.decision.entity.ShadowRuleLog;
import com.ruleforge.decision.mapper.DecisionFlowLogMapper;
import com.ruleforge.decision.mapper.DecisionFlowParamsMapper;
import com.ruleforge.decision.mapper.DecisionMessageLogMapper;
import com.ruleforge.decision.mapper.DecisionNodeLogMapper;
import com.ruleforge.decision.mapper.DecisionRuleLogMapper;
import com.ruleforge.decision.mapper.ShadowFlowLogMapper;
import com.ruleforge.decision.mapper.ShadowFlowParamsMapper;
import com.ruleforge.decision.mapper.ShadowMessageLogMapper;
import com.ruleforge.decision.mapper.ShadowNodeLogMapper;
import com.ruleforge.decision.mapper.ShadowRuleLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DecisionLogRepositoryImpl implements DecisionLogRepository {

    private final DecisionFlowLogMapper flowLogMapper;
    private final DecisionFlowParamsMapper flowParamsMapper;
    private final DecisionNodeLogMapper nodeLogMapper;
    private final DecisionRuleLogMapper ruleLogMapper;
    private final DecisionMessageLogMapper messageLogMapper;
    private final ShadowFlowLogMapper shadowFlowLogMapper;
    private final ShadowFlowParamsMapper shadowFlowParamsMapper;
    private final ShadowNodeLogMapper shadowNodeLogMapper;
    private final ShadowRuleLogMapper shadowRuleLogMapper;
    private final ShadowMessageLogMapper shadowMessageLogMapper;

    // ===== Query methods (for shadow comparison) =====

    @Override
    public DecisionFlowLog findFlowLogById(Long id) {
        return flowLogMapper.selectById(id);
    }

    @Override
    public DecisionFlowParams findFlowParamsByFlowLogId(Long flowLogId) {
        LambdaQueryWrapper<DecisionFlowParams> wrapper = new LambdaQueryWrapper<DecisionFlowParams>()
                .eq(DecisionFlowParams::getFlowLogId, flowLogId)
                .last("LIMIT 1");
        return flowParamsMapper.selectOne(wrapper);
    }

    @Override
    public ShadowFlowLog findShadowFlowLogByMainFlowLogId(Long mainFlowLogId) {
        LambdaQueryWrapper<ShadowFlowLog> wrapper = new LambdaQueryWrapper<ShadowFlowLog>()
                .eq(ShadowFlowLog::getMainFlowLogId, mainFlowLogId)
                .orderByDesc(ShadowFlowLog::getId)
                .last("LIMIT 1");
        return shadowFlowLogMapper.selectOne(wrapper);
    }

    @Override
    public ShadowFlowParams findShadowFlowParamsByFlowLogId(Long flowLogId) {
        LambdaQueryWrapper<ShadowFlowParams> wrapper = new LambdaQueryWrapper<ShadowFlowParams>()
                .eq(ShadowFlowParams::getFlowLogId, flowLogId)
                .last("LIMIT 1");
        return shadowFlowParamsMapper.selectOne(wrapper);
    }

    @Override
    public List<DecisionRuleLog> findRuleLogsByFlowLogId(Long flowLogId) {
        LambdaQueryWrapper<DecisionRuleLog> wrapper = new LambdaQueryWrapper<DecisionRuleLog>()
                .eq(DecisionRuleLog::getFlowLogId, flowLogId);
        return ruleLogMapper.selectList(wrapper);
    }

    @Override
    public List<ShadowRuleLog> findShadowRuleLogsByFlowLogId(Long flowLogId) {
        LambdaQueryWrapper<ShadowRuleLog> wrapper = new LambdaQueryWrapper<ShadowRuleLog>()
                .eq(ShadowRuleLog::getFlowLogId, flowLogId);
        return shadowRuleLogMapper.selectList(wrapper);
    }

    @Override
    public List<DecisionFlowLog> findFlowLogsByPackageAndTimeRange(String rulePackagePath, String startTime, String endTime, int limit) {
        LambdaQueryWrapper<DecisionFlowLog> wrapper = new LambdaQueryWrapper<DecisionFlowLog>()
                .eq(DecisionFlowLog::getRulePackagePath, rulePackagePath)
                .ge(startTime != null, DecisionFlowLog::getCreatedAt, startTime)
                .le(endTime != null, DecisionFlowLog::getCreatedAt, endTime)
                .orderByDesc(DecisionFlowLog::getId)
                .last("LIMIT " + limit);
        return flowLogMapper.selectList(wrapper);
    }

    // ===== Decision logs =====

    @Override
    public DecisionFlowLog insertFlowLog(DecisionFlowLog entity) {
        flowLogMapper.insert(entity);
        return entity;
    }

    @Override
    public void insertFlowParams(DecisionFlowParams entity) {
        flowParamsMapper.insert(entity);
    }

    @Override
    public void insertNodeLog(DecisionNodeLog entity) {
        nodeLogMapper.insert(entity);
    }

    @Override
    public void insertRuleLog(DecisionRuleLog entity) {
        ruleLogMapper.insert(entity);
    }

    @Override
    public void insertMessageLog(DecisionMessageLog entity) {
        messageLogMapper.insert(entity);
    }

    @Override
    public void batchInsertMessageLogs(List<DecisionMessageLog> entities) {
        for (DecisionMessageLog entity : entities) {
            messageLogMapper.insert(entity);
        }
    }

    @Override
    public void batchInsertRuleLogs(List<DecisionRuleLog> entities) {
        for (DecisionRuleLog entity : entities) {
            ruleLogMapper.insert(entity);
        }
    }

    @Override
    public void batchInsertNodeLogs(List<DecisionNodeLog> entities) {
        for (DecisionNodeLog entity : entities) {
            nodeLogMapper.insert(entity);
        }
    }

    // ===== Shadow decision logs =====

    @Override
    public ShadowFlowLog insertShadowFlowLog(ShadowFlowLog entity) {
        shadowFlowLogMapper.insert(entity);
        return entity;
    }

    @Override
    public void insertShadowFlowParams(ShadowFlowParams entity) {
        shadowFlowParamsMapper.insert(entity);
    }

    @Override
    public void insertShadowNodeLog(ShadowNodeLog entity) {
        shadowNodeLogMapper.insert(entity);
    }

    @Override
    public void insertShadowRuleLog(ShadowRuleLog entity) {
        shadowRuleLogMapper.insert(entity);
    }

    @Override
    public void insertShadowMessageLog(ShadowMessageLog entity) {
        shadowMessageLogMapper.insert(entity);
    }

    @Override
    public void batchInsertShadowMessageLogs(List<ShadowMessageLog> entities) {
        for (ShadowMessageLog entity : entities) {
            shadowMessageLogMapper.insert(entity);
        }
    }

    @Override
    public void batchInsertShadowRuleLogs(List<ShadowRuleLog> entities) {
        for (ShadowRuleLog entity : entities) {
            shadowRuleLogMapper.insert(entity);
        }
    }

    @Override
    public void batchInsertShadowNodeLogs(List<ShadowNodeLog> entities) {
        for (ShadowNodeLog entity : entities) {
            shadowNodeLogMapper.insert(entity);
        }
    }
}
