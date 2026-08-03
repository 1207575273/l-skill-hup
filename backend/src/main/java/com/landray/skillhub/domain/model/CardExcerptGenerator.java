package com.landray.skillhub.domain.model;

import java.util.regex.Pattern;

/**
 * 卡片摘要生成(业务建模文档 5.6):从 README(优先)或 SKILL.md 正文提取前 200 字符的纯文本,发布时算一次存库,
 * 前端不重复实现——校验才是安全边界要求"服务端重做一遍",摘要只是展示优化,算一遍就够。
 *
 * <p>纯文本化规则逐条对应文档 5.6 那张表,顺序也一致:frontmatter -&gt; 代码围栏 -&gt; 纯 badge 行 -&gt; 图片
 * -&gt; 链接 -&gt; 标题前缀 -&gt; 引用前缀 -&gt; 列表前缀 -&gt; 行内代码 -&gt; 强调 -&gt; HTML 标签 -&gt; 折叠空白。
 * <b>取前 200 字符,不加任何后缀</b>——列宽就是 200,加省略号会导致 {@code value too long}。
 */
public final class CardExcerptGenerator {

    private static final int MAX_LENGTH = 200;

    private static final Pattern FRONTMATTER = Pattern.compile("\\A---.*?\\n---\\n", Pattern.DOTALL);
    private static final Pattern CODE_FENCE = Pattern.compile("```.*?```", Pattern.DOTALL);
    private static final Pattern BADGE_ONLY_LINE = Pattern.compile("(?m)^\\s*(\\[!\\[.*?\\]\\(.*?\\)\\]\\(.*?\\)|!\\[.*?\\]\\(.*?\\))\\s*$");
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern HEADING_PREFIX = Pattern.compile("(?m)^#{1,6}\\s*");
    private static final Pattern QUOTE_PREFIX = Pattern.compile("(?m)^>\\s*");
    private static final Pattern LIST_PREFIX = Pattern.compile("(?m)^\\s*(?:[-*]|\\d+\\.)\\s+");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]*)`");
    private static final Pattern EMPHASIS = Pattern.compile("(\\*\\*\\*|\\*\\*|\\*|~~)([^*~]+?)\\1");
    private static final Pattern HTML_TAG = Pattern.compile("</?[a-zA-Z][^>]*>");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private CardExcerptGenerator() {}

    /**
     * @param markdown 原始 Markdown 全文(README.md 或 SKILL.md 正文)
     * @return 纯文本化并截断到 200 字符的摘要;输入为空或处理后为空文本时返回 {@code null}
     */
    public static String generate(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return null;
        }
        String text = markdown;
        text = FRONTMATTER.matcher(text).replaceFirst("");
        text = CODE_FENCE.matcher(text).replaceAll("");
        text = BADGE_ONLY_LINE.matcher(text).replaceAll("");
        text = IMAGE.matcher(text).replaceAll("");
        text = LINK.matcher(text).replaceAll("$1");
        text = HEADING_PREFIX.matcher(text).replaceAll("");
        text = QUOTE_PREFIX.matcher(text).replaceAll("");
        text = LIST_PREFIX.matcher(text).replaceAll("");
        text = INLINE_CODE.matcher(text).replaceAll("$1");
        text = EMPHASIS.matcher(text).replaceAll("$2");
        text = HTML_TAG.matcher(text).replaceAll("");
        text = WHITESPACE_RUN.matcher(text).replaceAll(" ").trim();

        if (text.isEmpty()) {
            return null;
        }
        return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text;
    }
}
