package com.landray.skillhub.interfaces.rest.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.landray.skillhub.domain.auth.PasswordHasher;
import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.UserIdentity;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.domain.repository.AppUserRepository;
import com.landray.skillhub.domain.repository.UserIdentityRepository;
import com.landray.skillhub.domain.storage.ArtifactStorage;

/**
 * 认证链路的端到端:真 HTTP + 真 PostgreSQL + 真登录 + 真安装脚本渲染。
 *
 * <p><b>这组测试存在的唯一理由是那条接缝</b>(根 CLAUDE.md 9.1):认证机制由两半组成 —— ADMIN 打标记
 * ({@code POST .../certify})与安装脚本渲染时拦截(5.9)。两边各自都有测试且都是绿的:切片测试 mock 掉了
 * application,而 {@code InstallAndDownloadFlowIntegrationTest} 里的 {@code REVOKED} 是<b>直接插库</b>造出来的。
 * 谁都没验过"ADMIN 真的点了撤销之后,脚本那边真的会拦" —— 而那正是整套机制的全部价值所在。
 *
 * <p>最关键的一条是 {@code should_blockAlreadyIssuedBundle_when_revokedAfterBundleCreated}:安装单在撤销
 * <b>之前</b>就生成好了,撤销之后用同一个 token 去取脚本仍然必须被拦。5.9 写的"渲染时必须实时查认证状态"
 * 就是这个意思 —— 一旦哪天有人为了省一次 join 把认证状态快照进 {@code install_bundle},这条会立刻红,
 * 而其他任何测试都不会。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "server.servlet.context-path=")
@Testcontainers
class SkillCertificationFlowIntegrationTest {

    @TempDir
    static Path artifactRoot;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("skillhub")
            .withUsername("skillhub")
            .withPassword("skillhub");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("skillhub.storage.local-root", () -> artifactRoot.toString());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ArtifactStorage artifactStorage;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void cleanup() throws SQLException {
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            s.execute("DELETE FROM install_bundle");
            s.execute("DELETE FROM skill_version");
            s.execute("DELETE FROM skill");
            s.execute("DELETE FROM user_session");
            s.execute("DELETE FROM user_identity WHERE user_id <> 1");
            s.execute("DELETE FROM app_user WHERE id <> 1");
        }
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("ADMIN 认证后,版本列表立刻显示 CERTIFIED 与意见")
    void should_reflectCertification_inVersionList() throws Exception {
        String adminToken = loginAsNewUser("管理员", UserRole.ADMIN);
        long skillId = insertSkillWithVersion("cert-flow", "1.0.0");

        HttpResponse<String> certified = postJson(
                "/api/v1/skills/" + skillId + "/versions/1.0.0/certify",
                "{\"certification\":\"CERTIFIED\",\"note\":\"看过了,没问题\"}",
                adminToken);
        assertThat(certified.statusCode()).isEqualTo(200);

        String versions = get("/api/v1/skills/" + skillId + "/versions", null).body();
        assertThat(versions).contains("CERTIFIED").contains("看过了,没问题");
    }

    /**
     * <b>本文件最要紧的一条。</b>安装单在撤销之前就生成好了,撤销之后同一个 token 仍然必须被拦 ——
     * 这是 5.9"渲染时必须实时查认证状态"的可执行版本。把认证状态快照进 install_bundle 会让这条红,
     * 而其他任何测试都不会。
     */
    @Test
    @DisplayName("撤销认证后,撤销前就发出去的安装单也会被拦下")
    void should_blockAlreadyIssuedBundle_when_revokedAfterBundleCreated() throws Exception {
        String adminToken = loginAsNewUser("管理员", UserRole.ADMIN);
        long skillId = insertSkillWithVersion("revoke-later", "1.0.0");
        long versionId = selectVersionId(skillId, "1.0.0");

        // 先发安装单 —— 此刻这个版本还是 NONE,脚本里不该有任何警示
        String token = createBundle(skillId, versionId);
        assertThat(get("/i/" + token + ".sh", null).body()).doesNotContain("已被撤销");

        HttpResponse<String> revoked = postJson(
                "/api/v1/skills/" + skillId + "/versions/1.0.0/certify",
                "{\"certification\":\"REVOKED\",\"note\":\"存在命令注入风险\"}",
                adminToken);
        assertThat(revoked.statusCode()).isEqualTo(200);

        String script = get("/i/" + token + ".sh", null).body();
        assertThat(script).contains("已被撤销");
        // 撤销理由必须原样进脚本:它是使用者判断"要不要继续装"的唯一依据(5.8)
        assertThat(script).contains("存在命令注入风险");
        // 默认中止,要显式 --force 才继续(5.9)
        assertThat(script).contains("--force");
    }

    @Test
    @DisplayName("PowerShell 脚本同样带警示与 -Force,两份不能只测一份")
    void should_blockPowerShellScript_too_when_revoked() throws Exception {
        String adminToken = loginAsNewUser("管理员", UserRole.ADMIN);
        long skillId = insertSkillWithVersion("revoke-ps1", "1.0.0");
        long versionId = selectVersionId(skillId, "1.0.0");
        String token = createBundle(skillId, versionId);

        postJson(
                "/api/v1/skills/" + skillId + "/versions/1.0.0/certify",
                "{\"certification\":\"REVOKED\",\"note\":\"有问题\"}",
                adminToken);

        String script = get("/i/" + token + ".ps1", null).body();
        assertThat(script).contains("已被撤销").contains("有问题").contains("Force");
    }

    /** 改回 NONE 是撤销一次误判的唯一出口,而它的效果同样必须实时反映到脚本上。 */
    @Test
    @DisplayName("撤销后再改回 NONE,脚本的警示随之消失")
    void should_clearWarning_when_certificationResetToNone() throws Exception {
        String adminToken = loginAsNewUser("管理员", UserRole.ADMIN);
        long skillId = insertSkillWithVersion("reset-flow", "1.0.0");
        long versionId = selectVersionId(skillId, "1.0.0");
        String token = createBundle(skillId, versionId);

        postJson(
                "/api/v1/skills/" + skillId + "/versions/1.0.0/certify",
                "{\"certification\":\"REVOKED\",\"note\":\"误判\"}",
                adminToken);
        assertThat(get("/i/" + token + ".sh", null).body()).contains("已被撤销");

        postJson(
                "/api/v1/skills/" + skillId + "/versions/1.0.0/certify",
                "{\"certification\":\"NONE\"}",
                adminToken);

        String script = get("/i/" + token + ".sh", null).body();
        assertThat(script).doesNotContain("已被撤销");
        assertThat(script).doesNotContain("误判");
    }

    /**
     * 5.8 写死"撤销认证不自动下架 skill"。两者烈度不同:撤销是"这个版本有问题,自己判断",
     * 下架是"这个 skill 不该再被用"。顺手下架会让作者莫名其妙发现自己的 skill 从列表里消失了。
     */
    @Test
    @DisplayName("撤销认证不下架 skill,它仍然出现在公开列表里")
    void should_keepSkillListed_when_revoked() throws Exception {
        String adminToken = loginAsNewUser("管理员", UserRole.ADMIN);
        long skillId = insertSkillWithVersion("still-listed", "1.0.0");

        postJson(
                "/api/v1/skills/" + skillId + "/versions/1.0.0/certify",
                "{\"certification\":\"REVOKED\",\"note\":\"有问题\"}",
                adminToken);

        assertThat(get("/api/v1/skills", null).body()).contains("still-listed");
    }

    /** owner 给自己发认证徽章会让整个认证机制当场归零,所以这条端点连 owner 都不放行。 */
    @Test
    @DisplayName("skill 的 owner 也不能认证自己的版本")
    void should_rejectCertify_when_callerIsOwnerButNotAdmin() throws Exception {
        String userToken = loginAsNewUser("普通用户", UserRole.USER);
        long skillId = insertSkillWithVersion("self-cert", "1.0.0");

        HttpResponse<String> response = postJson(
                "/api/v1/skills/" + skillId + "/versions/1.0.0/certify",
                "{\"certification\":\"CERTIFIED\"}",
                userToken);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(selectCertification(skillId, "1.0.0")).isEqualTo("NONE");
    }

    @Test
    @DisplayName("未登录认证 401,且认证状态不变")
    void should_rejectAnonymousCertify() throws Exception {
        long skillId = insertSkillWithVersion("anon-cert", "1.0.0");

        HttpResponse<String> response = postJson(
                "/api/v1/skills/" + skillId + "/versions/1.0.0/certify",
                "{\"certification\":\"CERTIFIED\"}",
                null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(selectCertification(skillId, "1.0.0")).isEqualTo("NONE");
    }

    @Test
    @DisplayName("撤销没填理由返回 4xx,库里一个字都没改")
    void should_rejectRevokeWithoutNote_andLeaveStateUntouched() throws Exception {
        String adminToken = loginAsNewUser("管理员", UserRole.ADMIN);
        long skillId = insertSkillWithVersion("no-note", "1.0.0");

        HttpResponse<String> response = postJson(
                "/api/v1/skills/" + skillId + "/versions/1.0.0/certify",
                "{\"certification\":\"REVOKED\"}",
                adminToken);

        assertThat(response.statusCode()).isBetween(400, 499);
        assertThat(selectCertification(skillId, "1.0.0")).isEqualTo("NONE");
    }

    @Test
    @DisplayName("版本号不存在返回 404")
    void should_return404_when_versionDoesNotExist() throws Exception {
        String adminToken = loginAsNewUser("管理员", UserRole.ADMIN);
        long skillId = insertSkillWithVersion("wrong-version", "1.0.0");

        HttpResponse<String> response = postJson(
                "/api/v1/skills/" + skillId + "/versions/9.9.9/certify",
                "{\"certification\":\"CERTIFIED\"}",
                adminToken);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    // ------------------------------------------------------------------
    // 脚手架
    // ------------------------------------------------------------------

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> get(String path, String token) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String jsonBody, String token)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 走真实登录接口换 token,不手插 {@code user_session}——那张表存的是 {@code token_hash} 而不是明文。 */
    private String loginAsNewUser(String displayName, UserRole role) throws Exception {
        String login = "cert-it-" + System.nanoTime();
        String password = "cert-it-password-1";
        AppUser user = appUserRepository.save(AppUser.newUser(displayName, role));
        userIdentityRepository.save(UserIdentity.newLocal(user.id(), login, passwordHasher.hash(password)));

        HttpResponse<String> response = postJson(
                "/api/v1/auth/login",
                "{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}",
                null);
        assertThat(response.statusCode()).isEqualTo(200);
        return extractString(response.body(), "token");
    }

    private long insertSkillWithVersion(String name, String version) throws Exception {
        byte[] bytes = ("artifact-of-" + name).getBytes(StandardCharsets.UTF_8);
        String artifactKey = "artifacts/" + name + "/" + version + ".zip";
        artifactStorage.store(artifactKey, bytes);
        String sha256 = sha256Hex(bytes);

        long ownerId = insertOwner(name);
        long skillId;
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("INSERT INTO skill "
                        + "(name, display_name, description, source, owner_user_id, card_excerpt) "
                        + "VALUES (?, ?, ?, 'INTERNAL', ?, '摘要') RETURNING id")) {
            ps.setString(1, name);
            ps.setString(2, "展示名 " + name);
            ps.setString(3, "描述 " + name);
            ps.setLong(4, ownerId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            skillId = rs.getLong(1);
        }

        long versionId;
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("INSERT INTO skill_version "
                        + "(skill_id, version, artifact_key, content_digest, sha256, size_bytes, file_count, "
                        + "skill_md_content, certification, created_by_user_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 1, '# skill', 'NONE', ?) RETURNING id")) {
            ps.setLong(1, skillId);
            ps.setString(2, version);
            ps.setString(3, artifactKey);
            ps.setString(4, sha256);
            ps.setString(5, sha256);
            ps.setLong(6, bytes.length);
            ps.setLong(7, ownerId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            versionId = rs.getLong(1);
        }

        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("UPDATE skill SET latest_version_id = ? WHERE id = ?")) {
            ps.setLong(1, versionId);
            ps.setLong(2, skillId);
            ps.executeUpdate();
        }
        return skillId;
    }

    private long insertOwner(String seed) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO app_user (display_name) VALUES (?) RETURNING id")) {
            ps.setString(1, "作者-" + seed);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    private String createBundle(long skillId, long versionId) throws Exception {
        HttpResponse<String> response = postJson(
                "/api/v1/bundles",
                "{\"client\":\"claude\",\"scope\":\"global\",\"items\":[{\"skillId\":" + skillId
                        + ",\"versionId\":" + versionId + "}]}",
                null);
        assertThat(response.statusCode()).isEqualTo(200);
        return extractString(response.body(), "token");
    }

    private long selectVersionId(long skillId, String version) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id FROM skill_version WHERE skill_id = ? AND version = ?")) {
            ps.setLong(1, skillId);
            ps.setString(2, version);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    private String selectCertification(long skillId, String version) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT certification FROM skill_version WHERE skill_id = ? AND version = ?")) {
            ps.setLong(1, skillId);
            ps.setString(2, version);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getString(1);
        }
    }

    /** 只取一个顶层字符串字段。这里要断言的是链路通不通,不是解析能力,不为此引 JSON 库。 */
    private String extractString(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int idx = json.indexOf(marker);
        assertThat(idx).isNotNegative();
        int start = idx + marker.length();
        return json.substring(start, json.indexOf('"', start));
    }

    private String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
