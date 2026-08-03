package com.landray.skillhub.domain.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.landray.skillhub.domain.exception.InvalidSkillVersionException;

/**
 * skill 版本号,SemVer 数值序(业务建模文档 5.4)。
 *
 * <p>只支持 {@code major.minor.patch} 三段非负整数,**不支持预发布标识与构建元数据**——内部平台用不上,
 * 支持了就要定义它们的排序规则,徒增复杂度。比较用数值序而不是字符串序:{@code "10.0.0"} 必须大于
 * {@code "9.0.0"},字符串序会判反(见 {@link #compareTo}的单测)。
 */
public record SemVer(int major, int minor, int patch, String raw) implements Comparable<SemVer> {

    /** 与业务建模文档 5.4 逐字一致:三段非负整数,不允许前导零(单独的 {@code 0} 除外)。 */
    private static final Pattern PATTERN = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");

    /**
     * 解析 SemVer 字符串。
     *
     * @throws InvalidSkillVersionException 格式不合时,对应 {@code SKILL_VERSION_INVALID}
     */
    public static SemVer parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidSkillVersionException("版本号不能为空");
        }
        Matcher matcher = PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            throw new InvalidSkillVersionException(
                    "版本号必须是 SemVer 格式 major.minor.patch(均为非负整数,不允许前导零),实际为 " + value);
        }
        return new SemVer(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                value.trim());
    }

    @Override
    public int compareTo(SemVer other) {
        int majorCompare = Integer.compare(this.major, other.major);
        if (majorCompare != 0) {
            return majorCompare;
        }
        int minorCompare = Integer.compare(this.minor, other.minor);
        if (minorCompare != 0) {
            return minorCompare;
        }
        return Integer.compare(this.patch, other.patch);
    }

    /** 是否严格大于另一个版本——发布新版本的核心校验(业务建模文档 5.4 第 3 条)。 */
    public boolean isGreaterThan(SemVer other) {
        return compareTo(other) > 0;
    }

    @Override
    public String toString() {
        return raw;
    }
}
