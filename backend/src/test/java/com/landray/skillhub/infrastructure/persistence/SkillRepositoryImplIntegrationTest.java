package com.landray.skillhub.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillName;
import com.landray.skillhub.domain.model.SkillSearchCriteria;
import com.landray.skillhub.domain.model.SkillSortOption;
import com.landray.skillhub.domain.model.SkillSource;
import com.landray.skillhub.domain.model.SkillStatus;
import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.shared.exception.ResourceConflictException;
import com.landray.skillhub.shared.model.PageResult;

/**
 * {@link SkillRepositoryImpl} 起真 PostgreSQL 测试(backend/CLAUDE.md 8.1:方言差异会让测试骗人,不用 H2 冒充)。
 *
 * <p>每个测试方法自行插入所需 fixture(用户 / skill / 版本 / 标签),{@link #cleanup()} 在每个测试后把库清回
 * Flyway 种子数据的基线(仅 system 账号 + 9 个 CURATED 标签),不同测试之间不共享 fixture、互不干扰。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class SkillRepositoryImplIntegrationTest {

    private static final String NONE = Certification.NONE.name();
    private static final String CERTIFIED = Certification.CERTIFIED.name();

    /** V0002 预置的 CURATED 标签 id:1=代码生成 2=代码审查 3=测试。 */
    private static final long TAG_CODE_GENERATION = 1L;

    private static final long TAG_CODE_REVIEW = 2L;
    private static final long TAG_TESTING = 3L;

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
    private SkillRepository skillRepository;

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
    @DisplayName("下架(UNLISTED)的 skill 不应出现在列表里")
    void should_excludeUnlistedSkill_whenSearchingList() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long published = insertSkill(c, "published-skill", "已发布", "INTERNAL", owner, "PUBLISHED");
            long unlisted = insertSkill(c, "unlisted-skill", "已下架", "INTERNAL", owner, "UNLISTED");
            attachVersion(c, published, "1.0.0", NONE);
            attachVersion(c, unlisted, "1.0.0", NONE);

            PageResult<Skill> result = skillRepository.search(defaultCriteria());

            assertThat(result.items()).extracting(skill -> skill.name().value()).containsExactly("published-skill");
            assertThat(result.total()).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("下架的 skill 仍可通过详情接口直接访问,只是不进列表")
    void should_returnUnlistedSkill_whenFindingDetailById() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long skillId = insertSkill(c, "unlisted-skill", "已下架", "INTERNAL", owner, "UNLISTED");
            attachVersion(c, skillId, "1.0.0", NONE);

            Optional<Skill> found = skillRepository.findDetailById(skillId);

            assertThat(found).isPresent();
            assertThat(found.get().status().name()).isEqualTo("UNLISTED");
        }
    }

    @Test
    @DisplayName("skill 不存在时详情查询返回空 Optional")
    void should_returnEmptyOptional_whenSkillDoesNotExist() {
        Optional<Skill> found = skillRepository.findDetailById(999_999L);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsById 对已存在的 skill 返回 true,对不存在的返回 false")
    void should_reportExistence_whenCheckingSkillId() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long skillId = insertSkill(c, "exists-skill", "存在", "INTERNAL", owner, "PUBLISHED");

            assertThat(skillRepository.existsById(skillId)).isTrue();
            assertThat(skillRepository.existsById(999_999L)).isFalse();
        }
    }

    @Test
    @DisplayName("多标签筛选按 AND 语义,total 与实际匹配行数一致——这是最容易写错又最难发现的一处")
    void should_returnCorrectTotal_whenFilteringByMultipleTagsWithAndSemantics() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");

            long matchesBoth = insertSkill(c, "matches-both", "同时命中两个标签", "INTERNAL", owner, "PUBLISHED");
            attachVersion(c, matchesBoth, "1.0.0", NONE);
            tagSkill(c, matchesBoth, TAG_CODE_GENERATION);
            tagSkill(c, matchesBoth, TAG_CODE_REVIEW);

            long matchesBothPlusExtra =
                    insertSkill(c, "matches-both-plus-extra", "命中两个再加一个额外标签", "INTERNAL", owner, "PUBLISHED");
            attachVersion(c, matchesBothPlusExtra, "1.0.0", NONE);
            tagSkill(c, matchesBothPlusExtra, TAG_CODE_GENERATION);
            tagSkill(c, matchesBothPlusExtra, TAG_CODE_REVIEW);
            tagSkill(c, matchesBothPlusExtra, TAG_TESTING);

            long matchesOnlyOne = insertSkill(c, "matches-only-one", "只命中一个标签", "INTERNAL", owner, "PUBLISHED");
            attachVersion(c, matchesOnlyOne, "1.0.0", NONE);
            tagSkill(c, matchesOnlyOne, TAG_CODE_GENERATION);

            SkillSearchCriteria criteria = new SkillSearchCriteria(
                    null, List.of(TAG_CODE_GENERATION, TAG_CODE_REVIEW), false, null, null, SkillSortOption.LATEST, 1,
                    20);

            PageResult<Skill> result = skillRepository.search(criteria);

            assertThat(result.total()).isEqualTo(2L);
            assertThat(result.items())
                    .extracting(skill -> skill.name().value())
                    .containsExactlyInAnyOrder("matches-both", "matches-both-plus-extra");
        }
    }

    @Test
    @DisplayName("keyword 命中字面 % 时只匹配真正包含该字符的行,不误伤只含数字部分的行")
    void should_matchLiteralPercentSign_whenKeywordContainsPercent() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");

            long withPercent = insertSkillWithExcerpt(
                    c, "promo-skill", "限时优惠", "INTERNAL", owner, "限时优惠100%力度大");
            attachVersion(c, withPercent, "1.0.0", NONE);

            long withoutPercent = insertSkillWithExcerpt(
                    c, "top100-ranking", "排行榜", "INTERNAL", owner, "人气top100排行榜");
            attachVersion(c, withoutPercent, "1.0.0", NONE);

            SkillSearchCriteria criteria =
                    new SkillSearchCriteria(null, List.of(), false, "100%", null, SkillSortOption.LATEST, 1, 20);

            PageResult<Skill> result = skillRepository.search(criteria);

            assertThat(result.items()).extracting(skill -> skill.name().value()).containsExactly("promo-skill");
        }
    }

    @Test
    @DisplayName("keyword 命中字面 _ 时只匹配真正包含该字符的行,不把它当成单字符通配符")
    void should_matchLiteralUnderscore_whenKeywordContainsUnderscore() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");

            long withUnderscore =
                    insertSkillWithExcerpt(c, "foo-bar-skill", "带下划线", "INTERNAL", owner, "foo_bar_baz");
            attachVersion(c, withUnderscore, "1.0.0", NONE);

            long withoutUnderscore =
                    insertSkillWithExcerpt(c, "fooxbarxskill", "不带下划线", "INTERNAL", owner, "fooxbarxbaz");
            attachVersion(c, withoutUnderscore, "1.0.0", NONE);

            SkillSearchCriteria criteria = new SkillSearchCriteria(
                    null, List.of(), false, "foo_bar", null, SkillSortOption.LATEST, 1, 20);

            PageResult<Skill> result = skillRepository.search(criteria);

            assertThat(result.items()).extracting(skill -> skill.name().value()).containsExactly("foo-bar-skill");
        }
    }

    @Test
    @DisplayName("没有任何 skill 匹配筛选条件时返回空列表而不是报错")
    void should_returnEmptyItems_whenNoSkillMatchesFilter() {
        SkillSearchCriteria criteria = new SkillSearchCriteria(
                SkillSource.COMMUNITY, List.of(), false, null, null, SkillSortOption.LATEST, 1, 20);

        PageResult<Skill> result = skillRepository.search(criteria);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    @DisplayName("page 超出实际总页数时返回空列表,total 仍反映真实匹配数,而不是报错")
    void should_returnEmptyItems_whenPageExceedsAvailableResults() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long skillId = insertSkill(c, "only-one-skill", "唯一一个", "INTERNAL", owner, "PUBLISHED");
            attachVersion(c, skillId, "1.0.0", NONE);

            SkillSearchCriteria criteria =
                    new SkillSearchCriteria(null, List.of(), false, null, null, SkillSortOption.LATEST, 99, 20);

            PageResult<Skill> result = skillRepository.search(criteria);

            assertThat(result.items()).isEmpty();
            assertThat(result.total()).isEqualTo(1L);
            assertThat(result.page()).isEqualTo(99);
        }
    }

    @Test
    @DisplayName("按 downloads 排序时降序返回")
    void should_sortByDownloadCountDescending_whenSortIsDownloads() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long low = insertSkillWithCounts(c, "low-download", "低下载", owner, 10L, 0, 0);
            long high = insertSkillWithCounts(c, "high-download", "高下载", owner, 200L, 0, 0);
            attachVersion(c, low, "1.0.0", NONE);
            attachVersion(c, high, "1.0.0", NONE);

            SkillSearchCriteria criteria = new SkillSearchCriteria(
                    null, List.of(), false, null, null, SkillSortOption.DOWNLOADS, 1, 20);

            PageResult<Skill> result = skillRepository.search(criteria);

            assertThat(result.items())
                    .extracting(skill -> skill.name().value())
                    .containsExactly("high-download", "low-download");
        }
    }

    @Test
    @DisplayName("certifiedOnly 只保留最新版本已认证的 skill")
    void should_filterByCertifiedOnly_whenCertifiedOnlyIsTrue() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long certified = insertSkill(c, "certified-skill", "已认证", "INTERNAL", owner, "PUBLISHED");
            long uncertified = insertSkill(c, "uncertified-skill", "未认证", "INTERNAL", owner, "PUBLISHED");
            attachVersion(c, certified, "1.0.0", CERTIFIED);
            attachVersion(c, uncertified, "1.0.0", NONE);

            SkillSearchCriteria criteria =
                    new SkillSearchCriteria(null, List.of(), true, null, null, SkillSortOption.LATEST, 1, 20);

            PageResult<Skill> result = skillRepository.search(criteria);

            assertThat(result.items()).extracting(skill -> skill.name().value()).containsExactly("certified-skill");
        }
    }

    @Test
    @DisplayName("source 筛选只保留指定来源的 skill")
    void should_filterBySource_whenSourceProvided() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long internal = insertSkill(c, "internal-skill", "内部", "INTERNAL", owner, "PUBLISHED");
            long community = insertSkill(c, "community-skill", "社区", "COMMUNITY", owner, "PUBLISHED");
            attachVersion(c, internal, "1.0.0", NONE);
            attachVersion(c, community, "1.0.0", NONE);

            SkillSearchCriteria criteria = new SkillSearchCriteria(
                    SkillSource.COMMUNITY, List.of(), false, null, null, SkillSortOption.LATEST, 1, 20);

            PageResult<Skill> result = skillRepository.search(criteria);

            assertThat(result.items()).extracting(skill -> skill.name().value()).containsExactly("community-skill");
        }
    }

    @Test
    @DisplayName("列表项携带完整的标签行,CURATED 与 FREE 都在,按 kind/sortOrder/name 排序")
    void should_attachTagsOrderedByKindThenSortOrder_whenSearchingList() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long freeTag = insertFreeTag(c, "金字塔原理");
            long skillId = insertSkill(c, "tagged-skill", "带标签", "INTERNAL", owner, "PUBLISHED");
            attachVersion(c, skillId, "1.0.0", NONE);
            tagSkill(c, skillId, TAG_CODE_REVIEW);
            tagSkill(c, skillId, freeTag);

            PageResult<Skill> result = skillRepository.search(defaultCriteria());

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).tags())
                    .extracting(tag -> tag.name())
                    .containsExactly("代码审查", "金字塔原理");
        }
    }

    @Test
    @DisplayName("详情查询携带最新版本全部字段,包括 TEXT[] 类型的 allowedTools 与正文")
    void should_populateFullLatestVersionContent_whenFindingDetailById() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long skillId = insertSkill(c, "detail-skill", "详情", "INTERNAL", owner, "PUBLISHED");
            attachVersion(c, skillId, "1.0.0", NONE);

            Optional<Skill> found = skillRepository.findDetailById(skillId);

            assertThat(found).isPresent();
            SkillVersion latestVersion = found.get().latestVersion();
            assertThat(latestVersion).isNotNull();
            assertThat(latestVersion.allowedTools()).containsExactly("Read", "Grep");
            assertThat(latestVersion.skillMdContent()).isEqualTo("# skill\n正文");
            assertThat(latestVersion.readmeContent()).isEqualTo("# readme");
        }
    }

    @Test
    @DisplayName("版本列表按发布时间倒序,含每个版本的认证状态")
    void should_returnVersionsOrderedByCreatedAtDescending_whenListingVersions() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long skillId = insertSkill(c, "multi-version-skill", "多版本", "INTERNAL", owner, "PUBLISHED");
            attachVersion(c, skillId, "1.0.0", NONE);
            attachVersion(c, skillId, "1.1.0", CERTIFIED);

            List<SkillVersion> versions = skillRepository.findVersionsBySkillId(skillId);

            assertThat(versions).extracting(SkillVersion::version).containsExactly("1.1.0", "1.0.0");
            assertThat(versions.get(0).certification()).isEqualTo(Certification.CERTIFIED);
        }
    }

    // ------------------------------------------------------------------
    // 写侧(上传管线任务新增):findByName / findVersionByContentDigest / findLatestVersionString /
    // insertSkill / insertVersion / updateLatestVersionIfGreater / replaceTags / updateMetadata / updateStatus
    // ------------------------------------------------------------------

    @Test
    @DisplayName("按 name 查详情,能查到 owner 与最新版本")
    void should_findSkillDetail_whenFindingByName() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            insertSkill(c, "find-by-name", "按名查找", "INTERNAL", owner, "PUBLISHED");

            Optional<Skill> found = skillRepository.findByName("find-by-name");

            assertThat(found).isPresent();
            assertThat(found.get().owner().displayName()).isEqualTo("张三");
        }
    }

    @Test
    @DisplayName("name 不存在时返回空 Optional")
    void should_returnEmpty_whenFindingByNameNotExists() {
        assertThat(skillRepository.findByName("does-not-exist")).isEmpty();
    }

    @Test
    @DisplayName("按 content_digest 查到已有版本(precheck 的\"完全相同的包已存在\"判定)")
    void should_findVersionByContentDigest_whenDigestMatches() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long skillId = insertSkill(c, "digest-skill", "摘要匹配", "INTERNAL", owner, "PUBLISHED");
            attachVersionWithDigest(c, skillId, "1.0.0", "abc123digest");

            Optional<SkillVersion> found = skillRepository.findVersionByContentDigest("abc123digest");

            assertThat(found).isPresent();
            assertThat(found.get().skillId()).isEqualTo(skillId);
            assertThat(found.get().version()).isEqualTo("1.0.0");
        }
    }

    @Test
    @DisplayName("content_digest 不存在时返回空 Optional")
    void should_returnEmpty_whenContentDigestNotFound() {
        assertThat(skillRepository.findVersionByContentDigest("no-such-digest")).isEmpty();
    }

    @Test
    @DisplayName("查最新版本号:有版本时返回,尚无版本时返回空")
    void should_findLatestVersionString() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long skillId = insertSkill(c, "latest-version-skill", "最新版本", "INTERNAL", owner, "PUBLISHED");

            assertThat(skillRepository.findLatestVersionString(skillId)).isEmpty();

            attachVersion(c, skillId, "1.0.0", NONE);

            assertThat(skillRepository.findLatestVersionString(skillId)).contains("1.0.0");
        }
    }

    @Test
    @DisplayName("插入新 skill 成功,返回生成的 id")
    void should_insertSkill_whenNameIsUnique() throws SQLException {
        long owner = insertUser(dataSource.getConnection(), "张三", "80380486");

        long skillId = skillRepository.insertSkill(
                new SkillName("insert-skill-test"), "展示名", "描述", SkillSource.INTERNAL, owner, "摘要");

        assertThat(skillId).isPositive();
        assertThat(skillRepository.findByName("insert-skill-test")).isPresent();
    }

    @Test
    @DisplayName("插入同名 skill 时抛 SKILL_NAME_DUPLICATED,不做先查后插")
    void should_throwConflict_whenInsertingDuplicateSkillName() throws SQLException {
        long owner = insertUser(dataSource.getConnection(), "张三", "80380486");
        skillRepository.insertSkill(new SkillName("dup-name"), "展示名", "描述", SkillSource.INTERNAL, owner, "摘要");

        assertThatThrownBy(() -> skillRepository.insertSkill(
                        new SkillName("dup-name"), "另一个展示名", "另一个描述", SkillSource.COMMUNITY, owner, "摘要"))
                .isInstanceOf(ResourceConflictException.class)
                .satisfies(ex -> assertThat(((ResourceConflictException) ex).code()).isEqualTo("SKILL_NAME_DUPLICATED"));
    }

    @Test
    @DisplayName("并发插入同名 skill:只有一个成功,另一个收到 SKILL_NAME_DUPLICATED(业务建模文档 5.14)")
    void should_allowOnlyOneWinner_whenConcurrentlyInsertingSameSkillName() throws InterruptedException, SQLException {
        long owner = insertUser(dataSource.getConnection(), "张三", "80380486");
        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    try {
                        skillRepository.insertSkill(
                                new SkillName("race-name"), "展示名", "描述", SkillSource.INTERNAL, owner, "摘要");
                        return true;
                    } catch (ResourceConflictException e) {
                        return false;
                    }
                }));
            }
            startLatch.countDown();

            long successCount = futures.stream()
                    .map(future -> {
                        try {
                            return future.get(10, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .filter(Boolean::booleanValue)
                    .count();

            assertThat(successCount).isEqualTo(1);
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("插入同一 skill 下重复版本号时抛 SKILL_VERSION_DUPLICATED")
    void should_throwConflict_whenInsertingDuplicateVersion() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long skillId = insertSkill(c, "dup-version-skill", "重复版本", "INTERNAL", owner, "PUBLISHED");
            skillRepository.insertVersion(newVersionDraft(skillId, "1.0.0"));

            assertThatThrownBy(() -> skillRepository.insertVersion(newVersionDraft(skillId, "1.0.0")))
                    .isInstanceOf(ResourceConflictException.class)
                    .satisfies(ex -> assertThat(((ResourceConflictException) ex).code())
                            .isEqualTo("SKILL_VERSION_DUPLICATED"));
        }
    }

    @Test
    @DisplayName("latest_version_id 首次设置:原本为 null 时无条件更新")
    void should_setLatestVersion_whenPreviouslyNull() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long skillId = insertSkill(c, "first-latest-skill", "首次设置", "INTERNAL", owner, "PUBLISHED");
            long versionId = skillRepository.insertVersion(newVersionDraft(skillId, "1.0.0"));

            boolean updated =
                    skillRepository.updateLatestVersionIfGreater(skillId, versionId, "1.0.0", "新描述", "新摘要");

            assertThat(updated).isTrue();
            assertThat(skillRepository.findLatestVersionString(skillId)).contains("1.0.0");
        }
    }

    @Test
    @DisplayName("latest_version_id 数值序比较:10.0.0 能正确覆盖 9.0.0,不按字符串序误判")
    void should_updateLatestVersion_whenNewVersionIsNumericallyGreater() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long skillId = insertSkill(c, "numeric-semver-skill", "数值序", "INTERNAL", owner, "PUBLISHED");
            long v9 = skillRepository.insertVersion(newVersionDraft(skillId, "9.0.0"));
            skillRepository.updateLatestVersionIfGreater(skillId, v9, "9.0.0", "d9", "e9");
            long v10 = skillRepository.insertVersion(newVersionDraft(skillId, "10.0.0"));

            boolean updated = skillRepository.updateLatestVersionIfGreater(skillId, v10, "10.0.0", "d10", "e10");

            assertThat(updated).isTrue();
            assertThat(skillRepository.findLatestVersionString(skillId)).contains("10.0.0");
        }
    }

    @Test
    @DisplayName("latest_version_id 带递增条件:传入不大于当前值的版本时不更新,返回 false")
    void should_notUpdateLatestVersion_whenNewVersionIsNotGreater() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long skillId = insertSkill(c, "not-greater-skill", "不递增", "INTERNAL", owner, "PUBLISHED");
            long v2 = skillRepository.insertVersion(newVersionDraft(skillId, "2.0.0"));
            skillRepository.updateLatestVersionIfGreater(skillId, v2, "2.0.0", "d2", "e2");
            long v1 = skillRepository.insertVersion(newVersionDraft(skillId, "1.0.0"));

            boolean updated = skillRepository.updateLatestVersionIfGreater(skillId, v1, "1.0.0", "d1", "e1");

            assertThat(updated).isFalse();
            assertThat(skillRepository.findLatestVersionString(skillId)).contains("2.0.0");
        }
    }

    @Test
    @DisplayName("整体替换标签:返回替换前的旧标签集合,新集合生效")
    void should_replaceTags_andReturnOldTagIds() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long skillId = insertSkill(c, "replace-tags-skill", "替换标签", "INTERNAL", owner, "PUBLISHED");
            tagSkill(c, skillId, TAG_CODE_GENERATION);

            List<Long> oldTagIds = skillRepository.replaceTags(skillId, List.of(TAG_CODE_REVIEW, TAG_TESTING));

            assertThat(oldTagIds).containsExactly(TAG_CODE_GENERATION);
            assertThat(skillRepository.findDetailById(skillId).orElseThrow().tags())
                    .extracting(tag -> tag.id())
                    .containsExactlyInAnyOrder(TAG_CODE_REVIEW, TAG_TESTING);
        }
    }

    @Test
    @DisplayName("改元数据:displayName / source 生效,name 不受影响")
    void should_updateMetadata_withoutChangingName() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long skillId = insertSkill(c, "update-metadata-skill", "旧展示名", "INTERNAL", owner, "PUBLISHED");

            skillRepository.updateMetadata(skillId, "新展示名", SkillSource.COMMUNITY);

            Skill updated = skillRepository.findDetailById(skillId).orElseThrow();
            assertThat(updated.displayName()).isEqualTo("新展示名");
            assertThat(updated.source()).isEqualTo(SkillSource.COMMUNITY);
            assertThat(updated.name().value()).isEqualTo("update-metadata-skill");
        }
    }

    @Test
    @DisplayName("下架与恢复:只改 status")
    void should_updateStatus_whenUnlistingAndRelisting() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long skillId = insertSkill(c, "status-toggle-skill", "上下架", "INTERNAL", owner, "PUBLISHED");

            skillRepository.updateStatus(skillId, SkillStatus.UNLISTED);
            assertThat(skillRepository.findDetailById(skillId).orElseThrow().status()).isEqualTo(SkillStatus.UNLISTED);

            skillRepository.updateStatus(skillId, SkillStatus.PUBLISHED);
            assertThat(skillRepository.findDetailById(skillId).orElseThrow().status())
                    .isEqualTo(SkillStatus.PUBLISHED);
        }
    }

    private SkillVersion newVersionDraft(long skillId, String version) {
        return new SkillVersion(
                null, skillId, version, "artifacts/" + skillId + "/" + version + ".zip", "digest-" + version,
                "sha-" + version, 100L, 1, List.of("Read"), "# skill\n正文", null, null, Certification.NONE, null,
                null, null, 1L, null);
    }

    private long attachVersionWithDigest(Connection c, long skillId, String version, String contentDigest)
            throws SQLException {
        long versionId;
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO skill_version "
                + "(skill_id, version, artifact_key, content_digest, sha256, size_bytes, file_count, "
                + "allowed_tools, skill_md_content, readme_content, certification, created_by_user_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id")) {
            ps.setLong(1, skillId);
            ps.setString(2, version);
            ps.setString(3, "artifacts/" + skillId + "/" + version + ".zip");
            ps.setString(4, contentDigest);
            ps.setString(5, "0".repeat(64));
            ps.setLong(6, 1024L);
            ps.setInt(7, 3);
            Array allowedTools = c.createArrayOf("text", new String[] {"Read"});
            ps.setArray(8, allowedTools);
            ps.setString(9, "# skill\n正文");
            ps.setString(10, "# readme");
            ps.setString(11, "NONE");
            ps.setLong(12, 1L);
            ResultSet rs = ps.executeQuery();
            rs.next();
            versionId = rs.getLong(1);
        }
        return versionId;
    }

    private SkillSearchCriteria defaultCriteria() {
        return new SkillSearchCriteria(null, List.of(), false, null, null, SkillSortOption.LATEST, 1, 20);
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

    private long insertSkill(Connection c, String name, String displayName, String source, long ownerId, String status)
            throws SQLException {
        long skillId = insertSkillWithExcerpt(c, name, displayName, source, ownerId, "摘要 " + name);
        if (!"PUBLISHED".equals(status)) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE skill SET status = ? WHERE id = ?")) {
                ps.setString(1, status);
                ps.setLong(2, skillId);
                ps.executeUpdate();
            }
        }
        return skillId;
    }

    private long insertSkillWithExcerpt(
            Connection c, String name, String displayName, String source, long ownerId, String cardExcerpt)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO skill "
                + "(name, display_name, description, source, owner_user_id, card_excerpt) "
                + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id")) {
            ps.setString(1, name);
            ps.setString(2, displayName);
            ps.setString(3, "描述 " + name);
            ps.setString(4, source);
            ps.setLong(5, ownerId);
            ps.setString(6, cardExcerpt);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    private long insertSkillWithCounts(
            Connection c, String name, String displayName, long ownerId, long downloadCount, int likeCount,
            int favoriteCount) throws SQLException {
        long skillId = insertSkillWithExcerpt(c, name, displayName, "INTERNAL", ownerId, "摘要 " + name);
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE skill SET download_count = ?, like_count = ?, favorite_count = ? WHERE id = ?")) {
            ps.setLong(1, downloadCount);
            ps.setInt(2, likeCount);
            ps.setInt(3, favoriteCount);
            ps.setLong(4, skillId);
            ps.executeUpdate();
        }
        return skillId;
    }

    private long attachVersion(Connection c, long skillId, String version, String certification) throws SQLException {
        long versionId;
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO skill_version "
                + "(skill_id, version, artifact_key, content_digest, sha256, size_bytes, file_count, "
                + "allowed_tools, skill_md_content, readme_content, certification, created_by_user_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id")) {
            ps.setLong(1, skillId);
            ps.setString(2, version);
            ps.setString(3, "artifacts/" + skillId + "/" + version + ".zip");
            ps.setString(4, "0".repeat(64));
            ps.setString(5, "0".repeat(64));
            ps.setLong(6, 1024L);
            ps.setInt(7, 3);
            Array allowedTools = c.createArrayOf("text", new String[] {"Read", "Grep"});
            ps.setArray(8, allowedTools);
            ps.setString(9, "# skill\n正文");
            ps.setString(10, "# readme");
            ps.setString(11, certification);
            ps.setLong(12, 1L);
            ResultSet rs = ps.executeQuery();
            rs.next();
            versionId = rs.getLong(1);
        }
        try (PreparedStatement ps = c.prepareStatement("UPDATE skill SET latest_version_id = ? WHERE id = ?")) {
            ps.setLong(1, versionId);
            ps.setLong(2, skillId);
            ps.executeUpdate();
        }
        return versionId;
    }

    private long insertFreeTag(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO tag (name, kind, created_by_user_id) VALUES (?, 'FREE', 1) RETURNING id")) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
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
