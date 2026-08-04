package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.landray.skillhub.domain.exception.TagNameInvalidException;

/** 词条名的归一化与长度规则(业务建模文档 5.7)。纯 Java,不起 Spring。 */
class TagNameTest {

    @Test
    void should_trimSurroundingWhitespace_whenNameHasPadding() {
        assertThat(new TagName("  代码审查  ").value()).isEqualTo("代码审查");
    }

    @Test
    void should_collapseInnerWhitespace_whenNameHasRepeatedSpaces() {
        assertThat(new TagName("code    review").value()).isEqualTo("code review");
    }

    /** 制表符与换行也算空白。换行尤其重要:它进日志能伪造整行(backend/CLAUDE.md 6.5)。 */
    @Test
    void should_collapseTabsAndNewlines_whenNameSpansLines() {
        assertThat(new TagName("code\t\n review").value()).isEqualTo("code review");
    }

    @Test
    void should_acceptExactly16Characters_whenAtUpperBound() {
        assertThat(new TagName("1234567890123456").value()).hasSize(16);
    }

    /** 上限按归一化<b>之后</b>算——否则"a" + 20 个空格会被判超长,而它其实只有一个字符。 */
    @Test
    void should_measureLengthAfterNormalization_whenNameIsPaddedBeyondLimit() {
        assertThat(new TagName("        代码审查        ").value()).isEqualTo("代码审查");
    }

    @Test
    void should_rejectName_whenLongerThan16Characters() {
        assertThatThrownBy(() -> new TagName("12345678901234567"))
                .isInstanceOf(TagNameInvalidException.class)
                .hasMessageContaining("16");
    }

    @Test
    void should_rejectName_whenBlank() {
        assertThatThrownBy(() -> new TagName("   ")).isInstanceOf(TagNameInvalidException.class);
    }

    @Test
    void should_rejectName_whenNull() {
        assertThatThrownBy(() -> new TagName(null)).isInstanceOf(TagNameInvalidException.class);
    }

    /** 值对象要能当 Map key 与做集合去重,record 的 equals 基于归一化后的值,两种写法必须相等。 */
    @Test
    void should_beEqual_whenTwoNamesNormalizeToTheSameValue() {
        assertThat(new TagName(" code  review ")).isEqualTo(new TagName("code review"));
    }
}
