package com.landray.skillhub.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.landray.skillhub.domain.exception.UserDisableNotAllowedException;
import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.AppUserStatus;
import com.landray.skillhub.domain.model.IdentityProvider;
import com.landray.skillhub.domain.model.IdentityStatus;
import com.landray.skillhub.domain.model.UserIdentity;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.domain.repository.AppUserRepository;
import com.landray.skillhub.domain.repository.UserIdentityRepository;
import com.landray.skillhub.domain.repository.UserSessionRepository;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.model.PageResult;

/** Mockito mock 掉仓储接口,只验编排逻辑(backend/CLAUDE.md 8.1)。 */
@ExtendWith(MockitoExtension.class)
class UserManagementApplicationServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private PasswordHasher passwordHasher;

    private UserManagementApplicationService service;

    @BeforeEach
    void setUp() {
        service = new UserManagementApplicationService(
                appUserRepository, userIdentityRepository, userSessionRepository, passwordHasher);
    }

    private AppUser activeUser(long id) {
        Instant now = Instant.now();
        return new AppUser(id, "用户" + id, null, null, null, UserRole.USER, AppUserStatus.ACTIVE, null, now, now);
    }

    @Test
    void should_zipUsersWithTheirIdentities_when_listUsersCalled() {
        AppUser user2 = activeUser(2L);
        AppUser user3 = activeUser(3L);
        when(appUserRepository.search(1, 20, null))
                .thenReturn(new PageResult<>(List.of(user2, user3), 2, 1, 20));
        Instant now = Instant.now();
        UserIdentity identityOfUser2 = new UserIdentity(
                20L, 2L, IdentityProvider.LOCAL, "user2-login", "hash", null, 0, null, null,
                IdentityStatus.ACTIVE, now, now);
        when(userIdentityRepository.findByUserIds(List.of(2L, 3L))).thenReturn(List.of(identityOfUser2));

        PageResult<UserWithIdentities> page = service.listUsers(1, 20, null);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).hasSize(2);
        UserWithIdentities userTwoRow =
                page.items().stream().filter(row -> row.user().id() == 2L).findFirst().orElseThrow();
        assertThat(userTwoRow.identities()).hasSize(1);
        UserWithIdentities userThreeRow =
                page.items().stream().filter(row -> row.user().id() == 3L).findFirst().orElseThrow();
        assertThat(userThreeRow.identities()).isEmpty();
    }

    @Test
    void should_passProviderFilterThrough_when_listUsersCalledWithFilter() {
        when(appUserRepository.search(1, 20, IdentityProvider.LOCAL))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));

        service.listUsers(1, 20, IdentityProvider.LOCAL);

        verify(appUserRepository).search(1, 20, IdentityProvider.LOCAL);
    }

    @Test
    void should_createUserWithLocalIdentity_when_createUserCalled() {
        when(passwordHasher.hash("a-valid-password")).thenReturn("hash");
        when(appUserRepository.save(any(AppUser.class))).thenReturn(activeUser(5L));

        AppUser created = service.createUser("新用户", "new-login", "a-valid-password", UserRole.ADMIN);

        assertThat(created.id()).isEqualTo(5L);
        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().role()).isEqualTo(UserRole.ADMIN);
        verify(userIdentityRepository).save(any(UserIdentity.class));
    }

    @Test
    void should_defaultToUserRole_when_createUserCalledWithoutRole() {
        when(passwordHasher.hash("a-valid-password")).thenReturn("hash");
        when(appUserRepository.save(any(AppUser.class))).thenReturn(activeUser(5L));

        service.createUser("新用户", "new-login", "a-valid-password", null);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().role()).isEqualTo(UserRole.USER);
    }

    @Test
    void should_rejectTooShortPassword_when_createUserCalled() {
        assertThatThrownBy(() -> service.createUser("新用户", "new-login", "short", null))
                .isInstanceOf(com.landray.skillhub.domain.exception.InvalidPasswordException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void should_disableUserAndRevokeSessions_when_disableUserCalled() {
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(activeUser(5L)));
        when(userSessionRepository.revokeAllActiveByUserId(eq(5L), any(Instant.class))).thenReturn(2);

        service.disableUser(5L, 1L);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).update(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(AppUserStatus.DISABLED);
        verify(userSessionRepository).revokeAllActiveByUserId(eq(5L), any(Instant.class));
    }

    @Test
    void should_throwUserDisableNotAllowed_when_disablingSystemAccount() {
        assertThatThrownBy(() -> service.disableUser(1L, 5L)).isInstanceOf(UserDisableNotAllowedException.class);
        verify(appUserRepository, never()).findById(anyLong());
    }

    @Test
    void should_throwUserDisableNotAllowed_when_disablingSelf() {
        assertThatThrownBy(() -> service.disableUser(5L, 5L)).isInstanceOf(UserDisableNotAllowedException.class);
        verify(appUserRepository, never()).findById(anyLong());
    }

    @Test
    void should_throwResourceNotFound_when_disablingUnknownUser() {
        when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disableUser(99L, 1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_enableUser_when_enableUserCalled() {
        Instant now = Instant.now();
        AppUser disabledUser =
                new AppUser(5L, "用户5", null, null, null, UserRole.USER, AppUserStatus.DISABLED, null, now, now);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(disabledUser));

        service.enableUser(5L);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).update(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(AppUserStatus.ACTIVE);
    }

    @Test
    void should_throwResourceNotFound_when_enablingUnknownUser() {
        when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enableUser(99L)).isInstanceOf(ResourceNotFoundException.class);
        verify(appUserRepository, times(0)).update(any());
    }
}
