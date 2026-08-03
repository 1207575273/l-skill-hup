package com.landray.skillhub.domain.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 版本文件预览判定:给一段已经从制品 zip 里精确匹配读出的原始字节,判断这段字节能不能安全地当文本展示给人看,
 * 能就解码成字符串返回,不能就给出原因。纯函数,不碰 IO、不解释"这段字节从哪来"(那是
 * {@code SkillArtifactManifestReader#readFileContent} 与 application 层编排的职责)。
 *
 * <p>两条判据均来自任务契约,顺序不能颠倒——先判大小再判二进制:判断 NUL 字节需要遍历整个数组,对已经判定
 * {@code TOO_LARGE} 的内容没必要再做这次遍历。
 *
 * <ol>
 *   <li><b>超过 256KB 不预览</b>——预览是给人看的,把几 MB 的文件塞进 JSON 响应体只会拖垮浏览器,这是产品体验
 *       判断,不是安全边界。
 *   <li><b>含 NUL 字节(0x00)判定为二进制</b>——按内容探测而不是按扩展名白名单:扩展名可能缺失(如
 *       {@code README} 没后缀)或造假({@code .md} 里塞二进制内容),而 NUL 字节是文本文件里几乎不会出现的
 *       可靠信号。
 * </ol>
 *
 * <p>UTF-8 解码遇到非法字节序列不抛异常,用替换字符(U+FFFD)兜底——预览接口的目的是"尽力展示",不是逐字节
 * 校验编码正确性。
 *
 * <p>domain 纯净性:只依赖 JDK 标准库,满足 {@code LayeredArchitectureTest#domainMustBePlainJava}。
 */
public final class SkillVersionFilePreviewer {

    /** 预览大小上限:256KB。超过此值不返回内容,响应体里 {@code previewable=false}。 */
    public static final long MAX_PREVIEWABLE_BYTES = 256L * 1024L;

    private static final byte NUL_BYTE = 0;

    private SkillVersionFilePreviewer() {}

    public static SkillVersionFileContent preview(String path, byte[] rawContent) {
        long sizeBytes = rawContent.length;
        if (sizeBytes > MAX_PREVIEWABLE_BYTES) {
            return SkillVersionFileContent.notPreviewable(path, sizeBytes, NotPreviewableReason.TOO_LARGE);
        }
        if (containsNulByte(rawContent)) {
            return SkillVersionFileContent.notPreviewable(path, sizeBytes, NotPreviewableReason.BINARY);
        }
        String decoded = decodeUtf8WithReplacement(path, rawContent);
        return SkillVersionFileContent.previewable(path, sizeBytes, decoded);
    }

    private static boolean containsNulByte(byte[] content) {
        for (byte b : content) {
            if (b == NUL_BYTE) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code CodingErrorAction.REPLACE} 保证 {@code decode} 不会因为非法/不可映射的字节序列抛出受检异常——
     * 这里仍然 catch 是防御性的(理论上不可达),转成非受检异常而不是吞掉,保留可观测性。
     */
    private static String decodeUtf8WithReplacement(String path, byte[] content) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new UncheckedIOException("UTF-8 解码失败, path=" + path, new IOException(e));
        }
    }
}
