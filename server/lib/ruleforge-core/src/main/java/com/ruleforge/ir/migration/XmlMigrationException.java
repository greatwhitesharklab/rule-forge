package com.ruleforge.ir.migration;

/**
 * V5.40.5 — 一次性 .xml → .dmn 迁移失败异常。
 *
 * <p>调用方(V5.40.5 启动钩子或运维脚本)捕获后决定 fallback 策略:
 * 保留原 .xml 不动 + 写日志,或转 fallback 路径(老 .xml 直接弃用,内容迁移到 retraining data)。
 *
 * @since 5.40
 */
public class XmlMigrationException extends RuntimeException {

    public XmlMigrationException(String message) {
        super(message);
    }

    public XmlMigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
