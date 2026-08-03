package com.landray.skillhub.application.auth;

import java.util.List;

import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.UserIdentity;

/** {@code GET /users} 列表一行的用例输出:用户本身 + 其持有的全部登录方式。 */
public record UserWithIdentities(AppUser user, List<UserIdentity> identities) {}
