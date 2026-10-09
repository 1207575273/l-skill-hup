package com.landray.skillhub.infrastructure.storage;

/**
 * {@code artifactKey} 的字符级越界校验,{@link LocalFsArtifactStorage} 与 {@link S3ArtifactStorage} 共用。
 *
 * <p><b>为什么抽出来而不是各自抄一份</b>:{@code domain.storage.ArtifactStorage} 的接口注释把这件事写成了
 * 对<b>所有</b>实现的硬要求("实现必须拒绝含 {@code ..}、绝对路径、控制字符的 key","实现必须对 key 做与
 * store 同样的越界校验")。两份拷贝迟早会漂 —— 给其中一份补了新形态、另一份漏掉,就等于悄悄开了个口子,
 * 而这个项目分发的是会被 Agent 执行的内容。共用同一段代码,是让"两边一致"这件事不依赖人的记性。
 *
 * <p><b>这里只做字符级黑名单,不做落点校验。</b>"解析完的最终位置是否越界"依赖存储形态:本地实现是
 * {@code startsWith(root)},对象存储是 key 前缀 —— 两者判定方式不同,留在各自实现里。黑名单 + 落点校验
 * 是两道独立的闸,不是一道的两种写法:黑名单可能被某种编码绕过,落点校验不依赖具体绕法。
 */
final class ArtifactKeyGuard {

    private ArtifactKeyGuard() {}

    /**
     * 校验通过则原样返回 key,方便链式调用;任何一条不满足直接抛 {@link IllegalArgumentException}。
     *
     * <p>异常信息里带上 key 本身:这类拒绝要么是上游管线有缺陷、要么是真的有人在试探,两种情况都需要知道
     * 被拒的具体内容才能往下查。key 不是凭证,不涉及脱敏。
     */
    static String requireSafe(String artifactKey) {
        if (artifactKey == null || artifactKey.isBlank()) {
            throw new IllegalArgumentException("artifactKey 不能为空");
        }
        if (artifactKey.contains("..")) {
            throw new IllegalArgumentException("artifactKey 不许含相对路径片段, artifactKey=" + artifactKey);
        }
        if (artifactKey.startsWith("/") || artifactKey.startsWith("\\") || artifactKey.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("artifactKey 不许是绝对路径, artifactKey=" + artifactKey);
        }
        if (artifactKey.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("artifactKey 不许含控制字符");
        }
        return artifactKey;
    }
}
