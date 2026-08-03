package com.landray.skillhub.interfaces.rest;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一错误响应体:{@code code / message / traceId / details}。
 *
 * <p>结构由业务建模文档 6.1 定死:{@code code} 是稳定的机器可读枚举,{@code message} 面向人,{@code traceId} 必填,
 * {@code details} 可选(为空时不参与序列化)。
 *
 * <p>{@code @JsonInclude} 来自 {@code jackson-annotations} 模块。Jackson 3 只重命名了 core / databind(处理引擎本身)
 * 的坐标为 {@code tools.jackson..},{@code jackson-annotations} 官方决定不重新发布、继续留在
 * {@code com.fasterxml.jackson.annotation} 包下——本项目引入的 {@code tools.jackson.core:jackson-databind:3.1.4} 在
 * 它自己的 POM 里就编译期依赖着 {@code com.fasterxml.jackson.core:jackson-annotations:2.21}。因此这里 import 的
 * {@code com.fasterxml.jackson.*} 不是 Jackson 2 残留,而是 Jackson 3 官方仍在用的注解模块;对应的 ArchUnit 规则
 * 只禁 {@code com.fasterxml.jackson.core..} / {@code com.fasterxml.jackson.databind..}(处理引擎),不禁
 * {@code com.fasterxml.jackson.annotation..},否则连 {@code @JsonInclude} 都用不了。
 */
public record ErrorResponse(
        String code,
        String message,
        String traceId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> details) {

    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(code, message, traceId, null);
    }

    public static ErrorResponse of(String code, String message, String traceId, Map<String, Object> details) {
        Map<String, Object> normalizedDetails = details == null || details.isEmpty() ? null : details;
        return new ErrorResponse(code, message, traceId, normalizedDetails);
    }
}
