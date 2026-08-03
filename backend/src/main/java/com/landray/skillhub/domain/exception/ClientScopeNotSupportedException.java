package com.landray.skillhub.domain.exception;

import java.util.Map;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 请求的 {@code (client, scope)} 组合未在适配表里标记为已实测(业务建模文档 5.9)。
 *
 * <p>典型场景:{@code (workbuddy, project)}——全局路径已实测,项目级路径未经本地验证,不许猜。
 */
public class ClientScopeNotSupportedException extends BusinessException {

    private static final String CODE = "CLIENT_SCOPE_NOT_SUPPORTED";

    public ClientScopeNotSupportedException(String client, String scope) {
        super(
                CODE,
                "该客户端与安装级别组合暂不支持: client=" + client + ", scope=" + scope,
                DEFAULT_HTTP_STATUS,
                Map.of("client", String.valueOf(client), "scope", String.valueOf(scope)));
    }
}
