package com.landray.skillhub.interfaces.rest.install;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.landray.skillhub.application.auth.AuthApplicationService;
import com.landray.skillhub.application.install.InstallApplicationService;
import com.landray.skillhub.application.install.RenderedScript;
import com.landray.skillhub.domain.exception.InstallBundleExpiredException;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;

/**
 * {@code @WebMvcTest} + MockMvc,mock 掉 application 层,只验路由匹配({@code {token}.ps1} 后缀模式)、
 * 状态码、Content-Type(backend/CLAUDE.md 8.1)。渲染出的脚本文本内容由
 * {@code InstallScriptRendererTest} 与端到端集成测试覆盖,这里不重复断言。
 */
@WebMvcTest(controllers = InstallScriptController.class)
@TestPropertySource(properties = "server.servlet.context-path=")
@Import(CurrentUserAccessor.class)
class InstallScriptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InstallApplicationService installApplicationService;

    @MockitoBean
    private AuthApplicationService authApplicationService;

    @Test
    @DisplayName("GET /i/{token}.ps1 正确匹配路由并拆出 token,返回 text/plain")
    void should_returnPlainTextScript_when_requestingPowerShellScript() throws Exception {
        when(installApplicationService.renderPowerShell(eq("abc123"), any()))
                .thenReturn(new RenderedScript("param()\nWrite-Host 'hi'\n"));

        mockMvc.perform(get("/i/abc123.ps1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string("param()\nWrite-Host 'hi'\n"));
    }

    @Test
    @DisplayName("GET /i/{token}.sh 正确匹配路由并拆出 token,返回 text/plain")
    void should_returnPlainTextScript_when_requestingBashScript() throws Exception {
        when(installApplicationService.renderBash(eq("abc123"), any()))
                .thenReturn(new RenderedScript("#!/usr/bin/env bash\necho hi\n"));

        mockMvc.perform(get("/i/abc123.sh"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string("#!/usr/bin/env bash\necho hi\n"));
    }

    @Test
    @DisplayName("token 不存在时返回 404")
    void should_return404_when_tokenNotFound() throws Exception {
        when(installApplicationService.renderPowerShell(eq("missing"), any()))
                .thenThrow(new ResourceNotFoundException("INSTALL_BUNDLE_NOT_FOUND", "安装单不存在或 token 无效"));

        mockMvc.perform(get("/i/missing.ps1")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("token 已过期时返回 410")
    void should_return410_when_tokenExpired() throws Exception {
        when(installApplicationService.renderBash(eq("expired"), any()))
                .thenThrow(new InstallBundleExpiredException());

        mockMvc.perform(get("/i/expired.sh")).andExpect(status().isGone());
    }
}
