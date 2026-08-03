package com.landray.skillhub.interfaces.rest.auth;

import java.lang.annotation.Annotation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.landray.skillhub.application.auth.AuthApplicationService;
import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.shared.exception.AccessDeniedException;
import com.landray.skillhub.shared.exception.UnauthenticatedException;

/**
 * 认证拦截器:默认放行,只有标了 {@link RequireLogin} / {@link RequireAdmin} 的端点才会被拦(业务建模文档
 * 权限矩阵 5.16)。这是"default open, endpoint declares requirement"的核心实现——不做"默认全拦、
 * 白名单放行"那种形态,因为匿名可浏览 / 可下载 / 可生成安装单的接口占多数,写白名单反而更容易漏。
 *
 * <p>在这里抛出的 {@link UnauthenticatedException} / {@link AccessDeniedException} 会被
 * {@code GlobalExceptionHandler} 正常捕获——Spring MVC 的 {@code DispatcherServlet} 把
 * {@link HandlerInterceptor#preHandle} 与 Controller 方法执行包在同一个异常处理范围内,不需要在这里
 * 自己 catch 并手写响应体。
 */
@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationInterceptor.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthApplicationService authApplicationService;
    private final CurrentUserAccessor currentUserAccessor;

    public AuthenticationInterceptor(
            AuthApplicationService authApplicationService, CurrentUserAccessor currentUserAccessor) {
        this.authApplicationService = authApplicationService;
        this.currentUserAccessor = currentUserAccessor;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            // 静态资源等非 Controller 处理器,不需要认证判断。
            return true;
        }

        boolean requiresAdmin = hasAnnotation(handlerMethod, RequireAdmin.class);
        boolean requiresLogin = requiresAdmin || hasAnnotation(handlerMethod, RequireLogin.class);
        if (!requiresLogin) {
            return true;
        }

        String rawToken = extractBearerToken(request);
        if (rawToken == null) {
            log.warn("请求缺少合法的 Authorization 头, path={}", request.getRequestURI());
            throw new UnauthenticatedException("请先登录");
        }

        AuthenticatedUser user = authApplicationService
                .resolveSession(rawToken)
                .orElseThrow(() -> {
                    log.warn("会话解析失败(不存在/已过期/已撤销/所属账号被禁用), path={}", request.getRequestURI());
                    return new UnauthenticatedException("登录状态已失效,请重新登录");
                });

        if (requiresAdmin && !user.isAdmin()) {
            log.warn(
                    "权限不足,该接口要求 ADMIN, path={}, userId={}, role={}",
                    request.getRequestURI(),
                    user.userId(),
                    user.role());
            throw new AccessDeniedException("需要管理员权限");
        }

        currentUserAccessor.bind(request, user);
        return true;
    }

    private boolean hasAnnotation(HandlerMethod handlerMethod, Class<? extends Annotation> annotationType) {
        return handlerMethod.hasMethodAnnotation(annotationType)
                || handlerMethod.getBeanType().isAnnotationPresent(annotationType);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isBlank() ? null : token;
    }
}
