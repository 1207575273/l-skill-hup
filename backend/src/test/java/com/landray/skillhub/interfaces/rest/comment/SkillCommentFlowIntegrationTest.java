package com.landray.skillhub.interfaces.rest.comment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.landray.skillhub.domain.auth.PasswordHasher;
import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.UserIdentity;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.domain.repository.AppUserRepository;
import com.landray.skillhub.domain.repository.UserIdentityRepository;

/**
 * 评论的端到端链路:真 HTTP + 真 PostgreSQL,不 mock 任何一层。
 *
 * <p><b>为什么切片测试不够。</b>controller 切片 mock 掉了 application,仓储集成测试又绕过了 HTTP 与鉴权。
 * 两边都绿并不能证明它们接得上 —— 本项目 9.1 记的三次事故全是这种形态:每块都对,连接它们的那件事没人验。
 * 这组测试专门验那些接缝:登录态透传、请求体绑定、<b>comment_count 真的被改了</b>、软删除后列表的实际表现。
 *
 * <p>用 JDK 内建的 {@code HttpClient} 而不是包装类:Spring Boot 4 把测试支持拆得很碎(根 CLAUDE.md 8.12,
 * {@code TestRestTemplate} 已经没有了),用 JDK 自带的就与那些搬迁无关;顺带它遇到 4xx/5xx 不抛异常,
 * 断状态码比包装类更直接。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SkillCommentFlowIntegrationTest {

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

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String url(String path) {
        return "http://localhost:" + port + "/skillhub/api/v1" + path;
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create(url(path))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String token)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("发评论 -> 匿名能读到 -> comment_count 加一 -> 删除后计数减回去")
    void should_maintainCommentCountAcrossFullFlow() throws Exception {
        String token = loginAsNewUser("评论者");
        long skillId;
        try (Connection c = dataSource.getConnection()) {
            skillId = insertSkill(c, "comment-flow-skill", currentUserId(token));
        }

        HttpResponse<String> created = post(
                "/skills/" + skillId + "/comments", "{\"content\":\"端到端的评论\"}", token);
        assertThat(created.statusCode()).isEqualTo(200);
        long commentId = extractId(created.body());

        // 匿名读得到 —— 权限矩阵 5.16 里读侧不要求登录
        HttpResponse<String> listed = get("/skills/" + skillId + "/comments");
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.body()).contains("端到端的评论");

        // 计数真的被改了。这一条是切片测试验不到的:那边 mock 掉了仓储,
        // "调用了 adjustCommentCount"与"数据库里的数变了"是两回事。
        assertThat(readCommentCount(skillId)).isEqualTo(1);

        HttpResponse<String> deleted = post("/comments/" + commentId + "/delete", null, token);
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(readCommentCount(skillId)).isZero();

        // 没有回复的已删除顶级评论整条消失,不留"该评论已删除"的噪音
        assertThat(get("/skills/" + skillId + "/comments").body()).doesNotContain("端到端的评论");
    }

    @Test
    @DisplayName("未登录发评论 401,且不产生任何行——鉴权必须在业务之前")
    void should_rejectAnonymousPost() throws Exception {
        long skillId;
        try (Connection c = dataSource.getConnection()) {
            skillId = insertSkill(c, "anon-post-skill", 1L);
        }

        HttpResponse<String> response = post("/skills/" + skillId + "/comments", "{\"content\":\"匿名\"}", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(readCommentCount(skillId)).isZero();
    }

    @Test
    @DisplayName("正文超 2000 字符返回 4xx 而不是 500——那是用户输错了,不是服务器坏了")
    void should_rejectOverlongContentAsClientError() throws Exception {
        String token = loginAsNewUser("话痨");
        long skillId;
        try (Connection c = dataSource.getConnection()) {
            skillId = insertSkill(c, "overlong-skill", currentUserId(token));
        }

        String body = "{\"content\":\"" + "字".repeat(2001) + "\"}";
        HttpResponse<String> response = post("/skills/" + skillId + "/comments", body, token);

        assertThat(response.statusCode()).isBetween(400, 499);
        assertThat(readCommentCount(skillId)).isZero();
    }

    @Test
    @DisplayName("回复一条回复被拒——一层回复的不变量数据库拦不住,只能靠应用层")
    void should_rejectReplyToReply() throws Exception {
        String token = loginAsNewUser("套娃者");
        long skillId;
        try (Connection c = dataSource.getConnection()) {
            skillId = insertSkill(c, "nested-reply-skill", currentUserId(token));
        }

        long topId = extractId(post("/skills/" + skillId + "/comments", "{\"content\":\"顶级\"}", token).body());
        String replyBody = "{\"parentId\":" + topId + ",\"content\":\"回复\"}";
        long replyId = extractId(post("/skills/" + skillId + "/comments", replyBody, token).body());

        String nestedBody = "{\"parentId\":" + replyId + ",\"content\":\"回复的回复\"}";
        HttpResponse<String> response = post("/skills/" + skillId + "/comments", nestedBody, token);

        // 漏了这条校验不会报错,只会长出一棵任意深度的树,而前端只渲染两层,多出来的层级静默消失。
        assertThat(response.statusCode()).isBetween(400, 499);
    }

    @Test
    @DisplayName("删别人的评论 403——作者本人或 ADMIN 才行")
    void should_rejectDeletingSomeoneElsesComment() throws Exception {
        String authorToken = loginAsNewUser("原作者");
        String strangerToken = loginAsNewUser("路人");
        long skillId;
        try (Connection c = dataSource.getConnection()) {
            skillId = insertSkill(c, "delete-perm-skill", currentUserId(authorToken));
        }

        long commentId =
                extractId(post("/skills/" + skillId + "/comments", "{\"content\":\"我的评论\"}", authorToken).body());

        HttpResponse<String> response = post("/comments/" + commentId + "/delete", null, strangerToken);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(readCommentCount(skillId)).isEqualTo(1);
    }

    private long extractId(String json) {
        // 只取顶层 id,不引 JSON 库:这里要断言的是链路通不通,不是解析能力。
        int idx = json.indexOf("\"id\":");
        assertThat(idx).isNotNegative();
        int start = idx + "\"id\":".length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }

    private int readCommentCount(long skillId) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT comment_count FROM skill WHERE id = ?")) {
            ps.setLong(1, skillId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    /** 用 token 反查自己的 userId —— skill 的 owner 必须是个真实存在的人。 */
    private long currentUserId(String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/auth/me")))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return extractId(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private long insertSkill(Connection c, String name, long ownerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO skill "
                + "(name, display_name, description, source, owner_user_id, card_excerpt) "
                + "VALUES (?, ?, '描述', 'INTERNAL', ?, '摘要') RETURNING id")) {
            ps.setString(1, name);
            ps.setString(2, name);
            ps.setLong(3, ownerId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    /**
     * 建账号并走<b>真实登录接口</b>换 token。
     *
     * <p>不手插 {@code user_session}:那张表存的是 {@code token_hash} 而不是明文,还要 {@code identity_id} ——
     * 手插等于在测试里重写一遍会话生成规则,而重写的那份迟早与真实实现漂开,到那时测试仍然是绿的。
     *
     * @return 明文 token
     */
    private String loginAsNewUser(String displayName) throws Exception {
        String login = "comment-it-" + System.nanoTime();
        String password = "comment-it-password-1";
        AppUser user = appUserRepository.save(AppUser.newUser(displayName, UserRole.USER));
        userIdentityRepository.save(UserIdentity.newLocal(user.id(), login, passwordHasher.hash(password)));

        HttpResponse<String> response =
                post("/auth/login", "{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}", null);
        assertThat(response.statusCode()).isEqualTo(200);
        return extractString(response.body(), "token");
    }

    /** 只取一个顶层字符串字段。同 {@link #extractId}:这里要断言的是链路通不通,不是解析能力。 */
    private String extractString(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int idx = json.indexOf(marker);
        assertThat(idx).isNotNegative();
        int start = idx + marker.length();
        return json.substring(start, json.indexOf('"', start));
    }
}
