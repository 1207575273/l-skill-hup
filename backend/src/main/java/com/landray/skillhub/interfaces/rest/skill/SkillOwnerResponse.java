package com.landray.skillhub.interfaces.rest.skill;

import com.landray.skillhub.domain.model.SkillOwner;

/**
 * skill 归属人的展示信息。业务建模文档 7.1:工号必须跟在展示名后面(展示为 {@code @张三(80380486)}),
 * {@code employeeNo} 为空时前端只显示展示名。
 */
public record SkillOwnerResponse(Long userId, String displayName, String employeeNo) {

    public static SkillOwnerResponse from(SkillOwner owner) {
        if (owner == null) {
            return null;
        }
        return new SkillOwnerResponse(owner.userId(), owner.displayName(), owner.employeeNo());
    }
}
