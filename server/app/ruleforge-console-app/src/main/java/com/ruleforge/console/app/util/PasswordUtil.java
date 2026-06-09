package com.ruleforge.console.app.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 密码工具 — V5.15 权限改造
 *
 * <p>包装 {@link BCryptPasswordEncoder},提供静态 encode/matches 方法。
 * 不依赖 Spring 容器(纯工具类),在 AuthService 和 Flyway seed 之外
 * 的任何地方都可直接调。
 */
public final class PasswordUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtil() {}

    /**
     * 对明文密码做 BCrypt 哈希
     */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验明文密码是否匹配哈希
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}
