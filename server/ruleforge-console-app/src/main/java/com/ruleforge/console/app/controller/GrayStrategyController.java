package com.ruleforge.console.app.controller;

import com.ruleforge.decision.entity.GrayStrategy;
import com.ruleforge.decision.service.IGrayStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 灰度策略管理 REST API
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforgeV2.root.path}/gray")
@RequiredArgsConstructor
public class GrayStrategyController {

    private final IGrayStrategyService grayStrategyService;

    @GetMapping("/strategies")
    public ResponseEntity<?> listStrategies(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String packageId) {
        List<GrayStrategy> strategies = grayStrategyService.listStrategies(projectId, packageId);
        return ResponseEntity.ok(strategies);
    }

    @GetMapping("/strategies/{id}")
    public ResponseEntity<?> getStrategy(@PathVariable Long id) {
        // 用 listStrategies + filter 或直接 selectById
        // 这里简单返回，后续可加 getById 到 service
        return ResponseEntity.ok().build();
    }

    @PostMapping("/strategies")
    public ResponseEntity<?> createStrategy(@RequestBody GrayStrategy strategy) {
        strategy.setId(null);
        strategy.setCreateTime(null);
        strategy.setUpdateTime(null);
        GrayStrategy created = grayStrategyService.createStrategy(strategy);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/strategies/{id}")
    public ResponseEntity<?> updateStrategy(@PathVariable Long id, @RequestBody GrayStrategy strategy) {
        strategy.setId(id);
        strategy.setCreateTime(null);
        strategy.setUpdateTime(null);
        GrayStrategy updated = grayStrategyService.updateStrategy(strategy);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/strategies/{id}")
    public ResponseEntity<?> deleteStrategy(@PathVariable Long id) {
        grayStrategyService.deleteStrategy(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/strategies/{id}/toggle")
    public ResponseEntity<?> toggleStrategy(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "enabled 参数必填"));
        }
        grayStrategyService.toggleStrategy(id, enabled);
        return ResponseEntity.ok().build();
    }
}
