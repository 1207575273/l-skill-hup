package com.landray.skillhub.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.slf4j.Logger;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

/**
 * 把 backend/CLAUDE.md 6.3 的日志写法硬规矩变成会失败的测试(对应 8.2 第 6~9 条)。
 *
 * <p>这几条最容易在赶进度时破防,靠 code review 拦不住——审查者不会逐行去找有没有偷偷塞了一个
 * {@code System.out.println} 或者 {@code e.printStackTrace()},但一条红色的测试会。
 *
 * <p>为什么单独成一个文件,不并进 {@link LayeredArchitectureTest}:那个文件管的是"依赖方向 / 包边界"(谁能依赖
 * 谁),这里管的是"怎么写代码"(日志门面用法、字段修饰符),两者关注的问题维度不同,拆开职责更清楚,文件也不至于
 * 膨胀成一份大杂烩。
 */
@AnalyzeClasses(
        packages = "com.landray.skillhub",
        importOptions = ImportOption.DoNotIncludeTests.class)
class LoggingConventionArchitectureTest {

    /** 禁止访问 System.out / System.err——控制台通道由结构化日志统一接管,散落的 print 不受日志级别与脱敏规则约束。 */
    @ArchTest
    static final ArchRule noStandardStreams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.because(
            "控制台输出统一走 SLF4J + 结构化日志,System.out/err 绕过日志级别、MDC 与脱敏规则");

    /** 禁止调用 Throwable.printStackTrace()——那是直接写向 System.err 的另一条后门,同样绕过日志体系。 */
    @ArchTest
    static final ArchRule noPrintStackTrace = noClasses()
            .should()
            .callMethod(Throwable.class, "printStackTrace")
            .because("printStackTrace() 直接写 System.err,绕过 SLF4J/结构化日志,栈会漏出控制台却进不了 JSONL 通道");

    /** 禁止使用 java.util.logging——本项目日志门面统一 SLF4J,混用会导致同一份配置管不到两套框架。 */
    @ArchTest
    static final ArchRule noJavaUtilLogging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.because(
            "本项目日志门面统一 SLF4J,java.util.logging 不受 logging.* 配置与结构化日志管辖");

    /**
     * org.slf4j.Logger 字段必须是 private static final,且命名固定为 log。
     *
     * <p>固定命名是为了让 6.3 的写法规矩("参数化占位""异常放最后一个参数"等)可以直接照抄成统一的 {@code log.xxx(...)}
     * 调用,不用每个类都重新确认门面变量叫什么;private static final 是常规的日志门面声明约定,防止被实例共享或
     * 意外重新赋值。
     */
    @ArchTest
    static final ArchRule loggerFieldsMustFollowConvention = fields()
            .that()
            .haveRawType(Logger.class)
            .should()
            .bePrivate()
            .andShould()
            .beStatic()
            .andShould()
            .beFinal()
            .andShould()
            .haveName("log")
            .because("org.slf4j.Logger 字段必须是 private static final Logger log,统一命名便于全项目复用同一套"
                    + "日志写法规矩(backend/CLAUDE.md 6.1)");
}
