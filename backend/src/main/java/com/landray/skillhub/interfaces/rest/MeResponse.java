package com.landray.skillhub.interfaces.rest;

import java.util.List;

import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.UserIdentity;
import com.landray.skillhub.domain.model.UserRole;

/** {@code GET /auth/me}:当前登录者 + 其全部已绑定身份(业务建模文档 6.2)。 */
public record MeResponse(
        long id,
        String displayName,
        String employeeNo,
        String email,
        String avatarUrl,
        UserRole role,
        List<IdentitySummaryResponse> identities) {

    public static MeResponse from(AppUser user, List<UserIdentity> identities) {
        return new MeResponse(
                user.id(),
                user.displayName(),
                user.employeeNo(),
                user.email(),
                user.avatarUrl(),
                user.role(),
                identities.stream().map(IdentitySummaryResponse::from).toList());
    }
}
