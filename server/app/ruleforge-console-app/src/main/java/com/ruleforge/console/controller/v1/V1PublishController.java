package com.ruleforge.console.controller.v1;

import com.ruleforge.console.app.v1.V1PublishService;
import com.ruleforge.console.app.v1.V1PublishedBundle;
import com.ruleforge.console.model.User;
import com.ruleforge.console.util.EnvironmentUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * V1 原生发布 REST 端点(V7.6)— 替代老 .rp 知识包发布管线。
 *
 * <ul>
 *   <li>{@code POST /v1/publish?flow=} — 发布决策流(draft→published):冻结闭包 bundle 入库 + git tag。</li>
 *   <li>{@code GET /v1/publish/bundle?flow=} — 取已发布 bundle(PR2 executor 拉,PR1 闭环测试用)。</li>
 *   <li>{@code GET /v1/publish/status?flow=} — 查发布状态(draft / published + 当前版本)。</li>
 * </ul>
 *
 * <p>无审批/影子/批测(砍老管线),直接发布。权限复用 console 现有 session(跟其他 controller 同)。
 */
@RestController
@RequestMapping("/${ruleforge.root.path}/v1/publish")
public class V1PublishController {

    private final V1PublishService publishService;
    private final EnvironmentUtils environmentUtils;

    public V1PublishController(V1PublishService publishService, EnvironmentUtils environmentUtils) {
        this.publishService = publishService;
        this.environmentUtils = environmentUtils;
    }

    /** 发布决策流:解析闭包 → 冻结 bundle → 记 rf_v1_publish → git tag。返回新版本号。 */
    @PostMapping
    public V1PublishService.PublishResult publish(@RequestParam("flow") String flow) throws Exception {
        User user = environmentUtils.getLoginUser(null);
        return publishService.publish(flow, user != null ? user.getUsername() : null);
    }

    /** 取已发布 bundle。未发布返 404(executor/闭环测试用)。 */
    @GetMapping("/bundle")
    public ResponseEntity<V1PublishedBundle> bundle(@RequestParam("flow") String flow) {
        V1PublishedBundle b = publishService.getPublishedBundle(flow);
        if (b == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(b);
    }

    /** 查发布状态:draft(未发布)/ published(含当前版本号 + 发布时间)。 */
    @GetMapping("/status")
    public V1PublishService.PublishStatus status(@RequestParam("flow") String flow) {
        return publishService.status(flow);
    }
}
