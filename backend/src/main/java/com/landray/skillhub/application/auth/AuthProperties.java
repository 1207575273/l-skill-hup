package com.landray.skillhub.application.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code skillhub.auth.*} 配置(业务建模文档 3.2)。
 *
 * <p>放在 application 而不是 infrastructure/config——虽然 {@code @ConfigurationProperties} 是 Spring
 * 注解,但它承载的是业务配置(自助注册开关、引导管理员账号),被 {@link AuthApplicationService} /
 * {@link BootstrapAdminRunner} 这些 application 层的用例编排直接消费。若放进 infrastructure,会违反
 * ArchUnit 的分层规则——"Application 只被 Interfaces 访问"意味着 infrastructure 中的类不能被
 * application 依赖,包括配置类本身。application 层已经在用 {@code @Transactional} 这样的 Spring
 * 注解,{@code @ConfigurationProperties} 并不会破坏纯净性上的任何既有约束(那条约束只对 domain 生效)。
 *
 * <p>通过 {@code SkillHubApplication} 上的 {@code @ConfigurationPropertiesScan} 自动注册为 bean。
 */
@ConfigurationProperties(prefix = "skillhub.auth")
public record AuthProperties(boolean localRegisterEnabled, BootstrapAdmin bootstrapAdmin) {

    public record BootstrapAdmin(String login, String password) {}
}
