package com.landray.skillhub.application.auth;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.landray.skillhub.domain.auth.PasswordHasher;
import com.landray.skillhub.domain.exception.UserDisableNotAllowedException;
import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.IdentityProvider;
import com.landray.skillhub.domain.model.UserIdentity;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.domain.repository.AppUserRepository;
import com.landray.skillhub.domain.repository.UserIdentityRepository;
import com.landray.skillhub.domain.repository.UserSessionRepository;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.model.PageResult;

/**
 * 用户管理用例编排(业务建模文档 6.2 的"用户管理(ADMIN)"一组接口)。是否为 ADMIN 由 interfaces
 * 层的 {@code @RequireAdmin} 拦截器统一把关,这里不重复判断角色。
 */
@Service
public class UserManagementApplicationService {

    private static final Logger log = LoggerFactory.getLogger(UserManagementApplicationService.class);

    /** V0002 种下的内置 system 账号,禁止被禁用(见 {@link UserDisableNotAllowedException} 类注释)。 */
    private static final long SYSTEM_USER_ID = 1L;

    private static final String USER_NOT_FOUND_CODE = "USER_NOT_FOUND";

    private final AppUserRepository appUserRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordHasher passwordHasher;

    public UserManagementApplicationService(
            AppUserRepository appUserRepository,
            UserIdentityRepository userIdentityRepository,
            UserSessionRepository userSessionRepository,
            PasswordHasher passwordHasher) {
        this.appUserRepository = appUserRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.userSessionRepository = userSessionRepository;
        this.passwordHasher = passwordHasher;
    }

    @Transactional(readOnly = true)
    public PageResult<UserWithIdentities> listUsers(int page, int size, IdentityProvider providerFilter) {
        PageResult<AppUser> userPage = appUserRepository.search(page, size, providerFilter);
        List<Long> userIds = userPage.items().stream().map(AppUser::id).toList();
        Map<Long, List<UserIdentity>> identitiesByUserId = userIdentityRepository.findByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(UserIdentity::userId));

        List<UserWithIdentities> items = userPage.items().stream()
                .map(user -> new UserWithIdentities(user, identitiesByUserId.getOrDefault(user.id(), List.of())))
                .toList();
        return new PageResult<>(items, userPage.total(), userPage.page(), userPage.size());
    }

    /** P0 唯一开户途径(业务建模文档 3.2)。 */
    @Transactional
    public AppUser createUser(String displayName, String login, String rawPassword, UserRole role) {
        PasswordPolicy.validate(rawPassword);
        UserRole effectiveRole = role == null ? UserRole.USER : role;

        AppUser savedUser = appUserRepository.save(AppUser.newUser(displayName, effectiveRole));
        userIdentityRepository.save(UserIdentity.newLocal(savedUser.id(), login, passwordHasher.hash(rawPassword)));

        log.info(
                "管理员建号, event=user_created, userId={}, login={}, role={}",
                savedUser.id(),
                login,
                effectiveRole);
        return savedUser;
    }

    /** 禁用并踢下线(业务建模文档 5.15)。护栏见 {@link UserDisableNotAllowedException} 类注释。 */
    @Transactional
    public void disableUser(long targetUserId, long operatorUserId) {
        if (targetUserId == SYSTEM_USER_ID) {
            throw new UserDisableNotAllowedException("不能禁用内置 system 账号");
        }
        if (targetUserId == operatorUserId) {
            throw new UserDisableNotAllowedException("不能禁用自己");
        }

        AppUser user = findUserOrThrow(targetUserId);
        appUserRepository.update(user.disable());
        int revokedSessionCount = userSessionRepository.revokeAllActiveByUserId(targetUserId, Instant.now());

        // 禁用属于安全事件,WARN 起步(backend/CLAUDE.md 6.6)。
        log.warn(
                "用户已禁用并踢下线, event=user_disabled, userId={}, revokedSessionCount={}, operatorUserId={}",
                targetUserId,
                revokedSessionCount,
                operatorUserId);
    }

    @Transactional
    public void enableUser(long targetUserId) {
        AppUser user = findUserOrThrow(targetUserId);
        appUserRepository.update(user.enable());
        log.info("用户已恢复, event=user_enabled, userId={}", targetUserId);
    }

    private AppUser findUserOrThrow(long userId) {
        return appUserRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_CODE, "用户不存在: " + userId));
    }
}
