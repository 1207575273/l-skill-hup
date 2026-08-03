package com.landray.skillhub.application.upload;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.landray.skillhub.domain.model.UploadLimits;

/**
 * {@code skillhub.upload.*} 配置(业务建模文档 5.2)。
 *
 * <p>放在 application 而不是 infrastructure/config,理由与 {@code AuthProperties} 完全一致(见其类注释):
 * {@code @ConfigurationProperties} 是 Spring 注解,但它承载的是"解包上限"这条业务规则的具体数值,被
 * {@link SkillUploadApplicationService} 直接消费。真正需要保持零框架依赖的是
 * {@link com.landray.skillhub.domain.service.SkillPackageValidator},它只认
 * {@link UploadLimits}——一个纯 Java record,{@link #toLimits()} 负责把这份 Spring 配置转换成那个纯粹的值
 * 对象,这个转换动作本身就是"application 层把外部配置接成 domain 能理解的形状"的职责所在。
 */
@ConfigurationProperties(prefix = "skillhub.upload")
public record UploadProperties(
        long maxUnpackedBytes,
        int maxEntryCount,
        long maxFileBytes,
        int maxPathDepth,
        int maxPathLength,
        int maxCompressionRatio) {

    public UploadLimits toLimits() {
        return new UploadLimits(maxUnpackedBytes, maxEntryCount, maxFileBytes, maxPathDepth, maxPathLength, maxCompressionRatio);
    }
}
