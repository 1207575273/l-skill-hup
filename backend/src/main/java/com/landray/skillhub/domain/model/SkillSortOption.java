package com.landray.skillhub.domain.model;

import java.util.Locale;

import com.landray.skillhub.domain.exception.InvalidSkillSortException;

/**
 * {@code GET /skills} 的排序方式(业务建模文档 6.2)。
 *
 * <p>{@link #LATEST} 取 {@code skill.updated_at} 倒序——业务建模文档 7.1 明确"更新日期必须显示,是选 skill
 * 的关键判据",默认排序落在同一个信号上是自然选择。这是文档未明写、由本次实现自行拍板的一处决策。
 */
public enum SkillSortOption {
    /** 按 download_count 降序。 */
    DOWNLOADS,
    /** 按 like_count 降序。 */
    LIKES,
    /** 按 updated_at 降序,未指定 sort 时的默认值。 */
    LATEST;

    public static final SkillSortOption DEFAULT = LATEST;

    /**
     * 从请求参数解析,大小写不敏感;为空时回落到默认值 {@link #LATEST}。
     *
     * @throws InvalidSkillSortException 值不属于 DOWNLOADS / LIKES / LATEST 时
     */
    public static SkillSortOption fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        try {
            return SkillSortOption.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidSkillSortException("不支持的排序方式, sort=" + raw);
        }
    }
}
