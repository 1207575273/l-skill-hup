package com.landray.skillhub.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.landray.skillhub.domain.model.UserRole;

/**
 * {@code POST /users}:ADMIN 直接建号,P0 唯一开户途径(业务建模文档 3.2)。
 *
 * <p>{@code role} 可空,缺省时 application 层按 {@code UserRole.USER} 处理——多数场景是给普通同事开户,
 * 显式传 {@code ADMIN} 才需要调用方多写一个字段,不是反过来。
 */
public record CreateUserRequest(
        @NotBlank(message = "展示名不能为空") @Size(max = 64, message = "展示名长度不得超过 64 个字符") String displayName,
        @NotBlank(message = "登录名不能为空") @Size(max = 128, message = "登录名长度不得超过 128 个字符") String login,
        @NotBlank(message = "密码不能为空")
                @Size(min = 8, max = 72, message = "密码长度必须在 8~72 个字符之间")
                String password,
        UserRole role) {}
