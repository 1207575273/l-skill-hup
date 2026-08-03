package com.landray.skillhub.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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

import com.landray.skillhub.domain.model.SkillComment;
import com.landray.skillhub.domain.repository.SkillCommentRepository;
import com.landray.skillhub.shared.model.PageResult;

/**
 * 起真 PostgreSQL 测评论仓储(backend/CLAUDE.md 8.1:不用 H2 冒充)。
 *
 * <p>这里的重点全在<b>软删除的可见性规则</b>与<b>分页口径</b>——两者都是"看起来能跑,数据一多就露馅"的类型:
 * 分页口径错了要等到评论超过一页才发现,软删除过滤错了要等到有人删了带回复的评论才发现。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class SkillCommentRepositoryImplIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SkillCommentRepository skillCommentRepository;

    @AfterEach
    void cleanup() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM skill_comment");
            statement.execute("DELETE FROM skill");
            statement.execute("DELETE FROM app_user WHERE id <> 1");
        }
    }

    @Test
    @DisplayName("回复挂在它的顶级评论上,不是平铺成独立条目")
    void should_nestRepliesUnderTopLevel_when_listing() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long user = insertUser(c, "张三");
            long skillId = insertSkill(c, "code-review", user);
            long topId = insertComment(c, skillId, user, null, "顶级评论");
            insertComment(c, skillId, user, topId, "回复一");
            insertComment(c, skillId, user, topId, "回复二");

            PageResult<SkillComment> result = skillCommentRepository.findBySkillId(skillId, 1, 20);

            assertThat(result.total()).isEqualTo(1L);
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).replies())
                    .extracting(SkillComment::content)
                    .containsExactly("回复一", "回复二");
        }
    }

    @Test
    @DisplayName("分页数的是顶级评论,不是评论行——回复不该把分页顶爆")
    void should_countTopLevelOnly_when_paging() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long user = insertUser(c, "张三");
            long skillId = insertSkill(c, "code-review", user);
            long topId = insertComment(c, skillId, user, null, "唯一的顶级评论");
            for (int i = 0; i < 5; i++) {
                insertComment(c, skillId, user, topId, "回复" + i);
            }

            PageResult<SkillComment> result = skillCommentRepository.findBySkillId(skillId, 1, 20);

            // 按行数就是 6。分页口径错了要等到评论超过一页才发现,那时已经很难联想到是 count 的口径问题。
            assertThat(result.total()).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("顶级倒序、回复正序——最新的评论先看到,而一段对话要顺着读")
    void should_orderTopLevelDescAndRepliesAsc_when_listing() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long user = insertUser(c, "张三");
            long skillId = insertSkill(c, "code-review", user);
            long first = insertComment(c, skillId, user, null, "先发的");
            insertComment(c, skillId, user, null, "后发的");
            insertComment(c, skillId, user, first, "回复A");
            insertComment(c, skillId, user, first, "回复B");

            PageResult<SkillComment> result = skillCommentRepository.findBySkillId(skillId, 1, 20);

            assertThat(result.items()).extracting(SkillComment::content).containsExactly("后发的", "先发的");
            assertThat(result.items().get(1).replies())
                    .extracting(SkillComment::content)
                    .containsExactly("回复A", "回复B");
        }
    }

    @Test
    @DisplayName("删掉的顶级评论若还有回复则保留占位,但正文不返回")
    void should_keepDeletedTopLevelAsPlaceholder_when_itStillHasReplies() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long user = insertUser(c, "张三");
            long skillId = insertSkill(c, "code-review", user);
            long topId = insertComment(c, skillId, user, null, "要被删掉的原文");
            insertComment(c, skillId, user, topId, "回复还在");

            skillCommentRepository.softDelete(topId);
            PageResult<SkillComment> result = skillCommentRepository.findBySkillId(skillId, 1, 20);

            assertThat(result.items()).hasSize(1);
            SkillComment deleted = result.items().get(0);
            assertThat(deleted.isDeleted()).isTrue();
            // 正文必须是 null 而不是原文:让已删除的内容根本不出仓储,漏判 status 的代码才不会把它重新展示出来。
            assertThat(deleted.content()).isNull();
            assertThat(deleted.replies()).extracting(SkillComment::content).containsExactly("回复还在");
        }
    }

    @Test
    @DisplayName("删掉的顶级评论若没有回复则整条不返回——占位是为了不让回复悬空,没回复就没有理由留噪音")
    void should_hideDeletedTopLevel_when_itHasNoReplies() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long user = insertUser(c, "张三");
            long skillId = insertSkill(c, "code-review", user);
            long topId = insertComment(c, skillId, user, null, "孤零零的评论");

            skillCommentRepository.softDelete(topId);
            PageResult<SkillComment> result = skillCommentRepository.findBySkillId(skillId, 1, 20);

            assertThat(result.items()).isEmpty();
            // total 必须与 items 同口径,否则会出现"显示有 1 条评论,列表却是空的"
            assertThat(result.total()).isZero();
        }
    }

    @Test
    @DisplayName("删掉的回复直接不返回——它下面不可能再挂东西")
    void should_hideDeletedReply_when_listing() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long user = insertUser(c, "张三");
            long skillId = insertSkill(c, "code-review", user);
            long topId = insertComment(c, skillId, user, null, "顶级");
            long replyId = insertComment(c, skillId, user, topId, "要删的回复");
            insertComment(c, skillId, user, topId, "留下的回复");

            skillCommentRepository.softDelete(replyId);
            PageResult<SkillComment> result = skillCommentRepository.findBySkillId(skillId, 1, 20);

            assertThat(result.items().get(0).replies())
                    .extracting(SkillComment::content)
                    .containsExactly("留下的回复");
        }
    }

    @Test
    @DisplayName("重复软删除返回 0 行——调用方据此不重复扣 comment_count")
    void should_returnZero_when_deletingAlreadyDeletedComment() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long user = insertUser(c, "张三");
            long skillId = insertSkill(c, "code-review", user);
            long topId = insertComment(c, skillId, user, null, "评论");

            assertThat(skillCommentRepository.softDelete(topId)).isEqualTo(1);
            // 连点两次删除,若这里也返回 1,计数会被扣两次,最终显示成负数或偏小,而冗余计数错了没有任何地方会报错
            assertThat(skillCommentRepository.softDelete(topId)).isZero();
        }
    }

    @Test
    @DisplayName("findById 对已删除的也返回——调用方要能区分『不存在』与『已经删过了』")
    void should_returnDeletedComment_when_findingById() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long user = insertUser(c, "张三");
            long skillId = insertSkill(c, "code-review", user);
            long topId = insertComment(c, skillId, user, null, "评论");
            skillCommentRepository.softDelete(topId);

            assertThat(skillCommentRepository.findById(topId)).isPresent();
            assertThat(skillCommentRepository.findById(999_999L)).isEmpty();
        }
    }

    @Test
    @DisplayName("空评论区返回空页而不是报错——foreach 展开成 IN () 是语法错误,必须短路")
    void should_returnEmptyPage_when_skillHasNoComment() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long user = insertUser(c, "张三");
            long skillId = insertSkill(c, "code-review", user);

            PageResult<SkillComment> result = skillCommentRepository.findBySkillId(skillId, 1, 20);

            assertThat(result.items()).isEmpty();
            assertThat(result.total()).isZero();
        }
    }

    @Test
    @DisplayName("只返回该 skill 的评论,不串到别的 skill 上")
    void should_scopeToSkill_when_listing() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long user = insertUser(c, "张三");
            long skillA = insertSkill(c, "skill-a", user);
            long skillB = insertSkill(c, "skill-b", user);
            insertComment(c, skillA, user, null, "A 的评论");
            insertComment(c, skillB, user, null, "B 的评论");

            PageResult<SkillComment> result = skillCommentRepository.findBySkillId(skillA, 1, 20);

            assertThat(result.items()).extracting(SkillComment::content).containsExactly("A 的评论");
        }
    }

    private long insertUser(Connection c, String displayName) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO app_user (display_name, employee_no) VALUES (?, ?) RETURNING id")) {
            ps.setString(1, displayName);
            ps.setString(2, "8000" + displayName.hashCode());
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    private long insertSkill(Connection c, String name, long ownerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO skill "
                + "(name, display_name, description, source, owner_user_id, card_excerpt) "
                + "VALUES (?, ?, ?, 'INTERNAL', ?, ?) RETURNING id")) {
            ps.setString(1, name);
            ps.setString(2, name);
            ps.setString(3, "描述");
            ps.setLong(4, ownerId);
            ps.setString(5, "摘要");
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    /**
     * 直接插库而不是走 repository.insert：fixture 要能精确控制插入顺序与父子关系,
     * 而 created_at 用数据库默认值时同一毫秒内的多条记录靠 id 兜底排序(与查询里的 ORDER BY 一致)。
     */
    private long insertComment(Connection c, long skillId, long userId, Long parentId, String content)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO skill_comment (skill_id, user_id, parent_id, content) VALUES (?, ?, ?, ?) RETURNING id")) {
            ps.setLong(1, skillId);
            ps.setLong(2, userId);
            if (parentId == null) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, parentId);
            }
            ps.setString(4, content);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }
}
