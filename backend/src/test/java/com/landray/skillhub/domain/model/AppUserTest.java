package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class AppUserTest {

    @Test
    void should_createActiveUser_when_newUserCalled() {
        AppUser user = AppUser.newUser("张三", UserRole.USER);

        assertThat(user.id()).isNull();
        assertThat(user.displayName()).isEqualTo("张三");
        assertThat(user.role()).isEqualTo(UserRole.USER);
        assertThat(user.status()).isEqualTo(AppUserStatus.ACTIVE);
        assertThat(user.isActive()).isTrue();
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    void should_markAdmin_when_roleIsAdmin() {
        AppUser admin = AppUser.newUser("system", UserRole.ADMIN);

        assertThat(admin.isAdmin()).isTrue();
    }

    @Test
    void should_throw_when_displayNameIsBlank() {
        assertThatThrownBy(() -> AppUser.newUser("  ", UserRole.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayName");
    }

    @Test
    void should_throw_when_displayNameExceedsMaxLength() {
        String tooLong = "a".repeat(AppUser.MAX_DISPLAY_NAME_LENGTH + 1);

        assertThatThrownBy(() -> AppUser.newUser(tooLong, UserRole.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64");
    }

    @Test
    void should_returnDisabledCopy_when_disableCalled() {
        AppUser user = AppUser.newUser("张三", UserRole.USER);

        AppUser disabled = user.disable();

        assertThat(disabled.status()).isEqualTo(AppUserStatus.DISABLED);
        assertThat(disabled.isActive()).isFalse();
        // 原实例不可变,不受影响
        assertThat(user.status()).isEqualTo(AppUserStatus.ACTIVE);
    }

    @Test
    void should_returnActiveCopy_when_enableCalledOnDisabledUser() {
        AppUser disabled = AppUser.newUser("张三", UserRole.USER).disable();

        AppUser enabled = disabled.enable();

        assertThat(enabled.isActive()).isTrue();
    }

    @Test
    void should_treatMergedStatusAsInactive_when_checkingIsActive() {
        AppUser merged = new AppUser(
                1L, "张三", null, null, null, UserRole.USER, AppUserStatus.MERGED, 2L, Instant.now(), Instant.now());

        assertThat(merged.isActive()).isFalse();
    }
}
