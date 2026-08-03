package com.landray.skillhub.domain.model;

/**
 * {@code install_bundle.items} 数组里的一个元素。
 *
 * <p><b>只存 {@code skillId} 与 {@code versionId}</b>,不内嵌 sha256 / artifactKey——那些字段渲染安装脚本时
 * 实时 join {@code skill_version} 取,内嵌等于制造第二个事实源,制品换了它不会跟着变(业务建模文档 4.13)。
 */
public record InstallItem(Long skillId, Long versionId) {

    public InstallItem {
        if (skillId == null) {
            throw new IllegalArgumentException("skillId 不能为空");
        }
        if (versionId == null) {
            throw new IllegalArgumentException("versionId 不能为空");
        }
    }
}
