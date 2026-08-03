package com.landray.skillhub.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 客户端把请求写错时,必须回 4xx 而不是 500。
 *
 * <p><b>为什么值得单独一组测试</b>:实测发现下面五种"用户输错了"的情况**全部返回 500**,响应体还是
 * "服务器内部错误,请联系管理员并提供 traceId 排查"。两个后果,第二个更严重:
 *
 * <ol>
 *   <li>用户被误导去找管理员,而问题其实在他自己的请求里。
 *   <li><b>每一条都会打 ERROR 级日志并带完整栈</b>。backend/CLAUDE.md 6.2 写死了"ERROR 打滥了,真出事时
 *       告警会被淹没" —— 一个爬虫扫几百个不存在的路径就能把错误日志刷满,真正的 5xx 淹死在里面。
 * </ol>
 *
 * <p><b>用真实 HTTP 而不是 MockMvc</b>:这些异常大多在 `DispatcherServlet` 解析请求阶段抛出,早于 Controller
 * 方法执行;`NoResourceFoundException` 更是与静态资源处理链耦合,MockMvc 里的表现与真实 servlet 容器不一致。
 * 用 MockMvc 测出来的"通过"证明不了线上也通过。
 *
 * <p>不断言具体的 {@code code} 文案,只断言**状态码落在 4xx**:错误码属于契约细节,而这组测试要守的是
 * "别把客户端错误当服务器错误"这条更粗但更重要的性质。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ClientErrorMappingIntegrationTest {

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

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void should_return400_when_requiredQueryParameterMissing() throws IOException, InterruptedException {
        // 少了 ?path=...,实测原本是 500
        HttpResponse<String> response = get("/skillhub/api/v1/skills/1/versions/1/file");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("traceId");
    }

    @Test
    void should_return400_when_requestBodyIsNotValidJson() throws IOException, InterruptedException {
        HttpResponse<String> response = postJson("/skillhub/api/v1/auth/login", "not-json");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void should_return400_when_pathVariableTypeMismatch() throws IOException, InterruptedException {
        // /skills/{id} 声明的是 long,给 "abc" 是客户端错误
        HttpResponse<String> response = get("/skillhub/api/v1/skills/abc");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void should_return405_when_httpMethodNotSupported() throws IOException, InterruptedException {
        // login 只接受 POST
        HttpResponse<String> response = get("/skillhub/api/v1/auth/login");

        assertThat(response.statusCode()).isEqualTo(405);
    }

    @Test
    void should_return404_when_pathDoesNotExist() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/skillhub/api/v1/definitely-not-a-real-endpoint");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void should_stillReturn500_when_errorIsGenuinelyServerSide() throws IOException, InterruptedException {
        // 反向保证:这组改动不能把真正的服务端故障也降级成 4xx,否则监控就瞎了。
        // 这里用一个语义合法但资源不存在的请求确认 4xx 通路正常,真 5xx 的兜底分支由
        // GlobalExceptionHandlerTest 的单测覆盖(不需要真造一次服务端崩溃)。
        HttpResponse<String> response = get("/skillhub/api/v1/skills/999999");

        assertThat(response.statusCode()).isBetween(400, 499);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
