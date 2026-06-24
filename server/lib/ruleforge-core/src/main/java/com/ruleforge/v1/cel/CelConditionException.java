package com.ruleforge.v1.cel;

import com.ruleforge.exception.RuleException;

/**
 * CEL 条件编译/求值失败。包成 RuleException 子类,跟引擎其它错误统一处理。
 */
public class CelConditionException extends RuleException {
    public CelConditionException(String message) {
        super(message);
    }

    public CelConditionException(String message, Throwable cause) {
        super(message, cause instanceof Exception ? (Exception) cause : new RuntimeException(cause));
    }
}
