package com.landray.skillhub.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class AccountLockedExceptionTest {

    @Test
    void should_mapToHttp403AndCarryLockedUntilDetail_when_constructed() {
        Instant lockedUntil = Instant.parse("2026-08-03T00:15:00Z");

        AccountLockedException ex = new AccountLockedException(lockedUntil);

        assertThat(ex.httpStatus()).isEqualTo(403);
        assertThat(ex.code()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(ex.details()).containsEntry("lockedUntil", lockedUntil.toString());
    }
}
