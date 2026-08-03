package com.landray.skillhub.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /identities/change-password} 请求体。 */
public record ChangePasswordRequest(
        @NotBlank(message = "原密码不能为空") String oldPassword,
        @NotBlank(message = "新密码不能为空")
                @Size(min = 8, max = 72, message = "密码长度必须在 8~72 个字符之间")
                String newPassword) {}
