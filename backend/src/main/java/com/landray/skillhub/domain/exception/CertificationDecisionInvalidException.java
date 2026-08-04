package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 一次认证状态变更的入参不合法(业务建模文档 5.8):认证状态不在 {@code NONE / CERTIFIED / REVOKED} 里、
 * 撤销时没填理由、或理由超长。
 *
 * <p><b>"撤销必填理由"不是表单洁癖。</b>撤销理由会原样进安装脚本的警示文本(5.9),是使用者判断"这个包到底
 * 出了什么事、我该不该继续装"的<b>唯一依据</b>。允许空理由等于给出一个只会吓人、不提供任何信息的红色警告,
 * 而那种警告的下场是被无视。
 */
public class CertificationDecisionInvalidException extends BusinessException {

    private static final String CODE = "CERTIFICATION_DECISION_INVALID";

    public CertificationDecisionInvalidException(String message) {
        super(CODE, message);
    }
}
