package com.landray.skillhub.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.landray.skillhub.domain.model.ClientCode;
import com.landray.skillhub.domain.model.InstallBundle;
import com.landray.skillhub.domain.model.InstallItem;
import com.landray.skillhub.domain.model.InstallScope;
import com.landray.skillhub.domain.repository.InstallBundleRepository;
import com.landray.skillhub.shared.exception.ResourceConflictException;

/**
 * {@link InstallBundleRepositoryImpl} 起真 PostgreSQL 测试(backend/CLAUDE.md 8.1:方言差异会让测试骗人)。
 *
 * <p>重点覆盖两处纯 MyBatis 认不出来的类型转换:{@code items}(JSONB,{@link
 * com.landray.skillhub.infrastructure.persistence.typehandler.InstallItemsTypeHandler})与
 * {@code client}/{@code scope}(数据库存小写字符串,与 Java 枚举常量名大小写不一致)。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class InstallBundleRepositoryImplIntegrationTest {

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
    private DataSource dataSource;

    @Autowired
    private InstallBundleRepository installBundleRepository;

    @AfterEach
    void cleanup() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM install_bundle");
        }
    }

    @Test
    @DisplayName("保存后按 token 查回,items(JSONB)与 client/scope(小写字符串)都正确往返")
    void should_roundTripItemsAndClientScope_when_savingAndFindingByToken() {
        InstallBundle bundle = InstallBundle.create(
                List.of(new InstallItem(1L, 10L), new InstallItem(2L, 20L)), ClientCode.CODEX,
                InstallScope.PROJECT, null, Instant.now().truncatedTo(ChronoUnit.MICROS),
                Duration.ofDays(30));

        InstallBundle saved = installBundleRepository.save(bundle);
        Optional<InstallBundle> found = installBundleRepository.findByToken(bundle.token());

        assertThat(saved.id()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().items()).containsExactly(new InstallItem(1L, 10L), new InstallItem(2L, 20L));
        assertThat(found.get().client()).isEqualTo(ClientCode.CODEX);
        assertThat(found.get().scope()).isEqualTo(InstallScope.PROJECT);
        assertThat(found.get().createdByUserId()).isNull();
    }

    @Test
    @DisplayName("createdByUserId 非空时同样正确往返(登录用户生成的安装单)")
    void should_persistCreatedByUserId_when_provided() throws SQLException {
        long userId = insertUser("张三");
        InstallBundle bundle = InstallBundle.create(
                List.of(new InstallItem(1L, 10L)), ClientCode.CLAUDE, InstallScope.GLOBAL, userId, Instant.now(),
                Duration.ofDays(30));

        installBundleRepository.save(bundle);
        Optional<InstallBundle> found = installBundleRepository.findByToken(bundle.token());

        assertThat(found).isPresent();
        assertThat(found.get().createdByUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("token 不存在时返回空 Optional")
    void should_returnEmptyOptional_when_tokenDoesNotExist() {
        Optional<InstallBundle> found = installBundleRepository.findByToken("never-issued-token");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("token 唯一索引冲突时映射成 409,而不是把原始约束异常一路抛给上层")
    void should_mapDuplicateTokenToResourceConflict_when_tokenAlreadyExists() {
        InstallBundle first = InstallBundle.create(
                List.of(new InstallItem(1L, 10L)), ClientCode.CLAUDE, InstallScope.GLOBAL, null, Instant.now(),
                Duration.ofDays(30));
        installBundleRepository.save(first);

        InstallBundle duplicateToken = new InstallBundle(
                null, first.token(), List.of(new InstallItem(2L, 20L)), ClientCode.CODEX, InstallScope.PROJECT,
                null, Instant.now(), Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> installBundleRepository.save(duplicateToken))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("token");
    }

    @Test
    @DisplayName("deleteExpiredBefore 只删阈值之前过期的行,阈值之后过期的行保留")
    void should_deleteOnlyRowsExpiredBeforeThreshold_when_cleaningUp() throws SQLException {
        Instant now = Instant.now();
        insertBundleWithExpiry("token-old", now.minusSeconds(3600));
        insertBundleWithExpiry("token-recent", now.minusSeconds(10));

        int deletedCount = installBundleRepository.deleteExpiredBefore(now.minusSeconds(60));

        assertThat(deletedCount).isEqualTo(1);
        assertThat(installBundleRepository.findByToken("token-old")).isEmpty();
        assertThat(installBundleRepository.findByToken("token-recent")).isPresent();
    }

    private long insertUser(String displayName) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps =
                        c.prepareStatement("INSERT INTO app_user (display_name) VALUES (?) RETURNING id")) {
            ps.setString(1, displayName);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    private void insertBundleWithExpiry(String token, Instant expiresAt) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("INSERT INTO install_bundle "
                        + "(token, items, client, scope, expires_at) "
                        + "VALUES (?, '[{\"skillId\":1,\"versionId\":10}]'::jsonb, 'claude', 'global', ?)")) {
            ps.setString(1, token);
            ps.setObject(2, expiresAt.atOffset(ZoneOffset.UTC));
            ps.executeUpdate();
        }
    }
}
