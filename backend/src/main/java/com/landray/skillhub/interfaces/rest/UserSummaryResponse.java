package com.landray.skillhub.interfaces.rest;

import java.time.Instant;
import java.util.List;

import com.landray.skillhub.application.auth.UserWithIdentities;
import com.landray.skillhub.domain.model.AppUserStatus;
import com.landray.skillhub.domain.model.UserRole;

/** {@code GET /users} 列表一行。{@code providers} 是这个人持有的登录方式集合,不展示 {@code externalId}(登录名)。 */
public record UserSummaryResponse(
        long id,
        String displayName,
        String employeeNo,
        String email,
        UserRole role,
        AppUserStatus status,
        List<String> providers,
        Instant createdAt) {

    public static UserSummaryResponse from(UserWithIdentities row) {
        return new UserSummaryResponse(
                row.user().id(),
                row.user().displayName(),
                row.user().employeeNo(),
                row.user().email(),
                row.user().role(),
                row.user().status(),
                row.identities().stream().map(identity -> identity.provider().name()).toList(),
                row.user().createdAt());
    }
}
