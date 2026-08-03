package com.landray.skillhub.infrastructure.config;

import static com.landray.skillhub.infrastructure.config.ConsoleEncodingEnvironmentPostProcessor.CONSOLE_CHARSET_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * {@link ConsoleEncodingEnvironmentPostProcessor} 的单测。
 *
 * <p>控制台探测结果通过构造器注入,所以这三条在任何平台上跑出来的结果都一样 —— 不会因为 CI 是 Linux、
 * 开发机是 Windows 就得到不同结论。真实探测那一段单独由 {@link #should_report_no_interactive_console_when_running_under_surefire()}
 * 覆盖。
 */
class ConsoleEncodingEnvironmentPostProcessorTest {

    private static final Charset GBK = Charset.forName("GBK");

    @Test
    void should_use_console_charset_when_interactive_console_present() {
        MockEnvironment environment = new MockEnvironment();

        new ConsoleEncodingEnvironmentPostProcessor(() -> GBK).postProcessEnvironment(environment, null);

        // 连着真实终端时跟随终端自报的编码:Windows 的 cmd / PowerShell 是 GBK,
        // 这时写 UTF-8 字节过去人眼看到的就是乱码。
        assertThat(environment.getProperty(CONSOLE_CHARSET_PROPERTY)).isEqualTo("GBK");
    }

    @Test
    void should_fall_back_to_utf8_when_no_interactive_console() {
        MockEnvironment environment = new MockEnvironment();

        new ConsoleEncodingEnvironmentPostProcessor(() -> null).postProcessEnvironment(environment, null);

        assertThat(environment.getProperty(CONSOLE_CHARSET_PROPERTY)).isEqualTo(StandardCharsets.UTF_8.name());
    }

    @Test
    void should_not_override_when_charset_explicitly_configured() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(CONSOLE_CHARSET_PROPERTY, "ISO-8859-1");

        new ConsoleEncodingEnvironmentPostProcessor(() -> GBK).postProcessEnvironment(environment, null);

        // 显式配置永远赢。探测是兜底,不是接管 —— 否则想临时钉死编码排查问题的人会发现配置不生效。
        assertThat(environment.getProperty(CONSOLE_CHARSET_PROPERTY)).isEqualTo("ISO-8859-1");
    }

    @Test
    void should_report_no_interactive_console_when_running_under_surefire() {
        // surefire 把 fork 进程的 stdout 接到管道上,所以 System.console() 必为 null。
        // 这条盯的是真实探测那一段:它一旦改成读 stdout.encoding 之类的系统属性,
        // 在这里就会拿到 GBK 而不是 null,测试立刻变红。
        assertThat(ConsoleEncodingEnvironmentPostProcessor.detectInteractiveConsoleCharset())
                .isNull();
    }
}
