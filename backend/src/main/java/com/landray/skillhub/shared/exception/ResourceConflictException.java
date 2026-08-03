package com.landray.skillhub.shared.exception;

import java.util.Map;

/**
 * 通用的"资源冲突"异常,统一映射为 HTTP 409。
 *
 * <p>典型场景是唯一约束冲突,例如业务建模文档 6.1 举例的 {@code SKILL_NAME_DUPLICATED}(两人并发上传同名
 * skill,precheck 时都不重名,insert 时撞上 {@code uk_skill_name})。放在 shared 而不是 domain 的理由同
 * {@link ResourceNotFoundException}——冲突语义跨实体通用。
 */
public class ResourceConflictException extends BusinessException {

    private static final int HTTP_STATUS_CONFLICT = 409;

    public ResourceConflictException(String code, String message) {
        super(code, message, HTTP_STATUS_CONFLICT);
    }

    public ResourceConflictException(String code, String message, Map<String, Object> details) {
        super(code, message, HTTP_STATUS_CONFLICT, details);
    }
}
