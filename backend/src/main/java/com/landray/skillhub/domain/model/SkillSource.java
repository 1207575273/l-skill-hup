package com.landray.skillhub.domain.model;

import java.util.Locale;

import com.landray.skillhub.domain.exception.InvalidSkillSourceException;

/**
 * skill 的来源,单选互斥。
 *
 * <p>业务建模文档 3.5:"企业内部 / 社区"回答的是这个 skill 从哪来、可信度如何,是单选且互斥的维度,
 * 因此建成枚举列而不是标签。
 */
public enum SkillSource {
    /** 企业内部沉淀。 */
    INTERNAL,
    /** 社区搬运。 */
    COMMUNITY;

    /**
     * 从请求参数解析,大小写不敏感。
     *
     * @throws InvalidSkillSourceException 值不属于 INTERNAL / COMMUNITY 时
     */
    public static SkillSource fromParam(String raw) {
        try {
            return SkillSource.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidSkillSourceException("不支持的来源筛选, source=" + raw);
        }
    }
}
