package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 纯领域单测。业务建模文档 6.3:keyword 搜索用 ILIKE,须转义 % 和 _,否则用户搜索 "100%" 会意外匹配所有行。
 */
class LikePatternEscaperTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("空白关键词应返回 null,表示不参与过滤")
    void should_returnNull_when_keywordIsBlank(String keyword) {
        assertThat(LikePatternEscaper.toLikePattern(keyword)).isNull();
    }

    @Test
    @DisplayName("普通关键词应被前后包裹一对 %")
    void should_wrapWithPercent_when_keywordIsPlainText() {
        assertThat(LikePatternEscaper.toLikePattern("代码审查")).isEqualTo("%代码审查%");
    }

    @Test
    @DisplayName("关键词里的字面 % 必须被转义,否则会被当成通配符匹配到所有行")
    void should_escapePercentSign_when_keywordContainsPercent() {
        assertThat(LikePatternEscaper.toLikePattern("100%")).isEqualTo("%100\\%%");
    }

    @Test
    @DisplayName("关键词里的字面 _ 必须被转义,否则会被当成单字符通配符")
    void should_escapeUnderscore_when_keywordContainsUnderscore() {
        assertThat(LikePatternEscaper.toLikePattern("foo_bar")).isEqualTo("%foo\\_bar%");
    }

    @Test
    @DisplayName("反斜杠本身必须先转义,否则会与后续的转义符产生歧义")
    void should_escapeBackslashFirst_when_keywordContainsBackslash() {
        assertThat(LikePatternEscaper.toLikePattern("a\\b")).isEqualTo("%a\\\\b%");
    }

    @Test
    @DisplayName("同时包含 % 与 _ 时两者都要转义")
    void should_escapeBothWildcards_when_keywordContainsBoth() {
        assertThat(LikePatternEscaper.toLikePattern("100%_off")).isEqualTo("%100\\%\\_off%");
    }
}
