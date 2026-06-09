package com.ruleforge.executor.app.controller;

import com.ruleforge.decision.flow.engine.FlowDefinitionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 决策流定义缓存失效端点(executor 侧)。
 * <p>
 * console BpmnFlowController.saveBpmn / deployBpmn 之后,
 * 调本端点清掉 executor 缓存的 FlowDefinition(IR) — 保证下次 evaluate 拉最新 XML。
 * <p>
 * 协议:POST /{root.path}/flow/invalidate?flowId=xxx
 * <p>
 * 失败处理:console 端 catch + warn,不影响 saveBpmn 自身返回。executor 端找不到 flowId 时也返
 * 200(幂等 invalidate,跟"本来就没缓存"语义对齐)。
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/flow")
@RequiredArgsConstructor
public class FlowDefinitionInvalidateController {

    private final FlowDefinitionRepo flowDefinitionRepo;

    @PostMapping(value = "/invalidate")
    public Map<String, Object> invalidate(@RequestParam String flowId) {
        long t0 = System.currentTimeMillis();
        flowDefinitionRepo.invalidate(flowId);
        log.info("[FLOW-INVALIDATE] flowId={} took={}ms", flowId, System.currentTimeMillis() - t0);
        return Map.of("result", true, "flowId", flowId);
    }
}
