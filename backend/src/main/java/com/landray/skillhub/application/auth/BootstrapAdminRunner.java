package com.landray.skillhub.application.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时引导管理员(业务建模文档 3.2)。
 *
 * <p>{@code prod} 下密码为空时的 fail-fast 校验<b>不</b>在这里做——那必须发生在触碰数据库之前,由
 * {@code infrastructure.config.BootstrapAdminEnvironmentValidator}(一个 {@code EnvironmentPostProcessor})
 * 在 {@link org.springframework.context.ApplicationContext} 刷新之前完成。到这个 {@link ApplicationRunner}
 * 执行时(整个上下文已刷新完毕,Flyway 迁移已跑完),prod 下密码必然非空——这里只负责幂等地创建账号。
 *
 * <p>放在 application 层而不是 infrastructure:它编排的是一个真实的用例("若引导管理员不存在则创建"),
 * 委托给同层的 {@link AuthApplicationService}(带 {@code @Transactional}),不是纯技术配置。若放进
 * infrastructure,依赖 application 层的服务会直接违反 ArchUnit 的分层规则(Application 只能被
 * Interfaces 访问)。
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private final AuthApplicationService authApplicationService;
    private final AuthProperties authProperties;

    public BootstrapAdminRunner(AuthApplicationService authApplicationService, AuthProperties authProperties) {
        this.authApplicationService = authApplicationService;
        this.authProperties = authProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin bootstrapAdmin = authProperties.bootstrapAdmin();
        authApplicationService.ensureBootstrapAdmin(bootstrapAdmin.login(), bootstrapAdmin.password());
    }
}
