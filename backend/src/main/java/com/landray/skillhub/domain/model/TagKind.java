package com.landray.skillhub.domain.model;

import java.util.Locale;

import com.landray.skillhub.domain.exception.InvalidTagKindException;

/**
 * 词条的职责分类。分类与标签合并成同一张表,靠 {@code kind} 区分职责(业务建模文档 3.5)。
 */
public enum TagKind {
    /** ADMIN 维护,进导航,每个 skill 必须 1~3 个。 */
    CURATED,
    /** 任何登录用户自由打,至多 10 个。 */
    FREE;

    /**
     * 从请求参数解析,大小写不敏感。
     *
     * @throws InvalidTagKindException 值不属于 CURATED / FREE 时
     */
    public static TagKind fromParam(String raw) {
        try {
            return TagKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidTagKindException("不支持的标签类型, kind=" + raw);
        }
    }
}
