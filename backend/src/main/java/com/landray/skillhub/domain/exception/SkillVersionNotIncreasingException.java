package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 新发布的版本号不大于该 skill 现有的最大版本(业务建模文档 5.4 第 3 条)。
 *
 * <p>不加这条校验的后果很具体:可以在 {@code 1.2.0} 之后发 {@code 0.0.1} 并把 {@code latest_version_id} 钉到它
 * 上面,列表页和安装脚本一起退化到旧版本。
 */
public class SkillVersionNotIncreasingException extends BusinessException {

    private static final String CODE = "SKILL_VERSION_NOT_INCREASING";

    public SkillVersionNotIncreasingException(String message) {
        super(CODE, message);
    }
}
