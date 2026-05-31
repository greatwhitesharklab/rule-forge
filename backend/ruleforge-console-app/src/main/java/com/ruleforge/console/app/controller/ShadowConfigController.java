package com.ruleforge.console.app.controller;

import com.ruleforge.decision.entity.ShadowConfig;
import com.ruleforge.decision.service.IShadowConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 陪跑配置管理 REST API
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforgeV2.root.path}/shadow")
@RequiredArgsConstructor
public class ShadowConfigController {

    private final IShadowConfigService shadowConfigService;

    @GetMapping("/configs")
    public ResponseEntity<?> listConfigs() {
        List<ShadowConfig> configs = shadowConfigService.listAll();
        return ResponseEntity.ok(configs);
    }

    @GetMapping("/configs/{id}")
    public ResponseEntity<?> getConfig(@PathVariable Long id) {
        ShadowConfig config = shadowConfigService.getById(id);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(config);
    }

    @PostMapping("/configs")
    public ResponseEntity<?> createConfig(@RequestBody ShadowConfig config) {
        config.setId(null);
        ShadowConfig created = shadowConfigService.create(config);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/configs/{id}")
    public ResponseEntity<?> updateConfig(@PathVariable Long id, @RequestBody ShadowConfig config) {
        config.setId(id);
        ShadowConfig updated = shadowConfigService.update(config);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/configs/{id}")
    public ResponseEntity<?> deleteConfig(@PathVariable Long id) {
        shadowConfigService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/configs/{id}/toggle")
    public ResponseEntity<?> toggleConfig(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "enabled 参数必填"));
        }
        shadowConfigService.toggle(id, enabled);
        return ResponseEntity.ok().build();
    }
}
