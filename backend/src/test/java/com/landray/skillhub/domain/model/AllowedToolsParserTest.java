package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AllowedToolsParserTest {

    @Test
    @DisplayName("null 或空白返回空列表——该字段允许缺席")
    void should_returnEmptyList_when_scalarIsBlank() {
        assertThat(AllowedToolsParser.parse(null)).isEmpty();
        assertThat(AllowedToolsParser.parse("  ")).isEmpty();
    }

    @Test
    @DisplayName("简单逗号分隔按普通规则切分")
    void should_splitByComma_when_noParentheses() {
        assertThat(AllowedToolsParser.parse("Read, Grep, Write")).containsExactly("Read", "Grep", "Write");
    }

    @Test
    @DisplayName("括号内的逗号不作为切分点——核心安全用例,文档明确点名的字符串")
    void should_notSplitInsideParentheses_when_commaIsInsideParens() {
        List<String> result =
                AllowedToolsParser.parse("Bash(git diff:*, git status:*)");

        assertThat(result).containsExactly("Bash(git diff:*, git status:*)");
    }

    @Test
    @DisplayName("括号工具与普通工具混合时,只在括号外切分")
    void should_splitOnlyOutsideParens_when_mixedWithPlainTools() {
        List<String> result = AllowedToolsParser.parse("Bash(git diff:*, git status:*), Read, Grep(*.md)");

        assertThat(result).containsExactly("Bash(git diff:*, git status:*)", "Read", "Grep(*.md)");
    }

    @Test
    @DisplayName("多组带逗号的括号工具都能各自完整保留")
    void should_keepEachParenthesizedGroupIntact_when_multipleGroupsPresent() {
        List<String> result = AllowedToolsParser.parse("Bash(a, b), Bash(c, d)");

        assertThat(result).containsExactly("Bash(a, b)", "Bash(c, d)");
    }

    @Test
    @DisplayName("每段去除首尾空白")
    void should_trimEachSegment_when_splitting() {
        assertThat(AllowedToolsParser.parse("  Read  ,  Grep  ")).containsExactly("Read", "Grep");
    }

    @Test
    @DisplayName("连续逗号或首尾逗号产生的空段被丢弃")
    void should_dropEmptySegments_when_consecutiveOrTrailingCommas() {
        assertThat(AllowedToolsParser.parse(",Read,,Grep,")).containsExactly("Read", "Grep");
    }

    @Test
    @DisplayName("单个工具没有逗号时原样返回一条")
    void should_returnSingleEntry_when_noCommaPresent() {
        assertThat(AllowedToolsParser.parse("Read")).containsExactly("Read");
    }
}
