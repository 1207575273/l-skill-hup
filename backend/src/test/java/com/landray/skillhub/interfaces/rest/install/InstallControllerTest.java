package com.landray.skillhub.interfaces.rest.install;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.landray.skillhub.application.auth.AuthApplicationService;
import com.landray.skillhub.application.install.CreateBundleResult;
import com.landray.skillhub.application.install.InstallApplicationService;
import com.landray.skillhub.domain.exception.ClientScopeNotSupportedException;
import com.landray.skillhub.domain.model.ClientCode;
import com.landray.skillhub.domain.model.ClientTarget;
import com.landray.skillhub.domain.model.InstallItem;
import com.landray.skillhub.domain.model.InstallScope;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;

/**
 * {@code @WebMvcTest} + MockMvc,mock 掉 application 层(backend/CLAUDE.md 8.1)。
 *
 * <p>不带 {@code Authorization} 头也能通过——业务建模文档 5.16:浏览、下载、生成安装单全部匿名可用,
 * 本类顺带验证"没有登录也能成功"这条权限矩阵结论,而不只是"字段绑对了"。
 */
@WebMvcTest(controllers = InstallController.class)
@TestPropertySource(properties = "server.servlet.context-path=")
@Import(CurrentUserAccessor.class)
class InstallControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InstallApplicationService installApplicationService;

    /** 与 {@code SkillControllerTest} 同样的理由:{@code AuthenticationInterceptor} 是切片必纳入的 web 层类型。 */
    @MockitoBean
    private AuthApplicationService authApplicationService;

    @Test
    @DisplayName("GET /clients 返回适配表里已实测的组合")
    void should_returnTestedClients_when_listingClients() throws Exception {
        when(installApplicationService.listSupportedClients())
                .thenReturn(List.of(new ClientTarget(ClientCode.CLAUDE, InstallScope.GLOBAL, true)));

        mockMvc.perform(get("/api/v1/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].client").value("claude"))
                .andExpect(jsonPath("$[0].scope").value("global"))
                .andExpect(jsonPath("$[0].skillsSubDir").value(".claude/skills"));
    }

    @Test
    @DisplayName("匿名(不带 Authorization 头)也能生成安装单")
    void should_createBundleAnonymously_when_noAuthorizationHeaderProvided() throws Exception {
        when(installApplicationService.createBundle(any(), eq("claude"), eq("global"), isNull()))
                .thenReturn(new CreateBundleResult(
                        "sample-token", Instant.parse("2026-09-02T00:00:00Z"), "claude", "global",
                        "http://localhost:18080/skillhub"));

        mockMvc.perform(post("/api/v1/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"items\":[{\"skillId\":1,\"versionId\":10}],\"client\":\"claude\",\"scope\":\"global\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("sample-token"))
                .andExpect(jsonPath("$.powerShellCommand")
                        .value("irm http://localhost:18080/skillhub/i/sample-token.ps1 | iex"))
                .andExpect(jsonPath("$.bashCommand")
                        .value("curl -fsSL http://localhost:18080/skillhub/i/sample-token.sh | sh"));

        verify(installApplicationService)
                .createBundle(List.of(new InstallItem(1L, 10L)), "claude", "global", null);
    }

    @Test
    @DisplayName("items 为空数组时返回 400")
    void should_return400_when_itemsIsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[],\"client\":\"claude\",\"scope\":\"global\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("(client, scope) 组合未实测时返回 400,code 为 CLIENT_SCOPE_NOT_SUPPORTED")
    void should_return400_when_clientScopeNotSupported() throws Exception {
        when(installApplicationService.createBundle(any(), eq("workbuddy"), eq("project"), isNull()))
                .thenThrow(new ClientScopeNotSupportedException("workbuddy", "project"));

        mockMvc.perform(post("/api/v1/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"items\":[{\"skillId\":1,\"versionId\":10}],\"client\":\"workbuddy\",\"scope\":\"project\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_SCOPE_NOT_SUPPORTED"));
    }

    @Test
    @DisplayName("引用不存在的 skill 版本时返回 404")
    void should_return404_when_skillVersionNotFound() throws Exception {
        when(installApplicationService.createBundle(any(), eq("claude"), eq("global"), isNull()))
                .thenThrow(new ResourceNotFoundException("SKILL_VERSION_NOT_FOUND", "skill 版本不存在"));

        mockMvc.perform(post("/api/v1/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"items\":[{\"skillId\":1,\"versionId\":999}],\"client\":\"claude\",\"scope\":\"global\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SKILL_VERSION_NOT_FOUND"));
    }
}
