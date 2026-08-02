package com.landray.skillhub.architecture;

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

    /** 整体分层校验,兜住上面几条没覆盖到的组合。 */
    @ArchTest
    static final ArchRule layersMustRespectDependencyDirection = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
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
