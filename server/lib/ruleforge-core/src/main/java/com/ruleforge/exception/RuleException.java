package com.ruleforge.exception;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author fred
 * 2018-11-05 5:33 PM
 */
@Getter
@Slf4j
public class RuleException extends RuntimeException {
    private String label;
    private Object val;
    private String tipMsg;

    public RuleException() {
    }

    public RuleException(String msg) {
        super(msg);
    }

    public RuleException(Exception ex) {
        super(ex);
        // 异步数据源暂停异常不打印 ERROR 日志
        if (!isAsyncPendingException(ex)) {
            log.error("RuleException", ex);
        }
    }

    public RuleException(String msg, Exception ex) {
        // P1 — V5.47 之前 super(ex) 让 getMessage() 拿 cause 消息,format 字符串
        // 只进 tipMsg,业务侧 catch(RuleException).getMessage() 拿不到"规则【X】包含
        // 语法错误"的高层描述,只能看到 cause 的 raw antlr 消息。改成 super(msg, ex)
        // 后,getMessage() 返 formatted msg,getCause() 返 ex — 跟 Java 标准
        // Throwable(msg, cause) 行为对齐。
        // 3 caller 受益:RulesRebuilder L98 / ScorecardParser L178 /
        // KnowledgeServiceImpl L146 — 都希望 getMessage() 是"业务描述",
        // getCause() 才是"根因细节"。Caller 不需要任何改动。
        super(msg, ex);
        // 异步数据源暂停异常不打印 ERROR 日志
        if (!isAsyncPendingException(ex)) {
            if (msg != null) {
                msg = "错误发生位置：" + msg;
                log.error(msg);
            }
            log.error("RuleException", ex);
        }
        this.tipMsg = msg;
    }

    /**
     * 检查异常链中是否包含 AsyncDataSourcePendingException
     */
    private static boolean isAsyncPendingException(Throwable e) {
        Throwable current = e;
        while (current != null) {
            // 使用类名判断，避免 ruleforge-core 依赖 ruleforge-prod-console
            if (current.getClass().getName().endsWith("AsyncDataSourcePendingException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public RuleException(String label, Object val, String msg, Exception ex) {
        super(ex);

        this.label = label;
        this.val = val;
        this.tipMsg = msg;
    }

}
