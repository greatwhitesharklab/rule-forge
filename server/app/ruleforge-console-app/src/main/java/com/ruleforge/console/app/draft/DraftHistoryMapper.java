package com.ruleforge.console.app.draft;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 草稿历史 Mapper (V5.22.3)
 */
@Mapper
public interface DraftHistoryMapper extends BaseMapper<DraftHistoryEntity> {

    @Select("SELECT * FROM rf_draft_history WHERE draft_id = #{draftId} ORDER BY created_at ASC, id ASC")
    List<DraftHistoryEntity> listByDraftId(String draftId);
}
