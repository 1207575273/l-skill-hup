package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.landray.skillhub.domain.exception.CommentContentInvalidException;

/**
 * 评论正文的值对象。规则来自业务建模文档 5.11:非空、上限 2000 字符、纯文本存储。
 *
 * <p>做成值对象而不是在 application 层写几行 if:正文长度是<b>业务规则</b>不是请求解释,发评论与将来可能有的
 * 编辑评论都要守同一条,规则散在调用方迟早会漂。
 */
class CommentContentTest {

    @Test
    @DisplayName("正常正文原样保留")
    void should_keepContent_when_valid() {
        assertThat(new CommentContent("这个 skill 很好用").value()).isEqualTo("这个 skill 很好用");
    }

    @Test
    @DisplayName("首尾空白被去掉——它不是正文的一部分")
    void should_trimSurroundingWhitespace_when_created() {
        assertThat(new CommentContent("  有内容  ").value()).isEqualTo("有内容");
    }

    @Test
    @DisplayName("空正文拒绝")
    void should_reject_when_blank() {
        assertThatThrownBy(() -> new CommentContent("   ")).isInstanceOf(CommentContentInvalidException.class);
    }

    @Test
    @DisplayName("null 拒绝")
    void should_reject_when_null() {
        assertThatThrownBy(() -> new CommentContent(null)).isInstanceOf(CommentContentInvalidException.class);
    }

    @Test
    @DisplayName("恰好 2000 字符放行")
    void should_accept_when_exactlyAtLimit() {
        assertThat(new CommentContent("字".repeat(2000)).value()).hasSize(2000);
    }

    @Test
    @DisplayName("超过 2000 字符拒绝")
    void should_reject_when_exceedsLimit() {
        assertThatThrownBy(() -> new CommentContent("字".repeat(2001)))
                .isInstanceOf(CommentContentInvalidException.class)
                .hasMessageContaining("2000");
    }

    @Test
    @DisplayName("长度按去空白之后算——否则一串空格就能把人卡在上限外")
    void should_measureLengthAfterTrim_when_paddedWithWhitespace() {
        assertThat(new CommentContent("  " + "字".repeat(2000) + "  ").value()).hasSize(2000);
    }
}
