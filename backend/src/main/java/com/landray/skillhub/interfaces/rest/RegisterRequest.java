package com.landray.skillhub.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /auth/register} 请求体。
 *
 * <p>长度上限对齐 domain 侧的列宽约束:{@code displayName} 64(app_user.display_name)、{@code login} 128
 * (user_identity.external_id)。密码上下限交给 application 层的 {@code PasswordPolicy} 做第二道校验
 * (业务建模文档没有给出密码策略的具体数值,这里的 8~72 是本轮实现按常识定的,详见
 * {@code domain.exception.InvalidPasswordException} 的类注释)。
 */
public record RegisterRequest(
        @NotBlank(message = "展示名不能为空") @Size(max = 64, message = "展示名长度不得超过 64 个字符") String displayName,
        @NotBlank(message = "登录名不能为空") @Size(max = 128, message = "登录名长度不得超过 128 个字符") String login,
        @NotBlank(message = "密码不能为空")
                @Size(min = 8, max = 72, message = "密码长度必须在 8~72 个字符之间")
                String password) {}
