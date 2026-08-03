package com.landray.skillhub.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /auth/login} 请求体。 */
public record LoginRequest(@NotBlank(message = "登录名不能为空") String login, @NotBlank(message = "密码不能为空") String password) {}
