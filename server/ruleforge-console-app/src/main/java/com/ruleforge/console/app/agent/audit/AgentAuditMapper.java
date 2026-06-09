package com.ruleforge.console.app.agent.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Agent 审计 mapper (V5.22.2)
 *
 * <p>权限域:app_db (由 appDataSource 注入,通过 appSqlSessionFactory)
 */
@Mapper
public interface AgentAuditMapper extends BaseMapper<AgentAuditEntity> {

    /** V5.22.3 — 按过滤条件列审计记录(时间倒序) */
    @Select({
        "<script>",
        "SELECT * FROM nd_agent_audit",
        "<where>",
        "  <if test='userId != null and userId != \"\"'>AND user_id = #{userId}</if>",
        "  <if test='sessionId != null and sessionId != \"\"'>AND session_id = #{sessionId}</if>",
        "  <if test='status != null and status != \"\"'>AND status = #{status}</if>",
        "</where>",
        "ORDER BY created_at DESC, id DESC",
        "LIMIT #{limit}",
        "</script>"
    })
    List<AgentAuditEntity> listByFilter(String userId, String sessionId, String status, int limit);
}

