package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 禁止禁用某个账号的业务护栏,例如"不能禁用自己"或"不能禁用内置 system 账号"。
 *
 * <p>业务建模文档没有明文规定这两条护栏,是本轮实现按常识补充的安全网:自助注册 P0 默认关闭,一旦所有 ADMIN
 * 都被误禁用,系统会重新陷入 3.2 描述的"没有任何路径能产生可登录管理员"的死锁,而这个死锁本该只在冷启动时
 * 存在一次。护栏只挡"最后一根稻草"式的自伤操作,不做更复杂的"至少保留一个 ADMIN"这类全局约束。
 */
public class UserDisableNotAllowedException extends BusinessException {

    private static final String CODE = "USER_DISABLE_NOT_ALLOWED";

    public UserDisableNotAllowedException(String message) {
        super(CODE, message);
    }
}
