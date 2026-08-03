package com.landray.skillhub.interfaces.rest;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.landray.skillhub.application.auth.AuthApplicationService;
import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.application.auth.LoginResult;
import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.UserIdentity;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.interfaces.rest.auth.RequireLogin;

/**
 * 认证接口:登录 / 登出 / 当前登录者 / 登录方式列表 / 注册(业务建模文档 6.2"认证"一组)。
 *
 * <p>薄控制器:只收参、调 application、返 DTO,不写业务、不碰 SQL、不 try-catch 业务异常
 * (backend/CLAUDE.md 第一节)。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final AuthApplicationService authApplicationService;
    private final CurrentUserAccessor currentUserAccessor;

    public AuthController(AuthApplicationService authApplicationService, CurrentUserAccessor currentUserAccessor) {
        this.authApplicationService = authApplicationService;
        this.currentUserAccessor = currentUserAccessor;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResult result = authApplicationService.login(
                request.login(),
                request.password(),
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                resolveClientIp(httpRequest));
        return LoginResponse.from(result);
    }

    @PostMapping("/register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        LoginResult result = authApplicationService.register(
                request.displayName(),
                request.login(),
                request.password(),
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                resolveClientIp(httpRequest));
        return LoginResponse.from(result);
    }

    @RequireLogin
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        AuthenticatedUser current = currentUserAccessor.require();
        authApplicationService.logout(current.sessionId());
        return ResponseEntity.noContent().build();
    }

    @RequireLogin
    @GetMapping("/me")
    public MeResponse me() {
        AuthenticatedUser current = currentUserAccessor.require();
        AppUser user = authApplicationService.getUser(current.userId());
        List<UserIdentity> identities = authApplicationService.listIdentities(current.userId());
        return MeResponse.from(user, identities);
    }

    @GetMapping("/providers")
    public ProvidersResponse providers() {
        return new ProvidersResponse(
                authApplicationService.enabledProviders(), authApplicationService.isLocalRegisterEnabled());
    }

    /**
     * 优先取 {@code X-Forwarded-For} 的第一段(内网若有反向代理,真实来源在这里),取不到再回落到
     * {@link HttpServletRequest#getRemoteAddr()}。只用于登录审计留痕,不是安全校验依据(该头可被客户端伪造)。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(X_FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
