package com.landray.skillhub.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
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
import com.landray.skillhub.domain.model.IdentityProvider;
import com.landray.skillhub.domain.model.IdentityStatus;
import com.landray.skillhub.domain.model.UserIdentity;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.domain.repository.AppUserRepository;
import com.landray.skillhub.domain.repository.UserIdentityRepository;
import com.landray.skillhub.shared.exception.ResourceConflictException;

/** 起真 PostgreSQL 测 {@link UserIdentityRepository}(backend/CLAUDE.md 8.1)。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class UserIdentityRepositoryImplTest {

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
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    private long newUserId() {
        return appUserRepository.save(AppUser.newUser("身份测试用户-" + System.nanoTime(), UserRole.USER)).id();
    }

    @Test
    void should_returnGeneratedId_when_saveCalled() {
        long userId = newUserId();

        UserIdentity saved = userIdentityRepository.save(
                UserIdentity.newLocal(userId, "login-" + System.nanoTime(), "bcrypt-hash"));

        assertThat(saved.id()).isNotNull();
    }

    @Test
    void should_findByProviderAndExternalId_when_identityExists() {
        long userId = newUserId();
        String login = "find-me-" + System.nanoTime();
        userIdentityRepository.save(UserIdentity.newLocal(userId, login, "hash"));

        Optional<UserIdentity> found =
                userIdentityRepository.findByProviderAndExternalId(IdentityProvider.LOCAL, login);

        assertThat(found).isPresent();
        assertThat(found.get().userId()).isEqualTo(userId);
        assertThat(found.get().status()).isEqualTo(IdentityStatus.ACTIVE);
    }

    @Test
    void should_returnEmpty_when_providerAndExternalIdNotFound() {
        Optional<UserIdentity> found = userIdentityRepository.findByProviderAndExternalId(
                IdentityProvider.LOCAL, "does-not-exist-" + System.nanoTime());

        assertThat(found).isEmpty();
    }

    @Test
    void should_throwResourceConflict_when_savingDuplicateProviderAndExternalId() {
        long firstUserId = newUserId();
        long secondUserId = newUserId();
        String duplicateLogin = "dup-login-" + System.nanoTime();
        userIdentityRepository.save(UserIdentity.newLocal(firstUserId, duplicateLogin, "hash-1"));

        assertThatThrownBy(() -> userIdentityRepository.save(
                        UserIdentity.newLocal(secondUserId, duplicateLogin, "hash-2")))
                .isInstanceOf(ResourceConflictException.class)
                .satisfies(ex ->
                        assertThat(((ResourceConflictException) ex).code()).isEqualTo("IDENTITY_EXTERNAL_ID_DUPLICATED"));
    }

    @Test
    void should_persistFailedLoginCountAndLockedUntil_when_updateCalled() {
        long userId = newUserId();
        UserIdentity saved = userIdentityRepository.save(
                UserIdentity.newLocal(userId, "lockout-" + System.nanoTime(), "hash"));
        Instant now = Instant.now();

        UserIdentity locked = saved.recordLoginFailure(now)
                .recordLoginFailure(now)
                .recordLoginFailure(now)
                .recordLoginFailure(now)
                .recordLoginFailure(now);
        userIdentityRepository.update(locked);

        Optional<UserIdentity> found = userIdentityRepository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().failedLoginCount()).isEqualTo(UserIdentity.MAX_FAILED_LOGIN_ATTEMPTS);
        assertThat(found.get().lockedUntil()).isNotNull();
        assertThat(found.get().isLocked(now)).isTrue();
    }

    @Test
    void should_returnIdentitiesForUser_when_findByUserIdCalled() {
        long userId = newUserId();
        userIdentityRepository.save(UserIdentity.newLocal(userId, "multi-1-" + System.nanoTime(), "hash"));
        userIdentityRepository.save(UserIdentity.newLocal(userId, "multi-2-" + System.nanoTime(), "hash"));

        List<UserIdentity> identities = userIdentityRepository.findByUserId(userId);

        assertThat(identities).hasSize(2);
        assertThat(identities).allMatch(identity -> identity.userId() == userId);
    }

    @Test
    void should_returnEmptyList_when_findByUserIdsCalledWithEmptyCollection() {
        List<UserIdentity> identities = userIdentityRepository.findByUserIds(List.of());

        assertThat(identities).isEmpty();
    }
}
