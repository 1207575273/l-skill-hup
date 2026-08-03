package com.landray.skillhub.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 端到端安全用例:真实起应用 + 真实 PostgreSQL + 真实 HTTP 往返,证明"默认放行、端点声明要求"这套机制、
 * 登录失败限流、账号禁用、权限矩阵在真实链路上都生效,不只是在 mock 过的切片测试里看起来正确
 * (backend/CLAUDE.md 8.1、8.3)。
 *
 * <p>用 JDK 内建 {@link HttpClient} 而不是 {@code TestRestTemplate}——后者在 Spring Boot 4.x 已经不存在
 * (根 CLAUDE.md 8.12)。
 *
 * <p>dev profile 下 {@code BootstrapAdminRunner} 会在应用启动时自动创建
 * {@code admin/admin123456}(见 application-dev.yml),这里直接复用它做 ADMIN 相关用例,不用再手工造一个。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "server.servlet.context-path=")
@Testcontainers
class AuthSecurityIntegrationTest {

    private static final String BOOTSTRAP_ADMIN_LOGIN = "admin";
    private static final String BOOTSTRAP_ADMIN_PASSWORD = "admin123456";

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
    private AppUserRepository appUserRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private String activeLogin;
    private static final String ACTIVE_PASSWORD = "correct-password-1";

    @BeforeEach
    void setUpActiveUser() {
        activeLogin = "active-user-" + System.nanoTime();
        AppUser user = appUserRepository.save(AppUser.newUser("集成测试用户", UserRole.USER));
        userIdentityRepository.save(
                UserIdentity.newLocal(user.id(), activeLogin, passwordHasher.hash(ACTIVE_PASSWORD)));
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> postJson(String path, String jsonBody, String bearerToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getJson(String path, String bearerToken) throws Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder().uri(URI.create(baseUrl() + path)).timeout(Duration.ofSeconds(10));
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String login(String login, String password) throws Exception {
        HttpResponse<String> response =
                postJson("/api/v1/auth/login", "{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}", null);
        assertThat(response.statusCode()).as("登录响应: " + response.body()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        return body.get("token").asString();
    }

    @Test
    void should_returnOkAndToken_when_loginWithCorrectPassword() throws Exception {
        String token = login(activeLogin, ACTIVE_PASSWORD);

        assertThat(token).isNotBlank();
    }

    @Test
    void should_returnUnauthorized_when_loginWithWrongPassword() throws Exception {
        HttpResponse<String> response =
                postJson("/api/v1/auth/login", "{\"login\":\"" + activeLogin + "\",\"password\":\"wrong\"}", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("LOGIN_FAILED");
    }

    @Test
    void should_lockAccountAfterFiveFailures_and_rejectEvenCorrectPasswordWhileLocked() throws Exception {
        String lockoutLogin = "lockout-user-" + System.nanoTime();
        AppUser user = appUserRepository.save(AppUser.newUser("锁定测试用户", UserRole.USER));
        userIdentityRepository.save(
                UserIdentity.newLocal(user.id(), lockoutLogin, passwordHasher.hash("right-password-1")));

        for (int attempt = 0; attempt < 5; attempt++) {
            HttpResponse<String> failedAttempt = postJson(
                    "/api/v1/auth/login", "{\"login\":\"" + lockoutLogin + "\",\"password\":\"wrong\"}", null);
            assertThat(failedAttempt.statusCode()).isEqualTo(401);
        }

        HttpResponse<String> lockedResponse = postJson(
                "/api/v1/auth/login",
                "{\"login\":\"" + lockoutLogin + "\",\"password\":\"right-password-1\"}",
                null);

        assertThat(lockedResponse.statusCode()).isEqualTo(403);
        assertThat(lockedResponse.body()).contains("ACCOUNT_LOCKED");
    }

    @Test
    void should_rejectLogin_when_userHasBeenDisabledByAdmin() throws Exception {
        String toBeDisabledLogin = "to-be-disabled-" + System.nanoTime();
        AppUser user = appUserRepository.save(AppUser.newUser("待禁用集成测试用户", UserRole.USER));
        userIdentityRepository.save(
                UserIdentity.newLocal(user.id(), toBeDisabledLogin, passwordHasher.hash("some-password-1")));
        String adminToken = login(BOOTSTRAP_ADMIN_LOGIN, BOOTSTRAP_ADMIN_PASSWORD);

        HttpResponse<String> disableResponse =
                postJson("/api/v1/users/" + user.id() + "/disable", "{}", adminToken);
        assertThat(disableResponse.statusCode()).isEqualTo(204);

        HttpResponse<String> loginAfterDisable = postJson(
                "/api/v1/auth/login",
                "{\"login\":\"" + toBeDisabledLogin + "\",\"password\":\"some-password-1\"}",
                null);

        assertThat(loginAfterDisable.statusCode()).isEqualTo(403);
        assertThat(loginAfterDisable.body()).contains("USER_DISABLED");
    }

    @Test
    void should_returnUnauthorized_when_meCalledWithoutToken() throws Exception {
        HttpResponse<String> response = getJson("/api/v1/auth/me", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("UNAUTHENTICATED");
    }

    @Test
    void should_returnUnauthorized_when_meCalledWithNonExistentToken() throws Exception {
        HttpResponse<String> response = getJson("/api/v1/auth/me", "this-token-was-never-issued");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void should_returnOk_when_meCalledWithValidToken() throws Exception {
        String token = login(activeLogin, ACTIVE_PASSWORD);

        HttpResponse<String> response = getJson("/api/v1/auth/me", token);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(activeLogin.isEmpty() ? "" : "集成测试用户");
    }

    @Test
    void should_returnForbidden_when_nonAdminCallsUserListEndpoint() throws Exception {
        String token = login(activeLogin, ACTIVE_PASSWORD);

        HttpResponse<String> response = getJson("/api/v1/users", token);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("ACCESS_DENIED");
    }

    @Test
    void should_returnOk_when_adminCallsUserListEndpoint() throws Exception {
        String adminToken = login(BOOTSTRAP_ADMIN_LOGIN, BOOTSTRAP_ADMIN_PASSWORD);

        HttpResponse<String> response = getJson("/api/v1/users", adminToken);

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void should_returnOkWithoutToken_when_providersCalledAnonymously() throws Exception {
        HttpResponse<String> response = getJson("/api/v1/auth/providers", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("LOCAL");
    }

    @Test
    void should_returnUnauthorized_when_tokenIsRevokedAfterLogout() throws Exception {
        String token = login(activeLogin, ACTIVE_PASSWORD);
        HttpResponse<String> logoutResponse = postJson("/api/v1/auth/logout", "{}", token);
        assertThat(logoutResponse.statusCode()).isEqualTo(204);

        HttpResponse<String> meAfterLogout = getJson("/api/v1/auth/me", token);

        assertThat(meAfterLogout.statusCode()).isEqualTo(401);
    }

    /** 顺带证明 {@link Instant} 相关字段真的往返成功——如果 InstantTypeHandler 出问题,登录接口会直接 500。 */
    @Test
    void should_includeFutureExpiresAt_when_loginSucceeds() throws Exception {
        HttpResponse<String> response = postJson(
                "/api/v1/auth/login", "{\"login\":\"" + activeLogin + "\",\"password\":\"" + ACTIVE_PASSWORD + "\"}",
                null);

        JsonNode body = objectMapper.readTree(response.body());
        Instant expiresAt = Instant.parse(body.get("expiresAt").asString());
        assertThat(expiresAt).isAfter(Instant.now());
    }
}
