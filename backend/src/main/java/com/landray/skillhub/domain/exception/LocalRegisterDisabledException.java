package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 本地自助注册未开启。P0 默认关闭(业务建模文档 3.2):内网谁都能访问页面,敞开注册意味着任何人都能建号后
 * 上传结构合规但正文恶意的 skill,而 P0 阶段还没有任何一道防线覆盖内容语义。
 *
 * <p>关闭时必须返回这个明确的业务错误码,不是 404——404 会让前端和排障者误以为接口不存在。
 */
public class LocalRegisterDisabledException extends BusinessException {

    private static final String CODE = "LOCAL_REGISTER_DISABLED";
    private static final int HTTP_STATUS_FORBIDDEN = 403;

    public LocalRegisterDisabledException() {
        super(CODE, "本地自助注册未开启,请联系管理员建号", HTTP_STATUS_FORBIDDEN);
    }
}
