package com.landray.skillhub.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.SessionToken;
import com.landray.skillhub.domain.model.UserIdentity;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.domain.model.UserSession;
import com.landray.skillhub.domain.repository.AppUserRepository;
import com.landray.skillhub.domain.repository.UserIdentityRepository;
import com.landray.skillhub.domain.repository.UserSessionRepository;

/** 起真 PostgreSQL 测 {@link UserSessionRepository}(backend/CLAUDE.md 8.1)。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class UserSessionRepositoryImplTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("skillhub")
            .withUsername("skillhub")
            .withPassword("skillhub");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    private long[] newUserAndIdentityId() {
        long userId = appUserRepository.save(AppUser.newUser("会话测试用户-" + System.nanoTime(), UserRole.USER))
                .id();
        long identityId = userIdentityRepository
                .save(UserIdentity.newLocal(userId, "session-login-" + System.nanoTime(), "hash"))
                .id();
        return new long[] {userId, identityId};
    }

    @Test
    void should_returnGeneratedId_when_saveCalled() {
        long[] ids = newUserAndIdentityId();
        SessionToken token = SessionToken.generate();

        UserSession saved = userSessionRepository.save(
                UserSession.issue(ids[0], ids[1], token, "junit-agent", "127.0.0.1", Instant.now()));

        assertThat(saved.id()).isNotNull();
    }

    @Test
    void should_findByTokenHash_when_sessionExists() {
        long[] ids = newUserAndIdentityId();
        SessionToken token = SessionToken.generate();
        userSessionRepository.save(UserSession.issue(ids[0], ids[1], token, null, null, Instant.now()));

        Optional<UserSession> found = userSessionRepository.findByTokenHash(token.hash());

        assertThat(found).isPresent();
        assertThat(found.get().userId()).isEqualTo(ids[0]);
        assertThat(found.get().identityId()).isEqualTo(ids[1]);
        assertThat(found.get().revokedAt()).isNull();
    }

    @Test
    void should_returnEmpty_when_tokenHashNotFound() {
        Optional<UserSession> found = userSessionRepository.findByTokenHash("does-not-exist");

        assertThat(found).isEmpty();
    }

    @Test
    void should_setRevokedAt_when_revokeCalled() {
        long[] ids = newUserAndIdentityId();
        SessionToken token = SessionToken.generate();
        UserSession saved = userSessionRepository.save(
                UserSession.issue(ids[0], ids[1], token, null, null, Instant.now()));

        userSessionRepository.revoke(saved.id(), Instant.now());

        Optional<UserSession> found = userSessionRepository.findByTokenHash(token.hash());
        assertThat(found).isPresent();
        assertThat(found.get().revokedAt()).isNotNull();
    }

    @Test
    void should_revokeOnlyActiveSessionsOfGivenUser_when_revokeAllActiveByUserIdCalled() {
        long[] ids = newUserAndIdentityId();
        SessionToken firstToken = SessionToken.generate();
        SessionToken secondToken = SessionToken.generate();
        userSessionRepository.save(UserSession.issue(ids[0], ids[1], firstToken, null, null, Instant.now()));
        userSessionRepository.save(UserSession.issue(ids[0], ids[1], secondToken, null, null, Instant.now()));

        int revokedCount = userSessionRepository.revokeAllActiveByUserId(ids[0], Instant.now());

        assertThat(revokedCount).isEqualTo(2);
        assertThat(userSessionRepository.findByTokenHash(firstToken.hash()).get().revokedAt())
                .isNotNull();
        assertThat(userSessionRepository.findByTokenHash(secondToken.hash()).get().revokedAt())
                .isNotNull();
    }
}
