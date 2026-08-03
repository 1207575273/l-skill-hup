package com.landray.skillhub.domain.model;

/**
 * skill 包解包上限,七项数值全部走配置(业务建模文档 5.2)。
 *
 * <p>刻意是纯 Java record,不直接携带 {@code @ConfigurationProperties}——那是 Spring 注解,domain 不能依赖。
 * {@code application.upload.UploadProperties} 负责把 {@code skillhub.upload.*} 配置转换成这个值对象,
 * {@link com.landray.skillhub.domain.service.SkillPackageValidator} 只认这个纯粹的数字集合。
 *
 * <p>不含"上传体积(multipart)"——那一项由 {@code spring.servlet.multipart.max-file-size} 管,卡的是"上传的
 * zip 本身多大";这里七项里的六项(不含 multipart 那项)卡的是"解压后能有多大",职责不同,不混进同一个配置块。
 */
public record UploadLimits(
        long maxUnpackedBytes,
        int maxEntryCount,
        long maxFileBytes,
        int maxPathDepth,
        int maxPathLength,
        int maxCompressionRatio) {}
