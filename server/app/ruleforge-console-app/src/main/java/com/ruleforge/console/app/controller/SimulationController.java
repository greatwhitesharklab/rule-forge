package com.ruleforge.console.app.controller;

import com.ruleforge.console.service.SimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仿真 REST API — 配置、执行、对比结果查询
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    /**
     * 启动仿真（异步）
     *
     * POST /simulation/startSimulation
     */
    @PostMapping("/startSimulation")
    public ResponseEntity<Map<String, Object>> startSimulation(@RequestBody Map<String, String> params) {
        String project = params.get("project");
        String packageId = params.get("packageId");
        String files = params.get("files");
        String flowId = params.get("flowId");
        String startTime = params.get("startTime");
        String endTime = params.get("endTime");
        String createdBy = params.getOrDefault("createdBy", "system");

        Long runId = simulationService.startSimulation(project, packageId, files, flowId,
                startTime, endTime, createdBy);

        Map<String, Object> result = new HashMap<>();
        result.put("runId", runId);
        result.put("status", "STARTED");
        return ResponseEntity.ok(result);
    }

    /**
     * 查询仿真进度
     *
     * GET /simulation/simulationProgress?runId=X
     */
    @GetMapping("/simulationProgress")
    public ResponseEntity<Map<String, Object>> getSimulationProgress(@RequestParam Long runId) {
        Map<String, Object> progress = simulationService.getSimulationProgress(runId);
        return ResponseEntity.ok(progress);
    }

    /**
     * 查询仿真对比结果（分页）
     *
     * GET /simulation/simulationResults?runId=X&page=1&size=20
     */
    @GetMapping("/simulationResults")
    public ResponseEntity<Map<String, Object>> listSimulationResults(
            @RequestParam Long runId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Map<String, Object>> results = simulationService.listSimulationResults(runId, page, size);
        Map<String, Object> response = new HashMap<>();
        response.put("results", results);
        response.put("page", page);
        response.put("size", size);
        return ResponseEntity.ok(response);
    }

    /**
     * 查询历史仿真记录（分页）
     *
     * GET /simulation/simulationRuns?rulePackagePath=X&page=1&size=20
     */
    @GetMapping("/simulationRuns")
    public ResponseEntity<Map<String, Object>> listSimulationRuns(
            @RequestParam String rulePackagePath,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Map<String, Object>> runs = simulationService.listSimulationRuns(rulePackagePath, page, size);
        Map<String, Object> response = new HashMap<>();
        response.put("runs", runs);
        response.put("page", page);
        response.put("size", size);
        return ResponseEntity.ok(response);
    }

    /**
     * 查询仿真聚合统计
     *
     * GET /simulation/simulationStats?rulePackagePath=X&startTime=X&endTime=X
     */
    @GetMapping("/simulationStats")
    public ResponseEntity<Map<String, Object>> getSimulationStats(
            @RequestParam String rulePackagePath,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        Map<String, Object> stats = simulationService.getSimulationStats(rulePackagePath, startTime, endTime);
        return ResponseEntity.ok(stats);
    }
}
