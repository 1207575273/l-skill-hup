package com.landray.skillhub.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

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

import com.landray.skillhub.domain.model.TagKind;
import com.landray.skillhub.domain.model.TagWithSkillCount;
import com.landray.skillhub.domain.repository.TagRepository;

/** {@link TagRepositoryImpl} 起真 PostgreSQL 测试。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class TagRepositoryImplIntegrationTest {

    private static final int SEEDED_CURATED_TAG_COUNT = 9;

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
    private TagRepository tagRepository;

    @AfterEach
    void cleanup() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM skill_tag");
            statement.execute("DELETE FROM skill_version");
            statement.execute("DELETE FROM skill");
            statement.execute("DELETE FROM tag WHERE kind = 'FREE'");
            statement.execute("DELETE FROM app_user WHERE id <> 1");
        }
    }

    @Test
    @DisplayName("不筛 kind 时返回全部 9 个种子 CURATED 标签")
    void should_returnAllSeededCuratedTags_whenKindFilterIsNull() {
        List<TagWithSkillCount> tags = tagRepository.findAll(null);

        long curatedCount =
                tags.stream().filter(item -> item.tag().kind() == TagKind.CURATED).count();
        assertThat(curatedCount).isEqualTo(SEEDED_CURATED_TAG_COUNT);
    }

    @Test
    @DisplayName("按 kind=FREE 筛选时只返回 FREE 标签")
    void should_returnOnlyFreeTags_whenFilteringByFreeKind() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            insertFreeTag(c, "自由标签A");

            List<TagWithSkillCount> tags = tagRepository.findAll(TagKind.FREE);

            assertThat(tags).extracting(item -> item.tag().name()).containsExactly("自由标签A");
        }
    }

    @Test
    @DisplayName("skill 数只统计当前可见(PUBLISHED)的 skill,下架的不计入")
    void should_countOnlyPublishedSkills_whenComputingSkillCount() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long publishedSkill = insertSkill(c, "published-skill", owner, "PUBLISHED");
            long unlistedSkill = insertSkill(c, "unlisted-skill", owner, "UNLISTED");
            tagSkill(c, publishedSkill, 1L);
            tagSkill(c, unlistedSkill, 1L);

            List<TagWithSkillCount> tags = tagRepository.findAll(TagKind.CURATED);

            TagWithSkillCount codeGenerationTag = tags.stream()
                    .filter(item -> item.tag().id() == 1L)
                    .findFirst()
                    .orElseThrow();
            assertThat(codeGenerationTag.skillCount()).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("没有任何 skill 关联的标签,skill 数为 0")
    void should_reportZeroSkillCount_whenTagHasNoSkills() {
        List<TagWithSkillCount> tags = tagRepository.findAll(TagKind.CURATED);

        assertThat(tags).allSatisfy(item -> assertThat(item.skillCount()).isZero());
    }

    private long insertUser(Connection c, String displayName, String employeeNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO app_user (display_name, employee_no) VALUES (?, ?) RETURNING id")) {
            ps.setString(1, displayName);
            ps.setString(2, employeeNo);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    private long insertSkill(Connection c, String name, long ownerId, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO skill "
                + "(name, display_name, description, source, owner_user_id, status) "
                + "VALUES (?, ?, ?, 'INTERNAL', ?, ?) RETURNING id")) {
            ps.setString(1, name);
            ps.setString(2, name);
            ps.setString(3, "描述");
            ps.setLong(4, ownerId);
            ps.setString(5, status);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    private void insertFreeTag(Connection c, String name) throws SQLException {
        try (PreparedStatement ps =
                c.prepareStatement("INSERT INTO tag (name, kind, created_by_user_id) VALUES (?, 'FREE', 1)")) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
    }

    private void tagSkill(Connection c, long skillId, long tagId) throws SQLException {
        try (PreparedStatement ps =
                c.prepareStatement("INSERT INTO skill_tag (skill_id, tag_id) VALUES (?, ?)")) {
            ps.setLong(1, skillId);
            ps.setLong(2, tagId);
            ps.executeUpdate();
        }
    }
}
