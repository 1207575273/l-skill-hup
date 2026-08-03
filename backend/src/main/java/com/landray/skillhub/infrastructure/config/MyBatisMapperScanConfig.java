package com.landray.skillhub.infrastructure.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Mapper 扫描范围。Mapper 只允许住在 infrastructure 层,由 ArchUnit 规则
 * {@code mapperMustNotLeakOutOfInfrastructure} 守住不外泄。
 *
 * <p><b>刻意不放在 {@code SkillHubApplication} 上</b>(2026-08-03 曾经这么放过,已用
 * {@code @WebMvcTest} 实测证伪)。{@code @WebMvcTest} 把 {@code SkillHubApplication} 当作根配置类直接处理,
 * 根配置类自身携带的注解(包括 {@code @MapperScan})不受 {@code @WebMvcTest} 的类型过滤器管辖——过滤器只对
 * “被组件扫描发现的类”生效,不对根配置类本身生效。后果是任何一个 {@code @WebMvcTest} 切片都会尝试实例化全部
 * Mapper bean,而切片上下文里根本没有 {@code sqlSessionFactory},直接报
 * {@code Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required} 启动失败。
 *
 * <p>把 {@code @MapperScan} 挪到这个独立的 {@code @Configuration} 类之后,它就变成"被组件扫描发现的普通配置类"
 * ——{@code @WebMvcTest} 的类型过滤器会把它排除掉(不是 {@code @Controller}/{@code @ControllerAdvice}/
 * {@code WebMvcConfigurer} 等切片测试认可的类型),Mapper bean 因此不会被尝试创建;而 {@code @SpringBootTest}
 * 与生产启动走的是完整组件扫描,行为不变。
 */
@Configuration
@MapperScan("com.landray.skillhub.infrastructure.persistence.mapper")
public class MyBatisMapperScanConfig {}
