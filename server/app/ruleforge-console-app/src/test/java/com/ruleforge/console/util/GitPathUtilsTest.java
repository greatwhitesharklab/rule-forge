package com.ruleforge.console.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 5.11: GitPathUtils.extractProjectName 单测.
 * 锁住所有边角:null / 空 / "/" / 无前导斜杠 / 嵌套路径.
 */
@DisplayName("GitPathUtils.extractProjectName (5.11)")
class GitPathUtilsTest {

    @Test
    @DisplayName("null → null")
    void nullPath() {
        assertThat(GitPathUtils.extractProjectName(null)).isNull();
    }

    @Test
    @DisplayName("空串 → null")
    void emptyPath() {
        assertThat(GitPathUtils.extractProjectName("")).isNull();
    }

    @Test
    @DisplayName("\"/\" → null(没 project)")
    void rootOnly() {
        assertThat(GitPathUtils.extractProjectName("/")).isNull();
    }

    @Test
    @DisplayName("\"/foo\" → \"foo\"")
    void singleSegmentWithLeadingSlash() {
        assertThat(GitPathUtils.extractProjectName("/foo")).isEqualTo("foo");
    }

    @Test
    @DisplayName("\"/foo/bar/baz.xml\" → \"foo\"")
    void nestedPath() {
        assertThat(GitPathUtils.extractProjectName("/foo/bar/baz.xml")).isEqualTo("foo");
    }

    @Test
    @DisplayName("\"foo/bar\" → \"foo\"(无前导斜杠也接受)")
    void noLeadingSlash() {
        assertThat(GitPathUtils.extractProjectName("foo/bar")).isEqualTo("foo");
    }

    @Test
    @DisplayName("\"foo\" → \"foo\"(无斜杠单段)")
    void noSlashSingleSegment() {
        assertThat(GitPathUtils.extractProjectName("foo")).isEqualTo("foo");
    }

    @Test
    @DisplayName("真实路径 \"/myproject/rules/rule.rs.xml\" → \"myproject\"")
    void realisticCase() {
        assertThat(GitPathUtils.extractProjectName("/myproject/rules/rule.rs.xml"))
                .isEqualTo("myproject");
    }
}
