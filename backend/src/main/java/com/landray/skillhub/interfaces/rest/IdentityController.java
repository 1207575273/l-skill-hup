package com.landray.skillhub.interfaces.rest;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.landray.skillhub.application.auth.AuthApplicationService;
import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.model.UserIdentity;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.interfaces.rest.auth.RequireLogin;

/**
 * 身份与会话接口(业务建模文档 6.2)。P0 范围只做 {@code GET /identities} 与
 * {@code POST /identities/change-password}——解绑、会话管理页是 P1,本轮不做。
 */
@RestController
@RequestMapping("/api/v1/identities")
@RequireLogin
public class IdentityController {

    private final AuthApplicationService authApplicationService;
    private final CurrentUserAccessor currentUserAccessor;

    public IdentityController(AuthApplicationService authApplicationService, CurrentUserAccessor currentUserAccessor) {
        this.authApplicationService = authApplicationService;
        this.currentUserAccessor = currentUserAccessor;
    }

    @GetMapping
    public List<IdentitySummaryResponse> list() {
        AuthenticatedUser current = currentUserAccessor.require();
        List<UserIdentity> identities = authApplicationService.listIdentities(current.userId());
        return identities.stream().map(IdentitySummaryResponse::from).toList();
    }

    /**
     * 改**当前会话所用身份**的密码,路径里刻意不带 identityId——规则定死"只能改当前会话所用的那个身份"
     * (靠 {@code user_session.identity_id} 定位),理由见业务建模文档 4.4:一个人可能有两个 LOCAL 身份,
     * 不定死就有歧义。
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        AuthenticatedUser current = currentUserAccessor.require();
        authApplicationService.changePassword(current.identityId(), request.oldPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
