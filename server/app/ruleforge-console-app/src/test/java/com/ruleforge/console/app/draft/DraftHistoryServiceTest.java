package com.ruleforge.console.app.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * DraftHistoryService 单元测试 (V5.22.3)
 *
 * 测:状态转换记录 + DTO 序列化
 */
@DisplayName("DraftHistoryService - 草稿状态历史")
class DraftHistoryServiceTest {

    private DraftHistoryMapper mapper;
    private DraftHistoryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(DraftHistoryMapper.class);
        service = new DraftHistoryService(mapper, new ObjectMapper());
    }

    @Test
    @DisplayName("记录 SUBMIT 历史(fromStatus=DRAFT to=PENDING_REVIEW)")
    void shouldRecordSubmit() {
        service.record("drf_1", DraftHistoryEntity.ACTION_SUBMIT,
                DraftEntity.STATUS_DRAFT, DraftEntity.STATUS_PENDING_REVIEW,
                "BA1", null);

        ArgumentCaptor<DraftHistoryEntity> captor = ArgumentCaptor.forClass(DraftHistoryEntity.class);
        verify(mapper).insert(captor.capture());
        DraftHistoryEntity h = captor.getValue();
        assertThat(h.getDraftId()).isEqualTo("drf_1");
        assertThat(h.getAction()).isEqualTo("SUBMIT");
        assertThat(h.getFromStatus()).isEqualTo("DRAFT");
        assertThat(h.getToStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(h.getActor()).isEqualTo("BA1");
        assertThat(h.getComment()).isNull();
    }

    @Test
    @DisplayName("记录 REJECT 历史带 reason")
    void shouldRecordReject() {
        service.record("drf_2", DraftHistoryEntity.ACTION_REJECT,
                DraftEntity.STATUS_PENDING_REVIEW, DraftEntity.STATUS_REJECTED,
                "BA2", "决策表 row 缺失");

        ArgumentCaptor<DraftHistoryEntity> captor = ArgumentCaptor.forClass(DraftHistoryEntity.class);
        verify(mapper).insert(captor.capture());
        DraftHistoryEntity h = captor.getValue();
        assertThat(h.getAction()).isEqualTo("REJECT");
        assertThat(h.getComment()).isEqualTo("决策表 row 缺失");
    }

    @Test
    @DisplayName("DTO 包含所有字段")
    void shouldSerializeToDto() {
        DraftHistoryEntity h = new DraftHistoryEntity();
        h.setAction("CREATE");
        h.setFromStatus(null);
        h.setToStatus("DRAFT");
        h.setActor("BA1");
        h.setComment("年龄拒贷测试");

        var dto = service.toDto(h);
        assertThat(dto.get("action").asText()).isEqualTo("CREATE");
        assertThat(dto.get("toStatus").asText()).isEqualTo("DRAFT");
        assertThat(dto.get("actor").asText()).isEqualTo("BA1");
        assertThat(dto.get("comment").asText()).isEqualTo("年龄拒贷测试");
    }
}
