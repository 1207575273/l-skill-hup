package com.landray.skillhub.shared.model;

import java.util.List;
import java.util.function.Function;

/**
 * 通用分页结果:{@code items / total / page / size}(业务建模文档 6.1)。
 *
 * <p>放在 {@code shared} 而不是某个实体的 {@code domain} 包下:分页是跨实体的通用读侧概念,不属于任何单一
 * 领域模型自己的规则。它同时被 {@code domain.repository} 的查询返回类型和 {@code interfaces.rest} 的响应体复用
 * ——{@link #map(Function)} 就是为了让 interfaces 层能把 {@code PageResult<Skill>} 原地转成
 * {@code PageResult<SkillSummaryResponse>} 而不用另起一个结构相同的响应包装类型。
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), total, page, size);
    }
}
