package com.ruleforge.executor.v1;

import com.ruleforge.v1.exec.V1PublishedBundle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * V1 资源提供者(V7.7)— executor 从 console 拉已发布决策流的闭包 bundle。
 *
 * <p>镜像老 {@code ExecResourceProvider} 调 console 的模式(拼绝对 URL),但 V1 走专用端点
 * {@code GET /ruleforge/v1/publish/bundle?flow=}。console 返回 {@link V1PublishedBundle}
 * (core 共享契约:{asset, libraries, ruleFiles}),executor 直接喂 {@link
 * com.ruleforge.v1.exec.V1FlowRunner},无需懂 V1 引用解析。
 *
 * <p>MVP 不缓存:每次 exec 拉 console 最新 bundle(发布即生效,无需 syncExec 通知)。
 * 后续若 perf 敏感,加按 flow 缓存 + 发布时 console 推 invalidate。
 *
 * <p>404(未发布)/ 网络/反序列化失败 → 返 null + warn(不抛,由 controller 决策)。
 */
@Slf4j
@Component
public class V1ResourceProvider {

    private static final String BUNDLE_PATH = "/ruleforge/v1/publish/bundle";

    private final RestClient consoleRestClient;

    public V1ResourceProvider(RestClient consoleRestClient) {
        this.consoleRestClient = consoleRestClient;
    }

    /**
     * 拉已发布 bundle。
     *
     * @param flowPath 决策流全路径(同 console 发布时的 flow 参数)
     * @return bundle;未发布 / 拉取失败 → null
     */
    public V1PublishedBundle fetchBundle(String flowPath) {
        try {
            return consoleRestClient.get()
                    .uri(b -> b.path(BUNDLE_PATH).queryParam("flow", flowPath).build())
                    .retrieve()
                    .body(V1PublishedBundle.class);
        } catch (RestClientException e) {
            log.warn("V1ResourceProvider: 拉 console bundle 失败(未发布?)[{}] - {}", flowPath, e.getMessage());
            return null;
        }
    }
}
