package com.ruleforge.console.app.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 草稿状态历史服务 (V5.22.3)
 *
 * <p>append-only — 每次状态转换插一行。
 * <p>由 {@link DraftService} 在状态机转换时调,不在 controller / tool 里直接调,集中管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftHistoryService {

    private final DraftHistoryMapper historyMapper;
    private final ObjectMapper objectMapper;

    /**
     * 记录一次状态转换
     *
     * @param draftId   草稿 ID
     * @param action    动作 (CREATE / SUBMIT / APPROVE / REJECT / APPLY / EDIT / EXPIRE)
     * @param fromStatus 转换前状态(可空,CREATE 时无)
     * @param toStatus  转换后状态
     * @param actor     操作人(用户/agent)
     * @param comment   备注 (可空)
     */
    @Transactional
    public void record(String draftId, String action, String fromStatus, String toStatus,
                       String actor, String comment) {
        DraftHistoryEntity h = new DraftHistoryEntity();
        h.setDraftId(draftId);
        h.setAction(action);
        h.setFromStatus(fromStatus);
        h.setToStatus(toStatus);
        h.setActor(actor);
        h.setComment(comment);
        historyMapper.insert(h);
        log.debug("[DraftHistory] {} {} -> {} actor={} comment={}", draftId, fromStatus, toStatus, actor, comment);
    }

    /**
     * 取草稿完整历史(按时间正序)
     */
    public List<DraftHistoryEntity> listByDraftId(String draftId) {
        return historyMapper.listByDraftId(draftId);
    }

    /**
     * 导出 DTO(给 BA 看的时间线)
     */
    public ObjectNode toDto(DraftHistoryEntity h) {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("action", h.getAction());
        out.put("fromStatus", h.getFromStatus());
        out.put("toStatus", h.getToStatus());
        out.put("actor", h.getActor());
        out.put("comment", h.getComment());
        out.put("at", h.getCreatedAt() != null ? h.getCreatedAt().toInstant().toString() : null);
        return out;
    }
}
