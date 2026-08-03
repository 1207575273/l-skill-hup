package com.landray.skillhub.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.SkillArtifact;
import com.landray.skillhub.domain.model.SkillStatus;
import com.landray.skillhub.domain.repository.SkillArtifactRepository;

/** {@link SkillArtifactRepositoryImpl} 起真 PostgreSQL 测试(backend/CLAUDE.md 8.1)。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class SkillArtifactRepositoryImplIntegrationTest {

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
    private SkillArtifactRepository skillArtifactRepository;

    @AfterEach
    void cleanup() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM skill_version");
            statement.execute("DELETE FROM skill");
            statement.execute("DELETE FROM app_user WHERE id <> 1");
        }
    }

    @Test
    @DisplayName("按 skillId + 版本号定位制品,字段全部正确 join 出来")
    void should_findArtifactBySkillIdAndVersion_when_versionExists() throws SQLException {
        long owner = insertUser("张三");
        long skillId = insertSkill("code-review", owner, "PUBLISHED");
        insertVersion(skillId, "1.0.0", "1234567890abcdef".repeat(4), "REVOKED", "存在安全隐患");

        Optional<SkillArtifact> found = skillArtifactRepository.findBySkillIdAndVersion(skillId, "1.0.0");

        assertThat(found).isPresent();
        assertThat(found.get().skillName()).isEqualTo("code-review");
        assertThat(found.get().certification()).isEqualTo(Certification.REVOKED);
        assertThat(found.get().certifyNote()).isEqualTo("存在安全隐患");
        assertThat(found.get().skillStatus()).isEqualTo(SkillStatus.PUBLISHED);
    }

    @Test
    @DisplayName("版本不存在时返回空 Optional")
    void should_returnEmptyOptional_when_versionDoesNotExist() throws SQLException {
        long owner = insertUser("张三");
        long skillId = insertSkill("code-review", owner, "PUBLISHED");

        assertThat(skillArtifactRepository.findBySkillIdAndVersion(skillId, "9.9.9")).isEmpty();
    }

    @Test
    @DisplayName("按一批 versionId 批量取,顺带带出 skill.status = UNLISTED")
    void should_findByVersionIds_and_reportUnlistedSkillStatus() throws SQLException {
        long owner = insertUser("张三");
        long skillId = insertSkill("unlisted-skill", owner, "UNLISTED");
        long versionId = insertVersion(skillId, "1.0.0", "0".repeat(64), "NONE", null);

        List<SkillArtifact> found = skillArtifactRepository.findByVersionIds(List.of(versionId));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).isUnlisted()).isTrue();
    }

    @Test
    @DisplayName("批量查询空列表时不发查询,直接返回空列表")
    void should_returnEmptyList_when_versionIdsIsEmpty() {
        assertThat(skillArtifactRepository.findByVersionIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("下载计数原子自增,不读不算")
    void should_incrementDownloadCountAtomically_when_called() throws SQLException {
        long owner = insertUser("张三");
        long skillId = insertSkill("code-review", owner, "PUBLISHED");

        skillArtifactRepository.incrementDownloadCount(skillId);
        skillArtifactRepository.incrementDownloadCount(skillId);

        assertThat(selectDownloadCount(skillId)).isEqualTo(2L);
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

    private long insertSkill(String name, long ownerId, String status) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("INSERT INTO skill "
                        + "(name, display_name, description, source, owner_user_id, status) "
                        + "VALUES (?, ?, ?, 'INTERNAL', ?, ?) RETURNING id")) {
            ps.setString(1, name);
            ps.setString(2, "展示名 " + name);
            ps.setString(3, "描述 " + name);
            ps.setLong(4, ownerId);
            ps.setString(5, status);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    private long insertVersion(long skillId, String version, String sha256, String certification, String certifyNote)
            throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("INSERT INTO skill_version "
                        + "(skill_id, version, artifact_key, content_digest, sha256, size_bytes, file_count, "
                        + "skill_md_content, certification, certify_note, created_by_user_id) "
                        + "VALUES (?, ?, ?, ?, ?, 1024, 3, '# skill', ?, ?, 1) RETURNING id")) {
            ps.setLong(1, skillId);
            ps.setString(2, version);
            ps.setString(3, "artifacts/" + skillId + "/" + version + ".zip");
            ps.setString(4, "0".repeat(64));
            ps.setString(5, sha256);
            ps.setString(6, certification);
            ps.setString(7, certifyNote);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    private long selectDownloadCount(long skillId) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT download_count FROM skill WHERE id = ?")) {
            ps.setLong(1, skillId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }
}
