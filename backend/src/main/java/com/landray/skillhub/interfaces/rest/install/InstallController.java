package com.landray.skillhub.interfaces.rest.install;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.application.install.CreateBundleResult;
import com.landray.skillhub.application.install.InstallApplicationService;
import com.landray.skillhub.domain.model.InstallItem;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 安装链路的 {@code /api/v1} 接口:客户端适配表、生成安装单(业务建模文档 6.2"安装"一组)。
 *
 * <p>匿名可用(业务建模文档 5.16 权限矩阵:浏览、下载、生成安装单都不要求登录)——{@code createdByUserId}
 * 是"如果登录了就记一下",不是"必须登录才能用"。
 *
 * <p>渲染 {@code /i/{token}.ps1} / {@code .sh} 的两个端点<b>不在这个 Controller 里</b>,见
 * {@link InstallScriptController}——它们不挂在 {@code /api/v1} 下,分开是为了让两个 Controller 的
 * {@code @RequestMapping} 前缀各自单纯。
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "安装")
public class InstallController {

    private final InstallApplicationService installApplicationService;
    private final CurrentUserAccessor currentUserAccessor;

    public InstallController(
            InstallApplicationService installApplicationService, CurrentUserAccessor currentUserAccessor) {
        this.installApplicationService = installApplicationService;
        this.currentUserAccessor = currentUserAccessor;
    }

    @GetMapping("/clients")
    public List<ClientTargetResponse> listClients() {
        return installApplicationService.listSupportedClients().stream()
                .map(ClientTargetResponse::from)
                .toList();
    }

    @PostMapping("/bundles")
    public CreateBundleResponse createBundle(@Valid @RequestBody CreateBundleRequest request) {
        List<InstallItem> items = request.items().stream()
                .map(item -> new InstallItem(item.skillId(), item.versionId()))
                .toList();
        Long createdByUserId =
                currentUserAccessor.current().map(AuthenticatedUser::userId).orElse(null);

        CreateBundleResult result =
                installApplicationService.createBundle(items, request.client(), request.scope(), createdByUserId);
        return CreateBundleResponse.from(result);
    }
}
