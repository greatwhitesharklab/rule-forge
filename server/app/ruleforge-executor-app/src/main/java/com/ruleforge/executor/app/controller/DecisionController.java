package com.ruleforge.executor.app.controller;

import com.ruleforge.decision.dto.DecisionRequest;
import com.ruleforge.decision.dto.DecisionResponse;
import com.ruleforge.decision.service.IDecisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 贷款决策评估 Controller
 * 用于生产环境接收贷款申请系统的决策流调用
 * 仅部署在 executor-app，console-app 不暴露此端点
 */
@Slf4j
@RestController
@RequestMapping("/api/loan")
@RequiredArgsConstructor
public class DecisionController {

    private final IDecisionService decisionService;

    /**
     * 贷款决策评估接口
     *
     * @param request 评估请求，包含 userId, rulePackagePath, flowId
     * @return 决策流执行后的所有参数
     */
    @PostMapping("/evaluate")
    public DecisionResponse evaluate(@Valid @RequestBody DecisionRequest request) {
        return decisionService.evaluate(request);
    }
}
