package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/** 安装单的条目数量不合法(空列表,或超过上限)——见 {@link com.landray.skillhub.domain.model.InstallBundle}。 */
public class InvalidInstallBundleException extends BusinessException {

    private static final String CODE = "INSTALL_BUNDLE_ITEMS_INVALID";

    public InvalidInstallBundleException(String message) {
        super(CODE, message);
    }
}
