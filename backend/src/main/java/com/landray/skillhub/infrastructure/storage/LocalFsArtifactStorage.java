package com.landray.skillhub.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.landray.skillhub.domain.storage.ArtifactStorage;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;

/**
 * 落在本机(容器里是挂载卷)文件夹里的制品存储。
 *
 * <p>这个量级不需要对象存储:skill zip 几十 KB 到几 MB,总量几百 MB(根 CLAUDE.md 第十一节)。真需要时换成 S3
 * 标准 API 的实现,业务代码一行不动。
 */
@Component
public class LocalFsArtifactStorage implements ArtifactStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFsArtifactStorage.class);

    private final Path root;

    public LocalFsArtifactStorage(StorageProperties properties) {
        this.root = Path.of(properties.localRoot()).toAbsolutePath().normalize();

        // 启动时把**解析后的绝对路径**打出来,而不是只信任配置里那个字符串。
        //
        // dev 的默认值是相对路径 `./data/artifacts`,它跟着当前工作目录走 —— 从 backend/ 启动和从仓库根启动
        // 指向两个不同的目录。踩过一次:从仓库根启动,制品全部读不到,而表现只是详情页文件树空着
        // (读不到制品被降级成"清单为空",见 SkillApplicationService#getLatestVersionFileManifest),
        // 界面上跟"这个包本来就没文件"长得一模一样,查了半天才定位到工作目录。
        //
        // 一行 INFO 就能让这类问题当场自证,所以它不是噪音。同时打出目录存不存在:不存在不算错
        // (首次启动本来就还没有),但配合上面那条路径,足够让人一眼看出是不是启错了地方。
        log.info(
                "制品存储根目录已就绪, root={}, exists={}, configured={}",
                root,
                Files.isDirectory(root),
                properties.localRoot());
    }

    @Override
    public void store(String artifactKey, byte[] content) {
        Path target = resolve(artifactKey);
        try {
            Files.createDirectories(target.getParent());
            // 先写临时文件再原子移动:直接往目标写,进程在写一半时挂掉会留下一个大小对、内容截断的制品,
            // 而它的 sha256 校验要等到有人安装时才失败 —— 那时已经很难追到是哪次写入坏的。
            Path temp = Files.createTempFile(target.getParent(), ".partial-", ".tmp");
            try {
                Files.write(temp, content);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.deleteIfExists(temp);
                throw e;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("制品写入失败, artifactKey=" + artifactKey, e);
        }
        log.info("制品已写入, artifactKey={}, sizeBytes={}", artifactKey, content.length);
    }

    @Override
    public InputStream openRead(String artifactKey) {
        Path source = requireExisting(artifactKey);
        try {
            return Files.newInputStream(source);
        } catch (IOException e) {
            throw new UncheckedIOException("制品读取失败, artifactKey=" + artifactKey, e);
        }
    }

    @Override
    public void delete(String artifactKey) {
        Path target = resolve(artifactKey);
        try {
            boolean removed = Files.deleteIfExists(target);
            // 物理删除不可逆,留一条能被巡检看到的记录。removed=false 不是错误(见接口注释的幂等约定),
            // 但它值得区分:调用方以为在删一个存在的制品,实际什么都没删。
            log.info("制品已删除, artifactKey={}, removed={}", artifactKey, removed);
        } catch (IOException e) {
            throw new UncheckedIOException("制品删除失败, artifactKey=" + artifactKey, e);
        }
    }

    @Override
    public boolean exists(String artifactKey) {
        return Files.isRegularFile(resolve(artifactKey));
    }

    @Override
    public long sizeOf(String artifactKey) {
        Path source = requireExisting(artifactKey);
        try {
            return Files.size(source);
        } catch (IOException e) {
            throw new UncheckedIOException("读取制品大小失败, artifactKey=" + artifactKey, e);
        }
    }

    private Path requireExisting(String artifactKey) {
        Path source = resolve(artifactKey);
        if (!Files.isRegularFile(source)) {
            throw new ResourceNotFoundException("ARTIFACT_NOT_FOUND", "制品不存在, artifactKey=" + artifactKey);
        }
        return source;
    }

    /**
     * 把 key 解析成根目录下的真实路径,并挡住一切越界。
     *
     * <p><b>不靠"key 是我们自己生成的"来省掉这一步。</b>校验后仍然用 {@code startsWith(root)} 兜底,是因为字符级
     * 的黑名单永远可能被某种编码绕过,而"解析完的绝对路径必须在根目录下"是个不依赖具体绕法的判定。这个项目分发的
     * 是会被 Agent 执行的内容,制品落盘是最后一道闸。
     */
    private Path resolve(String artifactKey) {
        if (artifactKey == null || artifactKey.isBlank()) {
            throw new IllegalArgumentException("artifactKey 不能为空");
        }
        if (artifactKey.contains("..")) {
            throw new IllegalArgumentException("artifactKey 不许含相对路径片段, artifactKey=" + artifactKey);
        }
        if (artifactKey.startsWith("/") || artifactKey.startsWith("\\") || artifactKey.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("artifactKey 不许是绝对路径, artifactKey=" + artifactKey);
        }
        if (artifactKey.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("artifactKey 不许含控制字符");
        }

        Path resolved = root.resolve(artifactKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("artifactKey 解析后越出制品根目录, artifactKey=" + artifactKey);
        }
        return resolved;
    }
}
