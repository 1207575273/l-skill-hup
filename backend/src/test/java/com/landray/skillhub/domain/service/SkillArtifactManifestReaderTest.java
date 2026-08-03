package com.landray.skillhub.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.landray.skillhub.domain.model.SkillVersionFile;

/**
 * {@link SkillArtifactManifestReader} 只做一件纯函数式的事:给一份合法的 zip 字节流,解析出文件清单。
 * 存储层失败要不要降级不是本类的职责(见类注释),那部分行为在
 * {@code SkillApplicationServiceTest} 里覆盖。
 */
class SkillArtifactManifestReaderTest {

    private final SkillArtifactManifestReader reader = new SkillArtifactManifestReader();

    @Test
    @DisplayName("剥离所有条目共享的顶层包裹目录,按 path 字典序返回")
    void should_stripCommonTopDirectoryAndSortByPath_whenAllEntriesShareSamePrefix() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("code-review/SKILL.md", "skill md".getBytes(StandardCharsets.UTF_8));
        entries.put("code-review/references/guide.md", "guide".getBytes(StandardCharsets.UTF_8));
        InputStream zip = buildZipStream(entries);

        List<SkillVersionFile> files = reader.read(zip);

        assertThat(files)
                .containsExactly(
                        new SkillVersionFile("SKILL.md", 8L),
                        new SkillVersionFile("references/guide.md", 5L));
    }

    @Test
    @DisplayName("条目不共享同一个顶层目录时,不剥离任何前缀")
    void should_keepOriginalPath_whenEntriesDoNotShareCommonTopDirectory() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("SKILL.md", "root file".getBytes(StandardCharsets.UTF_8));
        entries.put("references/guide.md", "guide".getBytes(StandardCharsets.UTF_8));
        InputStream zip = buildZipStream(entries);

        List<SkillVersionFile> files = reader.read(zip);

        assertThat(files)
                .containsExactly(
                        new SkillVersionFile("SKILL.md", 9L), new SkillVersionFile("references/guide.md", 5L));
    }

    @Test
    @DisplayName("目录条目被跳过,清单里只有文件")
    void should_skipDirectoryEntries() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry("code-review/references/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("code-review/SKILL.md"));
            zos.write("skill md".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        List<SkillVersionFile> files = reader.read(new ByteArrayInputStream(buffer.toByteArray()));

        assertThat(files).containsExactly(new SkillVersionFile("SKILL.md", 8L));
    }

    @Test
    @DisplayName("空 zip 返回空清单,不是 null")
    void should_returnEmptyList_whenZipHasNoEntries() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            // 不写任何条目
        }

        List<SkillVersionFile> files = reader.read(new ByteArrayInputStream(buffer.toByteArray()));

        assertThat(files).isEmpty();
    }

    @Test
    @DisplayName("不是合法 zip 时抛出 UncheckedIOException,不静默吞掉")
    void should_throwUncheckedIOException_whenBytesAreNotValidZip() {
        InputStream notAZip = new ByteArrayInputStream("这不是一个 zip 文件".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> reader.read(notAZip)).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    @DisplayName("size 字段为负数(未知)时按 0 处理,不报错——中央目录理论上不会出现,但防御性兜底")
    void should_normalizeNegativeSizeToZero() {
        assertThat(SkillArtifactManifestReader.sizeOrZero(-1L)).isZero();
        assertThat(SkillArtifactManifestReader.sizeOrZero(42L)).isEqualTo(42L);
    }

    // ------------------------------------------------------------------
    // readFileContent:按剥壳后的相对路径精确匹配读取单个文件的原始字节
    // ------------------------------------------------------------------

    @Test
    @DisplayName("按剥壳后的相对路径精确匹配时返回该条目的原始字节")
    void should_returnFileContent_whenRelativePathMatchesStrippedEntry() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("code-review/SKILL.md", "skill md".getBytes(StandardCharsets.UTF_8));
        entries.put("code-review/references/guide.md", "guide content".getBytes(StandardCharsets.UTF_8));
        InputStream zip = buildZipStream(entries);

        Optional<byte[]> content = reader.readFileContent(zip, "references/guide.md");

        assertThat(content).isPresent();
        assertThat(new String(content.get(), StandardCharsets.UTF_8)).isEqualTo("guide content");
    }

    @Test
    @DisplayName("请求的相对路径在清单里不存在时返回空,不抛异常")
    void should_returnEmpty_whenRelativePathNotInManifest() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("code-review/SKILL.md", "skill md".getBytes(StandardCharsets.UTF_8));
        InputStream zip = buildZipStream(entries);

        Optional<byte[]> content = reader.readFileContent(zip, "not-exist.md");

        assertThat(content).isEmpty();
    }

    @Test
    @DisplayName("不共享顶层目录时,readFileContent 用原始条目名精确匹配")
    void should_matchOriginalPath_whenNoCommonTopDirectory() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("SKILL.md", "root file".getBytes(StandardCharsets.UTF_8));
        entries.put("references/guide.md", "guide".getBytes(StandardCharsets.UTF_8));
        InputStream zip = buildZipStream(entries);

        Optional<byte[]> content = reader.readFileContent(zip, "SKILL.md");

        assertThat(content).isPresent();
        assertThat(new String(content.get(), StandardCharsets.UTF_8)).isEqualTo("root file");
    }

    @Test
    @DisplayName("readFileContent 不做前缀模糊匹配——请求剥壳前的完整路径不应命中")
    void should_notMatch_whenRequestingUnstrippedPath() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("code-review/SKILL.md", "skill md".getBytes(StandardCharsets.UTF_8));
        InputStream zip = buildZipStream(entries);

        Optional<byte[]> content = reader.readFileContent(zip, "code-review/SKILL.md");

        assertThat(content).isEmpty();
    }

    @Test
    @DisplayName("制品不是合法 zip 时 readFileContent 同样抛出 UncheckedIOException,不静默吞掉")
    void should_throwUncheckedIOException_whenReadingFileContentFromInvalidZip() {
        InputStream notAZip = new ByteArrayInputStream("这不是一个 zip 文件".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> reader.readFileContent(notAZip, "SKILL.md"))
                .isInstanceOf(UncheckedIOException.class);
    }

    // ------------------------------------------------------------------
    // isSafeRelativePath:HTTP 请求携带的外部 path 参数的安全校验(需求第 2 条,攻击面)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("正常的相对路径判定为安全")
    void should_returnTrue_whenPathIsNormalRelativePath() {
        assertThat(SkillArtifactManifestReader.isSafeRelativePath("references/guide.md")).isTrue();
        assertThat(SkillArtifactManifestReader.isSafeRelativePath("SKILL.md")).isTrue();
    }

    @Test
    @DisplayName("拒绝 null 或空白路径")
    void should_returnFalse_whenPathIsNullOrBlank() {
        assertThat(SkillArtifactManifestReader.isSafeRelativePath(null)).isFalse();
        assertThat(SkillArtifactManifestReader.isSafeRelativePath("")).isFalse();
        assertThat(SkillArtifactManifestReader.isSafeRelativePath("   ")).isFalse();
    }

    @Test
    @DisplayName("拒绝包含 .. 路径段的路径")
    void should_returnFalse_whenPathContainsDotDotSegment() {
        assertThat(SkillArtifactManifestReader.isSafeRelativePath("../../etc/passwd")).isFalse();
        assertThat(SkillArtifactManifestReader.isSafeRelativePath("references/../../../etc/passwd")).isFalse();
    }

    @Test
    @DisplayName("拒绝绝对路径(Unix 与 Windows 盘符两种形式)")
    void should_returnFalse_whenPathIsAbsolute() {
        assertThat(SkillArtifactManifestReader.isSafeRelativePath("/etc/passwd")).isFalse();
        assertThat(SkillArtifactManifestReader.isSafeRelativePath("C:\\Windows\\System32\\config")).isFalse();
    }

    @Test
    @DisplayName("拒绝含控制字符的路径")
    void should_returnFalse_whenPathContainsControlCharacter() {
        assertThat(SkillArtifactManifestReader.isSafeRelativePath("references/guide\u0000.md")).isFalse();
        assertThat(SkillArtifactManifestReader.isSafeRelativePath("references/guide\r\n.md")).isFalse();
    }

    private InputStream buildZipStream(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return new ByteArrayInputStream(buffer.toByteArray());
    }
}
