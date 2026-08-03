package com.landray.skillhub.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.landray.skillhub.domain.exception.InvalidSkillNameException;
import com.landray.skillhub.domain.exception.SkillPackageInvalidException;
import com.landray.skillhub.domain.model.ParsedSkillPackage;
import com.landray.skillhub.domain.model.RepackagedArtifact;
import com.landray.skillhub.domain.model.SkillName;
import com.landray.skillhub.domain.model.UploadLimits;

/**
 * {@link SkillPackageValidator} 是这个任务最核心的安全边界,覆盖业务建模文档 5.2/5.3 的全部规则与
 * backend/CLAUDE.md 8.3 要求的失败用例(zip-slip、zip 炸弹、缺 SKILL.md、frontmatter 非法)。
 */
class SkillPackageValidatorTest {

    /** 宽松上限,只用于验证"正常包能通过",不测边界。 */
    private static final UploadLimits GENEROUS_LIMITS =
            new UploadLimits(64L * 1024 * 1024, 500, 16L * 1024 * 1024, 8, 255, 100);

    private final SkillPackageValidator validator = new SkillPackageValidator(GENEROUS_LIMITS);

    // ------------------------------------------------------------------
    // 正常路径
    // ------------------------------------------------------------------

    @Test
    @DisplayName("结构合规的包能通过校验,frontmatter 三字段被正确解析")
    void should_parsePackage_when_wellFormed() throws IOException {
        byte[] zip = buildZip(Map.of(
                "code-review/SKILL.md", skillMd("code-review", "代码审查助手", "Read, Grep"),
                "code-review/README.md", "# code-review\n说明文档"));

        ParsedSkillPackage parsed = validator.parseAndValidate(zip);

        assertThat(parsed.name()).isEqualTo("code-review");
        assertThat(parsed.description()).isEqualTo("代码审查助手");
        assertThat(parsed.allowedTools()).containsExactly("Read", "Grep");
        assertThat(parsed.hasReadme()).isTrue();
        assertThat(parsed.readmeContent()).isEqualTo("# code-review\n说明文档");
        assertThat(parsed.fileCount()).isEqualTo(2);
        assertThat(parsed.contentDigest()).hasSize(64).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("没有顶层包裹目录的包(文件直接在根)同样能通过")
    void should_parsePackage_when_noWrapperDirectory() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("foo", "描述", null)));

        ParsedSkillPackage parsed = validator.parseAndValidate(zip);

        assertThat(parsed.name()).isEqualTo("foo");
    }

    @Test
    @DisplayName("allowed-tools 字段缺席时解析出空列表,不报错——待验证项 7 的 playwright 样本就是这种形态")
    void should_returnEmptyAllowedTools_when_fieldAbsent() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", skillMdWithoutAllowedTools("foo", "描述")));

        ParsedSkillPackage parsed = validator.parseAndValidate(zip);

        assertThat(parsed.allowedTools()).isEmpty();
    }

    @Test
    @DisplayName("allowed-tools 写成 YAML 列表时按元素独立解析")
    void should_parseAllowedToolsAsYamlList() throws IOException {
        String content = "---\nname: foo\ndescription: 描述\nallowed-tools:\n  - Read\n  - Grep\n---\n正文\n";
        byte[] zip = buildZip(Map.of("SKILL.md", content));

        ParsedSkillPackage parsed = validator.parseAndValidate(zip);

        assertThat(parsed.allowedTools()).containsExactly("Read", "Grep");
    }

    @Test
    @DisplayName("allowed-tools 写成带括号逗号的标量时,只在括号外切分——与 AllowedToolsParser 单测同一个字符串")
    void should_parseAllowedToolsAsScalarRespectingParentheses() throws IOException {
        byte[] zip =
                buildZip(Map.of("SKILL.md", skillMd("foo", "描述", "Bash(git diff:*, git status:*), Read")));

        ParsedSkillPackage parsed = validator.parseAndValidate(zip);

        assertThat(parsed.allowedTools()).containsExactly("Bash(git diff:*, git status:*)", "Read");
    }

    // ------------------------------------------------------------------
    // 顺序承重:剥垃圾必须排在剥顶层包裹目录之前
    // ------------------------------------------------------------------

    @Test
    @DisplayName("剥垃圾排在剥壳之前:__MACOSX/ 与顶层目录并存时仍能正确识别出唯一的顶层包裹目录")
    void should_stripJunkBeforeDetectingWrapper_when_macosxEntriesPresent() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("foo/SKILL.md", skillMd("foo", "描述", null));
        entries.put("__MACOSX/foo/._SKILL.md", "苹果压缩产生的元数据,内容无意义");
        entries.put("foo/.DS_Store", "垃圾文件");
        byte[] zip = buildZip(entries);

        ParsedSkillPackage parsed = validator.parseAndValidate(zip);

        assertThat(parsed.name()).isEqualTo("foo");
        assertThat(parsed.files()).containsOnlyKeys("SKILL.md");
    }

    @Test
    @DisplayName(".git/ 与 node_modules/ 目录下的内容整体丢弃")
    void should_stripGitAndNodeModulesDirectories() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("SKILL.md", skillMd("foo", "描述", null));
        entries.put(".git/HEAD", "ref: refs/heads/main");
        entries.put("node_modules/pkg/index.js", "module.exports = {}");
        byte[] zip = buildZip(entries);

        ParsedSkillPackage parsed = validator.parseAndValidate(zip);

        assertThat(parsed.files()).containsOnlyKeys("SKILL.md");
    }

    @Test
    @DisplayName("Thumbs.db 被丢弃")
    void should_stripThumbsDb() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("SKILL.md", skillMd("foo", "描述", null));
        entries.put("Thumbs.db", "windows 缩略图缓存");
        byte[] zip = buildZip(entries);

        ParsedSkillPackage parsed = validator.parseAndValidate(zip);

        assertThat(parsed.files()).containsOnlyKeys("SKILL.md");
    }

    @Test
    @DisplayName("顶层包裹目录识别:所有条目共享同一顶层目录且该目录直接含 SKILL.md 时才剥壳")
    void should_notStripWrapper_when_topDirDoesNotDirectlyContainSkillMd() throws IOException {
        // foo/ 目录下没有直接的 SKILL.md(在更深一层),不满足剥壳条件,结构校验应失败
        byte[] zip = buildZip(Map.of("foo/nested/SKILL.md", skillMd("foo", "描述", null)));

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex ->
                        assertThat(((SkillPackageInvalidException) ex).code()).isEqualTo("SKILL_PACKAGE_STRUCTURE_INVALID"));
    }

    // ------------------------------------------------------------------
    // zip-slip:拒绝 .. / 绝对路径 / 控制字符 / 符号链接
    // ------------------------------------------------------------------

    @Test
    @DisplayName("拒绝含 .. 的条目路径")
    void should_reject_when_entryContainsDotDotSegment() throws IOException {
        byte[] zip = buildZip(Map.of(
                "SKILL.md", skillMd("foo", "描述", null),
                "../../etc/evil.txt", "恶意内容"));

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_PATH_UNSAFE"));
    }

    @Test
    @DisplayName("拒绝以 / 开头的绝对路径条目")
    void should_reject_when_entryIsAbsolutePathUnix() throws IOException {
        byte[] zip = buildZip(Map.of(
                "SKILL.md", skillMd("foo", "描述", null),
                "/etc/passwd", "恶意内容"));

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_PATH_UNSAFE"));
    }

    @Test
    @DisplayName("拒绝带 Windows 盘符的绝对路径条目")
    void should_reject_when_entryIsAbsolutePathWindows() throws IOException {
        byte[] zip = buildZip(Map.of(
                "SKILL.md", skillMd("foo", "描述", null),
                "C:\\evil.txt", "恶意内容"));

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_PATH_UNSAFE"));
    }

    @Test
    @DisplayName("拒绝包含控制字符的条目路径")
    void should_reject_when_entryNameContainsControlCharacter() throws IOException {
        byte[] zip = buildZip(Map.of(
                "SKILL.md", skillMd("foo", "描述", null),
                "evil\u0001.txt", "恶意内容"));

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_PATH_UNSAFE"));
    }

    @Test
    @DisplayName("拒绝符号链接条目")
    void should_reject_when_entryIsUnixSymlink() throws IOException {
        byte[] zip = buildZipWithSymlink("SKILL.md", skillMd("foo", "描述", null), "evil-link", "/etc/passwd");

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_SYMLINK_NOT_ALLOWED"));
    }

    // ------------------------------------------------------------------
    // zip 炸弹:七条上限逐条测试
    // ------------------------------------------------------------------

    @Test
    @DisplayName("解压后总大小超过上限时拒绝")
    void should_reject_when_totalUnpackedBytesExceedsLimit() throws IOException {
        UploadLimits tightLimits = new UploadLimits(1000L, 500, 1000L, 8, 255, 100);
        SkillPackageValidator tightValidator = new SkillPackageValidator(tightLimits);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("SKILL.md", skillMd("foo", "描述", null));
        entries.put("references/a.md", "x".repeat(600));
        entries.put("references/b.md", "y".repeat(600));
        byte[] zip = buildZip(entries);

        assertThatThrownBy(() -> tightValidator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex ->
                        assertThat(((SkillPackageInvalidException) ex).code()).isEqualTo("SKILL_PACKAGE_TOO_LARGE"));
    }

    @Test
    @DisplayName("条目数超过上限时拒绝")
    void should_reject_when_entryCountExceedsLimit() throws IOException {
        UploadLimits tightLimits = new UploadLimits(64L * 1024 * 1024, 2, 16L * 1024 * 1024, 8, 255, 100);
        SkillPackageValidator tightValidator = new SkillPackageValidator(tightLimits);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("SKILL.md", skillMd("foo", "描述", null));
        entries.put("README.md", "说明");
        entries.put("references/a.md", "内容");
        byte[] zip = buildZip(entries);

        assertThatThrownBy(() -> tightValidator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_TOO_MANY_ENTRIES"));
    }

    @Test
    @DisplayName("单个文件超过上限时拒绝")
    void should_reject_when_singleFileExceedsLimit() throws IOException {
        UploadLimits tightLimits = new UploadLimits(64L * 1024 * 1024, 500, 500L, 8, 255, 100);
        SkillPackageValidator tightValidator = new SkillPackageValidator(tightLimits);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("SKILL.md", skillMd("foo", "描述", null));
        entries.put("references/big.md", "z".repeat(600));
        byte[] zip = buildZip(entries);

        assertThatThrownBy(() -> tightValidator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_FILE_TOO_LARGE"));
    }

    @Test
    @DisplayName("剥壳后的目录深度超过上限时拒绝")
    void should_reject_when_pathDepthExceedsLimit() throws IOException {
        UploadLimits tightLimits = new UploadLimits(64L * 1024 * 1024, 500, 16L * 1024 * 1024, 2, 255, 100);
        SkillPackageValidator tightValidator = new SkillPackageValidator(tightLimits);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("SKILL.md", skillMd("foo", "描述", null));
        entries.put("references/a/b/c.md", "内容");
        byte[] zip = buildZip(entries);

        assertThatThrownBy(() -> tightValidator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_PATH_TOO_DEEP"));
    }

    @Test
    @DisplayName("单条路径长度超过上限时拒绝")
    void should_reject_when_pathLengthExceedsLimit() throws IOException {
        UploadLimits tightLimits = new UploadLimits(64L * 1024 * 1024, 500, 16L * 1024 * 1024, 8, 20, 100);
        SkillPackageValidator tightValidator = new SkillPackageValidator(tightLimits);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("SKILL.md", skillMd("foo", "描述", null));
        entries.put("references/" + "a".repeat(30) + ".md", "内容");
        byte[] zip = buildZip(entries);

        assertThatThrownBy(() -> tightValidator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_PATH_TOO_LONG"));
    }

    @Test
    @DisplayName("压缩比超过 100:1 时判定为 zip 炸弹并拒绝")
    void should_reject_when_compressionRatioExceedsLimit() throws IOException {
        UploadLimits limits = new UploadLimits(64L * 1024 * 1024, 500, 16L * 1024 * 1024, 8, 255, 100);
        SkillPackageValidator bombValidator = new SkillPackageValidator(limits);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("SKILL.md", skillMd("foo", "描述", null));
        // 高度可压缩的重复内容,deflate 下压缩比远超 100:1
        entries.put("references/bomb.md", "A".repeat(2_000_000));
        byte[] zip = buildZip(entries);

        assertThatThrownBy(() -> bombValidator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex ->
                        assertThat(((SkillPackageInvalidException) ex).code()).isEqualTo("SKILL_PACKAGE_ZIP_BOMB"));
    }

    // ------------------------------------------------------------------
    // 结构 / frontmatter 校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("缺少 SKILL.md 时拒绝")
    void should_reject_when_skillMdMissing() throws IOException {
        byte[] zip = buildZip(Map.of("README.md", "只有说明,没有 SKILL.md"));

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_STRUCTURE_INVALID"));
    }

    @Test
    @DisplayName("顶层出现白名单之外的文件时拒绝")
    void should_reject_when_topLevelFileNotWhitelisted() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("SKILL.md", skillMd("foo", "描述", null));
        entries.put("LICENSE", "MIT");
        byte[] zip = buildZip(entries);

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_STRUCTURE_INVALID"));
    }

    @Test
    @DisplayName("顶层出现白名单之外的目录时拒绝")
    void should_reject_when_topLevelDirectoryNotWhitelisted() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("SKILL.md", skillMd("foo", "描述", null));
        entries.put("images/pic.png", "二进制假装的图片内容");
        byte[] zip = buildZip(entries);

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_STRUCTURE_INVALID"));
    }

    @Test
    @DisplayName("不是合法 zip 字节时拒绝")
    void should_reject_when_notAValidZip() {
        byte[] garbage = "这不是一个 zip 文件".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> validator.parseAndValidate(garbage))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex ->
                        assertThat(((SkillPackageInvalidException) ex).code()).isEqualTo("SKILL_PACKAGE_NOT_A_ZIP"));
    }

    @Test
    @DisplayName("SKILL.md 缺少起始 --- 时拒绝")
    void should_reject_when_frontmatterMissingOpeningDelimiter() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", "name: foo\ndescription: 描述\n没有 frontmatter 分隔符\n"));

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_FRONTMATTER_INVALID"));
    }

    @Test
    @DisplayName("SKILL.md 缺少结束 --- 时拒绝")
    void should_reject_when_frontmatterMissingClosingDelimiter() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", "---\nname: foo\ndescription: 描述\n没有结束分隔符\n"));

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_FRONTMATTER_INVALID"));
    }

    @Test
    @DisplayName("frontmatter 不是合法 YAML 时拒绝")
    void should_reject_when_frontmatterIsNotValidYaml() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", "---\nname: [foo\ndescription: 描述\n---\n正文\n"));

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_FRONTMATTER_INVALID"));
    }

    @Test
    @DisplayName("frontmatter 不是映射(例如是一段纯文本标量)时拒绝")
    void should_reject_when_frontmatterIsNotAMapping() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", "---\n就是一行纯文本\n---\n正文\n"));

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_FRONTMATTER_INVALID"));
    }

    @Test
    @DisplayName("缺少 name 字段时拒绝")
    void should_reject_when_nameFieldMissing() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", "---\ndescription: 只有描述没有 name\n---\n正文\n"));

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_FRONTMATTER_INVALID"));
    }

    @Test
    @DisplayName("缺少 description 字段时拒绝")
    void should_reject_when_descriptionFieldMissing() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", "---\nname: foo\n---\n正文\n"));

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_FRONTMATTER_INVALID"));
    }

    @Test
    @DisplayName("name 超过 50 字符时拒绝——为短哈希后缀留出余量(业务建模文档 5.5)")
    void should_reject_when_nameExceeds50Chars() throws IOException {
        String longName = "a" + "-b".repeat(30); // 远超 50 字符且仍是合法 kebab-case,单独验证长度这条规则
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd(longName, "描述", null)));

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_FRONTMATTER_INVALID"));
    }

    @Test
    @DisplayName("description 超过 512 字符时拒绝——与 skill.description 列宽一致")
    void should_reject_when_descriptionExceeds512Chars() throws IOException {
        String longDescription = "字".repeat(513);
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("foo", longDescription, null)));

        assertThatThrownBy(() -> validator.parseAndValidate(zip))
                .isInstanceOf(SkillPackageInvalidException.class)
                .satisfies(ex -> assertThat(((SkillPackageInvalidException) ex).code())
                        .isEqualTo("SKILL_PACKAGE_FRONTMATTER_INVALID"));
    }

    @Test
    @DisplayName("name 不是 kebab-case 时拒绝,复用 SkillName 的校验规则")
    void should_reject_when_nameIsNotKebabCase() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("Not_Valid_Name", "描述", null)));

        assertThatThrownBy(() -> validator.parseAndValidate(zip)).isInstanceOf(InvalidSkillNameException.class);
    }

    // ------------------------------------------------------------------
    // 两个摘要:content_digest 在改名之前算,与压缩器实现 / 顶层目录名无关
    // ------------------------------------------------------------------

    @Test
    @DisplayName("content_digest 只依赖文件路径与内容,与是否带顶层包裹目录无关")
    void should_produceSameContentDigest_regardlessOfWrapperDirectory() throws IOException {
        byte[] withWrapper = buildZip(Map.of("foo/SKILL.md", skillMd("foo", "描述", null)));
        byte[] withoutWrapper = buildZip(Map.of("SKILL.md", skillMd("foo", "描述", null)));

        String digest1 = validator.parseAndValidate(withWrapper).contentDigest();
        String digest2 = validator.parseAndValidate(withoutWrapper).contentDigest();

        assertThat(digest1).isEqualTo(digest2);
    }

    @Test
    @DisplayName("内容不同则 content_digest 不同")
    void should_produceDifferentContentDigest_when_contentDiffers() throws IOException {
        byte[] zipA = buildZip(Map.of("SKILL.md", skillMd("foo", "描述 A", null)));
        byte[] zipB = buildZip(Map.of("SKILL.md", skillMd("foo", "描述 B", null)));

        String digestA = validator.parseAndValidate(zipA).contentDigest();
        String digestB = validator.parseAndValidate(zipB).contentDigest();

        assertThat(digestA).isNotEqualTo(digestB);
    }

    @Test
    @DisplayName("重新打包改名之后,content_digest 不变——它在改名之前算,循环依赖被打断的地方")
    void should_keepContentDigestUnchanged_when_repackagingWithDifferentName() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("foo", "描述", null)));
        ParsedSkillPackage parsed = validator.parseAndValidate(zip);
        String digestBeforeRename = parsed.contentDigest();

        validator.repackage(parsed, new SkillName("foo-a1b2c3"));

        assertThat(parsed.contentDigest()).isEqualTo(digestBeforeRename);
    }

    @Test
    @DisplayName("重新打包只改写 frontmatter 的 name 一个键,其余字节不变")
    void should_rewriteOnlyNameField_when_repackaging() throws IOException {
        String original = "---\nname: foo\ndescription: 一段描述\nallowed-tools: Read, Grep\n---\n\n# 正文不动\n";
        byte[] zip = buildZip(Map.of("SKILL.md", original));
        ParsedSkillPackage parsed = validator.parseAndValidate(zip);

        RepackagedArtifact artifact = validator.repackage(parsed, new SkillName("foo-a1b2c3"));

        assertThat(artifact.rewrittenSkillMdContent())
                .isEqualTo("---\nname: foo-a1b2c3\ndescription: 一段描述\nallowed-tools: Read, Grep\n---\n\n# 正文不动\n");
    }

    @Test
    @DisplayName("最终制品的 sha256 是对重新打包后的 zip 字节算的,且是 64 位十六进制小写")
    void should_computeSha256OfFinalZipBytes_when_repackaging() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("foo", "描述", null)));
        ParsedSkillPackage parsed = validator.parseAndValidate(zip);

        RepackagedArtifact artifact = validator.repackage(parsed, new SkillName("foo"));

        assertThat(artifact.sha256()).hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(artifact.zipBytes()).isNotEmpty();
    }

    @Test
    @DisplayName("重新打包后的 zip 顶层目录名就是最终 name")
    void should_useFinalNameAsTopLevelDirectory_when_repackaging() throws IOException {
        byte[] zip = buildZip(Map.of(
                "foo/SKILL.md", skillMd("foo", "描述", null),
                "foo/references/a.md", "参考资料"));
        ParsedSkillPackage parsed = validator.parseAndValidate(zip);

        RepackagedArtifact artifact = validator.repackage(parsed, new SkillName("foo-a1b2c3"));

        Map<String, byte[]> repackagedEntries = readZipEntries(artifact.zipBytes());
        assertThat(repackagedEntries).containsOnlyKeys("foo-a1b2c3/SKILL.md", "foo-a1b2c3/references/a.md");
    }

    // ------------------------------------------------------------------
    // 测试夹具构造
    // ------------------------------------------------------------------

    private String skillMd(String name, String description, String allowedToolsScalar) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(name).append('\n');
        sb.append("description: ").append(description).append('\n');
        if (allowedToolsScalar != null) {
            sb.append("allowed-tools: ").append(allowedToolsScalar).append('\n');
        }
        sb.append("---\n\n# 正文\n");
        return sb.toString();
    }

    private String skillMdWithoutAllowedTools(String name, String description) {
        return skillMd(name, description, null);
    }

    private byte[] buildZip(Map<String, String> entries) throws IOException {
        Map<String, byte[]> binaryEntries = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : entries.entrySet()) {
            binaryEntries.put(e.getKey(), e.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return buildZipBytes(binaryEntries);
    }

    private byte[] buildZipBytes(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return buffer.toByteArray();
    }

    /** 构造一个含 Unix 符号链接条目的 zip,只有这个场景需要 commons-compress 的写侧 API 才能设置 unix mode。 */
    private byte[] buildZipWithSymlink(
            String skillMdPath, String skillMdContent, String linkName, String linkTarget) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream((OutputStream) buffer)) {
            ZipArchiveEntry normalEntry = new ZipArchiveEntry(skillMdPath);
            byte[] content = skillMdContent.getBytes(StandardCharsets.UTF_8);
            normalEntry.setSize(content.length);
            zos.putArchiveEntry(normalEntry);
            zos.write(content, 0, content.length);
            zos.closeArchiveEntry();

            ZipArchiveEntry symlinkEntry = new ZipArchiveEntry(linkName);
            byte[] targetBytes = linkTarget.getBytes(StandardCharsets.UTF_8);
            symlinkEntry.setSize(targetBytes.length);
            symlinkEntry.setUnixMode(0120777); // S_IFLNK | 0777
            zos.putArchiveEntry(symlinkEntry);
            zos.write(targetBytes, 0, targetBytes.length);
            zos.closeArchiveEntry();

            zos.finish();
        }
        return buffer.toByteArray();
    }

    private Map<String, byte[]> readZipEntries(byte[] zipBytes) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (java.util.zip.ZipInputStream zis =
                new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                result.put(entry.getName(), zis.readAllBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }
}
