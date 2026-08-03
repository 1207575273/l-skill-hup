package com.landray.skillhub.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code skillhub.storage.*} 配置。
 *
 * <p>放在 infrastructure 而不是 application(与 {@code AuthProperties} 的位置不同):它承载的是"制品落在哪个目录"
 * 这种纯基础设施细节,只被 {@link LocalFsArtifactStorage} 消费,application 层看不见也不该看见 —— 上层只认
 * {@code domain.storage.ArtifactStorage} 接口。
 *
 * <p>{@code type} 目前只有 {@code local} 一个取值,留着是为了将来切对象存储时有个显式开关,而不是靠"换了哪个 jar"
 * 来决定行为。通过 {@code SkillHubApplication} 上的 {@code @ConfigurationPropertiesScan} 自动注册。
 */
@ConfigurationProperties(prefix = "skillhub.storage")
public record StorageProperties(String type, String localRoot) {}
