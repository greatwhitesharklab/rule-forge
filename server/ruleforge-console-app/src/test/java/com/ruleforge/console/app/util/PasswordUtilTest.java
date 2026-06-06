package com.ruleforge.console.app.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: PasswordUtil BCrypt 哈希 (V5.15)
 */
@DisplayName("PasswordUtil - BCrypt 哈希")
class PasswordUtilTest {

    @Nested
    @DisplayName("encode + matches")
    class EncodeAndMatch {

        @Test
        @DisplayName("encode → matches 同一明文")
        void shouldMatchSamePassword() {
            String hash = PasswordUtil.encode("admin123");
            assertThat(PasswordUtil.matches("admin123", hash)).isTrue();
        }

        @Test
        @DisplayName("encode → 不匹配不同明文")
        void shouldNotMatchDifferentPassword() {
            String hash = PasswordUtil.encode("admin123");
            assertThat(PasswordUtil.matches("wrong", hash)).isFalse();
        }

        @Test
        @DisplayName("encode 每次产生不同哈希(salt 随机)")
        void shouldProduceDifferentHashes() {
            String h1 = PasswordUtil.encode("same");
            String h2 = PasswordUtil.encode("same");
            assertThat(h1).isNotEqualTo(h2);
            // 但两个都能 match
            assertThat(PasswordUtil.matches("same", h1)).isTrue();
            assertThat(PasswordUtil.matches("same", h2)).isTrue();
        }
    }
}
