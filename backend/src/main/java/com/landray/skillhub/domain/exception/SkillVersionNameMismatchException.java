package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 发布新版本时,包内 frontmatter 解析出的 name 与目标 skill 的 {@code name} 不一致。
 *
 * <p>这是本任务文档未覆盖、需要自行定夺的一条规则:{@code POST /skills/{id}/versions} 的目标 name
 * 由路径上的 skill id 唯一确定(且 {@code skill.name} 一经创建不可修改,业务建模文档 5.16),不存在"改名新建"
 * 的分支——因此这里选择**拒绝**而不是像 5.5 的重名场景那样静默改写 frontmatter:静默改写在"新建"语境下是
 * 刻意设计(用户已经在弹窗里看到并确认了最终 name),但在"发新版本"语境下悄悄改写等于放过一次
 * "上传了错误 skill 的包却混进了别的 skill 名下"的信号,对应根 CLAUDE.md 第七节"绕过前端直接打 API
 * 上传恶意包"的安全用例。
 */
public class SkillVersionNameMismatchException extends BusinessException {

    private static final String CODE = "SKILL_VERSION_NAME_MISMATCH";

    public SkillVersionNameMismatchException(String message) {
        super(CODE, message);
    }
}
