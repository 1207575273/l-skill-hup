package com.landray.skillhub.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.landray.skillhub.shared.exception.ResourceNotFoundException;

/**
 * {@link S3ArtifactStorage} 打**真的阿里云 OSS**。
 *
 * <p><b>为什么不起 MinIO 容器</b>:本机与内网构建机的 docker 都连不上 Docker Hub(实测连项目已有的
 * {@code postgres:17-alpine} 都拉不下来,CloudFront 直接 EOF),容器化的 S3 兼容件在这个环境里根本起不来。
 * 而 S3 兼容性恰恰是这个实现最该被验证的部分 —— 签名版本、path-style、chunked encoding、checksum 这四个
 * 开关任何一个配错都只会在真实服务端上暴露,MinIO 过了也不代表 OSS 过。所以这里直接打真 OSS。
 *
 * <p><b>代价是三条纪律,缺一不可:</b>
 *
 * <ol>
 *   <li><b>没凭证就整类跳过</b>,不假红。别人克隆下来不配 AK 也能跑完整个测试套件。
 *   <li><b>写进一个每次运行都不同的随机子前缀</b>({@code skillhub-dev/__it__/<uuid>/}),绝不碰 dev / prod
 *       真实制品所在的前缀。桶是和 share/ 等别的用途共用的,这条不是洁癖。
 *   <li><b>每个用例写了什么就删什么</b>,不靠人记得清理,也不依赖 ListObjects 权限。
 * </ol>
 *
 * <p>越界 key 的各种形态在 {@link ArtifactKeyGuardTest} 里测透了,这里只留一条 delete 的,用来证明
 * "这个实现确实把 guard 接上了"——接口注释特别点出删除漏校验等于开了个用删除做目录穿越的口子。
 */
@EnabledIfEnvironmentVariable(
        named = "OSS_ACCESS_KEY_ID",
        matches = ".+",
        disabledReason = "未配置 OSS_ACCESS_KEY_ID / OSS_SECRET_ACCESS_KEY,跳过真实对象存储测试")
class S3ArtifactStorageTest {

    /** 与 application-dev.yml 保持一致;允许用环境变量覆盖,换区/换桶时不用改代码。 */
    private static final String ENDPOINT =
            envOrDefault("OSS_S3_ENDPOINT", "https://s3.oss-cn-hongkong.aliyuncs.com");

    private static final String REGION = envOrDefault("OSS_S3_REGION", "aws-global");

    private static final String BUCKET = envOrDefault("OSS_BUCKET", "nodetimetop");

    /**
     * 本次运行独占的前缀。
     *
     * <p>落在 {@code skillhub-dev/} 之下而不是另开一个顶层前缀:环境治理就按 skillhub-dev / skillhub-prod
     * 两个前缀走,测试数据属于 dev 这一侧。{@code __it__/<uuid>} 这一层保证它既不会压到 dev 的真实制品,
     * 也不会在两次并发运行之间互相干扰。
     */
    private static String runPrefix;

    private static S3ArtifactStorage storage;

    /** 本用例写进去的 key,@AfterEach 逐个删掉。delete 是幂等的,所以多删一次也无所谓。 */
    private final List<String> written = new ArrayList<>();

    @BeforeAll
    static void initStorage() {
        runPrefix = "skillhub-dev/__it__/" + UUID.randomUUID() + "/";
        storage = new S3ArtifactStorage(propertiesWithPrefix(runPrefix));
    }

    @AfterAll
    static void closeStorage() {
        if (storage != null) {
            storage.close();
        }
    }

    @AfterEach
    void removeWritten() {
        written.forEach(storage::delete);
        written.clear();
    }

    @Test
    void should_roundTripContent_whenStoredThenRead() throws IOException {
        byte[] content = "zip-bytes".getBytes(StandardCharsets.UTF_8);

        store("code-review/1.0.0/a.zip", content);

        try (InputStream in = storage.openRead("code-review/1.0.0/a.zip")) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void should_overwrite_whenSameKeyStoredTwice() throws IOException {
        store("x.zip", "old".getBytes(StandardCharsets.UTF_8));

        store("x.zip", "new".getBytes(StandardCharsets.UTF_8));

        try (InputStream in = storage.openRead("x.zip")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("new");
        }
    }

    @Test
    void should_reportSizeAndExistence_whenArtifactStored() {
        store("x.zip", new byte[] {1, 2, 3, 4, 5});

        assertThat(storage.exists("x.zip")).isTrue();
        assertThat(storage.sizeOf("x.zip")).isEqualTo(5L);
    }

    @Test
    void should_reportNotExists_whenKeyNeverStored() {
        // 关键回归点:HeadObject 按协议没有响应体,OSS 对缺失对象只回一个裸 404,
        // SDK 抛的是泛化的 S3Exception 而不是 NoSuchKeyException。只 catch 后者会让这里直接炸。
        assertThat(storage.exists("never-stored.zip")).isFalse();
    }

    @Test
    void should_throwResourceNotFound_whenReadingMissingKey() {
        assertThatThrownBy(() -> storage.openRead("missing.zip"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing.zip");
    }

    @Test
    void should_throwResourceNotFound_whenSizingMissingKey() {
        assertThatThrownBy(() -> storage.sizeOf("missing.zip")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_removeArtifact_whenDeleted() {
        store("code-review/1.0.0/a.zip", new byte[] {1});

        storage.delete("code-review/1.0.0/a.zip");

        assertThat(storage.exists("code-review/1.0.0/a.zip")).isFalse();
    }

    @Test
    void should_notThrow_whenDeletingMissingKey() {
        // 幂等:S3 对删除不存在的 key 同样返 204,接口要的幂等天然满足。
        // 这条用例守的是"将来没人画蛇添足地先 head 一下再删、顺手把不存在改成抛异常"。
        storage.delete("never-stored.zip");

        assertThat(storage.exists("never-stored.zip")).isFalse();
    }

    @Test
    void should_rejectKey_whenDeletingWithTraversal() {
        // 证明 delete 确实接了 ArtifactKeyGuard。各形态本身在 ArtifactKeyGuardTest 里测。
        assertThatThrownBy(() -> storage.delete("../escaped.zip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("相对路径");
    }

    @Test
    void should_isolateArtifacts_whenTwoPrefixesShareOneBucket() {
        // 环境治理整个押在前缀上:dev 与 prod 共用 nodetimetop 这一个桶,只靠 key-prefix 互不可见。
        // 这条用例守的就是那个前提 —— 前缀要是没真正拼进对象 key,两个环境会直接串台。
        S3ArtifactStorage other = new S3ArtifactStorage(propertiesWithPrefix(runPrefix + "other/"));
        try {
            store("shared-name.zip", new byte[] {1});

            assertThat(storage.exists("shared-name.zip")).isTrue();
            assertThat(other.exists("shared-name.zip")).isFalse();
        } finally {
            other.close();
        }
    }

    @Test
    void should_resolveToSameObject_whenPrefixConfiguredWithoutTrailingSlash() {
        // key-prefix 漏写结尾斜杠不该把对象悄悄挪到一个相邻的伪前缀下(skillhub-devcode-review/...):
        // 那种错不报任何异常,只是数据进错地方,最难查。构造时补齐斜杠的行为由这条用例锁住。
        String withoutSlash = runPrefix.substring(0, runPrefix.length() - 1);
        S3ArtifactStorage sloppy = new S3ArtifactStorage(propertiesWithPrefix(withoutSlash));
        try {
            store("slash-test.zip", new byte[] {7});

            assertThat(sloppy.exists("slash-test.zip")).isTrue();
            assertThat(sloppy.sizeOf("slash-test.zip")).isEqualTo(1L);
        } finally {
            sloppy.close();
        }
    }

    private void store(String artifactKey, byte[] content) {
        storage.store(artifactKey, content);
        written.add(artifactKey);
    }

    private static S3StorageProperties propertiesWithPrefix(String keyPrefix) {
        return new S3StorageProperties(
                ENDPOINT,
                REGION,
                BUCKET,
                keyPrefix,
                System.getenv("OSS_ACCESS_KEY_ID"),
                System.getenv("OSS_SECRET_ACCESS_KEY"),
                false);
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
