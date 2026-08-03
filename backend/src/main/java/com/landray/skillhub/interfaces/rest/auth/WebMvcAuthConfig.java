package com.landray.skillhub.interfaces.rest.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册 {@link AuthenticationInterceptor}。
 *
 * <p>放在 interfaces 层而不是 infrastructure/config——它引用的 {@link AuthenticationInterceptor} 本身就住在
 * interfaces,而 ArchUnit 的分层规则里 interfaces "不被任何层访问",意味着 infrastructure 不能反过来依赖
 * interfaces 里的类;整条认证边界(注解 + 拦截器 + 注册配置 + 当前用户访问器)因此整体收在 interfaces 层,
 * 与 {@code TraceIdFilter} 放在这里的理由一致——都站在系统的 HTTP 边界上。
 */
@Configuration
public class WebMvcAuthConfig implements WebMvcConfigurer {

    private final AuthenticationInterceptor authenticationInterceptor;

    public WebMvcAuthConfig(AuthenticationInterceptor authenticationInterceptor) {
        this.authenticationInterceptor = authenticationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticationInterceptor);
    }
}
