package com.landray.skillhub.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;

/**
 * 证明控制台编码探测真的一路传到了 logback,而不是"类写了、注册了、logback 却没用上"。
 *
 * <p>踩坑点 8.2 的同一条教训:配置类静默不生效是最阴的失败模式。这条链路有四段 ——
 * {@link ConsoleEncodingEnvironmentPostProcessor} 写 {@code logging.charset.console} ->
 * Spring Boot 的 {@code LoggingSystemProperties} 转成系统属性 {@code CONSOLE_LOG_CHARSET} ->
 * logback 默认配置读它 -> encoder 拿到 charset。任何一段断了,应用都照常启动、日志照常打,
 * 只是中文变成乱码,而没人会为此看一眼启动日志。
 *
 * <p><b>断言值恒为 UTF-8 是因为 surefire 必然没有交互式终端</b>(fork 进程的 stdout 接在管道上),
 * 探测走的就是兜底分支。这条测试守不住"有终端时跟随终端编码"那条分支 —— 那条由
 * {@code ConsoleEncodingEnvironmentPostProcessorTest} 用注入的探测结果覆盖,两者合起来才完整。
 *
 * <p><b>按类型找 appender 而不是按名字找 "CONSOLE"</b>:Spring Boot 4 的默认 logback 配置由
 * {@code DefaultLogbackConfiguration} 这个 Java 类生成而不再是 XML,appender 名字属于它的内部约定,
 * 按名字找会在升级时以 NPE 的形式碎掉,而那个报错完全不指向真正的原因。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ConsoleEncodingIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("skillhub")
            .withUsername("skillhub")
            .withPassword("skillhub");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void should_encode_console_output_as_utf8_when_no_interactive_console() {
        assertThat(consoleAppenderCharsetName()).isEqualTo(StandardCharsets.UTF_8.name());
    }

    @Test
    void should_expose_console_charset_as_logback_system_property() {
        // 这是 Spring Boot 传给 logback 的唯一桥梁。它与上一条对不上时,说明断在了转换那一段。
        assertThat(System.getProperty("CONSOLE_LOG_CHARSET")).isEqualTo(StandardCharsets.UTF_8.name());
    }

    @Test
    void should_keep_file_channel_on_utf8_regardless_of_platform() {
        // 文件通道是 ECS JSONL,消费者永远是机器。它由 application-*.yml 显式钉死,
        // 不参与平台探测 —— 这条测试就是那句"显式钉死"的执行者。
        assertThat(System.getProperty("FILE_LOG_CHARSET")).isEqualTo(StandardCharsets.UTF_8.name());
    }

    private String consoleAppenderCharsetName() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Iterator<Appender<ILoggingEvent>> appenders =
                context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).iteratorForAppenders();
        while (appenders.hasNext()) {
            Appender<ILoggingEvent> appender = appenders.next();
            if (!(appender instanceof ConsoleAppender<ILoggingEvent> console)) {
                continue;
            }
            Encoder<ILoggingEvent> encoder = console.getEncoder();
            if (encoder instanceof LayoutWrappingEncoder<ILoggingEvent> wrapping) {
                return wrapping.getCharset().name();
            }
            throw new AssertionError(
                    "ConsoleAppender 的 encoder 不是 LayoutWrappingEncoder,拿不到 charset,实际类型="
                            + encoder.getClass().getName());
        }
        throw new AssertionError("root logger 上没有 ConsoleAppender,控制台日志根本没配上");
    }
}
