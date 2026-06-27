package com.ruleforge.executor.v1;

import com.ruleforge.v1.exec.V1FlowRunner;
import com.ruleforge.v1.exec.V1PublishedBundle;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * V1 生产执行端点(V7.7)— executor 跑已发布决策流。
 *
 * <p>{@code POST /v1/exec?flow=} + body{@code {fact}} → {@link V1ResourceProvider} 拉 console bundle
 * → {@link V1FlowRunner#execute} 跑闭包(asset + libraries + ruleFiles)→ 返 {@link V1FlowRunner.FlowResult}
 * (decision / fact / rejected / rejectReason / flags)。
 *
 * <p>跟 console 的 {@code POST /v1/execute}(画布试运行,asset 由前端发)区别:本端点跑**已发布**流
 * (executor 拉 console 冻结的 bundle),是生产执行路径。executor 依赖 core,V1FlowRunner 可用。
 *
 * <p>未发布(bundle 拉不到)→ IllegalArgumentException(返 4xx,提示先去 console 发布)。
 */
@RestController
@RequestMapping("/v1")
public class V1ExecutionController {

    private final V1ResourceProvider resourceProvider;

    public V1ExecutionController(V1ResourceProvider resourceProvider) {
        this.resourceProvider = resourceProvider;
    }

    @PostMapping("/exec")
    public V1FlowRunner.FlowResult exec(@RequestParam("flow") String flow,
                                         @RequestBody(required = false) Map<String, Object> fact) {
        V1PublishedBundle bundle = resourceProvider.fetchBundle(flow);
        if (bundle == null) {
            throw new IllegalArgumentException("决策流未发布或拉取失败 [" + flow + "] — 先在 console 发布");
        }
        return V1FlowRunner.execute(bundle.getAsset(), fact, bundle.getLibraries(), bundle.getRuleFiles());
    }
}
