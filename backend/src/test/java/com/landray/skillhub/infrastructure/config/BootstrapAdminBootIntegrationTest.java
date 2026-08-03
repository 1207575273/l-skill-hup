package com.landray.skillhub.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

import com.landray.skillhub.SkillHubApplication;

/**
 * 证明 {@code META-INF/spring.factories} 里的 SPI 注册真的接得上——{@link BootstrapAdminEnvironmentValidatorTest}
 * 只验证了校验逻辑本身,不验证"Spring Boot 是否真的发现并调用了这个类"。这类"注册项写错、SPI 静默不生效"是
 * 根 CLAUDE.md 踩坑点 8.2 记录过的同一类风险(Flyway 曾经就是这样悄悄不执行),必须有一条测试证明它真的跑了。
 *
 * <p>不需要 Testcontainers/真实数据库:{@link org.springframework.boot.env.EnvironmentPostProcessor} 在
 * {@link org.springframework.context.ApplicationContext} 刷新之前运行,早于 DataSource 创建——如果
 * fail-fast 生效,{@link SpringApplication#run} 会在触碰数据库之前就抛出异常。{@code @Timeout} 是双重保险:
 * 万一 fail-fast 失效、应用继续往下跑去连一个不存在的数据库,不能让这条测试挂起拖慢整个 {@code mvn verify}。
 */
class BootstrapAdminBootIntegrationTest {

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void should_failToStart_when_prodProfileActiveWithoutBootstrapAdminPassword() {
        // 用系统属性激活 prod profile,优先级高于 application.yml 里的
        // spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev},是标准的 -Dspring.profiles.active=prod 等价写法。
        // 不设置 BOOTSTRAP_ADMIN_PASSWORD,application-prod.yml 里对应属性因此解析为空字符串。
        System.setProperty("spring.profiles.active", "prod");
        try {
            SpringApplication app = new SpringApplication(SkillHubApplication.class);
            app.setWebApplicationType(WebApplicationType.NONE);

            assertThatThrownBy(app::run)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD");
        } finally {
            System.clearProperty("spring.profiles.active");
        }
    }
}
