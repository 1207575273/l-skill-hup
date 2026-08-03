package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardExcerptGeneratorTest {

    @Test
    @DisplayName("空输入返回 null")
    void should_returnNull_when_inputIsBlank() {
        assertThat(CardExcerptGenerator.generate(null)).isNull();
        assertThat(CardExcerptGenerator.generate("   ")).isNull();
    }

    @Test
    @DisplayName("丢弃 frontmatter 块")
    void should_dropFrontmatterBlock() {
        String md = "---\nname: foo\ndescription: 描述\n---\n正文内容";

        assertThat(CardExcerptGenerator.generate(md)).isEqualTo("正文内容");
    }

    @Test
    @DisplayName("丢弃代码围栏块")
    void should_dropCodeFenceBlock() {
        String md = "说明文字\n```bash\necho hello\n```\n结尾文字";

        assertThat(CardExcerptGenerator.generate(md)).isEqualTo("说明文字 结尾文字");
    }

    @Test
    @DisplayName("丢弃纯 badge 行")
    void should_dropBadgeOnlyLine() {
        String md = "[![build](https://ci.example.com/badge)](https://ci.example.com)\n正文说明";

        assertThat(CardExcerptGenerator.generate(md)).isEqualTo("正文说明");
    }

    @Test
    @DisplayName("图片整体丢弃")
    void should_dropImage() {
        String md = "开头 ![alt 文本](https://example.com/pic.png) 结尾";

        assertThat(CardExcerptGenerator.generate(md)).isEqualTo("开头 结尾");
    }

    @Test
    @DisplayName("链接保留文本,丢弃地址")
    void should_keepLinkText_dropUrl() {
        String md = "参见 [官方文档](https://example.com/docs) 了解更多";

        assertThat(CardExcerptGenerator.generate(md)).isEqualTo("参见 官方文档 了解更多");
    }

    @Test
    @DisplayName("标题前缀被去掉")
    void should_stripHeadingPrefix() {
        assertThat(CardExcerptGenerator.generate("# 标题内容")).isEqualTo("标题内容");
        assertThat(CardExcerptGenerator.generate("### 三级标题")).isEqualTo("三级标题");
    }

    @Test
    @DisplayName("引用前缀被去掉")
    void should_stripQuotePrefix() {
        assertThat(CardExcerptGenerator.generate("> 引用的内容")).isEqualTo("引用的内容");
    }

    @Test
    @DisplayName("列表前缀被去掉")
    void should_stripListPrefix() {
        assertThat(CardExcerptGenerator.generate("- 第一项\n- 第二项")).isEqualTo("第一项 第二项");
    }

    @Test
    @DisplayName("行内代码保留代码内容")
    void should_keepInlineCodeContent() {
        assertThat(CardExcerptGenerator.generate("运行 `npm install` 安装依赖")).isEqualTo("运行 npm install 安装依赖");
    }

    @Test
    @DisplayName("强调标记保留文本")
    void should_keepEmphasisText() {
        assertThat(CardExcerptGenerator.generate("**重要**的*说明*")).isEqualTo("重要的说明");
    }

    @Test
    @DisplayName("HTML 标签去掉标签保留文本")
    void should_stripHtmlTagsKeepText() {
        assertThat(CardExcerptGenerator.generate("<b>加粗文字</b>")).isEqualTo("加粗文字");
    }

    @Test
    @DisplayName("连续空白与换行折叠成单个空格")
    void should_collapseWhitespaceAndNewlines() {
        assertThat(CardExcerptGenerator.generate("第一行\n\n\n第二行   带多个空格")).isEqualTo("第一行 第二行 带多个空格");
    }

    @Test
    @DisplayName("超过 200 字符时截断,不加任何后缀")
    void should_truncateTo200Chars_withoutSuffix() {
        String longText = "字".repeat(250);

        String excerpt = CardExcerptGenerator.generate(longText);

        assertThat(excerpt).hasSize(200);
        assertThat(excerpt).isEqualTo("字".repeat(200));
    }

    @Test
    @DisplayName("处理后为空文本时返回 null(例如整段都是代码块)")
    void should_returnNull_when_resultIsEmptyAfterStripping() {
        assertThat(CardExcerptGenerator.generate("```\nonly code\n```")).isNull();
    }
}
