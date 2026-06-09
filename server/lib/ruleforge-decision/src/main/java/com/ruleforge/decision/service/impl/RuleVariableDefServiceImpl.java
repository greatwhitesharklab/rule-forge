package com.ruleforge.decision.service.impl;

import com.ruleforge.decision.entity.RuleVariableDef;
import com.ruleforge.decision.repository.DatasourceRepository;
import com.ruleforge.decision.service.IRuleVariableDefService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 规则变量定义服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleVariableDefServiceImpl implements IRuleVariableDefService {

    private final DatasourceRepository datasourceRepository;

    @Override
    public List<RuleVariableDef> findAll() {
        return datasourceRepository.findAllVariableDefs();
    }

    @Override
    public Map<String, List<RuleVariableDef>> groupByClazz() {
        List<RuleVariableDef> allDefs = findAll();
        return allDefs.stream()
                .collect(Collectors.groupingBy(RuleVariableDef::getClazz));
    }
}
