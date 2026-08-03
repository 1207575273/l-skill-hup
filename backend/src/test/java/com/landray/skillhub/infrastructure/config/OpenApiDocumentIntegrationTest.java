package com.landray.skillhub.infrastructure.config;

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
 * 证明 springdoc 真的挂上了,而不是"依赖引了、配置写了、端点却是 404"。
 *
 * <p>根 CLAUDE.md 踩坑点 8.2 的教训:Spring Boot 4 把自动配置拆成了独立模块,缺模块时功能会<b>静默不生效</b> ——
 * 应用正常启动、健康检查 UP,而那个功能根本没跑。Flyway 已经这么坑过一次。所以"引了依赖"不构成验证,必须有一个
 * 动作能证明端点真的在响应。
 *
 * <p>写成常驻测试而不是跑一次 curl:一次性验证只能证明"当时是好的"。真正会出问题的时刻是将来某次升级 Spring Boot
 * 或 springdoc 之后,而那时没人会想起来再 curl 一遍。
 *
 * <p><b>用 JDK 内建的 HttpClient 而不是 TestRestTemplate</b>:后者在 Spring Boot 4 的 {@code spring-boot-test}
 * 里已经不存在了(本地仓库 4.0.0 / 4.1.0 两个 jar 实测都只剩 {@code LocalServerPort})。这类模块搬迁在 Boot 4
 * 里很频繁,用 JDK 自带的就与它无关。顺带:{@code HttpClient} 不会因为 4xx/5xx 抛异常,断状态码更直接。
 *
 * <p>起真库是因为 {@link SpringBootTest} 会拉起完整上下文(含数据源与 Flyway),不给库就起不来。这里关心的不是
 * 数据,只是让上下文能起。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OpenApiDocumentIntegrationTest {

    private static final String API_DOCS_PATH = "/skillhub/v3/api-docs";

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

    @Test
    void should_serveOpenApiDocument_whenApiDocsEndpointRequested() throws IOException, InterruptedException {
        HttpResponse<String> response = getApiDocs();

        assertThat(response.statusCode())
                .as("端点返回 404 就意味着 springdoc 没生效 —— 这正是踩坑 8.2 那种静默失效")
                .isEqualTo(200);
        assertThat(response.body()).isNotBlank();
    }

    @Test
    void should_declareOpenApiThreeOne_whenApiDocsEndpointRequested() throws IOException, InterruptedException {
        // 版本号不是细节:springdoc 3.x 默认输出 3.1,其 schema 基于 JSON Schema 2020-12,与 3.0 有实质差异
        // (nullable / oneOf / const 的表达方式都不同),而前端要用 openapi-typescript 吃这份文档。
        // 哪天它悄悄退回 3.0,生成的 TS 类型就会错,而且错得不明显。
        assertThat(getApiDocs().body()).contains("\"openapi\":\"3.1");
    }

    @Test
    void should_carryGlobalInfoFromConfig_whenApiDocsEndpointRequested() throws IOException, InterruptedException {
        // 证明 OpenApiConfig 那个 Bean 真被采纳了,而不是 springdoc 在用自己的默认值。
        String body = getApiDocs().body();

        assertThat(body).contains("skill-hub API");
        assertThat(body).contains("bearerAuth");
    }

    private HttpResponse<String> getApiDocs() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + API_DOCS_PATH))
                .GET()
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
