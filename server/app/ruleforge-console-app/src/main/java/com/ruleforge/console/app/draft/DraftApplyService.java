package com.ruleforge.console.app.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruleforge.console.model.DefaultUser;
import com.ruleforge.console.storage.ProjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Draft 应用服务 (V5.22) — 把审批通过的 draft 写入主存储
 *
 * <p>行为:
 * <ol>
 *   <li>DraftService.approve() 把 status 置为 APPROVED
 *   <li>调用本服务.applyToPackage() 把 content 写到 package_path/fileName
 *   <li>写成功再调 DraftService.markApplied() 设置 appliedVersion
 * </ol>
 *
 * <p>NOTE: V5.22 v0 只支持"把 draft 写成 JSON 资源文件" — 不做 XML 反序列化。
 * 真正的"把 JSON 渲染到决策表编辑器"在 V5.23 完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftApplyService {

    private final DraftService draftService;
    private final ProjectStorageService projectStorageService;
    private final ObjectMapper objectMapper;

    /**
     * 把草稿应用到目标包
     *
     * @param draftId         草稿 ID
     * @param packagePath     目标包路径
     * @param fileName        落地文件名(相对 packagePath,不含路径前缀;默认 rule_&lt;type&gt;_&lt;draftId&gt;.json)
     * @param reviewer        审批人
     * @param versionComment  版本说明 (写到 git commit message)
     * @return 写入结果(包含 newVersion, appliedAt)
     */
    @Transactional
    public ObjectNode applyToPackage(String draftId, String packagePath, String fileName,
                                     String reviewer, String versionComment) throws Exception {
        DraftEntity draft = draftService.get(draftId)
                .orElseThrow(() -> new IllegalArgumentException("草稿不存在 draftId=" + draftId));

        // 状态机
        if (DraftEntity.STATUS_APPROVED.equals(draft.getStatus())) {
            log.info("[Draft] apply 已是 APPROVED,直接写,draftId={}", draftId);
        } else if (DraftEntity.STATUS_PENDING_REVIEW.equals(draft.getStatus())) {
            // 一步到位:同时审批+应用
            draftService.approve(draftId, reviewer, versionComment);
        } else {
            throw new IllegalStateException("草稿状态为 " + draft.getStatus() + ",不能应用 (要求 PENDING_REVIEW 或 APPROVED)");
        }

        if (fileName == null || fileName.isEmpty()) {
            fileName = "rule_" + draft.getRuleType() + "_" + draftId + ".json";
        }
        if (!fileName.endsWith(".json")) {
            fileName = fileName + ".json";
        }

        // 写文件到项目存储
        String fullPath = packagePath + "/" + fileName;
        DefaultUser systemUser = new DefaultUser();
        systemUser.setUsername(reviewer);
        systemUser.setAdmin(true);
        String newVersion = projectStorageService.saveFile(
                fullPath,
                draft.getContent(),
                systemUser,
                true,
                "V5.22 AI 草稿应用: " + (versionComment != null ? versionComment : draftId)
        );

        // 标记已应用
        draftService.markApplied(draftId, newVersion);
        log.info("[Draft] 应用成功 draftId={} → {} v{}", draftId, fullPath, newVersion);

        // 返回结果
        DraftEntity updated = draftService.get(draftId).orElseThrow();
        ObjectNode out = objectMapper.createObjectNode();
        out.put("draftId", draftId);
        out.put("packagePath", packagePath);
        out.put("fileName", fileName);
        out.put("fullPath", fullPath);
        out.put("newVersion", newVersion);
        out.put("appliedAt", updated.getAppliedAt() != null
                ? updated.getAppliedAt().toInstant().toString() : null);
        return out;
    }
}
