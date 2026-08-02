package com.landray.skillhub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * skill-hub 启动类。
 *
 * <p>Mapper 只允许住在 infrastructure 层,扫描范围写死于此,由 ArchUnit 规则守住不外泄。
 */
@SpringBootApplication
@MapperScan("com.landray.skillhub.infrastructure.persistence.mapper")
public class SkillHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillHubApplication.class, args);
    }
}
