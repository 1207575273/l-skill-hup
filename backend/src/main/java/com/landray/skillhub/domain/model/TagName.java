package com.landray.skillhub.domain.model;

import com.landray.skillhub.domain.exception.TagNameInvalidException;

/**
 * 词条名。归一化规则来自业务建模文档 5.7:<b>去首尾空白、折叠中间连续空白、长度上限 16 字符</b>
 * (表的列宽 32 是余量,不是允许值)。
 *
 * <p>做成值对象而不是散在各处的 {@code trim().replaceAll(...)}:同一条规则有两个入口——用户打 FREE 标签
 * ({@code SkillTagAssignmentService})和 ADMIN 建 / 改 CURATED 词条({@code TagGovernanceApplicationService})。
 * 两边各写一份的下场是迟早漂开一个空格的处理差异,而那会让"同一个名字"在 {@code uk_tag_name} 眼里变成两条,
 * 于是标签开始悄悄分裂——正是 5.7 要防的那件事。这符合 backend/CLAUDE.md 第二节对值对象的判定:
 * <b>只给真正带业务规则的概念做</b>,不是给裸 String 套壳。
 *
 * <p>长度按<b>归一化之后</b>算。先量后修的话,一个前后各带十个空格的两字标签会被判超长,而用户看不出哪里超了。
 */
public record TagName(String value) {

    /** 应用层上限;列宽 32 是余量,不是允许值(业务建模文档 4.6)。 */
    public static final int MAX_LENGTH = 16;

    public TagName {
        if (value == null) {
            throw new TagNameInvalidException("标签名不能为空");
        }
        value = value.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) {
            throw new TagNameInvalidException("标签名不能为空");
        }
        if (value.length() > MAX_LENGTH) {
            throw new TagNameInvalidException("标签名长度不得超过 " + MAX_LENGTH + " 个字符, name=" + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
