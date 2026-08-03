package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class UserIdentityTest {

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void should_createActiveLocalIdentity_when_newLocalCalled() {
        UserIdentity identity = UserIdentity.newLocal(1L, "zhangsan", "bcrypt-hash");

        assertThat(identity.provider()).isEqualTo(IdentityProvider.LOCAL);
        assertThat(identity.externalId()).isEqualTo("zhangsan");
        assertThat(identity.failedLoginCount()).isZero();
        assertThat(identity.lockedUntil()).isNull();
        assertThat(identity.isActive()).isTrue();
        assertThat(identity.isLocked(NOW)).isFalse();
    }

    @Test
    void should_throw_when_externalIdIsBlank() {
        assertThatThrownBy(() -> UserIdentity.newLocal(1L, "  ", "hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("externalId");
    }

    @Test
    void should_incrementFailedCountWithoutLocking_when_belowThreshold() {
        UserIdentity identity = UserIdentity.newLocal(1L, "zhangsan", "hash");

        UserIdentity afterOneFailure = identity.recordLoginFailure(NOW);

        assertThat(afterOneFailure.failedLoginCount()).isEqualTo(1);
        assertThat(afterOneFailure.lockedUntil()).isNull();
        assertThat(afterOneFailure.isLocked(NOW)).isFalse();
    }

    @Test
    void should_lockForFifteenMinutes_when_reachingFiveFailures() {
        UserIdentity identity = UserIdentity.newLocal(1L, "zhangsan", "hash");

        UserIdentity locked = failNTimes(identity, UserIdentity.MAX_FAILED_LOGIN_ATTEMPTS);

        assertThat(locked.failedLoginCount()).isEqualTo(UserIdentity.MAX_FAILED_LOGIN_ATTEMPTS);
        assertThat(locked.lockedUntil()).isEqualTo(NOW.plus(UserIdentity.LOCK_DURATION));
        assertThat(locked.isLocked(NOW)).isTrue();
    }

    @Test
    void should_stillBeLocked_when_checkedBeforeLockExpiry() {
        UserIdentity locked = failNTimes(
                UserIdentity.newLocal(1L, "zhangsan", "hash"), UserIdentity.MAX_FAILED_LOGIN_ATTEMPTS);
        Instant justBeforeExpiry = locked.lockedUntil().minus(Duration.ofSeconds(1));

        assertThat(locked.isLocked(justBeforeExpiry)).isTrue();
    }

    @Test
    void should_noLongerBeLocked_when_checkedAfterLockExpiry() {
        UserIdentity locked = failNTimes(
                UserIdentity.newLocal(1L, "zhangsan", "hash"), UserIdentity.MAX_FAILED_LOGIN_ATTEMPTS);
        Instant afterExpiry = locked.lockedUntil().plus(Duration.ofSeconds(1));

        assertThat(locked.isLocked(afterExpiry)).isFalse();
    }

    @Test
    void should_resetFailedCountAndLock_when_loginSucceeds() {
        UserIdentity locked = failNTimes(
                UserIdentity.newLocal(1L, "zhangsan", "hash"), UserIdentity.MAX_FAILED_LOGIN_ATTEMPTS);

        UserIdentity recovered = locked.recordLoginSuccess(NOW);

        assertThat(recovered.failedLoginCount()).isZero();
        assertThat(recovered.lockedUntil()).isNull();
        assertThat(recovered.lastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void should_replaceCredentialOnly_when_changingPassword() {
        UserIdentity identity = UserIdentity.newLocal(1L, "zhangsan", "old-hash").recordLoginFailure(NOW);

        UserIdentity changed = identity.changeCredential("new-hash");

        assertThat(changed.credential()).isEqualTo("new-hash");
        // 改密码不清空登录失败计数,两者是独立关注点
        assertThat(changed.failedLoginCount()).isEqualTo(1);
    }

    private static UserIdentity failNTimes(UserIdentity identity, int times) {
        UserIdentity current = identity;
        for (int i = 0; i < times; i++) {
            current = current.recordLoginFailure(NOW);
        }
        return current;
    }
}
