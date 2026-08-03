package com.landray.skillhub.domain.model;

/**
 * {@link com.landray.skillhub.domain.service.SkillPackageValidator#repackage} 的产物:业务建模文档 5.3
 * 第 9~11 步(改写 frontmatter -&gt; 按最终 name 重新打包 -&gt; 算最终 zip 的 sha256)完成后的最终制品字节。
 *
 * <p>{@code sha256} 在这里算,而不是留给 {@code ArtifactStorage} 或 application 层算——"对哪一份字节算"
 * 必须紧挨着"这份字节最终长什么样"的那段代码,分开写容易在两处的字节不完全一致时(例如中间被别的逻辑改了一次
 * 编码)产生一个校验着"另一份内容"的哈希,那种 bug 极难被发现。
 */
public record RepackagedArtifact(byte[] zipBytes, String sha256, String rewrittenSkillMdContent) {}
