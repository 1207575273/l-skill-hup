package com.landray.skillhub.interfaces.rest;

import static org.mockito.ArgumentMatchers.eq;
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
import com.landray.skillhub.domain.exception.OldPasswordMismatchException;
import com.landray.skillhub.domain.model.IdentityProvider;
import com.landray.skillhub.domain.model.IdentityStatus;
import com.landray.skillhub.domain.model.UserIdentity;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.interfaces.rest.auth.AuthenticationInterceptor;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.interfaces.rest.auth.WebMvcAuthConfig;

/** {@code @WebMvcTest} 只验参数绑定、状态码、错误映射,mock 掉 application 层(backend/CLAUDE.md 8.1)。 */
@WebMvcTest(controllers = IdentityController.class)
@Import({AuthenticationInterceptor.class, WebMvcAuthConfig.class, CurrentUserAccessor.class})
@TestPropertySource(properties = "server.servlet.context-path=")
class IdentityControllerTest {

    private static final String BASE = "/api/v1/identities";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthApplicationService authApplicationService;

    private void stubValidSession() {
        when(authApplicationService.resolveSession("valid-token"))
                .thenReturn(Optional.of(new AuthenticatedUser(1L, UserRole.USER, 10L, 99L)));
    }

    @Test
    void should_returnUnauthorized_when_listCalledWithoutToken() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnIdentityList_when_listCalledWithValidToken() throws Exception {
        stubValidSession();
        Instant now = Instant.now();
        UserIdentity identity = new UserIdentity(
                10L, 1L, IdentityProvider.LOCAL, "zhangsan", "hash", null, 0, null, now, IdentityStatus.ACTIVE, now,
                now);
        when(authApplicationService.listIdentities(1L)).thenReturn(List.of(identity));

        mockMvc.perform(get(BASE).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalId").value("zhangsan"))
                .andExpect(jsonPath("$[0].provider").value("LOCAL"));
    }

    @Test
    void should_changePasswordUsingSessionIdentityId_when_requestValid() throws Exception {
        stubValidSession();

        mockMvc.perform(post(BASE + "/change-password")
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"oldPassword\":\"old-pass-1\",\"newPassword\":\"new-password-1\"}"))
                .andExpect(status().isNoContent());

        verify(authApplicationService).changePassword(eq(10L), eq("old-pass-1"), eq("new-password-1"));
    }

    @Test
    void should_returnBadRequest_when_oldPasswordMismatch() throws Exception {
        stubValidSession();
        doThrow(new OldPasswordMismatchException())
                .when(authApplicationService)
                .changePassword(eq(10L), eq("wrong-old"), eq("new-password-1"));

        mockMvc.perform(post(BASE + "/change-password")
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"oldPassword\":\"wrong-old\",\"newPassword\":\"new-password-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OLD_PASSWORD_INVALID"));
    }
}
