package com.landray.skillhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * skill-hub 启动类。
 *
 * <p>Mapper 扫描<b>刻意不</b>放在这里,见 {@code infrastructure.config.MyBatisMapperScanConfig} 的类注释——
 * 放在根配置类上会被 {@code @WebMvcTest} 切片全部继承到,导致每个切片测试都要实例化 Mapper bean。
 *
 * <p>{@code @ConfigurationPropertiesScan} 让 {@code @ConfigurationProperties} 类(如
 * {@code application.auth.AuthProperties})自动注册为 bean,不用每加一个配置类就手动
 * {@code @EnableConfigurationProperties} 登记一次。它不依赖任何外部资源(只读配置值),不会重现
 * {@code @MapperScan} 那个问题,留在根配置类上没有风险。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SkillHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillHubApplication.class, args);
    }
}
