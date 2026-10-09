package com.landray.skillhub.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code skillhub.storage.s3.*} 配置,只在 {@code skillhub.storage.type=s3} 时被
 * {@link S3ArtifactStorage} 消费。
 *
 * <p><b>为什么独立成一个 record,而不是塞进 {@link StorageProperties} 当嵌套字段</b>:那样
 * {@code LocalFsArtifactStorage} 的构造参数里会凭空多出一坨它永远用不到的对象存储配置,
 * 本地实现不该知道 S3 的存在。按配置前缀一个 record,两个实现各取所需,互不感知。
 *
 * <p><b>这里刻意不加 {@code @NotNull} 校验。</b>{@code @ConfigurationPropertiesScan} 会无条件创建这个
 * bean,{@code type=local} 时整个 {@code skillhub.storage.s3} 前缀是缺席的,绑出来就是一堆 null ——
 * 那是正常状态,不该让应用启动失败。必填项的校验放在 {@link S3ArtifactStorage} 的构造函数里:
 * 那个 bean 只在真的选了 s3 时才创建,校验失败即启动失败,位置正好。
 *
 * @param endpoint S3 兼容端点。阿里云 OSS 要用带 {@code s3.} 前缀的兼容端点
 *     (如 {@code https://s3.oss-cn-hongkong.aliyuncs.com}),不是控制台里那个 {@code oss-cn-*} 域名;
 *     也<b>不能</b>用 {@code -internal} 内网端点 —— 那个只在同地域阿里云 ECS 内可解析,
 *     本项目部署在公司内网机器上,实测 DNS 能解析但连不通。
 * @param region SigV4 签名用的 region 字符串。阿里云 OSS 的 S3 兼容层取 {@code aws-global}
 *     (已对真 OSS 实测:{@code cn-hongkong} / {@code oss-cn-hongkong} 都不通过签名校验)。
 * @param bucket 桶名
 * @param keyPrefix 所有 key 的统一前缀,用来在**共用一个桶**的前提下隔开环境与用途
 *     (如 {@code skillhub-dev/} / {@code skillhub-prod/})。末尾斜杠可省,构造时会补齐。
 *     留空表示直接落在桶根下 —— 只有独占整个桶时才该这么配。
 * @param accessKey AccessKeyId,由环境变量注入,不写进 yml
 * @param secretKey AccessKeySecret,由环境变量注入,不写进 yml
 * @param pathStyleAccess true=path-style({@code endpoint/bucket/key}),false=virtual-hosted
 *     ({@code bucket.endpoint/key})。阿里云 OSS 用 false;MinIO 之类没配通配符 DNS 的要 true。
 *     默认 false。
 */
@ConfigurationProperties(prefix = "skillhub.storage.s3")
public record S3StorageProperties(
        String endpoint,
        String region,
        String bucket,
        String keyPrefix,
        String accessKey,
        String secretKey,
        boolean pathStyleAccess) {}
