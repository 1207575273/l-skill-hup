package com.landray.skillhub.interfaces.rest.tag;

/**
 * {@code POST /tags/{id}/delete} 的请求体,整体可省略。
 *
 * <p>{@code migrateToTagId} 非空时把该词条的全部引用迁到目标词条上;删一个仍被引用的 CURATED 词条时它是
 * <b>必填</b>,服务端会用 409 告诉你(业务建模文档 6.2)。必填与否取决于词条当前的 kind 与引用数,不是请求体
 * 自己能判断的事,所以这里不加 {@code @NotNull}。
 */
public record DeleteTagRequest(Long migrateToTagId) {}
