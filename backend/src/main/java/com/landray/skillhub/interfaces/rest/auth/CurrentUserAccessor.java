package com.landray.skillhub.interfaces.rest.auth;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.shared.exception.UnauthenticatedException;

/**
 * 请求范围内"当前是谁"的读写入口:{@link AuthenticationInterceptor} 解析成功后写入,Controller 读取。
 *
 * <p>用 {@link HttpServletRequest} 属性而不是 {@code ThreadLocal} 自己维护——请求属性天然跟着请求生命周期
 * 走,不需要额外操心"什么时候清理"这类问题(对比手写 ThreadLocal 在线程池复用场景下必须记得 remove,否则会像
 * MDC 没清一样串到下一个请求上)。
 */
@Component
public class CurrentUserAccessor {

    private static final String ATTRIBUTE_NAME = CurrentUserAccessor.class.getName() + ".AUTHENTICATED_USER";

    /** 供 {@link AuthenticationInterceptor} 在校验通过后写入。 */
    void bind(HttpServletRequest request, AuthenticatedUser user) {
        request.setAttribute(ATTRIBUTE_NAME, user);
    }

    public Optional<AuthenticatedUser> current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        Object value = attributes.getAttribute(ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST);
        return Optional.ofNullable((AuthenticatedUser) value);
    }

    /**
     * 在标了 {@code @RequireLogin}/{@code @RequireAdmin} 的端点里调用,拦截器已经保证走到这里时一定有值。
     * 万一出现(拦截器配置遗漏之类的编程错误),不能静默返回 null 让 NPE 在业务代码里到处出现,而是转成一个
     * 明确的未认证响应——同时这也是"注解漏标"这类回归的一道安全网。
     */
    public AuthenticatedUser require() {
        return current().orElseThrow(() -> new UnauthenticatedException("当前接口需要登录,但未解析到有效会话"));
    }
}
