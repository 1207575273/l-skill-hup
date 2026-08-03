package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class UserSessionTest {

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void should_issueSessionExpiringInSevenDays_when_issueCalled() {
        SessionToken token = SessionToken.generate();

        UserSession session = UserSession.issue(1L, 2L, token, "junit-agent", "127.0.0.1", NOW);

        assertThat(session.id()).isNull();
        assertThat(session.tokenHash()).isEqualTo(token.hash());
        assertThat(session.expiresAt()).isEqualTo(NOW.plus(UserSession.DEFAULT_TTL));
        assertThat(session.revokedAt()).isNull();
        assertThat(session.isValid(NOW)).isTrue();
    }

    @Test
    void should_beInvalid_when_expired() {
        UserSession session = UserSession.issue(1L, 2L, SessionToken.generate(), null, null, NOW);
        Instant afterExpiry = session.expiresAt().plus(Duration.ofSeconds(1));

        assertThat(session.isValid(afterExpiry)).isFalse();
    }

    @Test
    void should_beInvalid_when_revoked() {
        UserSession issued = UserSession.issue(1L, 2L, SessionToken.generate(), null, null, NOW);
        UserSession revoked = new UserSession(
                issued.id(),
                issued.tokenHash(),
                issued.userId(),
                issued.identityId(),
                issued.userAgent(),
                issued.clientIp(),
                issued.createdAt(),
                issued.expiresAt(),
                NOW);

        assertThat(revoked.isValid(NOW)).isFalse();
    }

    @Test
    void should_throw_when_tokenHashBlank() {
        assertThatThrownBy(() -> new UserSession(null, " ", 1L, 2L, null, null, NOW, NOW.plusSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_expiresAtMissing() {
        assertThatThrownBy(() -> new UserSession(null, "hash", 1L, 2L, null, null, NOW, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
