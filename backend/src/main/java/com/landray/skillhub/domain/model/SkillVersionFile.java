package com.landray.skillhub.domain.model;

/**
 * skill 某个版本内的一个文件条目,供详情页把版本展开成文件树。
 *
 * <p><b>不落库</b>——{@code skill_version} 表没有对应列。这是 {@code GET /skills/{id}} 详情请求时,现读已发布制品
 * zip 的中央目录(不解压任何内容)得到的运行时数据,见
 * {@link com.landray.skillhub.domain.service.SkillArtifactManifestReader}。之所以不落库:制品本身就是唯一事实源,
 * 现读的成本可忽略(制品几十 KB 到几 MB,读中央目录是微秒级),没必要为一份可以随时重新推导的数据再维护一份存储、
 * 再操心存量数据回填。
 *
 * <p>{@code path} 是剥壳后的相对路径(不含重新打包时加上的顶层目录前缀),与
 * {@link ParsedSkillPackage#files()} 的 key 口径一致。
 */
public record SkillVersionFile(String path, long sizeBytes) {

    public SkillVersionFile {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path 不能为空");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes 不能为负数");
        }
    }
}
