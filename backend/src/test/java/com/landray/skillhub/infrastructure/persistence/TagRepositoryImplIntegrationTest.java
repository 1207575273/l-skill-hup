package com.landray.skillhub.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.domain.model.TagKind;
import com.landray.skillhub.domain.model.TagName;
import com.landray.skillhub.domain.model.TagWithSkillCount;
import com.landray.skillhub.domain.repository.TagRepository;
import com.landray.skillhub.shared.exception.ResourceConflictException;

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

    /**
     * V0002 预置的 9 个 CURATED 词条占掉 id 1~9,测试自己建的一律在这之后。
     *
     * <p><b>本文件的测试不许改动种子词条</b>(改名 / 转 kind / 删),只能建自己的:清理只做删除,没法把被改过的
     * 种子行恢复原状,而 {@code should_returnAllSeededCuratedTags} 之类的断言依赖它们原封不动。测试之间靠顺序
     * 才能通过是最难查的那种红。
     */
    private static final long LAST_SEEDED_TAG_ID = SEEDED_CURATED_TAG_COUNT;

    @AfterEach
    void cleanup() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM skill_tag");
            statement.execute("DELETE FROM skill_version");
            statement.execute("DELETE FROM skill");
            statement.execute("DELETE FROM tag WHERE id > " + LAST_SEEDED_TAG_ID);
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

    // ------------------------------------------------------------------
    // findByIds / findOrCreateFreeTag / deleteFreeTagIfOrphaned(上传管线任务新增)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("按 id 批量查询,id 不存在时结果里少一条,不报错")
    void should_returnOnlyExistingTags_whenFindingByIds() {
        List<Tag> tags = tagRepository.findByIds(List.of(1L, 2L, 999_999L));

        assertThat(tags).extracting(Tag::id).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("空 id 集合直接返回空列表,不发查询")
    void should_returnEmptyList_whenFindingByEmptyIds() {
        assertThat(tagRepository.findByIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("FREE 标签不存在时自动创建")
    void should_createFreeTag_whenNameDoesNotExist() {
        Tag tag = tagRepository.findOrCreateFreeTag("全新标签", 1L);

        assertThat(tag.id()).isNotNull();
        assertThat(tag.kind()).isEqualTo(TagKind.FREE);
        assertThat(tagRepository.findAll(TagKind.FREE)).extracting(item -> item.tag().name()).contains("全新标签");
    }

    @Test
    @DisplayName("FREE 标签已存在时直接返回已有行,不重复创建")
    void should_returnExistingFreeTag_whenNameAlreadyExists() {
        Tag first = tagRepository.findOrCreateFreeTag("已有标签", 1L);

        Tag second = tagRepository.findOrCreateFreeTag("已有标签", 1L);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(tagRepository.findAll(TagKind.FREE)).hasSize(1);
    }

    @Test
    @DisplayName("并发创建同名 FREE 标签:两个线程都能拿到同一个 tag id,uk_tag_name 是仲裁者(业务建模文档 5.14)")
    void should_resolveToSameTagId_whenConcurrentlyCreatingSameFreeTagName() throws InterruptedException {
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Long>> futures = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    return tagRepository.findOrCreateFreeTag("并发标签", 1L).id();
                }));
            }
            startLatch.countDown();

            Set<Long> resolvedIds = futures.stream()
                    .map(future -> {
                        try {
                            return future.get(10, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .collect(Collectors.toSet());

            assertThat(resolvedIds).hasSize(1);
            assertThat(tagRepository.findAll(TagKind.FREE)).hasSize(1);
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("失去全部引用的 FREE 标签会被删除")
    void should_deleteFreeTag_whenOrphaned() {
        Tag tag = tagRepository.findOrCreateFreeTag("孤儿标签", 1L);

        tagRepository.deleteFreeTagIfOrphaned(tag.id());

        assertThat(tagRepository.findByIds(List.of(tag.id()))).isEmpty();
    }

    @Test
    @DisplayName("仍有引用的 FREE 标签不会被删除")
    void should_keepFreeTag_whenStillReferenced() throws SQLException {
        Tag tag = tagRepository.findOrCreateFreeTag("仍被引用的标签", 1L);
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            long skillId = insertSkill(c, "referencing-skill", owner, "PUBLISHED");
            tagSkill(c, skillId, tag.id());
        }

        tagRepository.deleteFreeTagIfOrphaned(tag.id());

        assertThat(tagRepository.findByIds(List.of(tag.id()))).isNotEmpty();
    }

    @Test
    @DisplayName("CURATED 标签即使无引用也不会被这个方法删除——它只清理 FREE")
    void should_notDeleteCuratedTag_evenWhenUnreferenced() {
        tagRepository.deleteFreeTagIfOrphaned(1L);

        assertThat(tagRepository.findByIds(List.of(1L))).isNotEmpty();
    }

    // ------------------------------------------------------------------
    // 标签治理(P1):建 / 改 / 删、kind 转换的判定依据、引用迁移
    // ------------------------------------------------------------------

    @Test
    @DisplayName("按 id 查单条,不存在时返回空")
    void should_returnEmpty_whenFindingByMissingId() {
        assertThat(tagRepository.findById(1L)).isPresent();
        assertThat(tagRepository.findById(999_999L)).isEmpty();
    }

    @Test
    @DisplayName("按名称精确查找")
    void should_findTagByExactName() {
        assertThat(tagRepository.findByName("代码审查")).isPresent();
        assertThat(tagRepository.findByName("代码审")).isEmpty();
    }

    @Test
    @DisplayName("新建 CURATED 词条后能查到,字段原样落库")
    void should_persistAllFields_whenCreatingCuratedTag() {
        Tag created = tagRepository.create(new TagName("性能优化"), TagKind.CURATED, "压测与调优", 95, 1L);

        Tag reloaded = tagRepository.findById(created.id()).orElseThrow();
        assertThat(reloaded.name()).isEqualTo("性能优化");
        assertThat(reloaded.kind()).isEqualTo(TagKind.CURATED);
        assertThat(reloaded.description()).isEqualTo("压测与调优");
        assertThat(reloaded.sortOrder()).isEqualTo(95);
    }

    /** 与 {@code findOrCreateFreeTag} 的"撞名就复用"相反:ADMIN 显式建词条时撞名必须报错。 */
    @Test
    @DisplayName("新建撞 uk_tag_name 时抛 409 而不是静默复用")
    void should_throwConflict_whenCreatingDuplicateName() {
        assertThatThrownBy(() -> tagRepository.create(new TagName("代码审查"), TagKind.CURATED, null, 0, 1L))
                .isInstanceOf(ResourceConflictException.class)
                .satisfies(e -> assertThat(((ResourceConflictException) e).code()).isEqualTo("TAG_NAME_DUPLICATED"));
    }

    @Test
    @DisplayName("更新会把 kind 一起写进去,转换后按新 kind 能查到")
    void should_persistKindConversion_whenUpdatingTag() {
        Tag created = tagRepository.create(new TagName("待转换"), TagKind.CURATED, "描述", 10, 1L);

        tagRepository.update(created.id(), new TagName("已转换"), TagKind.FREE, null, 0);

        Tag reloaded = tagRepository.findById(created.id()).orElseThrow();
        assertThat(reloaded.name()).isEqualTo("已转换");
        assertThat(reloaded.kind()).isEqualTo(TagKind.FREE);
        assertThat(reloaded.description()).isNull();
    }

    /**
     * 这一条是治理侧与读侧刻意分开的口径:{@code findAll} 的 skillCount 只数 PUBLISHED(展示要与列表一致),
     * 而删除前的引用计数必须含 UNLISTED —— 按可见数判断会把挂在下架 skill 上的标签静默剥掉,作者重新上架时
     * 才发现分类没了。
     */
    @Test
    @DisplayName("引用计数含已下架 skill,与列表页的可见计数不是同一个口径")
    void should_countReferencesIncludingUnlistedSkills() throws SQLException {
        Tag tag = tagRepository.create(new TagName("计数用"), TagKind.CURATED, null, 0, 1L);
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            tagSkill(c, insertSkill(c, "published-one", owner, "PUBLISHED"), tag.id());
            tagSkill(c, insertSkill(c, "unlisted-one", owner, "UNLISTED"), tag.id());
        }

        assertThat(tagRepository.countReferences(tag.id())).isEqualTo(2L);
        assertThat(tagRepository.findAll(TagKind.CURATED).stream()
                        .filter(item -> item.tag().id().equals(tag.id()))
                        .findFirst()
                        .orElseThrow()
                        .skillCount())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("受影响 skill 清单按名称升序,并受样本上限约束")
    void should_returnAffectedSkillNames_whenTagIsReferenced() throws SQLException {
        Tag tag = tagRepository.create(new TagName("清单用"), TagKind.CURATED, null, 0, 1L);
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            tagSkill(c, insertSkill(c, "zzz-skill", owner, "PUBLISHED"), tag.id());
            tagSkill(c, insertSkill(c, "aaa-skill", owner, "PUBLISHED"), tag.id());
        }

        assertThat(tagRepository.findSkillNamesReferencing(tag.id(), 20))
                .containsExactly("aaa-skill", "zzz-skill");
        assertThat(tagRepository.findSkillNamesReferencing(tag.id(), 1)).containsExactly("aaa-skill");
    }

    /**
     * {@code FREE -> CURATED} 的拦截依据。挂了 3 个 CURATED 的 skill 会被找出来(再加一个就是 4),
     * 挂了 2 个的不该被找出来——把它误报进去会让一次合法转换被无理由拒绝。
     */
    @Test
    @DisplayName("只找出 CURATED 已达上限的 skill,没达到的不误报")
    void should_findOnlySkillsAtCuratedLimit() throws SQLException {
        Tag freeTag = tagRepository.findOrCreateFreeTag("待升级", 1L);
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");

            long full = insertSkill(c, "full-skill", owner, "PUBLISHED");
            tagSkill(c, full, 1L);
            tagSkill(c, full, 2L);
            tagSkill(c, full, 3L);
            tagSkill(c, full, freeTag.id());

            long room = insertSkill(c, "room-skill", owner, "PUBLISHED");
            tagSkill(c, room, 1L);
            tagSkill(c, room, 2L);
            tagSkill(c, room, freeTag.id());
        }

        assertThat(tagRepository.findSkillNamesAtCuratedLimit(freeTag.id(), 3, 20)).containsExactly("full-skill");
    }

    /**
     * 迁移的关键用例:一个 skill<b>同时挂着源与目标</b>。直接 UPDATE 会撞 {@code uk_skill_tag},
     * 而"插不存在的 + 删旧的"这条路径要把它退化成净减一行。
     */
    @Test
    @DisplayName("迁移引用时跳过已挂目标的 skill,不撞唯一索引")
    void should_skipSkillsAlreadyHoldingTarget_whenMigratingReferences() throws SQLException {
        Tag source = tagRepository.create(new TagName("源分类"), TagKind.CURATED, null, 0, 1L);
        Tag target = tagRepository.create(new TagName("目标分类"), TagKind.CURATED, null, 0, 1L);
        long onlySource;
        long both;
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            onlySource = insertSkill(c, "only-source", owner, "PUBLISHED");
            both = insertSkill(c, "both-tags", owner, "PUBLISHED");
            tagSkill(c, onlySource, source.id());
            tagSkill(c, both, source.id());
            tagSkill(c, both, target.id());
        }

        tagRepository.migrateReferences(source.id(), target.id());

        assertThat(tagRepository.countReferences(source.id())).isZero();
        assertThat(tagRepository.countReferences(target.id())).isEqualTo(2L);
        assertThat(countSkillTagRows(both, target.id())).isEqualTo(1);
        assertThat(countSkillTagRows(onlySource, target.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("删除词条会先断开引用,skill 本身不受影响")
    void should_deleteReferencesAndTag_whenDeleting() throws SQLException {
        Tag tag = tagRepository.create(new TagName("待删除"), TagKind.CURATED, null, 0, 1L);
        long skillId;
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            skillId = insertSkill(c, "survivor-skill", owner, "PUBLISHED");
            tagSkill(c, skillId, tag.id());
        }

        tagRepository.deleteReferences(tag.id());
        tagRepository.deleteTag(tag.id());

        assertThat(tagRepository.findById(tag.id())).isEmpty();
        assertThat(countSkillTagRows(skillId, tag.id())).isZero();
        assertThat(skillExists(skillId)).isTrue();
    }

    // ------------------------------------------------------------------
    // 输入联想
    // ------------------------------------------------------------------

    @Test
    @DisplayName("联想按包含匹配,且大小写不敏感")
    void should_matchCaseInsensitively_whenSuggesting() {
        tagRepository.create(new TagName("CodeReview"), TagKind.CURATED, null, 0, 1L);

        assertThat(tagRepository.suggest("codereview", null, 10))
                .extracting(item -> item.tag().name())
                .containsExactly("CodeReview");
        // 中段匹配也算命中,不是只按前缀
        assertThat(tagRepository.suggest("eRev", null, 10))
                .extracting(item -> item.tag().name())
                .containsExactly("CodeReview");
        // 不是"什么都匹配":这个词不是 CodeReview 的子串
        assertThat(tagRepository.suggest("reviewer", null, 10)).isEmpty();
    }

    @Test
    @DisplayName("联想按引用数倒序,引用多的排前面")
    void should_orderByUsage_whenSuggesting() throws SQLException {
        Tag cold = tagRepository.create(new TagName("联想冷门"), TagKind.CURATED, null, 0, 1L);
        Tag hot = tagRepository.create(new TagName("联想热门"), TagKind.CURATED, null, 0, 1L);
        try (Connection c = dataSource.getConnection()) {
            long owner = insertUser(c, "张三", "80380486");
            tagSkill(c, insertSkill(c, "s1", owner, "PUBLISHED"), hot.id());
            tagSkill(c, insertSkill(c, "s2", owner, "PUBLISHED"), hot.id());
            tagSkill(c, insertSkill(c, "s3", owner, "PUBLISHED"), cold.id());
        }

        assertThat(tagRepository.suggest("联想", null, 10))
                .extracting(item -> item.tag().name())
                .containsExactly("联想热门", "联想冷门");
    }

    @Test
    @DisplayName("联想的 limit 与 kind 筛选都生效")
    void should_applyLimitAndKindFilter_whenSuggesting() {
        tagRepository.create(new TagName("筛选甲"), TagKind.CURATED, null, 0, 1L);
        tagRepository.findOrCreateFreeTag("筛选乙", 1L);

        assertThat(tagRepository.suggest("筛选", null, 10)).hasSize(2);
        assertThat(tagRepository.suggest("筛选", null, 1)).hasSize(1);
        assertThat(tagRepository.suggest("筛选", TagKind.FREE, 10))
                .extracting(item -> item.tag().name())
                .containsExactly("筛选乙");
    }

    private int countSkillTagRows(long skillId, long tagId) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps =
                        c.prepareStatement("SELECT count(*) FROM skill_tag WHERE skill_id = ? AND tag_id = ?")) {
            ps.setLong(1, skillId);
            ps.setLong(2, tagId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    private boolean skillExists(long skillId) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM skill WHERE id = ?")) {
            ps.setLong(1, skillId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1) > 0;
        }
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
