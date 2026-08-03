package com.landray.skillhub.infrastructure.config;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Collections;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 计算 {@code skillhub.project-home} —— 一个<b>与当前工作目录无关</b>的项目根目录绝对路径,供 dev 档把制品与
 * 日志落在项目里而不是随启动方式漂。
 *
 * <p><b>为什么需要它。</b>dev 的路径默认值最早写成 {@code ./data/artifacts},相对路径按 JVM 的当前工作目录解析:
 * 从 {@code backend/} 跑、从仓库根跑、从 IDEA 跑分别指向三个目录。实际后果是制品全部读不到,而界面上只表现为
 * 详情页文件树空着(读不到被降级成"清单为空"),跟"这个包本来就没文件"长得一模一样;排查时应用日志又写在
 * 另一个目录,两边对不上。
 *
 * <p><b>为什么不用 {@code ${user.home}}。</b>那样确实与工作目录无关,但会把项目数据堆进用户配置目录,
 * 既不好找也和项目本身脱节。锚到项目根两个目标都满足。
 *
 * <p><b>定位方式:从"这个类的 class 文件在哪"出发向上走。</b>代码位置由 JVM 给出,不受工作目录影响,这是它比
 * {@code user.dir} 可靠的原因。每一级按两条判据认:
 *
 * <ol>
 *   <li>该目录直接含 {@code pom.xml} -> 它就是模块根。命中 Maven 的输出布局
 *       {@code backend/target/classes} -> 向上两级到 {@code backend/}。
 *   <li>该目录含 {@code backend/pom.xml} -> 它是仓库根,返回其下的 {@code backend/}。<b>这条不是冗余</b>:
 *       IntelliJ 默认把 class 编到 <b>{@code <仓库根>/out/production/<模块名>}</b> 而不是 {@code target/classes},
 *       那条路径向上走永远碰不到 {@code pom.xml}(它在 {@code backend/} 里,仓库根没有),只靠第一条会在
 *       "从 IDEA 启动"这个最主要的场景下失效 —— 而那正是这套机制要解决的场景。
 * </ol>
 *
 * <p><b>找不到就回落到当前工作目录,并且这是正确的行为</b>:找不到意味着跑的是打包后的 jar(容器里),而那个场景
 * {@code ARTIFACT_ROOT} / {@code LOG_ROOT} 本来就由 compose 显式给绝对路径,这个值根本不会被用到。
 */
public class ProjectHomeEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROJECT_HOME_PROPERTY = "skillhub.project-home";

    private static final String PROPERTY_SOURCE_NAME = "skillhubProjectHome";
    private static final String MODULE_MARKER = "pom.xml";

    /** 后端模块的目录名。仓库根没有 pom.xml,认出仓库根之后要靠它再下一层。 */
    private static final String BACKEND_MODULE_DIR = "backend";

    /**
     * 向上查找的层数上限。Maven 布局要 2 层({@code target/classes} -> {@code target} -> 模块根),
     * IntelliJ 布局要 3 层({@code out/production/<模块>} -> ... -> 仓库根),给足余量即可。
     */
    private static final int MAX_LOOKUP_DEPTH = 8;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.containsProperty(PROJECT_HOME_PROPERTY)) {
            return;
        }
        Path projectHome = resolveProjectHome();
        environment
                .getPropertySources()
                .addLast(new MapPropertySource(
                        PROPERTY_SOURCE_NAME,
                        Collections.singletonMap(PROJECT_HOME_PROPERTY, projectHome.toString())));
    }

    /**
     * 优先按代码位置向上找模块根,找不到时回落到当前工作目录。
     *
     * <p>包级可见是为了让测试直接验它,而不是绕一大圈去起一个应用上下文。
     */
    static Path resolveProjectHome() {
        return resolveFrom(findCodeLocation());
    }

    /**
     * 从给定的代码位置推项目根。独立出来是为了让测试能直接构造 Maven 与 IntelliJ 两种目录布局来验 ——
     * 测试进程本身只可能处在其中一种,靠真实环境验不到另一种。
     */
    static Path resolveFrom(Path codeLocation) {
        if (codeLocation != null) {
            Path moduleRoot = findModuleRoot(codeLocation);
            if (moduleRoot != null) {
                return moduleRoot;
            }
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    private static Path findCodeLocation() {
        ProtectionDomain domain = ProjectHomeEnvironmentPostProcessor.class.getProtectionDomain();
        CodeSource codeSource = domain == null ? null : domain.getCodeSource();
        URL location = codeSource == null ? null : codeSource.getLocation();
        if (location == null) {
            return null;
        }
        try {
            return new File(location.toURI()).toPath().toAbsolutePath().normalize();
        } catch (URISyntaxException | IllegalArgumentException e) {
            // 非 file: 协议(嵌套 jar、自定义 ClassLoader)拿不到本地路径。这不是错误,是"这里没有项目根"
            // 的一种表现,交给上层回落即可 —— 抛异常会让应用起不来,而这个值在那些场景本来就用不上。
            return null;
        }
    }

    private static Path findModuleRoot(Path codeLocation) {
        Path current = Files.isDirectory(codeLocation) ? codeLocation : codeLocation.getParent();
        for (int depth = 0; current != null && depth < MAX_LOOKUP_DEPTH; depth++) {
            if (Files.isRegularFile(current.resolve(MODULE_MARKER))) {
                return current;
            }
            // 仓库根:自己没有 pom.xml,但下面挂着 backend 模块。IntelliJ 的 out/production/<模块> 布局
            // 只能靠这条命中(详见类注释)。
            Path backendModule = current.resolve(BACKEND_MODULE_DIR);
            if (Files.isRegularFile(backendModule.resolve(MODULE_MARKER))) {
                return backendModule;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * 必须早于日志系统初始化。{@code EnvironmentPostProcessor} 在 {@code ApplicationEnvironmentPreparedEvent}
     * 上执行,而 {@code LoggingApplicationListener} 的顺序排在其后,所以 {@code logging.file.name} 里引用
     * 这个占位符是安全的。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
