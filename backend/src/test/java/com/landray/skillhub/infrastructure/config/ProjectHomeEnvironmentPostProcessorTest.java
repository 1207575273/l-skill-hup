package com.landray.skillhub.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.StandardEnvironment;

/**
 * {@link ProjectHomeEnvironmentPostProcessor} 必须给出一个<b>与当前工作目录无关</b>的绝对路径。
 *
 * <p>这组测试盯的是一次真实事故:dev 的路径默认值原本是 {@code ./data/artifacts},从 {@code backend/} 跑、
 * 从仓库根跑、从 IDEA 跑分别指向三个目录,制品全部读不到,而界面上只表现为文件树空着。
 */
class ProjectHomeEnvironmentPostProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("解析出的项目根是绝对路径,且确实含 pom.xml——那正是判定它是模块根的依据")
    void should_resolveToModuleRootContainingPom_when_runningFromClasses() {
        Path projectHome = ProjectHomeEnvironmentPostProcessor.resolveProjectHome();

        assertThat(projectHome.isAbsolute()).isTrue();
        assertThat(Files.isRegularFile(projectHome.resolve("pom.xml")))
                .withFailMessage("项目根应含 pom.xml,实际为 %s", projectHome)
                .isTrue();
    }

    @Test
    @DisplayName("Maven 布局:从 backend/target/classes 出发,落到含 pom.xml 的 backend/")
    void should_resolveToModuleRoot_when_layoutIsMavenTargetClasses() throws IOException {
        Path repoRoot = Files.createDirectories(tempDir.resolve("repo"));
        Path backend = Files.createDirectories(repoRoot.resolve("backend"));
        Files.createFile(backend.resolve("pom.xml"));
        Path classes = Files.createDirectories(backend.resolve("target/classes"));

        assertThat(ProjectHomeEnvironmentPostProcessor.resolveFrom(classes)).isEqualTo(backend);
    }

    @Test
    @DisplayName("IntelliJ 布局:从 <仓库根>/out/production/<模块> 出发,同样落到 backend/")
    void should_resolveToBackendModule_when_layoutIsIntellijOutDir() throws IOException {
        // 这条盯的是一个真实会踩的坑:IntelliJ 默认不编到 target/classes,而是编到 out/production/<模块>。
        // 那条路径向上走永远碰不到 pom.xml —— 它在 backend/ 里,仓库根没有 —— 于是只认 pom.xml 的实现
        // 会回落到当前工作目录,而"从 IDEA 启动"恰恰是这套机制最主要的服务对象。
        Path repoRoot = Files.createDirectories(tempDir.resolve("repo"));
        Path backend = Files.createDirectories(repoRoot.resolve("backend"));
        Files.createFile(backend.resolve("pom.xml"));
        Path intellijOut = Files.createDirectories(repoRoot.resolve("out/production/skill-hub"));

        assertThat(ProjectHomeEnvironmentPostProcessor.resolveFrom(intellijOut)).isEqualTo(backend);
    }

    @Test
    @DisplayName("两种布局都认不出时回落到当前工作目录,而不是抛异常让应用起不来")
    void should_fallBackToWorkingDirectory_when_noMarkerFound() throws IOException {
        // 打包后的 jar(容器里)就是这种情况。那个场景 ARTIFACT_ROOT / LOG_ROOT 由 compose 显式给绝对路径,
        // 这个值不会被用到,所以回落是安全的 —— 但绝不能因为"推不出来"就让应用启动失败。
        Path orphan = Files.createDirectories(tempDir.resolve("nothing/here"));

        assertThat(ProjectHomeEnvironmentPostProcessor.resolveFrom(orphan))
                .isEqualTo(Path.of("").toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("同一次 JVM 内多次解析结果一致——它不能依赖任何会变的外部状态")
    void should_beStable_when_resolvedRepeatedly() {
        assertThat(ProjectHomeEnvironmentPostProcessor.resolveProjectHome())
                .isEqualTo(ProjectHomeEnvironmentPostProcessor.resolveProjectHome());
    }

    @Test
    @DisplayName("把 skillhub.project-home 写进 Environment,供 yml 里的占位符引用")
    void should_publishProjectHomeProperty_when_postProcessing() {
        StandardEnvironment environment = new StandardEnvironment();

        new ProjectHomeEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        String value = environment.getProperty(ProjectHomeEnvironmentPostProcessor.PROJECT_HOME_PROPERTY);
        assertThat(value).isNotNull();
        assertThat(Path.of(value).isAbsolute()).isTrue();
    }

    @Test
    @DisplayName("已显式配置时不覆盖——使用者的决定优先于自动推断")
    void should_keepExistingValue_when_alreadyConfigured() {
        StandardEnvironment environment = new StandardEnvironment();
        System.setProperty(ProjectHomeEnvironmentPostProcessor.PROJECT_HOME_PROPERTY, "/explicit/home");
        try {
            new ProjectHomeEnvironmentPostProcessor().postProcessEnvironment(environment, null);

            assertThat(environment.getProperty(ProjectHomeEnvironmentPostProcessor.PROJECT_HOME_PROPERTY))
                    .isEqualTo("/explicit/home");
        } finally {
            System.clearProperty(ProjectHomeEnvironmentPostProcessor.PROJECT_HOME_PROPERTY);
        }
    }
}
