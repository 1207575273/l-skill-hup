package com.landray.skillhub.domain.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

import com.landray.skillhub.domain.model.SkillVersionFile;

/**
 * 详情页"文件树/文件预览"的读取器:打开一份已发布制品 zip,按需读取中央目录(清单)或某个条目的实际内容。
 *
 * <p>本类刻意不做任何"存储层失败要不要降级"的判断——制品缺失、字节不是合法 zip、请求的相对路径不存在,都不是
 * 本类的关注点,那是调用方(application 层)的编排职责。本类只负责"给我一份合法的 zip 字节流,我给你解析好的
 * 结果"这一件纯函数式的事,解析失败就让异常照常抛出,不吞。
 *
 * <p>为什么用 commons-compress 而不是 stdlib {@code java.util.zip}:与 {@link SkillPackageValidator} 同样的
 * 理由——中央目录里的 {@code size} 字段来自随机访问读取,比流式的 {@code ZipInputStream}(可能因为源 zip 用了
 * data descriptor 而在读到条目内容前拿不到准确大小)更可靠。这里复用同一个库,不需要再引入第二套 zip 处理方式。
 *
 * <p>domain 纯净性:只依赖 JDK 标准库与 commons-compress,均非 Spring/MyBatis/Jackson,满足
 * {@code LayeredArchitectureTest#domainMustBePlainJava}。
 */
public final class SkillArtifactManifestReader {

    /**
     * 解析制品 zip,得到剥壳后按 path 字典序排列的文件清单。
     *
     * <p><b>剥壳判据见 {@link #findCommonTopDirectory}</b>:只看"所有条目是否共享同一个顶层目录段"来决定要不要
     * 剥,不假设"顶层目录一定等于 skill 的最终 name"这个事实。{@link #readFileContent} 复用同一份判据,保证
     * 前端从清单拿到的 path 与后端按 path 查找文件内容时用的相对路径口径一致。
     *
     * @param zipStream 制品字节流,读取完毕后本方法负责关闭
     */
    public List<SkillVersionFile> read(InputStream zipStream) {
        byte[] zipBytes = readAllBytes(zipStream);
        List<RawEntry> rawEntries = readRawEntries(zipBytes);
        return stripCommonTopDirectory(rawEntries);
    }

    /**
     * 读取制品 zip 内某一个文件的原始字节。{@code relativePath} 必须与 {@link #read} 剥壳后的清单条目
     * <b>精确</b>匹配——调用方(版本文件预览接口)传入的 path 来自 HTTP 请求,是外部输入,不做前缀/包含关系的
     * 模糊匹配是刻意的安全设计:真实制品内的条目名在上传时已经被 {@code SkillPackageValidator} 拒绝过
     * {@code ..}/绝对路径/控制字符,精确匹配天然不会命中这类畸形输入,不需要在这里重新解释一遍"这个字符串
     * 是不是一个合法路径"——{@link #isSafeRelativePath} 提供了额外一层快速失败,但真正兜底的是这里的精确匹配。
     *
     * @param zipStream 制品字节流,读取完毕后本方法负责关闭
     * @return 找不到匹配条目时返回空,不区分"路径本身不合法"与"目录存在但没有这个文件"——两者对调用方而言是
     *     同一件事:没有这个文件可读
     */
    public Optional<byte[]> readFileContent(InputStream zipStream, String relativePath) {
        byte[] zipBytes = readAllBytes(zipStream);
        try (SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(zipBytes);
                ZipFile zipFile = ZipFile.builder().setSeekableByteChannel(channel).get()) {
            List<ZipArchiveEntry> fileEntries = listFileEntries(zipFile);
            String prefix = commonTopDirectoryPrefix(fileEntries.stream().map(ZipArchiveEntry::getName).toList());
            for (ZipArchiveEntry entry : fileEntries) {
                String relative = stripPrefix(entry.getName(), prefix);
                if (relative.equals(relativePath)) {
                    try (InputStream entryStream = zipFile.getInputStream(entry)) {
                        return Optional.of(entryStream.readAllBytes());
                    }
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("解析制品 zip 失败,无法读取文件内容", e);
        }
    }

    /**
     * 校验来自 HTTP 请求的相对路径是否可能构成攻击面:拒绝空白、控制字符、绝对路径(Unix 或 Windows 盘符形式)、
     * 以及任意包含 {@code ..} 的路径段。
     *
     * <p>与 {@code SkillPackageValidator} 校验上传时 zip 内部原始条目名是同一类判据,但服务的输入来源不同——
     * 那边校验的是包内条目名(上传时机),这里校验的是 HTTP 请求携带的外部 {@code path} 参数(读取时机),两者
     * 独立维护。<b>这层校验不是唯一防线</b>:真正兜底的是 {@link #readFileContent} 的精确匹配——即使这里被
     * 意外跳过,这类畸形路径也不会命中任何真实的清单条目(制品内条目名本身在上传时就已经被拒绝过同样的模式)。
     * 之所以还要显式做一遍,是为了在打开制品 zip 之前就快速拒绝,不必为一个显然恶意的输入去解压整个制品。
     *
     * @return 路径是否安全,可以进入下一步的精确匹配
     */
    public static boolean isSafeRelativePath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        if (path.chars().anyMatch(Character::isISOControl)) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            return false;
        }
        for (String segment : normalized.split("/", -1)) {
            if (segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private List<RawEntry> readRawEntries(byte[] zipBytes) {
        try (SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(zipBytes);
                ZipFile zipFile = ZipFile.builder().setSeekableByteChannel(channel).get()) {
            List<ZipArchiveEntry> fileEntries = listFileEntries(zipFile);
            List<RawEntry> rawEntries = new ArrayList<>(fileEntries.size());
            for (ZipArchiveEntry entry : fileEntries) {
                rawEntries.add(new RawEntry(entry.getName(), sizeOrZero(entry.getSize())));
            }
            return rawEntries;
        } catch (IOException e) {
            throw new UncheckedIOException("解析制品 zip 失败,无法读取文件清单", e);
        }
    }

    private List<ZipArchiveEntry> listFileEntries(ZipFile zipFile) {
        List<ZipArchiveEntry> result = new ArrayList<>();
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
                result.add(entry);
            }
        }
        return result;
    }

    private byte[] readAllBytes(InputStream zipStream) {
        try (InputStream in = zipStream) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("读取制品字节流失败", e);
        }
    }

    /**
     * {@code ZipArchiveEntry.getSize()} 来自中央目录,正常情况下可靠;个别流式写入的 zip 可能给出 -1(未知)。
     * 清单的用途是给前端展示大小,不是校验,遇到未知值按 0 处理、不报错——容错优先于精确。
     *
     * <p>包内可见(而不是 private),供单测直接验证这条边界逻辑,不必为了触发它去构造一个真实的"中央目录 size
     * 未知"的 zip——真实场景里几乎不可能出现(Java 写 zip 时中央目录的 size 字段在 finish() 时必然已知)。
     */
    static long sizeOrZero(long rawSize) {
        return rawSize < 0 ? 0 : rawSize;
    }

    private List<SkillVersionFile> stripCommonTopDirectory(List<RawEntry> rawEntries) {
        String prefix = commonTopDirectoryPrefix(rawEntries.stream().map(RawEntry::name).toList());
        List<SkillVersionFile> result = new ArrayList<>(rawEntries.size());
        for (RawEntry entry : rawEntries) {
            String relativePath = stripPrefix(entry.name(), prefix);
            result.add(new SkillVersionFile(relativePath, entry.sizeBytes()));
        }
        result.sort(Comparator.comparing(SkillVersionFile::path));
        return result;
    }

    private String stripPrefix(String name, String prefix) {
        return prefix.isEmpty() ? name : name.substring(prefix.length());
    }

    private String commonTopDirectoryPrefix(List<String> names) {
        String commonTopDir = findCommonTopDirectory(names);
        return commonTopDir == null ? "" : commonTopDir + "/";
    }

    /**
     * 共享的剥壳判据:所有条目是否共享同一个顶层目录段。{@link #read}(产出清单)与
     * {@link #readFileContent}(按清单里的相对路径读取内容)必须用同一份判断——否则前端从清单拿到的 path
     * 与后端按 path 查找时用的相对路径口径就会对不上。判据本身与 {@code SkillPackageValidator#stripTopLevelWrapper}
     * 同构,但刻意不假设"顶层目录一定等于最终 name"。
     */
    private String findCommonTopDirectory(List<String> names) {
        String commonTopDir = null;
        for (String name : names) {
            int slashIndex = name.indexOf('/');
            if (slashIndex < 0) {
                // 已经有文件直接位于根,不可能存在统一的顶层包裹目录
                return null;
            }
            String topSegment = name.substring(0, slashIndex);
            if (commonTopDir == null) {
                commonTopDir = topSegment;
            } else if (!commonTopDir.equals(topSegment)) {
                return null;
            }
        }
        return commonTopDir;
    }

    private record RawEntry(String name, long sizeBytes) {}
}
