package com.landray.skillhub.domain.model;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * {@code install_bundle.token} 的生成算法。
 *
 * <p>与 {@link SessionToken} 不同,这里<b>不需要哈希配对</b>——会话 token 是"证明你是谁"的凭据,库里只能存哈希;
 * 安装单 token 天生就是要被公开分享的东西(甩进群聊里),它的安全属性只是"难以被猜出另一个人的安装单",不是保密,
 * 因此 {@code install_bundle} 表也没有单独的哈希列,直接明文存 {@code token} 列即可(业务建模文档 4.13)。
 *
 * <p>18 字节(144 位)随机数,Base64 URL 编码不带填充后是 24 个字符,在 {@code VARCHAR(32)} 列宽内留有余量,
 * 且 URL 安全(不含 {@code +} {@code /} {@code =}),可以直接拼进 {@code /i/{token}.ps1} 路径。
 */
public final class InstallBundleTokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final int TOKEN_BYTE_LENGTH = 18;

    private InstallBundleTokenGenerator() {}

    public static String generate() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
