package com.landray.skillhub.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * sha256 十六进制小写字符串的计算工具。
 *
 * <p>被两个互不相干的概念复用(业务建模文档 3.7):{@code content_digest}(规范化文件树清单的摘要)与
 * {@code sha256}(最终 zip 字节的摘要)——两者算的"输入"完全不同,但"字节数组 -&gt; 十六进制小写字符串"这一步
 * 是同一段逻辑,抽成一个纯函数避免两处各写一份可能出现大小写/位数不一致的 hex 编码。
 *
 * <p>{@code SHA-256} 是 JDK 保证内建的算法(见 {@code MessageDigest} 标准算法名列表),{@link NoSuchAlgorithmException}
 * 在本项目运行的任何 JDK 21 环境下不可能真正抛出,包成 {@link IllegalStateException} 而不是继续往外抛检查异常,
 * 让调用方不必为一个不可能发生的分支写 try-catch。
 */
public final class Sha256 {

    private static final String ALGORITHM = "SHA-256";

    private Sha256() {}

    public static String hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            return toHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 " + ALGORITHM + " 算法实现,环境异常", e);
        }
    }

    public static String hex(String utf8Text) {
        return hex(utf8Text.getBytes(StandardCharsets.UTF_8));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
