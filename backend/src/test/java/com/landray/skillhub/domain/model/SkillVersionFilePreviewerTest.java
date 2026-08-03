package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SkillVersionFilePreviewer} 只做一件纯函数式的事:给定路径与原始字节,判断能不能预览。存储/清单读取
 * 都不是本类的关注点(见类注释),那部分行为在 {@code SkillArtifactManifestReaderTest} 与
 * {@code SkillApplicationServiceTest} 里覆盖。
 */
class SkillVersionFilePreviewerTest {

    @Test
    @DisplayName("正常 UTF-8 文本在阈值内时判定为可预览,解码出正确内容")
    void should_returnPreviewableContent_whenTextIsWithinLimitAndValidUtf8() {
        byte[] content = "# 标题\n正文...".getBytes(StandardCharsets.UTF_8);

        SkillVersionFileContent result = SkillVersionFilePreviewer.preview("references/guide.md", content);

        assertThat(result.previewable()).isTrue();
        assertThat(result.content()).isEqualTo("# 标题\n正文...");
        assertThat(result.notPreviewableReason()).isNull();
        assertThat(result.sizeBytes()).isEqualTo(content.length);
        assertThat(result.path()).isEqualTo("references/guide.md");
    }

    @Test
    @DisplayName("超过 256KB 时判定为不可预览,原因是 TOO_LARGE,且不带正文")
    void should_returnNotPreviewableTooLarge_whenContentExceeds256KB() {
        byte[] oversized = new byte[(int) SkillVersionFilePreviewer.MAX_PREVIEWABLE_BYTES + 1];

        SkillVersionFileContent result = SkillVersionFilePreviewer.preview("scripts/big.sh", oversized);

        assertThat(result.previewable()).isFalse();
        assertThat(result.content()).isNull();
        assertThat(result.notPreviewableReason()).isEqualTo(NotPreviewableReason.TOO_LARGE);
        assertThat(result.sizeBytes()).isEqualTo(oversized.length);
    }

    @Test
    @DisplayName("恰好等于 256KB 上限时仍判定为可预览,只有严格超过才拒绝")
    void should_returnPreviewable_whenContentExactlyAtLimit() {
        // 全零字节数组本身就是清一色的 NUL 字节,会被误判成 BINARY——填充非 NUL 字符,只测大小阈值这一个变量。
        byte[] exactlyAtLimit = new byte[(int) SkillVersionFilePreviewer.MAX_PREVIEWABLE_BYTES];
        Arrays.fill(exactlyAtLimit, (byte) 'a');

        SkillVersionFileContent result = SkillVersionFilePreviewer.preview("scripts/exact.sh", exactlyAtLimit);

        assertThat(result.previewable()).isTrue();
    }

    @Test
    @DisplayName("含 NUL 字节时判定为二进制,不看扩展名——.md 后缀但内容是二进制同样拒绝")
    void should_returnNotPreviewableBinary_whenContentContainsNulByte() {
        byte[] binaryLikeMd = new byte[] {'a', 'b', 0x00, 'c'};

        SkillVersionFileContent result = SkillVersionFilePreviewer.preview("SKILL.md", binaryLikeMd);

        assertThat(result.previewable()).isFalse();
        assertThat(result.content()).isNull();
        assertThat(result.notPreviewableReason()).isEqualTo(NotPreviewableReason.BINARY);
    }

    @Test
    @DisplayName("先判大小再判二进制:超大文件即使含 NUL 字节,原因仍是 TOO_LARGE")
    void should_prioritizeTooLargeOverBinary_whenBothConditionsMatch() {
        byte[] oversizedWithNul = new byte[(int) SkillVersionFilePreviewer.MAX_PREVIEWABLE_BYTES + 1];
        oversizedWithNul[0] = 0x00;

        SkillVersionFileContent result = SkillVersionFilePreviewer.preview("scripts/big.bin", oversizedWithNul);

        assertThat(result.notPreviewableReason()).isEqualTo(NotPreviewableReason.TOO_LARGE);
    }

    @Test
    @DisplayName("非法 UTF-8 字节序列用替换字符兜底,不抛异常")
    void should_replaceInvalidUtf8Sequence_withoutThrowing() {
        // 0xFF 0xFE 不是合法的 UTF-8 序列
        byte[] invalidUtf8 = new byte[] {'a', (byte) 0xFF, (byte) 0xFE, 'b'};

        SkillVersionFileContent result = SkillVersionFilePreviewer.preview("references/broken.md", invalidUtf8);

        assertThat(result.previewable()).isTrue();
        assertThat(result.content()).contains("a").contains("b").contains("�");
    }

    @Test
    @DisplayName("空文件判定为可预览,内容为空字符串")
    void should_returnPreviewableEmptyContent_whenFileIsEmpty() {
        SkillVersionFileContent result = SkillVersionFilePreviewer.preview("empty.md", new byte[0]);

        assertThat(result.previewable()).isTrue();
        assertThat(result.content()).isEmpty();
        assertThat(result.sizeBytes()).isZero();
    }
}
