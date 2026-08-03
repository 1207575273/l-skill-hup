package com.landray.skillhub.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * dev 档里那几个"目录位置"配置必须解析成<b>绝对路径</b>。
 *
 * <p>这条测试存在的理由是一次真实事故:{@code local-root} 与 {@code logging.file.name} 原本都写成
 * {@code ./xxx},而相对路径按 JVM 的当前工作目录解析 —— 从 {@code backend/} 跑、从仓库根跑、从 IDEA 跑分别
 * 指向三个不同的目录。结果是制品全部读不到,而界面上只表现为详情页文件树空着(读不到被降级成"清单为空"),
 * 跟"这个包本来就没文件"长得一模一样;排查时应用日志又写在另一个目录,两边对不上,绕了一大圈。
 *
 * <p><b>为什么值得单独写一条测试:这类问题没有任何单测会自然覆盖到。</b>业务测试都用 {@code @TempDir} 或
 * mock 掉存储,配置里那个默认值在测试里从来不生效;它只在真启动时才起作用,而"启动成功"并不代表路径是对的。
 * 所以只能直接对着配置文件断言。
 *
 * <p>不起 Spring 容器:这里要验的是"YAML 里的占位符能否解析成绝对路径",{@link StandardEnvironment} 自带
 * {@code systemProperties} 属性源,足够验证 {@code ${user.home}} 这类占位符,不需要数据库也不需要 web 环境。
 */
class DevPathConfigurationTest {

    private static final String DEV_YAML = "application-dev.yml";

    private StandardEnvironment devEnvironment() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> loaded =
                new YamlPropertySourceLoader().load(DEV_YAML, new ClassPathResource(DEV_YAML));
        // 倒序添加,保证 YAML 里靠前的文档优先级更高(与 Spring Boot 自身的加载顺序一致)
        for (int i = loaded.size() - 1; i >= 0; i--) {
            environment.getPropertySources().addFirst(loaded.get(i));
        }
        // yml 里的路径默认值引用 ${skillhub.project-home},真实启动时由这个 EnvironmentPostProcessor 提供。
        // 这里如实地跑它一遍,而不是塞一个假值 —— 塞假值就等于把"占位符能不能被解析"这件事绕过去了,
        // 而那正是这组测试要验的东西。
        new ProjectHomeEnvironmentPostProcessor().postProcessEnvironment(environment, null);
        return environment;
    }

    @Test
    @DisplayName("制品根目录解析成绝对路径,不跟当前工作目录走")
    void should_resolveArtifactRootToAbsolutePath_when_usingDevDefaults() throws IOException {
        String resolved = devEnvironment().getProperty("skillhub.storage.local-root");

        assertThat(resolved).isNotNull();
        assertThat(Path.of(resolved).isAbsolute())
                .withFailMessage("制品根目录必须是绝对路径,实际为 %s —— 相对路径会跟着启动方式漂", resolved)
                .isTrue();
    }

    @Test
    @DisplayName("日志文件路径同样是绝对路径——日志是排障的起点,它自己的位置最不该是个变量")
    void should_resolveLogFileToAbsolutePath_when_usingDevDefaults() throws IOException {
        String resolved = devEnvironment().getProperty("logging.file.name");

        assertThat(resolved).isNotNull();
        assertThat(Path.of(resolved).isAbsolute())
                .withFailMessage("日志文件必须落在绝对路径下,实际为 %s", resolved)
                .isTrue();
    }

    @Test
    @DisplayName("占位符确实被展开了,不是把 ${user.home} 原样当成目录名")
    void should_expandPlaceholder_when_resolvingDevDefaults() throws IOException {
        String resolved = devEnvironment().getProperty("skillhub.storage.local-root");

        // 只断言"没有残留占位符"而不断言具体路径:具体值随机器而变,写死会让这条测试在别人机器上假红。
        assertThat(resolved).doesNotContain("${");
    }

    @Test
    @DisplayName("制品与日志都落在项目根之下,不散到用户目录去")
    void should_keepPathsInsideProjectHome_when_usingDevDefaults() throws IOException {
        StandardEnvironment environment = devEnvironment();
        String projectHome = environment.getProperty(ProjectHomeEnvironmentPostProcessor.PROJECT_HOME_PROPERTY);

        assertThat(environment.getProperty("skillhub.storage.local-root")).startsWith(projectHome);
        assertThat(environment.getProperty("logging.file.name")).startsWith(projectHome);
    }
}
