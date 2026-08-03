package com.landray.skillhub.infrastructure.config;

import java.io.Console;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.function.Supplier;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 按运行环境决定控制台日志的字符编码,让中文在各个平台上都不乱码。
 *
 * <p><b>规则只有两条</b>:
 *
 * <ul>
 *   <li><b>连着交互式终端</b> -> 跟随终端自报的编码。Windows 的 cmd / PowerShell 是 GBK,
 *       往那里写 UTF-8 字节人眼看到的就是乱码。
 *   <li><b>没连终端</b>(重定向到文件、被管道接走、容器里、CI 里)-> 恒定 UTF-8。
 *       这时读日志的是 grep / jq / 日志采集,它们默认按 UTF-8 解释。
 * </ul>
 *
 * <p><b>Spring Boot 4 已经做了大半,这个类补的是第二条。</b>它的
 * {@code LoggingSystemProperties#getDefaultConsoleCharset()} 实现是
 * {@code console != null ? console.charset() : Charset.defaultCharset()}(反编译
 * spring-boot-4.1.0.jar 核实)。第一条完全一致,差别在没有终端时:它用
 * {@link Charset#defaultCharset()},而那个值等于 {@code file.encoding}。JDK 18 起(JEP 400)
 * {@code file.encoding} 默认就是 UTF-8,所以今天两者结果相同 —— <b>但这是巧合,不是保证</b>。
 * 只要有人为了别的目的加一句 {@code -Dfile.encoding=GBK}(Windows 老项目的常见习惯),
 * 日志文件就会悄悄变成 GBK,而应用照常启动、没有任何报错。把它与 {@code file.encoding} 解耦,
 * 就是这个类存在的全部理由。
 *
 * <p><b>刻意不跟随 {@code stdout.encoding} 系统属性。</b>它看起来更权威(JDK 19 起就是为描述
 * {@code System.out} 的真实编码而引入的),但在 Windows 上它的值由 JVM 按 ANSI 代码页推断,
 * 而 JVM 无从知道 stdout 那头接的是什么。本机实测:在 Git Bash(mintty)里 {@code stdout.encoding}
 * 报 GBK,可 mintty 本身是 UTF-8 终端 —— 跟随它反而制造乱码。{@link System#console()} 为 null
 * 这个信号则是可靠的:它说明 stdout 没接终端,那么按 UTF-8 写给工具读一定不会错。
 *
 * <p><b>文件通道不归这里管</b>,它在两个 {@code application-*.yml} 里显式钉死 UTF-8。文件是 ECS
 * JSONL,消费者永远是机器,没有"跟随平台"的余地,不需要探测也不该被探测结果影响。
 */
public class ConsoleEncodingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String CONSOLE_CHARSET_PROPERTY = "logging.charset.console";

    private static final String PROPERTY_SOURCE_NAME = "consoleEncodingAdaptation";

    private final Supplier<Charset> interactiveConsoleCharset;

    /** SPI 走无参构造器,探测真实控制台。 */
    public ConsoleEncodingEnvironmentPostProcessor() {
        this(ConsoleEncodingEnvironmentPostProcessor::detectInteractiveConsoleCharset);
    }

    ConsoleEncodingEnvironmentPostProcessor(Supplier<Charset> interactiveConsoleCharset) {
        this.interactiveConsoleCharset = interactiveConsoleCharset;
    }

    /**
     * 探测 stdout 是否接着交互式终端,是则返回终端编码,否则返回 {@code null}。
     *
     * <p>JDK 21 下 stdout 或 stdin 任一被重定向,{@link System#console()} 即为 {@code null} ——
     * 这正是我们要的"没人在看"的信号。JDK 22 起 {@code System.console()} 恒返回非 null 对象,
     * 判断要改用 {@code Console.isTerminal()};升 JDK 时这里必须跟着改,
     * 否则重定向场景会退回终端编码。届时 {@code should_report_no_interactive_console_when_running_under_surefire}
     * 会失败,不会静默。
     */
    static Charset detectInteractiveConsoleCharset() {
        Console console = System.console();
        return console == null ? null : console.charset();
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 显式配置永远赢:探测是兜底,不是接管。
        if (environment.containsProperty(CONSOLE_CHARSET_PROPERTY)) {
            return;
        }
        Charset detected = interactiveConsoleCharset.get();
        Charset charset = detected == null ? StandardCharsets.UTF_8 : detected;
        environment
                .getPropertySources()
                .addLast(new MapPropertySource(
                        PROPERTY_SOURCE_NAME,
                        Collections.singletonMap(CONSOLE_CHARSET_PROPERTY, charset.name())));
    }

    @Override
    public int getOrder() {
        // 排在所有 EnvironmentPostProcessor 之后,保证 application-*.yml 已经加载完 ——
        // 否则 containsProperty 判断不到 yml 里的显式配置,兜底会盖掉用户的选择。
        // 这仍然早于日志系统初始化:所有 EPP 都由 EnvironmentPostProcessorApplicationListener 统一驱动,
        // 那个 listener 整体排在 LoggingApplicationListener 之前。
        return Ordered.LOWEST_PRECEDENCE;
    }
}
