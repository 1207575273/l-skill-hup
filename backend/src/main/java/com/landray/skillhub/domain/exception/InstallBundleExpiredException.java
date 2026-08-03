package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 安装单已过期(业务建模文档 5.9)。
 *
 * <p>与"token 不存在"故意映射成不同的 HTTP 状态与错误码——前者是 410 Gone(这个资源曾经存在,现在不再有效),
 * 后者复用通用的 {@code ResourceNotFoundException}(404,压根没找到)。分开是为了让前端与用户都能分清
 * "链接打错了"和"链接是对的,只是过期了,请重新生成"这两种完全不同的处置方式。
 */
public class InstallBundleExpiredException extends BusinessException {

    private static final String CODE = "INSTALL_BUNDLE_EXPIRED";
    private static final int HTTP_STATUS_GONE = 410;

    public InstallBundleExpiredException() {
        super(CODE, "安装单已过期,请重新生成安装命令", HTTP_STATUS_GONE);
    }
}
