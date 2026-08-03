package com.landray.skillhub.interfaces.rest.install;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
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

import com.landray.skillhub.domain.storage.ArtifactStorage;

/**
 * 端到端安装链路:真实起应用 + 真实 PostgreSQL + 真实 {@link ArtifactStorage} + 真实 HTTP 往返
 * (backend/CLAUDE.md 8.1、8.3)。覆盖根 CLAUDE.md 第七节安全底线四条在这条链路上的真实生效情况,
 * 而不只是在 mock 过的切片测试或纯字符串断言里"看起来正确"。
 *
 * <p>用 JDK 内建 {@link HttpClient} 而不是 {@code TestRestTemplate}——后者在 Spring Boot 4.x 已经不存在
 * (根 CLAUDE.md 8.12)。{@code skillhub.storage.local-root} 覆盖到 {@code @TempDir},避免测试往仓库工作目录
 * 写文件。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "server.servlet.context-path=")
@Testcontainers
class InstallAndDownloadFlowIntegrationTest {

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

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @AfterEach
    void cleanup() throws SQLException {
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            s.execute("DELETE FROM install_bundle");
            s.execute("DELETE FROM skill_version");
            s.execute("DELETE FROM skill");
            s.execute("DELETE FROM app_user WHERE id <> 1");
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> getBytes(String path) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<Void> head(String path) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + path))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.discarding());
    }

    private HttpResponse<String> postJson(String path, String jsonBody) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void should_returnFiveTestedClients_withoutWorkbuddyProject_when_listingClients() throws Exception {
        HttpResponse<String> response = get("/api/v1/clients");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("\"workbuddy\",\"scope\":\"project\"");
        assertThat(response.body()).contains("\"claude\"").contains("\"codex\"").contains("\"workbuddy\"");
    }

    @Test
    void should_rejectUntestedClientScope_when_creatingBundle() throws Exception {
        long owner = insertUser("张三");
        Fixture fixture = insertSkillWithVersion(owner, "code-review", "1.0.0", "content-a", "NONE", null);

        HttpResponse<String> response = postJson(
                "/api/v1/bundles",
                "{\"items\":[{\"skillId\":" + fixture.skillId() + ",\"versionId\":" + fixture.versionId()
                        + "}],\"client\":\"workbuddy\",\"scope\":\"project\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("CLIENT_SCOPE_NOT_SUPPORTED");
    }

    @Test
    void should_rejectUnknownSkillVersion_when_creatingBundle() throws Exception {
        HttpResponse<String> response =
                postJson("/api/v1/bundles", "{\"items\":[{\"skillId\":9999,\"versionId\":9999}],"
                        + "\"client\":\"claude\",\"scope\":\"global\"}");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("SKILL_VERSION_NOT_FOUND");
    }

    @Test
    void should_renderShWithoutCarriageReturn_andEmbedSha256_when_bundleIsValid() throws Exception {
        long owner = insertUser("张三");
        Fixture fixture = insertSkillWithVersion(owner, "code-review", "1.0.0", "artifact-bytes-A", "NONE", null);
        String token = createBundle(fixture, "claude", "global");

        HttpResponse<String> response = get("/i/" + token + ".sh");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v)
                .contains("text/plain"));
        assertThat(response.body()).doesNotContain("\r");
        assertThat(response.body()).contains(fixture.sha256());
        assertThat(response.body()).contains("code-review");
    }

    @Test
    void should_renderPs1_when_bundleIsValid() throws Exception {
        long owner = insertUser("张三");
        Fixture fixture = insertSkillWithVersion(owner, "code-review", "1.0.0", "artifact-bytes-B", "NONE", null);
        String token = createBundle(fixture, "claude", "global");

        HttpResponse<String> response = get("/i/" + token + ".ps1");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(fixture.sha256());
        assertThat(response.body()).contains("param(");
    }

    @Test
    void should_embedForceGuardAndCertifyNote_when_versionIsRevoked() throws Exception {
        long owner = insertUser("张三");
        Fixture fixture =
                insertSkillWithVersion(owner, "revoked-skill", "1.0.0", "artifact-bytes-C", "REVOKED", "存在安全隐患");
        String token = createBundle(fixture, "claude", "global");

        HttpResponse<String> response = get("/i/" + token + ".sh");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("已被撤销");
        assertThat(response.body()).contains("存在安全隐患");
        assertThat(response.body()).contains("--force");
    }

    @Test
    void should_return404_when_tokenDoesNotExist() throws Exception {
        HttpResponse<String> response = get("/i/this-token-was-never-issued.sh");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("INSTALL_BUNDLE_NOT_FOUND");
    }

    @Test
    void should_return410_when_tokenExpired() throws Exception {
        long owner = insertUser("张三");
        Fixture fixture = insertSkillWithVersion(owner, "code-review", "1.0.0", "artifact-bytes-D", "NONE", null);
        String expiredToken = "expired-token-value";
        insertExpiredBundle(expiredToken, fixture);

        HttpResponse<String> response = get("/i/" + expiredToken + ".sh");

        assertThat(response.statusCode()).isEqualTo(410);
        assertThat(response.body()).contains("INSTALL_BUNDLE_EXPIRED");
    }

    @Test
    void should_downloadCorrectBytesAndIncrementCount_when_downloadingArtifact() throws Exception {
        long owner = insertUser("张三");
        Fixture fixture = insertSkillWithVersion(owner, "code-review", "1.0.0", "artifact-bytes-E", "NONE", null);

        HttpResponse<byte[]> response = getBytes("/api/v1/skills/" + fixture.skillId() + "/versions/1.0.0/download");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("artifact-bytes-E".getBytes(StandardCharsets.UTF_8));
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/zip");
        assertThat(selectDownloadCountEventually(fixture.skillId(), 1L)).isEqualTo(1L);
    }

    @Test
    void should_notIncrementCountTwice_when_downloadingSameVersionWithinTenMinuteWindow() throws Exception {
        long owner = insertUser("张三");
        Fixture fixture = insertSkillWithVersion(owner, "code-review", "1.0.0", "artifact-bytes-F", "NONE", null);
        String path = "/api/v1/skills/" + fixture.skillId() + "/versions/1.0.0/download";

        getBytes(path);
        getBytes(path);

        assertThat(selectDownloadCountEventually(fixture.skillId(), 1L)).isEqualTo(1L);
    }

    @Test
    void should_notIncrementCount_when_headRequest() throws Exception {
        long owner = insertUser("张三");
        Fixture fixture = insertSkillWithVersion(owner, "code-review", "1.0.0", "artifact-bytes-G", "NONE", null);

        HttpResponse<Void> response =
                head("/api/v1/skills/" + fixture.skillId() + "/versions/1.0.0/download");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(selectDownloadCount(fixture.skillId())).isZero();
    }

    private String createBundle(Fixture fixture, String client, String scope) throws Exception {
        HttpResponse<String> response = postJson(
                "/api/v1/bundles",
                "{\"items\":[{\"skillId\":" + fixture.skillId() + ",\"versionId\":" + fixture.versionId()
                        + "}],\"client\":\"" + client + "\",\"scope\":\"" + scope + "\"}");
        assertThat(response.statusCode()).as("创建安装单: " + response.body()).isEqualTo(200);
        String body = response.body();
        int tokenStart = body.indexOf("\"token\":\"") + "\"token\":\"".length();
        int tokenEnd = body.indexOf('"', tokenStart);
        return body.substring(tokenStart, tokenEnd);
    }

    private record Fixture(long skillId, long versionId, String sha256) {}

    private Fixture insertSkillWithVersion(
            long ownerId, String name, String version, String artifactContent, String certification,
            String certifyNote) throws Exception {
        byte[] bytes = artifactContent.getBytes(StandardCharsets.UTF_8);
        String artifactKey = "artifacts/" + name + "/" + version + ".zip";
        artifactStorage.store(artifactKey, bytes);
        String sha256 = sha256Hex(bytes);

        long skillId;
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("INSERT INTO skill "
                        + "(name, display_name, description, source, owner_user_id) "
                        + "VALUES (?, ?, ?, 'INTERNAL', ?) RETURNING id")) {
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
                        + "skill_md_content, certification, certify_note, created_by_user_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 1, '# skill', ?, ?, ?) RETURNING id")) {
            ps.setLong(1, skillId);
            ps.setString(2, version);
            ps.setString(3, artifactKey);
            ps.setString(4, sha256);
            ps.setString(5, sha256);
            ps.setLong(6, bytes.length);
            ps.setString(7, certification);
            ps.setString(8, certifyNote);
            ps.setLong(9, ownerId);
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

        return new Fixture(skillId, versionId, sha256);
    }

    private void insertExpiredBundle(String token, Fixture fixture) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("INSERT INTO install_bundle "
                        + "(token, items, client, scope, expires_at) "
                        + "VALUES (?, ?::jsonb, 'claude', 'global', ?)")) {
            ps.setString(1, token);
            ps.setString(
                    2,
                    "[{\"skillId\":" + fixture.skillId() + ",\"versionId\":" + fixture.versionId() + "}]");
            ps.setObject(3, Instant.now().minusSeconds(3600).atOffset(ZoneOffset.UTC));
            ps.executeUpdate();
        }
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

    private long selectDownloadCount(long skillId) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT download_count FROM skill WHERE id = ?")) {
            ps.setLong(1, skillId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    /**
     * 轮询等待计数达到期望值,而不是直接读一次就断言。
     *
     * <p><b>这不是在掩盖生产代码的 bug,是 HTTP 语义本身的时序特性</b>:业务建模文档 5.12 要求"计数只在响应实际
     * 写出完整制品字节之后 +1",生产代码因此把 {@code recordDownload} 放在字节流写完<b>之后</b>。但"客户端收到完整
     * 响应体"与"服务端处理线程执行完 Controller 方法剩余的那几行代码(含一次数据库写)"是两件不保证同时发生的事——
     * 一旦最后一个字节被 flush 到 socket,{@link HttpClient#send} 就可以返回,不需要等服务端线程真正 return。
     * 测试如果在收到响应后立刻同步读库,极小概率会在服务端完成计数之前抢先读到旧值。轮询(而不是加
     * {@code Thread.sleep} 定长等待)既能吸收这个时序窗口,又不会在正常情况下拖慢测试——多数情况下第一次
     * 轮询就能读到期望值。
     */
    private long selectDownloadCountEventually(long skillId, long expected) throws SQLException, InterruptedException {
        long lastObserved = -1;
        for (int attempt = 0; attempt < 20; attempt++) {
            lastObserved = selectDownloadCount(skillId);
            if (lastObserved == expected) {
                return lastObserved;
            }
            Thread.sleep(50);
        }
        return lastObserved;
    }

    private String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }
}
