package com.landray.skillhub.application.install;

import java.time.Instant;

/** {@code POST /bundles} 的用例结果,interfaces 层据此拼装响应体(含可直接复制粘贴的安装命令)。 */
public record CreateBundleResult(
        String token, Instant expiresAt, String client, String scope, String publicBaseUrl) {}
