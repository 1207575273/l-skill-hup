package com.landray.skillhub.infrastructure.storage;

import java.io.InputStream;
import java.net.URI;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.landray.skillhub.domain.storage.ArtifactStorage;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 落在 S3 兼容对象存储里的制品存储。当前线上打的是阿里云 OSS 的 S3 兼容端点。
 *
 * <p><b>只用 S3 标准 API,不引阿里云官方 SDK</b> —— 这是 {@code domain.storage.ArtifactStorage} 接口注释
 * 定下的调子("将来真需要对象存储时只写 S3 标准 API,不碰厂商特有接口")。代价是要自己摆平下面几个兼容开关;
 * 换来的是 MinIO / RustFS / 自建 S3 只需改 endpoint 与 path-style 两项配置,代码一行不动,而且不用为了
 * JDK 21 去给官方 SDK 补三个 {@code javax.xml.bind} 依赖(JAXB 自 JDK 11 已移出 JDK)。
 *
 * <p><b>与本地实现的两处行为差异,都是协议层面的,不是偷懒:</b>
 *
 * <ol>
 *   <li><b>写入不需要"临时文件 + 原子移动"那一套</b>。本地实现要那么做,是因为直接往目标写、进程中途挂掉会
 *       留下一个大小对、内容截断的制品。S3 的 PutObject 本身就是原子的:要么整个对象可见,要么完全不可见,
 *       不存在"写一半的对象"。所以这里直接 put,不是漏了一道防护。
 *   <li><b>删除不报告"到底删掉了没有"</b>。本地实现能打 {@code removed=true/false};S3 对删除不存在的 key
 *       同样返 204,协议不回传这个信息。接口约定的幂等反而因此天然满足,不用额外判断。
 * </ol>
 */
@Component
@ConditionalOnProperty(name = "skillhub.storage.type", havingValue = "s3")
public class S3ArtifactStorage implements ArtifactStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ArtifactStorage.class);

    private static final String NOT_FOUND_CODE = "ARTIFACT_NOT_FOUND";

    private final S3Client s3;
    private final String bucket;
    private final String keyPrefix;

    public S3ArtifactStorage(S3StorageProperties properties) {
        this.bucket = required(properties.bucket(), "bucket");
        this.keyPrefix = normalizePrefix(properties.keyPrefix());
        String endpoint = required(properties.endpoint(), "endpoint");
        String region = required(properties.region(), "region");
        String accessKey = required(properties.accessKey(), "access-key");
        String secretKey = required(properties.secretKey(), "secret-key");

        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccess())
                        // 阿里云 OSS 不支持 aws-chunked 传输编码,不关掉 PutObject 直接报
                        // "InvalidArgument: aws-chunked encoding is not supported"。
                        // 对支持 chunked 的实现(MinIO 等)关掉它也无害,所以不做成开关。
                        .chunkedEncodingEnabled(false)
                        .build())
                // AWS SDK 2.30.0 起默认给上传算 CRC32 校验和并走 aws-chunked trailer,而那是 AWS 自家扩展,
                // OSS / MinIO 等兼容实现不认,表现为 PutObject 直接失败。置成 WHEN_REQUIRED 即
                // "只在 API 强制要求时才算",是官方文档给第三方兼容存储的做法。
                // 【勿删】这两行看着像多余配置,删掉后一旦升级 SDK 必炸,而且错在上传链路、不在启动期。
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();

        // 与本地实现一样,启动时把**解析后**的真实落点打出来,而不是只信任配置里那个字符串。
        // 制品读不到的表现是详情页文件树空着(被降级成"清单为空"),跟"这个包本来就没文件"长得一模一样;
        // 一行 INFO 能让"是不是连到了别的桶 / 别的前缀"当场自证。凭证不打。
        log.info(
                "制品存储根已就绪(对象存储), endpoint={}, bucket={}, keyPrefix={}, pathStyleAccess={}",
                endpoint,
                bucket,
                keyPrefix.isEmpty() ? "<桶根>" : keyPrefix,
                properties.pathStyleAccess());
    }

    @PreDestroy
    void close() {
        s3.close();
    }

    @Override
    public void store(String artifactKey, byte[] content) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey(artifactKey))
                        .contentLength((long) content.length)
                        .build(),
                RequestBody.fromBytes(content));
        log.info("制品已写入, artifactKey={}, sizeBytes={}", artifactKey, content.length);
    }

    @Override
    public InputStream openRead(String artifactKey) {
        try {
            return s3.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey(artifactKey))
                    .build());
        } catch (S3Exception e) {
            throw translateNotFound(e, artifactKey);
        }
    }

    @Override
    public void delete(String artifactKey) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey(artifactKey))
                .build());
        // 物理删除不可逆,留一条能被巡检看到的记录。与本地实现不同,这里没有 removed 标记:
        // 见类注释第 2 条,S3 删除不存在的 key 同样返 204,协议不回传这个信息。
        log.info("制品已删除, artifactKey={}", artifactKey);
    }

    @Override
    public boolean exists(String artifactKey) {
        try {
            headObject(artifactKey);
            return true;
        } catch (S3Exception e) {
            if (isNotFound(e)) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public long sizeOf(String artifactKey) {
        try {
            return headObject(artifactKey).contentLength();
        } catch (S3Exception e) {
            throw translateNotFound(e, artifactKey);
        }
    }

    private HeadObjectResponse headObject(String artifactKey) {
        return s3.headObject(HeadObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey(artifactKey))
                .build());
    }

    /**
     * 把 S3 的"对象不存在"翻成接口约定的 {@link ResourceNotFoundException};其余错误原样上抛。
     *
     * <p><b>按 HTTP 404 判,而不是只 catch {@code NoSuchKeyException}</b>:AWS 自家 S3 会回标准的
     * {@code NoSuchKey} 错误码,SDK 据此抛出具体子类异常;而兼容实现在某些操作上(典型是 HeadObject,
     * 它按协议没有响应体)只给一个裸 404,SDK 只能抛泛化的 {@code S3Exception}。只认前者,会让
     * "对象不存在"在 OSS 上漏成 500 —— 而接口约定的是读取缺失 key 必须抛 ResourceNotFound。
     */
    private RuntimeException translateNotFound(S3Exception e, String artifactKey) {
        if (isNotFound(e)) {
            return new ResourceNotFoundException(NOT_FOUND_CODE, "制品不存在, artifactKey=" + artifactKey);
        }
        return e;
    }

    private boolean isNotFound(S3Exception e) {
        return e.statusCode() == 404;
    }

    /** 业务 key 拼成真实对象 key。校验走与本地实现共用的 {@link ArtifactKeyGuard},两边不会漂。 */
    private String objectKey(String artifactKey) {
        return keyPrefix + ArtifactKeyGuard.requireSafe(artifactKey);
    }

    /**
     * 前缀归一化:去掉开头的斜杠、补上结尾的斜杠。
     *
     * <p>少一个结尾斜杠会让 {@code skillhub-dev} 这种配置把对象写成 {@code skillhub-devcode-review/...},
     * 悄无声息地落到一个相邻的伪前缀下 —— 不报错,但数据进错地方,是最难查的一类问题。所以这里补齐,
     * 而不是要求配置的人自己记得写。前缀同样过 {@code ..} 检查:它来自配置不是用户输入,但配置写错一样会越界。
     */
    private static String normalizePrefix(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String prefix = raw.strip();
        while (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }
        if (prefix.contains("..")) {
            throw new IllegalStateException("skillhub.storage.s3.key-prefix 不许含相对路径片段, keyPrefix=" + raw);
        }
        if (prefix.isEmpty()) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    /**
     * 必填项缺失直接让应用启动失败,不带着空配置跑起来。
     *
     * <p>与 {@code docker/.env.example} 里"没有默认值的项漏配时在启动最早期失败"的约定一致:带着空 AK 起来,
     * 错会推迟到第一个人上传 skill 时才暴露,那时查起来远不如启动即失败便宜。
     *
     * <p>刻意<b>不</b>在这里顺手探测桶是否存在。"桶名写错"与"网络抖一下"在一次 HeadBucket 失败里长得一模一样,
     * 为前者让应用启动失败,代价是后者会把一次普通重启变成一场故障。桶错会在第一次上传时以明确的
     * NoSuchBucket 报出来,那个信号已经足够清楚。
     */
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "skillhub.storage.type=s3 时 skillhub.storage.s3." + name + " 必填,当前为空");
        }
        return value;
    }
}
