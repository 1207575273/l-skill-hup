package com.landray.skillhub.interfaces.rest.install;

import java.time.Instant;

import com.landray.skillhub.application.install.CreateBundleResult;

/**
 * {@code POST /bundles} 响应体。
 *
 * <p>直接给出可复制粘贴的一条命令(而不是只给 token 让前端自己拼)——这个平台唯一的价值就是"选 skill -> 一条命令",
 * 拼接规则只值得在服务端写一遍。
 */
public record CreateBundleResponse(
        String token,
        Instant expiresAt,
        String client,
        String scope,
        String powerShellCommand,
        String bashCommand) {

    public static CreateBundleResponse from(CreateBundleResult result) {
        String base = result.publicBaseUrl().endsWith("/")
                ? result.publicBaseUrl().substring(0, result.publicBaseUrl().length() - 1)
                : result.publicBaseUrl();
        String powerShellCommand = "irm " + base + "/i/" + result.token() + ".ps1 | iex";
        String bashCommand = "curl -fsSL " + base + "/i/" + result.token() + ".sh | sh";
        return new CreateBundleResponse(
                result.token(), result.expiresAt(), result.client(), result.scope(), powerShellCommand,
                bashCommand);
    }
}
