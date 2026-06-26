package com.ruleforge.console.controller.v1;

import com.ruleforge.v1.exec.V1FlowRunner;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V1 决策流执行 REST 端点(V7.2)。
 *
 * <p>POST /{root}/v1/execute — 接收 RuleAsset(画布 JSON)+ 输入 fact,跑 {@link V1FlowRunner}
 * 图遍历执行(Start → RuleSet/DecisionTable/ScoreCard/Decision + exclusiveGateway 分流),
 * 返回最终 decision + 执行后 fact。
 *
 * <p>让 V1 画布从"画 + 存"升级到"画 + 存 + 运行":前端填 fact → POST → 显示 decision +
 * 节点执行轨迹(fact 字段变化)。复用 V7.0 引擎基建(V1FlowRunner 纯静态,生产 EngineContext
 * 由 {@code SpringEnginePluginRegistry} @PostConstruct 装配,无需额外 wiring)。
 *
 * <p>权限:V1 执行复用 console-app 现有 session/权限体系(跟其他 controller 同),MVP 不加额外管控。
 */
@RestController
@RequestMapping("/${ruleforge.root.path}/v1")
public class V1ExecutionController {

    @PostMapping("/execute")
    public V1ExecutionResponse execute(@RequestBody V1ExecutionRequest request) {
        // V7.4.1:libraries(vl/pl/cl/al)优先;无则用 parameters(V7.4-1b 兼容)
        V1FlowRunner.FlowResult result = request.getLibraries() != null
                ? V1FlowRunner.execute(request.getAsset(), request.getFact(), request.getLibraries())
                : V1FlowRunner.execute(request.getAsset(), request.getFact(), request.getParameters());
        return new V1ExecutionResponse(result);
    }
}
