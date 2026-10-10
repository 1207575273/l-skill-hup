package com.landray.skillhub.interfaces.rest.skill;

import com.landray.skillhub.domain.model.DeclaredOwner;

/**
 * 匿名上传时上传者**自称**的归属人。
 *
 * <p>与 {@link SkillOwnerResponse} 是两个独立字段,刻意不合并成一个 —— 前端必须能区分"已认领的真实归属"
 * 与"未经核实的自称归属",并在展示上明确标出后者不可信(业务上这是使用者判断"要不要装这个包"的依据之一,
 * 而包里的内容是会被 Agent 执行的)。合并成一个字段会让这个区别在 API 层就消失。
 *
 * <p>没有 {@code userId}:它背后没有账号,给不出能查人的 id,这也是它与 {@link SkillOwnerResponse} 最实质的
 * 差别 —— 后者的 {@code userId} 能拿去判权限,这里的两格字符串不能拿去判断任何事。
 */
public record DeclaredOwnerResponse(String name, String employeeNo) {

    public static DeclaredOwnerResponse from(DeclaredOwner declaredOwner) {
        if (declaredOwner == null) {
            return null;
        }
        return new DeclaredOwnerResponse(declaredOwner.name(), declaredOwner.employeeNo());
    }
}
