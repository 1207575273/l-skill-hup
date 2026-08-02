package com.landray.skillhub.domain.model;

import java.util.regex.Pattern;

import com.landray.skillhub.domain.exception.InvalidSkillNameException;

/**
 * skill 的唯一标识,kebab-case。
 *
 * <p>与 SKILL.md frontmatter 的 name 一致,同时**直接参与安装路径拼接**
 * （落盘到 {@code <skills 根目录>/<name>/}）。因此这里的字符集限制不只是命名规范,更是一道安全边界:
 * 允许 {@code .} 或 {@code /} 就等于允许路径穿越。修改正则前务必想清楚这一点。
 *
 * <p>规则:仅小写字母、数字与单个连字符;不得以连字符开头或结尾;不得出现连续连字符。
 */
public record SkillName(String value) {

    /** 单个连字符分隔的小写字母数字段。不允许 . / \ 空格 下划线 大写。 */
    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private static final int MAX_LENGTH = 64;

    public SkillName {
        if (value == null || value.isBlank()) {
            throw new InvalidSkillNameException("skill 名称不能为空");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidSkillNameException(
                    "skill 名称长度不得超过 " + MAX_LENGTH + " 个字符,实际 " + value.length());
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new InvalidSkillNameException(
                    "skill 名称必须是 kebab-case(小写字母、数字、单个连字符,不以连字符开头或结尾),实际为 " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
