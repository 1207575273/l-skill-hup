package com.landray.skillhub.interfaces.rest.install;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.landray.skillhub.application.install.InstallApplicationService;
import com.landray.skillhub.application.install.RenderedScript;
import com.landray.skillhub.interfaces.rest.support.ClientIpResolver;

/**
 * 渲染安装脚本:{@code GET /i/{token}.ps1} / {@code .sh}。
 *
 * <p><b>刻意不挂在 {@code /api/v1} 下</b>——这两个端点交付的是要被 {@code curl | sh} / {@code iwr | iex}
 * 直接执行的脚本文本,不是 JSON API,业务建模文档 6.2 明确把它们排除在 {@code /api/v1} 之外。
 *
 * <p>匿名可用,不做任何鉴权(业务建模文档 5.16)。安装单本身的有效期与渲染时实时读取的认证状态才是这条链路真正的
 * 安全边界,详见 {@link InstallApplicationService} 与 {@code InstallScriptRenderer} 的类注释。
 */
@RestController
public class InstallScriptController {

    private static final MediaType SCRIPT_MEDIA_TYPE = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final InstallApplicationService installApplicationService;

    public InstallScriptController(InstallApplicationService installApplicationService) {
        this.installApplicationService = installApplicationService;
    }

    @GetMapping(value = "/i/{token}.ps1")
    public ResponseEntity<String> renderPowerShell(@PathVariable String token, HttpServletRequest request) {
        RenderedScript script =
                installApplicationService.renderPowerShell(token, ClientIpResolver.resolve(request));
        return ResponseEntity.ok().contentType(SCRIPT_MEDIA_TYPE).body(script.content());
    }

    @GetMapping(value = "/i/{token}.sh")
    public ResponseEntity<String> renderBash(@PathVariable String token, HttpServletRequest request) {
        RenderedScript script = installApplicationService.renderBash(token, ClientIpResolver.resolve(request));
        return ResponseEntity.ok().contentType(SCRIPT_MEDIA_TYPE).body(script.content());
    }
}
