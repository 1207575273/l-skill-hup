package com.landray.skillhub.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * 直接调用 {@link BootstrapAdminEnvironmentValidator#postProcessEnvironment} 验证 fail-fast 逻辑本身,不需要
 * Spring 容器、不需要 Testcontainers——这正是把它实现成 {@code EnvironmentPostProcessor} 而不是
 * {@code ApplicationRunner} 的好处之一(见类注释)。SPI 注册链路(spring.factories 是否真的接得上)由
 * {@code BootstrapAdminBootIntegrationTest} 另外验证。
 */
class BootstrapAdminEnvironmentValidatorTest {

    private final BootstrapAdminEnvironmentValidator validator = new BootstrapAdminEnvironmentValidator();

    @Test
    void should_throw_when_prodProfileAndPasswordBlank() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty(BootstrapAdminEnvironmentValidator.PASSWORD_PROPERTY, "");

        assertThatThrownBy(() -> validator.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD");
    }

    @Test
    void should_throw_when_prodProfileAndPasswordMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        // 未设置该属性,property 解析结果为空字符串默认值

        assertThatThrownBy(() -> validator.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_notThrow_when_prodProfileAndPasswordConfigured() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty(BootstrapAdminEnvironmentValidator.PASSWORD_PROPERTY, "a-real-password");

        assertThatCode(() -> validator.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    @Test
    void should_notThrow_when_devProfileAndPasswordBlank() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.setProperty(BootstrapAdminEnvironmentValidator.PASSWORD_PROPERTY, "");

        assertThatCode(() -> validator.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    @Test
    void should_notThrow_when_noProfileActive() {
        MockEnvironment environment = new MockEnvironment();

        assertThatCode(() -> validator.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }
}
