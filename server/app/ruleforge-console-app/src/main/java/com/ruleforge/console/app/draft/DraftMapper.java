package com.ruleforge.console.app.draft;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * rf_draft Mapper
 *
 * <p>V5.22 AI 规则草稿 — 复用 ruleforgeSqlSessionFactory (跟 rf_user 同源)
 */
@Mapper
public interface DraftMapper extends BaseMapper<DraftEntity> {

    @Select("SELECT * FROM rf_draft WHERE draft_id = #{draftId} LIMIT 1")
    DraftEntity selectByDraftId(@Param("draftId") String draftId);

    @Select("SELECT * FROM rf_draft WHERE project = #{project} ORDER BY created_at DESC LIMIT #{limit}")
    List<DraftEntity> listByProject(@Param("project") String project, @Param("limit") int limit);

    @Select("SELECT * FROM rf_draft WHERE status = #{status} ORDER BY created_at DESC LIMIT #{limit}")
    List<DraftEntity> listByStatus(@Param("status") String status, @Param("limit") int limit);

    @Update("UPDATE rf_draft SET status = #{newStatus}, updated_at = NOW() " +
            "WHERE status = #{oldStatus} AND expires_at IS NOT NULL AND expires_at < NOW()")
    int markExpiredDrafts(@Param("oldStatus") String oldStatus, @Param("newStatus") String newStatus);
}
