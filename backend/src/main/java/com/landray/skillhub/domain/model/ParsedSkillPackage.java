package com.landray.skillhub.domain.model;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@link com.landray.skillhub.domain.service.SkillPackageValidator} 完成业务建模文档 5.3 第 1~7 步之后的
 * 产物:一份已经过全部安全校验、已剥垃圾、已剥顶层包裹目录、已解析 frontmatter、已算好
 * {@code content_digest} 的规范化文件树。
 *
 * <p><b>{@link #files()} 的 key 是"剥壳后的相对路径",不含任何顶层包裹目录前缀</b>,例如
 * {@code "SKILL.md"}、{@code "references/a.md"}。第 8~11 步(定最终 name、改写 frontmatter、重新打包、算
 * sha256)需要额外知道"最终 name"这个只有 application 层(要查库判重名)才能决定的信息,因此不在这个类型里,
 * 由 {@link com.landray.skillhub.domain.service.SkillPackageValidator#repackage} 单独接收。
 *
 * <p>{@code files} 用 {@link TreeMap} 存,天然按路径字典序遍历——与 {@code content_digest} 定义"按相对路径字典序
 * 遍历"这句话对齐,重新打包时复用同一份已排序的 entrySet 即可,不需要在 repackage 阶段再排一次序。
 */
public record ParsedSkillPackage(
        String name,
        String description,
        List<String> allowedTools,
        String contentDigest,
        int fileCount,
        long unpackedBytes,
        String readmeContent,
        Map<String, byte[]> files) {

    public ParsedSkillPackage {
        files = files == null ? Map.of() : new TreeMap<>(files);
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }

    /** {@code files} 里是否有顶层 {@code README.md}——precheck 响应的 {@code hasReadme} 字段直接取它。 */
    public boolean hasReadme() {
        return readmeContent != null;
    }

    /** 剥壳后 {@code SKILL.md} 的原始字节内容(改写 frontmatter 之前),重新打包阶段的改写起点。 */
    public byte[] skillMdBytes() {
        return files.get("SKILL.md");
    }
}
