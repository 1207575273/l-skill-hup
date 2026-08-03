package com.landray.skillhub.domain.model;

/**
 * 把用户输入的搜索关键词转成安全的 ILIKE 匹配模式。
 *
 * <p>业务建模文档 5.13 锁定 {@code keyword} 搜索用 {@code ILIKE '%kw%'};6.3 补充"注意转义 % 和 _"——
 * ILIKE 的通配符是 {@code %}(任意长度)与 {@code _}(单字符),用户搜索字面量 {@code "100%"} 若不转义,
 * {@code %} 会被当成通配符,导致匹配到几乎所有行而不是字面包含 "100%" 的行。
 *
 * <p>转义规则:先转义反斜杠本身(避免转义字符自己造成歧义),再转义 {@code %} 与 {@code _}。 配合 SQL 侧的
 * {@code ESCAPE '\'} 子句使用,产出的模式串通过 JDBC 绑定变量传递,不做任何字符串拼接 SQL。
 */
public final class LikePatternEscaper {

    private static final String ESCAPE_CHAR = "\\";
    private static final String WILDCARD_ANY = "%";
    private static final String WILDCARD_SINGLE = "_";

    private LikePatternEscaper() {}

    /**
     * @param rawKeyword 用户原始输入,空白视为"未提供关键词"
     * @return 已转义并加上前后 {@code %} 的 ILIKE 模式串;{@code rawKeyword} 为空白时返回 {@code null}
     */
    public static String toLikePattern(String rawKeyword) {
        if (rawKeyword == null || rawKeyword.isBlank()) {
            return null;
        }
        String escaped = rawKeyword
                .replace(ESCAPE_CHAR, ESCAPE_CHAR + ESCAPE_CHAR)
                .replace(WILDCARD_ANY, ESCAPE_CHAR + WILDCARD_ANY)
                .replace(WILDCARD_SINGLE, ESCAPE_CHAR + WILDCARD_SINGLE);
        return WILDCARD_ANY + escaped + WILDCARD_ANY;
    }
}
