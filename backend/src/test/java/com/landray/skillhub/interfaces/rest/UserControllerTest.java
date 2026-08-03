package com.landray.skillhub.interfaces.rest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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
import com.landray.skillhub.application.auth.UserManagementApplicationService;
import com.landray.skillhub.application.auth.UserWithIdentities;
import com.landray.skillhub.domain.exception.UserDisableNotAllowedException;
import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.AppUserStatus;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.interfaces.rest.auth.AuthenticationInterceptor;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.interfaces.rest.auth.WebMvcAuthConfig;
import com.landray.skillhub.shared.model.PageResult;

/**
 * {@code @WebMvcTest} 只验参数绑定、状态码、错误映射,mock 掉 application 层(backend/CLAUDE.md 8.1)。
 *
 * <p>整个 Controller 类标了 {@code @RequireAdmin},重点覆盖"非 ADMIN 调用 ADMIN 接口"这条安全用例。
 */
@WebMvcTest(controllers = UserController.class)
@Import({AuthenticationInterceptor.class, WebMvcAuthConfig.class, CurrentUserAccessor.class})
@TestPropertySource(properties = "server.servlet.context-path=")
class UserControllerTest {

    private static final String BASE = "/api/v1/users";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthApplicationService authApplicationService;

    @MockitoBean
    private UserManagementApplicationService userManagementApplicationService;

    private AppUser sampleUser(long id) {
        Instant now = Instant.now();
        return new AppUser(id, "用户" + id, null, null, null, UserRole.USER, AppUserStatus.ACTIVE, null, now, now);
    }

    private void stubSession(long userId, UserRole role) {
        when(authApplicationService.resolveSession("valid-token"))
                .thenReturn(Optional.of(new AuthenticatedUser(userId, role, 10L, 99L)));
    }

    @Test
    void should_returnUnauthorized_when_listCalledWithoutToken() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnForbidden_when_listCalledByNonAdmin() throws Exception {
        stubSession(2L, UserRole.USER);

        mockMvc.perform(get(BASE).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void should_returnUserList_when_listCalledByAdmin() throws Exception {
        stubSession(1L, UserRole.ADMIN);
        when(userManagementApplicationService.listUsers(1, 20, null))
                .thenReturn(new PageResult<>(List.of(new UserWithIdentities(sampleUser(2L), List.of())), 1, 1, 20));

        mockMvc.perform(get(BASE).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(2));
    }

    @Test
    void should_returnBadRequest_when_listCalledWithInvalidProviderFilter() throws Exception {
        stubSession(1L, UserRole.ADMIN);

        mockMvc.perform(get(BASE).param("provider", "NOT_A_PROVIDER").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_createUser_when_calledByAdmin() throws Exception {
        stubSession(1L, UserRole.ADMIN);
        when(userManagementApplicationService.createUser(
                        eq("新同事"), eq("new-login"), eq("a-valid-password"), isNull()))
                .thenReturn(sampleUser(5L));

        mockMvc.perform(post(BASE)
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content(
                                "{\"displayName\":\"新同事\",\"login\":\"new-login\",\"password\":\"a-valid-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void should_disableUserUsingOperatorFromSession_when_calledByAdmin() throws Exception {
        stubSession(1L, UserRole.ADMIN);

        mockMvc.perform(post(BASE + "/5/disable").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNoContent());

        verify(userManagementApplicationService).disableUser(5L, 1L);
    }

    @Test
    void should_returnBadRequest_when_disableNotAllowed() throws Exception {
        stubSession(1L, UserRole.ADMIN);
        doThrow(new UserDisableNotAllowedException("不能禁用自己"))
                .when(userManagementApplicationService)
                .disableUser(1L, 1L);

        mockMvc.perform(post(BASE + "/1/disable").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER_DISABLE_NOT_ALLOWED"));
    }

    @Test
    void should_enableUser_when_calledByAdmin() throws Exception {
        stubSession(1L, UserRole.ADMIN);

        mockMvc.perform(post(BASE + "/5/enable").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNoContent());

        verify(userManagementApplicationService).enableUser(5L);
    }
}
