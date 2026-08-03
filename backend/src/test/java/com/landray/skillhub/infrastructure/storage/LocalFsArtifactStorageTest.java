package com.landray.skillhub.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.landray.skillhub.shared.exception.ResourceNotFoundException;

/**
 * {@link LocalFsArtifactStorage} 的行为与安全边界。
 *
 * <p>安全用例不是附属品(backend/CLAUDE.md 8.3):涉及落盘的代码只写正常路径不算写完。这里逐条验的是
 * {@code artifactKey} 越界的各种形态 —— 制品落盘是"会被 Agent 执行的内容"进入本机之前的最后一道闸。
 */
class LocalFsArtifactStorageTest {

    @TempDir
    Path tempRoot;

    private LocalFsArtifactStorage storageAt(Path root) {
        return new LocalFsArtifactStorage(new StorageProperties("local", root.toString()));
    }

    @Test
    void should_roundTripContent_whenStoredThenRead() throws IOException {
        LocalFsArtifactStorage storage = storageAt(tempRoot);
        byte[] content = "zip-bytes".getBytes(StandardCharsets.UTF_8);

        storage.store("code-review/1.0.0.zip", content);

        try (InputStream in = storage.openRead("code-review/1.0.0.zip")) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void should_createMissingParentDirectories_whenKeyHasNestedPath() {
        LocalFsArtifactStorage storage = storageAt(tempRoot);

        storage.store("a/b/c/artifact.zip", new byte[] {1, 2, 3});

        assertThat(Files.isRegularFile(tempRoot.resolve("a/b/c/artifact.zip"))).isTrue();
    }

    @Test
    void should_overwrite_whenSameKeyStoredTwice() throws IOException {
        LocalFsArtifactStorage storage = storageAt(tempRoot);
        storage.store("x.zip", "old".getBytes(StandardCharsets.UTF_8));

        storage.store("x.zip", "new".getBytes(StandardCharsets.UTF_8));

        try (InputStream in = storage.openRead("x.zip")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("new");
        }
    }

    @Test
    void should_leaveNoTempFile_whenStoreCompletes() throws IOException {
        LocalFsArtifactStorage storage = storageAt(tempRoot);

        storage.store("x.zip", new byte[] {1});

        try (var entries = Files.list(tempRoot)) {
            assertThat(entries.map(p -> p.getFileName().toString()))
                    .as("先写临时文件再原子移动,完成后不该留下 .partial-*")
                    .containsExactly("x.zip");
        }
    }

    @Test
    void should_reportSizeAndExistence_whenArtifactStored() {
        LocalFsArtifactStorage storage = storageAt(tempRoot);
        storage.store("x.zip", new byte[] {1, 2, 3, 4, 5});

        assertThat(storage.exists("x.zip")).isTrue();
        assertThat(storage.sizeOf("x.zip")).isEqualTo(5L);
    }

    @Test
    void should_reportNotExists_whenKeyNeverStored() {
        assertThat(storageAt(tempRoot).exists("missing.zip")).isFalse();
    }

    @Test
    void should_throwResourceNotFound_whenReadingMissingKey() {
        LocalFsArtifactStorage storage = storageAt(tempRoot);

        assertThatThrownBy(() -> storage.openRead("missing.zip"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing.zip");
    }

    @Test
    void should_throwResourceNotFound_whenSizingMissingKey() {
        assertThatThrownBy(() -> storageAt(tempRoot).sizeOf("missing.zip"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_rejectKey_whenContainingParentTraversal() {
        LocalFsArtifactStorage storage = storageAt(tempRoot);

        assertThatThrownBy(() -> storage.store("../escaped.zip", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("相对路径");
    }

    @Test
    void should_rejectKey_whenTraversalHiddenInMiddle() {
        LocalFsArtifactStorage storage = storageAt(tempRoot);

        // 归一化之后仍在根目录内,但依然拒绝:允许它就等于承认"只要最终落点没越界就行",
        // 而那个前提一旦被后续改动打破,这里不会有任何提示。
        assertThatThrownBy(() -> storage.store("a/../b.zip", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_rejectKey_whenAbsoluteUnixPath() {
        assertThatThrownBy(() -> storageAt(tempRoot).store("/etc/passwd", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("绝对路径");
    }

    @Test
    void should_rejectKey_whenAbsoluteWindowsPath() {
        assertThatThrownBy(() -> storageAt(tempRoot).store("C:\\Windows\\evil.zip", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("绝对路径");
    }

    @Test
    void should_rejectKey_whenContainingControlCharacter() {
        assertThatThrownBy(() -> storageAt(tempRoot).store("a\nb.zip", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("控制字符");
    }

    @Test
    void should_rejectKey_whenBlank() {
        assertThatThrownBy(() -> storageAt(tempRoot).store("   ", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
