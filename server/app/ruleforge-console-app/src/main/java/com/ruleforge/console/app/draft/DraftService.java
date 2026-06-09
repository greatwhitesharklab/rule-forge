package com.ruleforge.console.app.draft;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruleforge.console.app.agent.schema.RuleSchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * AI 规则草稿生命周期服务 (V5.22)
 *
 * <p>状态机:
 * <pre>
 *   DRAFT  ──submit──→  PENDING_REVIEW  ──approve──→  APPROVED  ──apply──→  (写入主存储)
 *     │                       │                                                   (appliedVersion 记录)
 *     │                       │
 *     │                       └──reject──→  REJECTED  (reviewComment 记原因)
 *     │
 *     └──expire──→  EXPIRED  (7 天后自动)
 * </pre>
 *
 * <p>权限域:ruleforge_db
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftService {

    private final DraftMapper draftMapper;
    private final RuleSchemaService ruleSchemaService;
    private final ObjectMapper objectMapper;
    private final DraftHistoryService historyService;  // V5.22.3

    /** 默认 7 天过期 */
    private static final int DEFAULT_EXPIRY_DAYS = 7;

    // ========== 创建 / 读取 ==========

    /**
     * 创建草稿
     *
     * @param ruleType  规则类型 (decision_table / ul / ...)
     * @param project   项目名
     * @param content   规则 JSON(字符串,跟 schema 一致)
     * @param createdBy 创建人(用户/agent)
     * @param title     草稿标题(可选,BA 视角)
     * @param source    来源(LLM / CLI / MANUAL)
     * @param sessionId 关联的 LLM 会话 ID(可选)
     * @param messageId 关联的 LLM 消息 ID(可选)
     * @return 新建的 draft
     */
    public DraftEntity createDraft(String ruleType, String project, String content,
                                   String createdBy, String title, String source,
                                   String sessionId, String messageId) {
        validateRuleType(ruleType);
        validateContent(ruleType, content);

        DraftEntity draft = new DraftEntity();
        draft.setDraftId(generateDraftId());
        draft.setRuleType(ruleType);
        draft.setProject(project);
        draft.setContent(content);
        draft.setTitle(title);
        draft.setStatus(DraftEntity.STATUS_DRAFT);
        draft.setSource(source != null ? source : "LLM");
        draft.setCreatedBy(createdBy);
        draft.setSessionId(sessionId);
        draft.setMessageId(messageId);
        draft.setExpiresAt(Date.from(Instant.now().plus(DEFAULT_EXPIRY_DAYS, ChronoUnit.DAYS)));

        draftMapper.insert(draft);
        log.info("[Draft] 创建草稿 draftId={} type={} project={} by={}", draft.getDraftId(), ruleType, project, createdBy);
        // V5.22.3 — 记一条 CREATE 历史
        historyService.record(draft.getDraftId(), DraftHistoryEntity.ACTION_CREATE,
                null, DraftEntity.STATUS_DRAFT, createdBy, title);
        return draft;
    }

    /**
     * 取草稿详情
     */
    public Optional<DraftEntity> get(String draftId) {
        return Optional.ofNullable(draftMapper.selectByDraftId(draftId));
    }

    /**
     * 列项目下草稿
     */
    public List<DraftEntity> listByProject(String project, int limit) {
        if (limit <= 0 || limit > 200) limit = 50;
        return draftMapper.listByProject(project, limit);
    }

    /**
     * 按状态过滤草稿
     */
    public List<DraftEntity> listByStatus(String status, int limit) {
        if (limit <= 0 || limit > 200) limit = 50;
        return draftMapper.listByStatus(status, limit);
    }

    // ========== 状态流转 ==========

    /**
     * 提交审批(DRAFT → PENDING_REVIEW)
     */
    @Transactional
    public DraftEntity submitForReview(String draftId, String submittedBy) {
        DraftEntity d = mustExist(draftId);
        if (!DraftEntity.STATUS_DRAFT.equals(d.getStatus())) {
            throw new IllegalStateException("草稿状态为 " + d.getStatus() + ",不能提交审批 (要求 DRAFT)");
        }
        d.setStatus(DraftEntity.STATUS_PENDING_REVIEW);
        d.setUpdatedAt(new Date());
        draftMapper.updateById(d);
        log.info("[Draft] {} 提交审批 by={}", draftId, submittedBy);
        historyService.record(draftId, DraftHistoryEntity.ACTION_SUBMIT,
                DraftEntity.STATUS_DRAFT, DraftEntity.STATUS_PENDING_REVIEW, submittedBy, null);
        return d;
    }

    /**
     * 拒绝(PENDING_REVIEW → REJECTED)
     */
    @Transactional
    public DraftEntity reject(String draftId, String reviewer, String reason) {
        DraftEntity d = mustExist(draftId);
        if (!DraftEntity.STATUS_PENDING_REVIEW.equals(d.getStatus())) {
            throw new IllegalStateException("草稿状态为 " + d.getStatus() + ",不能拒绝 (要求 PENDING_REVIEW)");
        }
        d.setStatus(DraftEntity.STATUS_REJECTED);
        d.setReviewedBy(reviewer);
        d.setReviewedAt(new Date());
        d.setReviewComment(reason);
        draftMapper.updateById(d);
        log.info("[Draft] {} 被 {} 拒绝: {}", draftId, reviewer, reason);
        historyService.record(draftId, DraftHistoryEntity.ACTION_REJECT,
                DraftEntity.STATUS_PENDING_REVIEW, DraftEntity.STATUS_REJECTED, reviewer, reason);
        return d;
    }

    /**
     * 审批通过(APPROVED) — 仅状态变更,实际写入主存储由 {@link com.ruleforge.console.app.draft.DraftApplyService} 处理
     */
    @Transactional
    public DraftEntity approve(String draftId, String reviewer, String comment) {
        DraftEntity d = mustExist(draftId);
        if (!DraftEntity.STATUS_PENDING_REVIEW.equals(d.getStatus())) {
            throw new IllegalStateException("草稿状态为 " + d.getStatus() + ",不能审批通过 (要求 PENDING_REVIEW)");
        }
        d.setStatus(DraftEntity.STATUS_APPROVED);
        d.setReviewedBy(reviewer);
        d.setReviewedAt(new Date());
        d.setReviewComment(comment);
        draftMapper.updateById(d);
        log.info("[Draft] {} 被 {} 审批通过: {}", draftId, reviewer, comment);
        historyService.record(draftId, DraftHistoryEntity.ACTION_APPROVE,
                DraftEntity.STATUS_PENDING_REVIEW, DraftEntity.STATUS_APPROVED, reviewer, comment);
        return d;
    }

    /**
     * 标记为已应用(APPROVED → 设置 appliedVersion)
     * 应用层在写入主存储成功后回调此方法
     */
    @Transactional
    public DraftEntity markApplied(String draftId, String appliedVersion) {
        DraftEntity d = mustExist(draftId);
        if (!DraftEntity.STATUS_APPROVED.equals(d.getStatus())) {
            throw new IllegalStateException("草稿状态为 " + d.getStatus() + ",不能标记应用 (要求 APPROVED)");
        }
        d.setAppliedVersion(appliedVersion);
        d.setAppliedAt(new Date());
        draftMapper.updateById(d);
        log.info("[Draft] {} 标记应用 version={}", draftId, appliedVersion);
        historyService.record(draftId, DraftHistoryEntity.ACTION_APPLY,
                DraftEntity.STATUS_APPROVED, DraftEntity.STATUS_APPROVED, appliedVersion, "appliedVersion=" + appliedVersion);
        return d;
    }

    /**
     * 把过期 DRAFT 标 EXPIRED(定时任务调)
     */
    public int sweepExpired() {
        int n = draftMapper.markExpiredDrafts(DraftEntity.STATUS_DRAFT, DraftEntity.STATUS_EXPIRED);
        if (n > 0) {
            // 批量更新:重新查过期草稿,逐个记历史
            List<DraftEntity> drafts = draftMapper.listByStatus(DraftEntity.STATUS_EXPIRED, n + 10);
            for (DraftEntity d : drafts) {
                historyService.record(d.getDraftId(), DraftHistoryEntity.ACTION_EXPIRE,
                        DraftEntity.STATUS_DRAFT, DraftEntity.STATUS_EXPIRED, "system", "7 天未提交审批自动过期");
            }
        }
        return n;
    }

    // ========== 校验 ==========

    /**
     * 校验 rule_type 已知 + 草稿 JSON 字段最少有 type/rows|columns|rules|...
     *
     * @throws IllegalArgumentException 未知类型或结构错误
     */
    public void validateContent(String ruleType, String content) {
        validateRuleType(ruleType);
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("content 不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            // 跟 schema 对一遍 — 至少要符合 type 字段
            JsonNode typeNode = root.get("type");
            if (typeNode == null || !typeNode.isTextual()) {
                throw new IllegalArgumentException("content.type 缺失或非字符串");
            }
            String declaredType = typeNode.asText();
            if (!ruleType.equals(declaredType)) {
                throw new IllegalArgumentException(
                        "content.type='" + declaredType + "' 跟 path 参数 type='" + ruleType + "' 不一致");
            }
            // 类型特异性必填字段检查
            switch (ruleType) {
                case "decision_table", "script_decision_table" -> {
                    requireField(root, "rows");
                    requireField(root, "columns");
                    requireField(root, "cellMap");
                }
                case "ul" -> requireField(root, "rules");
                case "decision_tree" -> {
                    requireField(root, "rootNodeId");
                    requireField(root, "nodes");
                }
                case "scorecard" -> {
                    requireField(root, "threshold");
                    requireField(root, "conditions");
                }
                case "decision_flow" -> {
                    requireField(root, "processId");
                    requireField(root, "nodes");
                    requireField(root, "edges");
                }
                default -> {
                    // 占位 / 未来类型放过
                }
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("content 不是合法 JSON: " + e.getMessage());
        }
    }

    private void validateRuleType(String ruleType) {
        if (ruleType == null || ruleType.isEmpty()) {
            throw new IllegalArgumentException("ruleType 必填");
        }
        List<String> supported = ruleSchemaService.supportedV522Types();
        if (!supported.contains(ruleType)) {
            // 允许 unknown 类型(向后兼容),但 warn
            log.warn("[Draft] ruleType='{}' 不在 V5.22 supportedV522Types 列表,允许创建但请检查", ruleType);
        }
    }

    private void requireField(JsonNode root, String field) {
        if (root.get(field) == null) {
            throw new IllegalArgumentException("content." + field + " 必填");
        }
    }

    private DraftEntity mustExist(String draftId) {
        DraftEntity d = draftMapper.selectByDraftId(draftId);
        if (d == null) {
            throw new IllegalArgumentException("草稿不存在 draftId=" + draftId);
        }
        return d;
    }

    private static String generateDraftId() {
        return "drf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 列出所有项目(用 draft 表反查 — 项目权限以后再做)
     */
    public List<String> listProjectsWithDrafts() {
        return draftMapper.selectList(new QueryWrapper<DraftEntity>()
                        .select("DISTINCT project")
                        .isNotNull("project")
                        .orderByDesc("created_at"))
                .stream()
                .map(DraftEntity::getProject)
                .distinct()
                .toList();
    }

    /**
     * 导出 DTO(给 LLM/CLI 看不带内部 id 字段的版本)
     */
    public ObjectNode toDto(DraftEntity d) {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("draftId", d.getDraftId());
        out.put("ruleType", d.getRuleType());
        out.put("project", d.getProject());
        out.put("packagePath", d.getPackagePath());
        out.put("status", d.getStatus());
        out.put("title", d.getTitle());
        out.put("source", d.getSource());
        out.put("createdBy", d.getCreatedBy());
        out.put("createdAt", d.getCreatedAt() != null ? d.getCreatedAt().toInstant().toString() : null);
        out.put("updatedAt", d.getUpdatedAt() != null ? d.getUpdatedAt().toInstant().toString() : null);
        out.put("reviewedBy", d.getReviewedBy());
        out.put("reviewedAt", d.getReviewedAt() != null ? d.getReviewedAt().toInstant().toString() : null);
        out.put("reviewComment", d.getReviewComment());
        out.put("appliedVersion", d.getAppliedVersion());
        out.put("appliedAt", d.getAppliedAt() != null ? d.getAppliedAt().toInstant().toString() : null);
        out.put("expiresAt", d.getExpiresAt() != null ? d.getExpiresAt().toInstant().toString() : null);
        // content 已是 JSON 字符串,放回对象里(可读性更好)
        try {
            out.set("content", objectMapper.readTree(d.getContent()));
        } catch (JsonProcessingException e) {
            out.put("content", d.getContent());
        }
        return out;
    }
}
