package com.landray.skillhub.domain.model;

import java.util.Locale;

import com.landray.skillhub.domain.exception.InvalidTagKindException;

/**
 * 词条的职责分类。分类与标签合并成同一张表,靠 {@code kind} 区分职责(业务建模文档 3.5)。
 */
public enum TagKind {
    /** ADMIN 维护,进导航。每个 skill 至多 3 个,<b>没有下限</b>(业务建模文档 5.7)。 */
    CURATED(3),
    /** 任何登录用户自由打,每个 skill 至多 10 个。 */
    FREE(10);

    private final int maxPerSkill;

    TagKind(int maxPerSkill) {
        this.maxPerSkill = maxPerSkill;
    }

    /**
     * 单个 skill 能挂多少个本类词条的上限(业务建模文档 5.7)。
     *
     * <p>挂在枚举上而不是散在校验代码里:这个数字有两个消费者——上传/改标签时的数量校验,以及
     * {@code FREE -> CURATED} 转换时"会不会让某些 skill 溢出"的预判。两处各写一个 3,改上限那天必然只改一处。
     */
    public int maxPerSkill() {
        return maxPerSkill;
    }

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
