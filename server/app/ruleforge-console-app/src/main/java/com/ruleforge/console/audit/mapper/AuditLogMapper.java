package com.ruleforge.console.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruleforge.console.audit.entity.AuditLogEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * V5.17 audit log mapper — 走 {@code ruleforgeSqlSessionFactory}
 * (绑 {@code ruleforgeDataSource})。V5.15.0 把 UserMapper 移到 {@code
 * com.ruleforge.console.mapper} 包就是这个原因。
 */
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {

    /**
     * 按 actor / action 过滤 + 按 occurred_at 倒序查 audit log(限 limit 条)。
     *
     * <p>用 XML 写 SQL(MyBatis-Plus 的 LambdaQueryWrapper 不好直接表达 OR NULL
     * 的过滤语义),实现在 {@code resources/mapper/AuditLogMapper.xml}。
     *
     * @param actor  可选,按 actor_username 过滤;null/空 = 不过滤
     * @param action 可选,按 action 过滤;null/空 = 不过滤
     * @param limit  最大条数(由 controller clamp 到 500)
     * @return 倒序(最新在前)的 audit log 列表
     */
    List<AuditLogEntity> selectListByFilters(@Param("actor") String actor,
                                              @Param("action") String action,
                                              @Param("limit") int limit);
}
