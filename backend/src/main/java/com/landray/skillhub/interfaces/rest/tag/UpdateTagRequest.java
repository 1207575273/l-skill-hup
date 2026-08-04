package com.landray.skillhub.interfaces.rest.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /tags/{id}/update} 的请求体。
 *
 * <p>{@code kind} 缺省表示"不动它",不是"改成默认值"——治理界面上改个描述不该顺手把 kind 也重置了。
 *
 * <p>{@code confirmed} 只对 {@code CURATED -> FREE} 有意义:那个方向会让一批 skill 从导航里消失,服务端第一次
 * 会带受影响清单返回 409,ADMIN 看过后带 {@code true} 再提交一次(业务建模文档 5.7:从"拒绝"降级为"告知")。
 */
public record UpdateTagRequest(
        @NotBlank(message = "标签名不能为空") @Size(max = 32, message = "标签名过长") String name,
        @Pattern(regexp = "CURATED|FREE|curated|free", message = "kind 必须是 CURATED 或 FREE") String kind,
        @Size(max = 128, message = "描述不得超过 128 字符") String description,
        Integer sortOrder,
        Boolean confirmed) {}
