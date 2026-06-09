package com.ruleforge.decision.exception;

import lombok.Getter;

/**
 * 异步数据源等待异常
 * 当数据源返回 asyncPending=true 时抛出，表示需要等待异步数据
 */
@Getter
public class AsyncDataSourcePendingException extends RuntimeException {

    /**
     * 异步数据源ID
     */
    private final String asyncDataSourceId;

    /**
     * 实体ID（用户ID）
     */
    private final String entityId;

    /**
     * 数据源类名
     */
    private final String clazz;

    /**
     * 字段名
     */
    private final String fieldName;

    /**
     * 是否成功触发异步任务
     */
    private final boolean taskTriggered;

    public AsyncDataSourcePendingException(String asyncDataSourceId, String entityId,
                                            String clazz, String fieldName, boolean taskTriggered) {
        super(String.format("Async data pending: dataSourceId=%s, entityId=%s, clazz=%s, field=%s, taskTriggered=%s",
                asyncDataSourceId, entityId, clazz, fieldName, taskTriggered));
        this.asyncDataSourceId = asyncDataSourceId;
        this.entityId = entityId;
        this.clazz = clazz;
        this.fieldName = fieldName;
        this.taskTriggered = taskTriggered;
    }
}
