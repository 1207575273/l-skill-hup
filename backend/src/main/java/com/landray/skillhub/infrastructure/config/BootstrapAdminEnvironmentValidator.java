package com.landray.skillhub.infrastructure.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * prod 下引导管理员密码未配置时拒绝启动(业务建模文档 3.2)。
 *
 * <p>刻意实现成 {@link EnvironmentPostProcessor} 而不是 {@code ApplicationRunner} 或 {@code @PostConstruct}
 * 检查——两个理由:
 *
 * <ol>
 *   <li><b>必须在触碰数据库之前失败。</b>{@link EnvironmentPostProcessor} 在
 *       {@link org.springframework.context.ApplicationContext} 刷新之前运行,早于 DataSource
 *       创建、早于 Flyway 迁移。一个带公开默认密码的 ADMIN 账号比没有管理员更危险,"启动失败"要尽可能早、
 *       尽可能确定地发生,不能等到"连上库、跑完迁移"之后才发现密码没配——那样每次误配置都要先付一轮数据库连接
 *       的开销才能看到失败信息,而且失败时机会随 Bean 初始化顺序漂移,不可预测。
 *   <li><b>不依赖 Spring 容器。</b>这个校验只读 {@link ConfigurableEnvironment} 与写死的 profile 名 "prod",
 *       不需要任何其他 bean,天然可以在没有真实数据库的单元测试里直接 new 出来调用
 *       {@link #postProcessEnvironment}——不需要 Testcontainers 就能验证 fail-fast 逻辑本身。
 * </ol>
 *
 * <p>通过 {@code META-INF/spring.factories} 注册(SPI key 是
 * {@code org.springframework.boot.EnvironmentPostProcessor}——注意 Spring Boot 4 把这个接口从
 * 3.x 的 {@code org.springframework.boot.env.EnvironmentPostProcessor} 挪到了
 * {@code org.springframework.boot} 包下,旧包名仍保留但不是 SPI 实际读取的 key,已用
 * {@code spring-boot-4.1.0.jar} 里的 {@code META-INF/spring.factories} 实测核实)。
 */
public class BootstrapAdminEnvironmentValidator implements EnvironmentPostProcessor, Ordered {

    static final String PROD_PROFILE = "prod";

    static final String PASSWORD_PROPERTY = "skillhub.auth.bootstrap-admin.password";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.matchesProfiles(PROD_PROFILE)) {
            return;
        }
        String password = environment.getProperty(PASSWORD_PROPERTY, "");
        if (password.isBlank()) {
            throw new IllegalStateException(
                    "prod 环境下 " + PASSWORD_PROPERTY + "(环境变量 BOOTSTRAP_ADMIN_PASSWORD)未配置,拒绝启动。"
                            + "一个带公开默认密码的 ADMIN 账号比没有管理员更危险,请设置该环境变量后重试。");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
