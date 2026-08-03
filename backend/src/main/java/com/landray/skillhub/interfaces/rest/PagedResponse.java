package com.landray.skillhub.interfaces.rest;

import java.util.List;
import java.util.function.Function;

import com.landray.skillhub.shared.model.PageResult;

/** 通用分页响应体:{@code {items, total, page, size}}(业务建模文档 6.1)。 */
public record PagedResponse<T>(List<T> items, long total, int page, int size) {

    public static <S, T> PagedResponse<T> from(PageResult<S> source, Function<S, T> mapper) {
        return new PagedResponse<>(
                source.items().stream().map(mapper).toList(), source.total(), source.page(), source.size());
    }
}
