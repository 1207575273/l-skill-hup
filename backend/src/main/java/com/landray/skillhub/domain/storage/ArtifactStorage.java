package com.landray.skillhub.domain.storage;

import java.io.InputStream;

/**
 * 制品(skill zip)的存取接口。
 *
 * <p><b>业务代码一律走这个接口,不许直连文件系统或任何存储 SDK</b>(根 CLAUDE.md 第十一节)。当前唯一实现是
 * {@code infrastructure.storage.LocalFsArtifactStorage};将来真需要对象存储时只写 S3 标准 API,不碰厂商特有接口,
 * 这样 MinIO / RustFS 之间换只是改一行 endpoint 配置。
 *
 * <p><b>{@code artifactKey} 对本接口是不透明的</b>——键怎么构造是上传管线的事,存储侧只负责存取。但实现必须把它
 * 当作<b>不可信输入</b>校验(见 {@link #store}),即使它眼下只来自本库的 {@code skill_version.artifact_key}。
 *
 * <p>读写形态刻意不对称:写入时字节本来就已经在内存里(重新打包的产物),用 {@code byte[]} 更直白;读取要直接
 * 灌给 HTTP 响应,用 {@link InputStream} 免得把整个制品再读进堆。上限见业务建模文档 5.2。
 */
public interface ArtifactStorage {

    /**
     * 写入制品。同 key 重复写入应当覆盖。
     *
     * <p>实现<b>必须拒绝</b>含 {@code ..}、绝对路径、控制字符的 key —— 这是纵深防御:上传链路已经做过路径校验,
     * 但存储层是制品落盘前的最后一道,而这个项目分发的是会被 Agent 执行的内容,不能指望上游永远没有疏漏。
     */
    void store(String artifactKey, byte[] content);

    /**
     * 读取制品。key 不存在时抛 {@code ResourceNotFoundException},不返回 null —— 让调用方漏判空的可能性归零。
     */
    InputStream openRead(String artifactKey);

    /**
     * 删除制品。<b>幂等</b>:key 不存在时正常返回,不抛异常。
     *
     * <p>与 {@link #openRead} 对缺失 key 抛异常刻意相反,因为两者的调用语境不同:读取时"文件不在"意味着调用方
     * 拿到的数据是错的,必须炸;删除时"文件不在"意味着目标状态已经达成,抛异常只会让上层的收尾流程半途而废,
     * 反而留下更多孤儿文件。
     *
     * <p>实现必须对 key 做与 {@link #store} 同样的越界校验 —— 少一处就等于开了个"用删除做目录穿越"的口子,
     * 而删除的破坏力比读取更大。
     */
    void delete(String artifactKey);

    boolean exists(String artifactKey);

    /** 制品字节数。用于下载时设置 {@code Content-Length},免得调用方为了拿长度先把整个流读一遍。 */
    long sizeOf(String artifactKey);
}
