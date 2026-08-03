package com.landray.skillhub.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.landray.skillhub.domain.auth.PasswordHasher;
import com.landray.skillhub.domain.exception.AccountLockedException;
import com.landray.skillhub.domain.exception.InvalidCredentialException;
import com.landray.skillhub.domain.exception.InvalidPasswordException;
import com.landray.skillhub.domain.exception.LocalRegisterDisabledException;
import com.landray.skillhub.domain.exception.OldPasswordMismatchException;
import com.landray.skillhub.domain.exception.UserDisabledException;
import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.AppUserStatus;
import com.landray.skillhub.domain.model.IdentityProvider;
import com.landray.skillhub.domain.model.IdentityStatus;
import com.landray.skillhub.domain.model.UserIdentity;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.domain.model.UserSession;
import com.landray.skillhub.domain.repository.AppUserRepository;
import com.landray.skillhub.domain.repository.UserIdentityRepository;
import com.landray.skillhub.domain.repository.UserSessionRepository;

/**
 * Mockito mock 掉仓储接口,只验编排逻辑与事务语义(backend/CLAUDE.md 8.1)。
 *
 * <p>安全用例覆盖 backend/CLAUDE.md 任务要求的全部场景:密码错误、账号被禁用、连续失败触发锁定、
 * 锁定期内即使密码正确也拒绝、token 过期、token 不存在。
 */
@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {

    private static final String LOGIN = "zhangsan";
    private static final String RAW_PASSWORD = "correct-password";
    private static final String HASH = "bcrypt-hash";

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private PasswordHasher passwordHasher;

    private AuthApplicationService service;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(false, new AuthProperties.BootstrapAdmin("admin", "x"));
        service = new AuthApplicationService(
                appUserRepository, userIdentityRepository, userSessionRepository, passwordHasher, properties);
    }

    private UserIdentity activeIdentity() {
        Instant now = Instant.now();
        return new UserIdentity(10L, 1L, IdentityProvider.LOCAL, LOGIN, HASH, null, 0, null, null,
                IdentityStatus.ACTIVE, now, now);
    }

    private AppUser activeUser() {
        Instant now = Instant.now();
        return new AppUser(1L, "张三", null, null, null, UserRole.USER, AppUserStatus.ACTIVE, null, now, now);
    }

    @Test
    void should_issueSession_when_loginSucceeds() {
        when(userIdentityRepository.findByProviderAndExternalId(IdentityProvider.LOCAL, LOGIN))
                .thenReturn(Optional.of(activeIdentity()));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(activeUser()));
        when(passwordHasher.matches(RAW_PASSWORD, HASH)).thenReturn(true);
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> {
            UserSession session = invocation.getArgument(0);
            return new UserSession(99L, session.tokenHash(), session.userId(), session.identityId(),
                    session.userAgent(), session.clientIp(), session.createdAt(), session.expiresAt(), null);
        });

        LoginResult result = service.login(LOGIN, RAW_PASSWORD, "junit-agent", "127.0.0.1");

        assertThat(result.token()).isNotBlank();
        assertThat(result.user().id()).isEqualTo(1L);
        assertThat(result.identityId()).isEqualTo(10L);
        verify(userIdentityRepository).update(any(UserIdentity.class));
    }

    @Test
    void should_throwInvalidCredential_when_loginNameNotFound() {
        when(userIdentityRepository.findByProviderAndExternalId(IdentityProvider.LOCAL, LOGIN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(LOGIN, RAW_PASSWORD, null, null))
                .isInstanceOf(InvalidCredentialException.class);
        verify(userSessionRepository, never()).save(any());
    }

    @Test
    void should_throwInvalidCredentialAndIncrementFailedCount_when_passwordWrong() {
        when(userIdentityRepository.findByProviderAndExternalId(IdentityProvider.LOCAL, LOGIN))
                .thenReturn(Optional.of(activeIdentity()));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(activeUser()));
        when(passwordHasher.matches(RAW_PASSWORD, HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.login(LOGIN, RAW_PASSWORD, null, null))
                .isInstanceOf(InvalidCredentialException.class);

        ArgumentCaptor<UserIdentity> captor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).update(captor.capture());
        assertThat(captor.getValue().failedLoginCount()).isEqualTo(1);
        verify(userSessionRepository, never()).save(any());
    }

    @Test
    void should_lockAccount_when_fifthConsecutiveFailureReached() {
        Instant now = Instant.now();
        UserIdentity nearlyLocked = new UserIdentity(10L, 1L, IdentityProvider.LOCAL, LOGIN, HASH, null,
                UserIdentity.MAX_FAILED_LOGIN_ATTEMPTS - 1, null, null, IdentityStatus.ACTIVE, now, now);
        when(userIdentityRepository.findByProviderAndExternalId(IdentityProvider.LOCAL, LOGIN))
                .thenReturn(Optional.of(nearlyLocked));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(activeUser()));
        when(passwordHasher.matches(RAW_PASSWORD, HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.login(LOGIN, RAW_PASSWORD, null, null))
                .isInstanceOf(InvalidCredentialException.class);

        ArgumentCaptor<UserIdentity> captor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).update(captor.capture());
        assertThat(captor.getValue().failedLoginCount()).isEqualTo(UserIdentity.MAX_FAILED_LOGIN_ATTEMPTS);
        assertThat(captor.getValue().lockedUntil()).isNotNull();
    }

    @Test
    void should_throwAccountLocked_when_identityIsLocked() {
        Instant now = Instant.now();
        UserIdentity locked = new UserIdentity(10L, 1L, IdentityProvider.LOCAL, LOGIN, HASH, null,
                UserIdentity.MAX_FAILED_LOGIN_ATTEMPTS, now.plusSeconds(600), null, IdentityStatus.ACTIVE, now, now);
        when(userIdentityRepository.findByProviderAndExternalId(IdentityProvider.LOCAL, LOGIN))
                .thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> service.login(LOGIN, RAW_PASSWORD, null, null))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void should_throwAccountLocked_when_lockedEvenWithCorrectPassword() {
        Instant now = Instant.now();
        UserIdentity locked = new UserIdentity(10L, 1L, IdentityProvider.LOCAL, LOGIN, HASH, null,
                UserIdentity.MAX_FAILED_LOGIN_ATTEMPTS, now.plusSeconds(600), null, IdentityStatus.ACTIVE, now, now);
        when(userIdentityRepository.findByProviderAndExternalId(IdentityProvider.LOCAL, LOGIN))
                .thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> service.login(LOGIN, RAW_PASSWORD, null, null))
                .isInstanceOf(AccountLockedException.class);
        // 锁定期内根本不应该走到密码校验这一步
        verify(passwordHasher, never()).matches(anyString(), anyString());
    }

    @Test
    void should_throwUserDisabled_when_userStatusDisabled() {
        Instant now = Instant.now();
        AppUser disabledUser =
                new AppUser(1L, "张三", null, null, null, UserRole.USER, AppUserStatus.DISABLED, null, now, now);
        when(userIdentityRepository.findByProviderAndExternalId(IdentityProvider.LOCAL, LOGIN))
                .thenReturn(Optional.of(activeIdentity()));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(disabledUser));

        assertThatThrownBy(() -> service.login(LOGIN, RAW_PASSWORD, null, null))
                .isInstanceOf(UserDisabledException.class);
        verify(passwordHasher, never()).matches(anyString(), anyString());
    }

    @Test
    void should_throwLocalRegisterDisabled_when_registerCalledWhileFeatureOff() {
        assertThatThrownBy(() -> service.register("张三", LOGIN, RAW_PASSWORD, null, null))
                .isInstanceOf(LocalRegisterDisabledException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void should_createUserAndIssueSession_when_registerCalledWhileFeatureOn() {
        AuthProperties enabledProperties =
                new AuthProperties(true, new AuthProperties.BootstrapAdmin("admin", "x"));
        AuthApplicationService enabledService = new AuthApplicationService(
                appUserRepository, userIdentityRepository, userSessionRepository, passwordHasher, enabledProperties);
        when(passwordHasher.hash(RAW_PASSWORD)).thenReturn(HASH);
        when(appUserRepository.save(any(AppUser.class))).thenReturn(activeUser());
        when(userIdentityRepository.save(any(UserIdentity.class))).thenReturn(activeIdentity());
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> {
            UserSession session = invocation.getArgument(0);
            return new UserSession(99L, session.tokenHash(), session.userId(), session.identityId(), null, null,
                    session.createdAt(), session.expiresAt(), null);
        });

        LoginResult result = enabledService.register("张三", LOGIN, RAW_PASSWORD, null, null);

        assertThat(result.token()).isNotBlank();
        verify(appUserRepository).save(any(AppUser.class));
        verify(userIdentityRepository).save(any(UserIdentity.class));
    }

    @Test
    void should_throwInvalidPassword_when_registerPasswordTooShort() {
        AuthProperties enabledProperties =
                new AuthProperties(true, new AuthProperties.BootstrapAdmin("admin", "x"));
        AuthApplicationService enabledService = new AuthApplicationService(
                appUserRepository, userIdentityRepository, userSessionRepository, passwordHasher, enabledProperties);

        assertThatThrownBy(() -> enabledService.register("张三", LOGIN, "short", null, null))
                .isInstanceOf(InvalidPasswordException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void should_createBootstrapAdmin_when_notExists() {
        when(userIdentityRepository.findByProviderAndExternalId(IdentityProvider.LOCAL, "admin"))
                .thenReturn(Optional.empty());
        when(passwordHasher.hash("admin-pass")).thenReturn(HASH);
        AppUser savedAdmin =
                new AppUser(2L, "admin", null, null, null, UserRole.ADMIN, AppUserStatus.ACTIVE, null, Instant.now(),
                        Instant.now());
        when(appUserRepository.save(any(AppUser.class))).thenReturn(savedAdmin);

        service.ensureBootstrapAdmin("admin", "admin-pass");

        verify(appUserRepository).save(any(AppUser.class));
        verify(userIdentityRepository).save(any(UserIdentity.class));
    }

    @Test
    void should_doNothing_when_bootstrapAdminAlreadyExists() {
        when(userIdentityRepository.findByProviderAndExternalId(IdentityProvider.LOCAL, "admin"))
                .thenReturn(Optional.of(activeIdentity()));

        service.ensureBootstrapAdmin("admin", "admin-pass");

        verify(appUserRepository, never()).save(any());
        verify(userIdentityRepository, never()).save(any());
    }

    @Test
    void should_revokeSession_when_logoutCalled() {
        service.logout(99L);

        verify(userSessionRepository).revoke(eq(99L), any(Instant.class));
    }

    @Test
    void should_returnEmpty_when_resolveSessionTokenNotFound() {
        when(userSessionRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        Optional<AuthenticatedUser> resolved = service.resolveSession("some-raw-token");

        assertThat(resolved).isEmpty();
    }

    @Test
    void should_returnEmpty_when_resolveSessionExpired() {
        Instant now = Instant.now();
        UserSession expired = new UserSession(
                99L, "hash", 1L, 10L, null, null, now.minusSeconds(1000), now.minusSeconds(1), null);
        when(userSessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        Optional<AuthenticatedUser> resolved = service.resolveSession("some-raw-token");

        assertThat(resolved).isEmpty();
    }

    @Test
    void should_returnEmpty_when_resolveSessionUserDisabled() {
        Instant now = Instant.now();
        UserSession valid = new UserSession(99L, "hash", 1L, 10L, null, null, now, now.plusSeconds(3600), null);
        AppUser disabledUser =
                new AppUser(1L, "张三", null, null, null, UserRole.USER, AppUserStatus.DISABLED, null, now, now);
        when(userSessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(valid));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(disabledUser));

        Optional<AuthenticatedUser> resolved = service.resolveSession("some-raw-token");

        assertThat(resolved).isEmpty();
    }

    @Test
    void should_returnAuthenticatedUser_when_resolveSessionValid() {
        Instant now = Instant.now();
        UserSession valid = new UserSession(99L, "hash", 1L, 10L, null, null, now, now.plusSeconds(3600), null);
        when(userSessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(valid));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(activeUser()));

        Optional<AuthenticatedUser> resolved = service.resolveSession("some-raw-token");

        assertThat(resolved).isPresent();
        assertThat(resolved.get().userId()).isEqualTo(1L);
        assertThat(resolved.get().identityId()).isEqualTo(10L);
        assertThat(resolved.get().sessionId()).isEqualTo(99L);
    }

    @Test
    void should_returnIdentityList_when_listIdentitiesCalled() {
        when(userIdentityRepository.findByUserId(1L)).thenReturn(List.of(activeIdentity()));

        List<UserIdentity> identities = service.listIdentities(1L);

        assertThat(identities).hasSize(1);
    }

    @Test
    void should_changePassword_when_oldPasswordMatches() {
        when(userIdentityRepository.findById(10L)).thenReturn(Optional.of(activeIdentity()));
        when(passwordHasher.matches("old-pass", HASH)).thenReturn(true);
        when(passwordHasher.hash("new-password")).thenReturn("new-hash");

        service.changePassword(10L, "old-pass", "new-password");

        ArgumentCaptor<UserIdentity> captor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).update(captor.capture());
        assertThat(captor.getValue().credential()).isEqualTo("new-hash");
    }

    @Test
    void should_throwOldPasswordMismatch_when_oldPasswordWrong() {
        when(userIdentityRepository.findById(10L)).thenReturn(Optional.of(activeIdentity()));
        when(passwordHasher.matches("wrong-old-pass", HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(10L, "wrong-old-pass", "new-password"))
                .isInstanceOf(OldPasswordMismatchException.class);
        verify(userIdentityRepository, times(0)).update(any());
    }

    @Test
    void should_returnLocalOnly_when_enabledProvidersCalled() {
        assertThat(service.enabledProviders()).containsExactly("LOCAL");
    }

    @Test
    void should_reflectConfiguredFlag_when_isLocalRegisterEnabledCalled() {
        assertThat(service.isLocalRegisterEnabled()).isFalse();
    }
}
