package com.landray.skillhub.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import org.springframework.transaction.annotation.Transactional;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 把 CLAUDE.md 里的分层规矩变成会失败的测试。
 *
 * <p>文字约束拦不住"某天有人图省事在 Controller 里直接注入 Mapper",一条红色的测试可以。
 * <b>新增架构约定时必须同步加规则</b>,否则那条约定三个月后必然失效。
 *
 * <p>本类只装"依赖方向 / 包边界"这一类规则(backend/CLAUDE.md 8.2 的第 1~5 条,以及业务建模文档 6.4 追加的 3
 * 条)。日志写法相关的编码纪律(8.2 第 6~9 条)拆到 {@link LoggingConventionArchitectureTest},两类规则关注点不同:
 * 前者管"谁能依赖谁",后者管"怎么写代码",混在一个文件里会让人分不清一条规则到底防的是架构腐化还是编码陋习。
 */
@AnalyzeClasses(
        packages = "com.landray.skillhub",
        importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    /** domain 是被依赖方,不依赖任何人。这是唯一不许破的规矩。 */
    @ArchTest
    static final ArchRule domainMustNotDependOnOuterLayers = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infrastructure..", "..interfaces..")
            .because("domain 不依赖任何外层,否则领域模型会被框架和存储细节绑架");

    /** domain 必须能脱离 Spring 单独跑单测,所以零框架依赖。 */
    @ArchTest
    static final ArchRule domainMustBePlainJava = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "org.apache.ibatis..", "tools.jackson..")
            .because("domain 必须是纯 Java,不含任何框架注解,才能脱离 Spring 快速单测");

    /** 事务边界只有一处,散开之后没人说得清一次请求到底几个事务。 */
    @ArchTest
    static final ArchRule transactionalOnlyInApplicationLayer = methods()
            .that()
            .areAnnotatedWith(Transactional.class)
            .should()
            .beDeclaredInClassesThat()
            .resideInAPackage("..application..")
            .because("事务边界统一在 application 层,不许下沉到领域服务或仓储实现");

    /** Mapper 是基础设施细节,泄漏出去等于分层白做。 */
    @ArchTest
    static final ArchRule mapperMustNotLeakOutOfInfrastructure = noClasses()
            .that()
            .resideOutsideOfPackage("..infrastructure..")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Mapper")
            .because("MyBatis Mapper 只许住在 infrastructure,上层只认 domain.repository 接口");

    /**
     * 全项目只用 Jackson 3:{@code com.fasterxml.jackson..} 下<b>除 {@code annotation} 子包外全部禁止</b>。
     *
     * <p><b>为什么放行 {@code annotation}</b>:{@code jackson-annotations} 模块刻意不随 Jackson 3 改坐标,仍在
     * {@code com.fasterxml.jackson.annotation} 下(这样同一个注解过的 DTO 能被 Jackson 2 和 3 都处理)。本项目的
     * {@code tools.jackson.core:jackson-databind:3.1.4} 自己就编译期依赖 {@code jackson-annotations:2.21}。所以
     * {@code @JsonInclude} / {@code @JsonProperty} 从这个包 import <b>是正确的,不是 Jackson 2 残留</b>。业务建模
     * 文档 6.4 早先写的"禁止任何类依赖 com.fasterxml.jackson.."若照字面实现会把这些合法注解一起误伤,已订正。
     *
     * <p><b>为什么用"全禁 + 一个例外"而不是逐个列举子包</b>:本规则曾只禁 {@code core..} 与 {@code databind..},
     * 而 {@code mvn dependency:tree} 实测 springdoc 3.1.0 一次就拖进四个 Jackson 2 制品,全是 compile scope:
     *
     * <pre>
     * springdoc-openapi-starter-webmvc-ui:3.1.0
     *   -&gt; -webmvc-api -&gt; -common -&gt; io.swagger.core.v3:swagger-core-jakarta:2.2.52
     *        +- com.fasterxml.jackson.core:jackson-databind:2.21.4
     *        |    \- com.fasterxml.jackson.core:jackson-core:2.21.4
     *        +- com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.4
     *        \- com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.4
     * </pre>
     *
     * 后两个不在 {@code core}/{@code databind} 里,枚举式的规则放它们过去。这不是理论风险:<b>解析 SKILL.md 的
     * frontmatter 就是 YAML</b>,而 {@code jackson-dataformat-yaml} 正躺在 compile classpath 上,谁要找 YAML
     * 解析器顺手 import {@code com.fasterxml.jackson.dataformat.yaml.YAMLFactory} 完全合理 —— 那就混进了 Jackson 2。
     * 改成"全禁 + 放行 annotation"后,将来任何三方库拖进任何新的 Jackson 2 模块都自动被覆盖,不用回来补名单。
     */
    @ArchTest
    static final ArchRule mustNotUseJacksonTwoProcessingEngine = noClasses()
            .should()
            .dependOnClassesThat(resideInAPackage("com.fasterxml.jackson..")
                    .and(not(resideInAPackage("com.fasterxml.jackson.annotation.."))))
            .because("全项目只用 Jackson 3(tools.jackson..)处理 JSON;"
                    + "com.fasterxml.jackson.. 下除 annotation 子包外全部属于 Jackson 2,一律禁止");

    /** domain 不碰 HTTP。挡的是 JDK 内建的 HttpClient,已有规则只禁了 org.springframework..,拦不住它。 */
    @ArchTest
    static final ArchRule domainMustNotUseHttpClient = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("java.net.http..", "org.springframework.web.client..")
            .because("domain 不碰 HTTP,任何对外调用都应由 infrastructure 实现、经 domain.repository 接口暴露;"
                    + "现有规则只禁了 org.springframework..,挡不住 JDK 内建的 java.net.http.HttpClient");

    /** OpenAPI 注解只许出现在 interfaces 层的 Controller / DTO 上,domain 不感知任何接口文档细节。 */
    @ArchTest
    static final ArchRule domainMustNotUseOpenApiAnnotations = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.swagger..", "org.springdoc..")
            .because("OpenAPI 注解(@Schema / @Operation 等)只服务于 HTTP 接口文档,属于 interfaces 层的关注点,"
                    + "domain 混入这些注解会让领域模型被文档工具绑架");

    /**
     * 整体分层校验,兜住上面几条没覆盖到的组合。
     *
     * <p>{@code withOptionalLayers(true)} 是必须的:项目按层逐步落地,某一层还没开始写时它是空的,
     * 默认行为会把"空层"当成违规报错。这条规则该在<b>有人违反依赖方向</b>时红,而不是在某层还没写时红 ——
     * 后者会逼着人去关掉规则,那才是真正的损失。
     *
     * <p>代价是:包名写错会让对应层静默变空、规则形同虚设。所以四个层的包名以第一节的目录结构为准,
     * 改包名时必须同步改这里。
     */
    @ArchTest
    static final ArchRule layersMustRespectDependencyDirection = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Interfaces")
            .definedBy("..interfaces..")
            .layer("Application")
            .definedBy("..application..")
            .layer("Domain")
            .definedBy("..domain..")
            .layer("Infrastructure")
            .definedBy("..infrastructure..")
            .whereLayer("Interfaces")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Application")
            .mayOnlyBeAccessedByLayers("Interfaces")
            .whereLayer("Infrastructure")
            .mayNotBeAccessedByAnyLayer();
}
