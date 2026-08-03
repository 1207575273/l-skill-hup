package com.landray.skillhub.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.landray.skillhub.application.auth.AuthApplicationService;
import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.application.auth.LoginResult;
import com.landray.skillhub.domain.exception.InvalidCredentialException;
import com.landray.skillhub.domain.exception.LocalRegisterDisabledException;
import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.AppUserStatus;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.interfaces.rest.auth.AuthenticationInterceptor;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.interfaces.rest.auth.WebMvcAuthConfig;

/**
 * {@code @WebMvcTest} 只验参数绑定、状态码、错误映射,mock 掉 application 层(backend/CLAUDE.md 8.1)。
 *
 * <p>{@code @Import} 真实的认证支持类(拦截器 / 注册配置 / 当前用户访问器),不 mock 它们——这样才能验证
 * "默认放行、端点声明要求"这套机制本身真的接上了,而不只是验证 Controller 方法体自己的逻辑。
 */
@WebMvcTest(controllers = AuthController.class)
@Import({AuthenticationInterceptor.class, WebMvcAuthConfig.class, CurrentUserAccessor.class})
@TestPropertySource(properties = "server.servlet.context-path=")
class AuthControllerTest {

    private static final String BASE = "/api/v1/auth";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthApplicationService authApplicationService;

    private AppUser sampleUser() {
        Instant now = Instant.now();
        return new AppUser(1L, "张三", null, null, null, UserRole.USER, AppUserStatus.ACTIVE, null, now, now);
    }

    @Test
    void should_returnTokenAndUserInfo_when_loginSucceeds() throws Exception {
        when(authApplicationService.login(anyString(), anyString(), any(), any()))
                .thenReturn(new LoginResult("raw-token", Instant.now().plusSeconds(3600), sampleUser(), 10L));

        mockMvc.perform(post(BASE + "/login")
                        .contentType("application/json")
                        .content("{\"login\":\"zhangsan\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("raw-token"))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.displayName").value("张三"));
    }

    @Test
    void should_returnBadRequest_when_loginRequestBodyBlank() throws Exception {
        mockMvc.perform(post(BASE + "/login")
                        .contentType("application/json")
                        .content("{\"login\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void should_returnUnauthorized_when_loginServiceThrowsInvalidCredential() throws Exception {
        when(authApplicationService.login(anyString(), anyString(), any(), any()))
                .thenThrow(new InvalidCredentialException());

        mockMvc.perform(post(BASE + "/login")
                        .contentType("application/json")
                        .content("{\"login\":\"zhangsan\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void should_returnForbidden_when_registerServiceThrowsLocalRegisterDisabled() throws Exception {
        when(authApplicationService.register(anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new LocalRegisterDisabledException());

        mockMvc.perform(post(BASE + "/register")
                        .contentType("application/json")
                        .content(
                                "{\"displayName\":\"新用户\",\"login\":\"newlogin\",\"password\":\"a-valid-password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOCAL_REGISTER_DISABLED"));
    }

    @Test
    void should_returnProvidersWithoutAuth_when_providersCalledAnonymously() throws Exception {
        when(authApplicationService.enabledProviders()).thenReturn(List.of("LOCAL"));
        when(authApplicationService.isLocalRegisterEnabled()).thenReturn(false);

        mockMvc.perform(get(BASE + "/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[0]").value("LOCAL"))
                .andExpect(jsonPath("$.localRegisterEnabled").value(false));
    }

    @Test
    void should_returnUnauthorized_when_logoutCalledWithoutToken() throws Exception {
        mockMvc.perform(post(BASE + "/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void should_returnNoContent_when_logoutCalledWithValidToken() throws Exception {
        when(authApplicationService.resolveSession("valid-token"))
                .thenReturn(Optional.of(new AuthenticatedUser(1L, UserRole.USER, 10L, 99L)));

        mockMvc.perform(post(BASE + "/logout").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void should_returnUnauthorized_when_meCalledWithExpiredToken() throws Exception {
        when(authApplicationService.resolveSession("expired-token")).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE + "/me").header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void should_returnMeProfile_when_meCalledWithValidToken() throws Exception {
        when(authApplicationService.resolveSession("valid-token"))
                .thenReturn(Optional.of(new AuthenticatedUser(1L, UserRole.USER, 10L, 99L)));
        when(authApplicationService.getUser(1L)).thenReturn(sampleUser());
        when(authApplicationService.listIdentities(1L)).thenReturn(List.of());

        mockMvc.perform(get(BASE + "/me").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.displayName").value("张三"));
    }
}
