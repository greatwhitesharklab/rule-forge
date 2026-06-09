package com.ruleforge.console.repository.data;

import com.ruleforge.console.entity.GitDualwriteFailureEntity;

import java.util.Date;
import java.util.List;

/**
 * 5.10-C: dualWrite 失败 audit log 仓储.
 */
public interface GitDualwriteFailureRepository {

    /** 记一次失败事件. */
    GitDualwriteFailureEntity insert(GitDualwriteFailureEntity entity);

    /** 总数(无过滤,运维偶尔查). */
    long countAll();

    /** since(包含) 之后的失败总数. */
    long countSince(Date since);

    /** 最近 N 条,按 occurredAt DESC. */
    List<GitDualwriteFailureEntity> findRecent(int limit);

    /** 清理早于 before 的旧行,返回删除条数(留给未来的 @Scheduled job). */
    int deleteOlderThan(Date before);
}
