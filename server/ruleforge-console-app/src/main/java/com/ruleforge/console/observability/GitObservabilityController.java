package com.ruleforge.console.observability;

import com.ruleforge.console.entity.GitDualwriteFailureEntity;
import com.ruleforge.console.exception.NoPermissionException;
import com.ruleforge.console.repository.data.GitDualwriteFailureRepository;
import com.ruleforge.console.service.PermissionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 5.10-C: 暴露 dualWrite 健康状态给 admin.
 *
 * <ul>
 *   <li>{@code GET /ruleforge/git/observability/summary} — 总数 + 1h/24h 计数 + counter 快照</li>
 *   <li>{@code GET /ruleforge/git/observability/recent}   — 最近 N 条失败 (默认 50, 最大 500)</li>
 * </ul>
 *
 * admin 门控:permissionService.isAdmin() 与 RuleForgeRepositoryServiceImpl:216 同款.
 */
@RestController
@RequestMapping("/ruleforge/git/observability")
public class GitObservabilityController {

    private final PermissionService permissionService;
    private final GitDualwriteFailureRepository failureRepository;
    private final MeterRegistry meterRegistry;

    public GitObservabilityController(PermissionService permissionService,
                                      GitDualwriteFailureRepository failureRepository,
                                      MeterRegistry meterRegistry) {
        this.permissionService = permissionService;
        this.failureRepository = failureRepository;
        this.meterRegistry = meterRegistry;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        requireAdmin();
        Date now = new Date();
        Date oneHourAgo = new Date(now.getTime() - 60L * 60_000L);
        Date oneDayAgo = new Date(now.getTime() - 24L * 60L * 60_000L);

        Map<String, Object> body = new HashMap<>();
        body.put("totalFailures", failureRepository.countAll());
        body.put("last1h", failureRepository.countSince(oneHourAgo));
        body.put("last24h", failureRepository.countSince(oneDayAgo));
        body.put("counters", counterSnapshot());
        return body;
    }

    @GetMapping("/recent")
    public List<GitDualwriteFailureEntity> recent(@RequestParam(defaultValue = "50") int limit) {
        requireAdmin();
        int safe = Math.max(1, Math.min(limit, 500));
        return failureRepository.findRecent(safe);
    }

    private void requireAdmin() {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
    }

    private Map<String, Double> counterSnapshot() {
        Map<String, Double> out = new HashMap<>();
        for (String name : new String[]{"ruleforge_git_dualwrite_total",
                "ruleforge_git_dualdelete_total"}) {
            try {
                java.util.Collection<Counter> all = meterRegistry.find(name).counters();
                for (Counter c : all) {
                    String key = name + "{" + c.getId().getTags().stream()
                            .map(t -> t.getKey() + "=" + t.getValue())
                            .reduce((a, b) -> a + "," + b).orElse("") + "}";
                    out.put(key, c.count());
                }
            } catch (MeterNotFoundException ignored) {
                // 没有该 counter,跳过
            }
        }
        return out;
    }
}
