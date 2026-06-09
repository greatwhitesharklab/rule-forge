package com.ruleforge.decision.service;

import com.ruleforge.decision.entity.RuleVariableDef;

import java.util.List;
import java.util.Map;

/**
 * 规则变量定义服务接口
 */
public interface IRuleVariableDefService {

    /**
     * 查询所有变量定义
     */
    List<RuleVariableDef> findAll();

    /**
     * 按 clazz 分组查询变量定义
     */
    Map<String, List<RuleVariableDef>> groupByClazz();
}
