package com.landray.skillhub.shared.exception;

/**
 * 通用的"未认证"异常,统一映射为 HTTP 401。
 *
 * <p>覆盖三种场景:请求未带 {@code Authorization} 头、token 在库里查不到、token 已过期或被撤销——三者对客户端
 * 而言都是同一个动作("请重新登录"),刻意不再细分错误码,以免前端要为每种失效原因各写一套分支,同时也避免把
 * "token 到底是哪种失效方式"这类内部细节暴露给调用方。
 *
 * <p>放在 shared 而不是 domain:未认证是跨越具体业务实体的通用 HTTP 边界语义,由 interfaces 层的认证拦截器抛出,
 * 不属于 {@code UserSession} 自己的领域规则(领域规则是 {@code UserSession.isValid(Instant)} 这个纯粹的判定
 * 方法,不负责决定"查不到/过期该返回什么错误码"——那是 HTTP 边界的关注点)。
 */
public class UnauthenticatedException extends BusinessException {

    private static final String CODE = "UNAUTHENTICATED";
    private static final int HTTP_STATUS_UNAUTHORIZED = 401;

    public UnauthenticatedException(String message) {
        super(CODE, message, HTTP_STATUS_UNAUTHORIZED);
    }
}
