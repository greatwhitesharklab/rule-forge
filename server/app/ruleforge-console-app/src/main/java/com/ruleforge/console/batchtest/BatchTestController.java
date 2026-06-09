package com.ruleforge.console.batchtest;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.entity.BatchTestRowEntity;
import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import com.ruleforge.console.app.mapper.BatchTestRowMapper;
import com.ruleforge.console.app.mapper.BatchTestSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BatchTest REST API(V5.8.0+)
 *
 * 端点:
 *   POST   /ruleforge/batchtest/start                    — 启动 session(JSON)
 *   POST   /ruleforge/batchtest/start-with-file          — 启动 session(multipart,v5.8.4)
 *   GET    /ruleforge/batchtest/sessions/{id}/progress   — 轮询进度
 *   GET    /ruleforge/batchtest/sessions/{id}/results    — 拉行结果(分页)
 *   GET    /ruleforge/batchtest/sessions                 — 列历史 session
 *
 * V5.8.4 状态:
 *   - 3 个 mode 全部支持(FLOW+FILE / FLOW+DATASOURCE / DATASOURCE+FILE)
 *   - /start-with-file 走 multipart,file 必填,config 是 JSON 字符串
 *     (multipart 不能跟 application/json 一起用,所以 config 用独立 part)
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/batchtest")
@RequiredArgsConstructor
public class BatchTestController {

    private final BatchTestOrchestrator orchestrator;
    private final BatchTestSessionMapper sessionMapper;
    private final BatchTestRowMapper rowMapper;
    private final ObjectMapper objectMapper;

    /**
     * 启动一次批量测试
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestBody StartBatchTestRequest req) {
        try {
            Long sessionId = orchestrator.startBatchTest(req);
            Map<String, Object> resp = new HashMap<>();
            resp.put("sessionId", sessionId);
            resp.put("status", BatchTestSessionEntity.STATUS_RUNNING);
            resp.put("subjectType", req.subjectType());
            resp.put("inputSourceType", req.inputSourceType());
            return ResponseEntity.ok(resp);
        } catch (UnsupportedOperationException e) {
            // V5.8.0 暂未实现的 mode
            log.warn("BatchTest start refused: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("BatchTest start invalid: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * v5.8.4 新:multipart 启动,Excel 文件 + JSON config
     *
     *   curl -F file=@test.xlsx -F config='{"subjectType":"DATASOURCE",...}' /ruleforge/batchtest/start-with-file
     *
     * 错误码:
     *   200 成功 → {sessionId, status, subjectType, inputSourceType}
     *   400 config 解析失败 / Excel 缺必填列 / Excel 行数 0
     *   501 暂未支持的 mode 组合
     *   500 其他未预期错误
     */
    @PostMapping("/start-with-file")
    public ResponseEntity<Map<String, Object>> startWithFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("config") String configJson) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "file 必填且不能为空"));
            }
            StartBatchTestRequest req;
            try {
                req = objectMapper.readValue(configJson, StartBatchTestRequest.class);
            } catch (Exception jsonEx) {
                log.warn("BatchTest start-with-file config JSON 解析失败: {}", jsonEx.getMessage());
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "config 解析失败: " + jsonEx.getMessage()));
            }

            Long sessionId = orchestrator.startBatchTestFromExcel(file, req);
            Map<String, Object> resp = new HashMap<>();
            resp.put("sessionId", sessionId);
            resp.put("status", BatchTestSessionEntity.STATUS_RUNNING);
            resp.put("subjectType", req.subjectType());
            resp.put("inputSourceType", req.inputSourceType());
            return ResponseEntity.ok(resp);
        } catch (UnsupportedOperationException e) {
            log.warn("BatchTest start-with-file refused: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("BatchTest start-with-file invalid: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("BatchTest start-with-file 异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Excel 处理失败: " + e.getMessage()));
        }
    }

    /**
     * 轮询进度(Vue BatchTestDialog 每 1-2s 调一次)
     */
    @GetMapping("/sessions/{id}/progress")
    public ResponseEntity<Map<String, Object>> progress(@PathVariable("id") Long sessionId) {
        Map<String, Object> progress = orchestrator.getProgress(sessionId);
        if ("NOT_FOUND".equals(progress.get("status"))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(progress);
    }

    /**
     * 拉行结果(分页)
     */
    @GetMapping("/sessions/{id}/results")
    public ResponseEntity<Map<String, Object>> results(
            @PathVariable("id") Long sessionId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        // 简单分页(以后改成 keyset 优化大列表)
        int offset = Math.max(0, (page - 1) * size);
        List<BatchTestRowEntity> rows = orchestrator.getResults(sessionId, offset, size);
        Long total = rowMapper.selectCount(
                new QueryWrapper<BatchTestRowEntity>().eq("session_id", sessionId));
        Map<String, Object> resp = new HashMap<>();
        resp.put("rows", rows);
        resp.put("page", page);
        resp.put("size", size);
        resp.put("total", total);
        return ResponseEntity.ok(resp);
    }

    /**
     * 列历史 session(给 dashboard 用)
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<BatchTestSessionEntity>> list(
            @RequestParam(value = "subjectType", required = false) String subjectType,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        QueryWrapper<BatchTestSessionEntity> qw = new QueryWrapper<>();
        if (subjectType != null) {
            qw.eq("subject_type", subjectType);
        }
        qw.orderByDesc("create_time").last("LIMIT " + limit);
        List<BatchTestSessionEntity> sessions = sessionMapper.selectList(qw);
        return ResponseEntity.ok(sessions);
    }
}
