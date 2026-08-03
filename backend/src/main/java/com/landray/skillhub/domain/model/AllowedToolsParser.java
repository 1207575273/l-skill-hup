package com.landray.skillhub.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code allowed-tools} frontmatter 字段的标量写法解析(业务建模文档 5.3 第 6 步末尾)。
 *
 * <p><b>只在括号外的逗号处切分,不能按逗号裸切。</b>它的值里合法地含逗号:
 * {@code Bash(git diff:*, git status:*)} 是一条工具声明,不是两条。裸切会把它劈成
 * {@code Bash(git diff:*} 和 {@code git status:*)} 两个残缺项,而这一列的用途正是"安装前告诉使用者这个 skill
 * 能调用什么"——切错等于在安全提示上撒谎。
 *
 * <p>YAML 列表形态({@code allowed-tools: [Read, Grep]})不经过这个类——那种形态在
 * {@code SkillPackageValidator} 里直接取 YAML 解析出的 List,每个元素独立即是一条工具声明,天然没有这个问题。
 * 这个类只处理标量写法({@code allowed-tools: Bash(...), Read, Grep}这种一整行字符串)。
 */
public final class AllowedToolsParser {

    private AllowedToolsParser() {}

    /**
     * 按"括号外逗号"切分,每段去首尾空白,丢弃切分后为空的段(防御连续逗号 / 首尾逗号)。
     *
     * @param scalar 标量形式的原始字符串,{@code null} 或空白返回空列表(该字段本身允许缺席,见业务建模文档
     *     待验证项 7)
     */
    public static List<String> parse(String scalar) {
        if (scalar == null || scalar.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenDepth = 0;

        for (int i = 0; i < scalar.length(); i++) {
            char c = scalar.charAt(i);
            if (c == '(') {
                parenDepth++;
                current.append(c);
            } else if (c == ')') {
                parenDepth = Math.max(0, parenDepth - 1);
                current.append(c);
            } else if (c == ',' && parenDepth == 0) {
                appendIfNotBlank(result, current);
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        appendIfNotBlank(result, current);

        return List.copyOf(result);
    }

    private static void appendIfNotBlank(List<String> result, StringBuilder current) {
        String trimmed = current.toString().trim();
        if (!trimmed.isEmpty()) {
            result.add(trimmed);
        }
    }
}
