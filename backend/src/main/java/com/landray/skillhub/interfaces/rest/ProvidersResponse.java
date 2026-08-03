package com.landray.skillhub.interfaces.rest;

import java.util.List;

/**
 * {@code GET /auth/providers}:本站启用了哪些登录方式,以及自助注册是否开启。
 *
 * <p>让服务端告诉前端有哪些按钮,而不是前端硬编码一个点了就报错的选项(业务建模文档 6.2 对 providers
 * 的原话,同样的道理延伸到 {@code localRegisterEnabled}——前端要不要显示"注册"入口,不该由前端猜配置)。
 */
public record ProvidersResponse(List<String> providers, boolean localRegisterEnabled) {}
