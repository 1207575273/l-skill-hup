package com.landray.skillhub.domain.model;

import java.util.List;

import com.landray.skillhub.domain.exception.InvalidSkillSortException;

/**
 * {@code GET /skills} 的查询条件,由 application 层规范化后构造。
 *
 * <p>刻意不在这里做 {@code page} / {@code size} 的默认值与夹紧(20 默认、100 上限)——那属于"解释外部输入"的
 * application 层职责(业务建模文档 6.1)。本记录的紧凑构造器只守住"值一旦构造出来就必须合法"这条不变量,
 * 不负责把非法输入变成合法输入。
 *
 * @param source 来源筛选,{@code null} 表示不筛
 * @param tagIds 标签筛选,多值 AND;{@code null} 或空表示不筛
 * @param certifiedOnly 只看已认证(最新版本 CERTIFIED)
 * @param keyword 原始关键词(未转义),转义交给 {@link LikePatternEscaper}
 * @param ownerUserId 归属人筛选,{@code null} 表示不筛
 * @param sort 排序方式,不允许为 {@code null}
 * @param page 页码,从 1 开始
 * @param size 每页条数,上限 100
 */
public record SkillSearchCriteria(
        SkillSource source,
        List<Long> tagIds,
        boolean certifiedOnly,
        String keyword,
        Long ownerUserId,
        SkillSortOption sort,
        int page,
        int size) {

    private static final int MIN_PAGE = 1;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    public SkillSearchCriteria {
        if (sort == null) {
            throw new InvalidSkillSortException("排序方式不能为空");
        }
        if (page < MIN_PAGE) {
            throw new IllegalArgumentException("page 必须 >= " + MIN_PAGE + ",实际为 " + page);
        }
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException("size 必须在 [" + MIN_SIZE + ", " + MAX_SIZE + "] 之间,实际为 " + size);
        }
    }

    /** LIMIT/OFFSET 分页的 offset,由 page/size 推导,不单独作为构造参数暴露。 */
    public int offset() {
        return (page - 1) * size;
    }
}
