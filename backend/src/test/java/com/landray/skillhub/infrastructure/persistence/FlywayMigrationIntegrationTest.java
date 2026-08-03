package com.landray.skillhub.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 证明 Flyway 迁移真的在应用启动时被执行,而不是"应用起来了但一张表都没建"。
 *
 * <p>根 CLAUDE.md 踩坑点 8.2:Spring Boot 4 把自动配置拆成了多个独立模块,缺
 * {@code spring-boot-flyway} 时 Flyway 会静默不执行——不报任何错、应用正常启动、健康检查 UP、
 * 库里一张表都没有。所以本测试**必须**通过 {@link SpringBootTest} 让 Spring Boot 走真实的自动配置链路
 * 触发 Flyway,而不是绕开 Spring、直接手工调用 Flyway API——后者测不出这类"自动配置模块缺失"的回归。
 *
 * <p>起真 PostgreSQL(与 docker/docker-compose.yml 相同的 postgres:17-alpine),不用 H2 冒充,
 * 理由是方言差异会让测试骗人(backend/CLAUDE.md 8.1)。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class FlywayMigrationIntegrationTest {

    private static final String[] EXPECTED_TABLES = {
        "app_user",
        "user_identity",
        "user_session",
        "tag",
        "skill_tag",
        "skill",
        "skill_version",
        "skill_reaction",
        "skill_favorite",
        "skill_comment",
        "install_bundle"
    };

    private static final int EXPECTED_CURATED_TAG_COUNT = 9;

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

    @Test
    void should_createAllElevenTables_whenFlywayMigrationRuns() throws SQLException {
        Set<String> actualTables = fetchPublicTableNames();

        assertThat(actualTables).containsExactlyInAnyOrder(EXPECTED_TABLES);
    }

    @Test
    void should_notCreateUserMergeLogTable_whenFlywayMigrationRuns() throws SQLException {
        Set<String> actualTables = fetchPublicTableNames();

        assertThat(actualTables).doesNotContain("user_merge_log");
    }

    @Test
    void should_seedSystemAccountAsId1_whenFlywayMigrationRuns() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT display_name, role, status FROM app_user WHERE id = 1")) {
            ResultSet resultSet = statement.executeQuery();

            assertThat(resultSet.next()).as("id=1 的 system 账号必须存在").isTrue();
            assertThat(resultSet.getString("display_name")).isEqualTo("system");
            assertThat(resultSet.getString("role")).isEqualTo("ADMIN");
            assertThat(resultSet.getString("status")).isEqualTo("ACTIVE");
        }
    }

    @Test
    void should_seedNineCuratedTagsCreatedBySystem_whenFlywayMigrationRuns() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT count(*) FROM tag WHERE kind = 'CURATED' AND created_by_user_id = 1")) {
            ResultSet resultSet = statement.executeQuery();

            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(EXPECTED_CURATED_TAG_COUNT);
        }
    }

    @Test
    void should_rejectDuplicateProviderAndExternalId_whenInsertingUserIdentityTwice() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            long firstUserId = insertTestUser(connection, "constraint-test-user-1");
            long secondUserId = insertTestUser(connection, "constraint-test-user-2");
            try {
                insertUserIdentity(connection, firstUserId, "LOCAL", "duplicate-external-id");

                assertThatThrownBy(
                                () -> insertUserIdentity(connection, secondUserId, "LOCAL", "duplicate-external-id"))
                        .as("同一 (provider, external_id) 组合必须被 uk_user_identity_external 拒绝")
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("uk_user_identity_external");
            } finally {
                cleanupTestUsers(connection, firstUserId, secondUserId);
            }
        }
    }

    private Set<String> fetchPublicTableNames() throws SQLException {
        Set<String> tables = new HashSet<>();
        // flyway_schema_history 是 Flyway 自己的记账表,不是文档 4.3~4.13 定义的业务表,排除掉。
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT table_name FROM information_schema.tables "
                                + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                                + "AND table_name <> 'flyway_schema_history'")) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("table_name"));
            }
        }
        return tables;
    }

    private long insertTestUser(Connection connection, String displayName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO app_user (display_name) VALUES (?) RETURNING id")) {
            statement.setString(1, displayName);
            ResultSet resultSet = statement.executeQuery();
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private void insertUserIdentity(Connection connection, long userId, String provider, String externalId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO user_identity (user_id, provider, external_id) VALUES (?, ?, ?)")) {
            statement.setLong(1, userId);
            statement.setString(2, provider);
            statement.setString(3, externalId);
            statement.executeUpdate();
        }
    }

    private void cleanupTestUsers(Connection connection, long... userIds) throws SQLException {
        try (PreparedStatement deleteIdentities =
                        connection.prepareStatement("DELETE FROM user_identity WHERE user_id = ANY (?)");
                PreparedStatement deleteUsers =
                        connection.prepareStatement("DELETE FROM app_user WHERE id = ANY (?)")) {
            Long[] boxedIds = new Long[userIds.length];
            for (int i = 0; i < userIds.length; i++) {
                boxedIds[i] = userIds[i];
            }
            java.sql.Array idArray = connection.createArrayOf("bigint", boxedIds);
            deleteIdentities.setArray(1, idArray);
            deleteIdentities.executeUpdate();
            deleteUsers.setArray(1, idArray);
            deleteUsers.executeUpdate();
        }
    }
}
