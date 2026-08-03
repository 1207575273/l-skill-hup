package com.landray.skillhub.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 一次性生成的不透明会话令牌:明文只在生成时短暂存在(登录响应里出现一次),库里只存
 * {@link #hash()}(业务建模文档 3.1、4.5)。
 *
 * <p>值对象满足"只给真正带业务规则的概念做"的判定标准——生成算法(256 位随机数)与哈希算法(sha256)是安全边界,
 * 不是给 String 套壳,与已有的 {@code SkillName} 同构。
 *
 * <p>只用 JDK 内建的 {@link SecureRandom} / {@link MessageDigest} / {@link HexFormat},零框架依赖,满足 domain
 * 纯净性。
 */
public record SessionToken(String rawValue, String hash) {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 256 位随机数,与 {@code user_session.token_hash CHAR(64)}(sha256 十六进制长度)不是同一件事,不要混淆。 */
    private static final int TOKEN_BYTE_LENGTH = 32;

    private static final String DIGEST_ALGORITHM = "SHA-256";

    public SessionToken {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("rawValue 不能为空");
        }
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("hash 不能为空");
        }
    }

    /** 生成一枚新令牌:明文 + 对应的 sha256 哈希。 */
    public static SessionToken generate() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        String raw = HexFormat.of().formatHex(randomBytes);
        return new SessionToken(raw, sha256Hex(raw));
    }

    /** 对外部传入的明文令牌算哈希,用于按 {@code token_hash} 查库(会话解析路径不需要生成新令牌)。 */
    public static String hashOf(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("rawValue 不能为空");
        }
        return sha256Hex(rawValue);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 强制要求的标准算法(JCA 规范),任何合规 JVM 都必须提供——这里抛出说明运行时环境本身
            // 已经不正常,不是可恢复的业务异常,直接转成非受检异常让它冒泡。
            throw new IllegalStateException(DIGEST_ALGORITHM + " 摘要算法不可用", e);
        }
    }
}
