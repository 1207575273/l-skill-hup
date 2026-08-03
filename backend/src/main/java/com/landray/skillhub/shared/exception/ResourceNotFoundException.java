package com.landray.skillhub.shared.exception;

import java.util.Map;

/**
 * 通用的"资源不存在"异常,统一映射为 HTTP 404。
 *
 * <p>放在 shared 而不是 domain:"资源不存在"是跨所有实体的通用语义(skill / tag / user 未来都会用到),不属于任何单一
 * 领域概念自己的规则,不值得为每个实体各写一个 XxxNotFoundException 只为了改个 message。code 由调用方传入(如
 * {@code SKILL_NOT_FOUND}),保持业务语义可辨识,前端仍按 code 分支。
 */
public class ResourceNotFoundException extends BusinessException {

    private static final int HTTP_STATUS_NOT_FOUND = 404;

    public ResourceNotFoundException(String code, String message) {
        super(code, message, HTTP_STATUS_NOT_FOUND);
    }

    public ResourceNotFoundException(String code, String message, Map<String, Object> details) {
        super(code, message, HTTP_STATUS_NOT_FOUND, details);
    }
}
