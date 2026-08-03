package com.landray.skillhub.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI 文档的全局描述。
 *
 * <p><b>刻意只写全局信息,不给每个接口、每个字段挂注解</b>(业务建模文档 8.2:注解用量克制)。springdoc 能从
 * Controller 签名、DTO 字段类型、Bean Validation 注解里推出绝大部分 schema;再手写一遍 {@code @Schema} 只是把同一件
 * 事说两遍,而且两份说法必然会漂。只有推不出来、又确实影响调用方的地方才补注解。
 *
 * <p>安全方案在这里声明一次的理由:声明了 swagger-ui 才能带 token 试接口,由 {@code openapi-typescript} 生成的
 * 前端类型也才知道哪些接口需要认证。不声明的话每个接口都得自己写一遍。
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI skillHubOpenApi() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .description("登录后由 /auth/login 返回的会话 token");

        return new OpenAPI()
                .info(new Info()
                        .title("skill-hub API")
                        .version("v1")
                        .description("团队内部 Agent Skill 分发平台。只用 GET / POST:查询 GET,一切写操作 POST"
                                + "(更新 POST /{id}/update,删除 POST /{id}/delete)。"
                                + "错误响应体统一为 { code, message, traceId, details? },其中 traceId 必填,"
                                + "报障时贴它即可定位到具体请求。"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME_NAME, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
    }
}
