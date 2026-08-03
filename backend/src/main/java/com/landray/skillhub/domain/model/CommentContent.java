package com.landray.skillhub.domain.model;

import com.landray.skillhub.domain.exception.CommentContentInvalidException;

/**
 * 评论正文。业务建模文档 5.11:非空、上限 2000 字符、<b>纯文本存储</b>。
 *
 * <p><b>为什么是值对象而不是在 application 层写几行 if</b>:长度与非空是业务规则,不是"解释外部输入"。
 * 发评论、将来可能有的编辑评论都要守同一条,规则散在各调用方迟早会漂 —— 而漂掉的那一处不会报错,
 * 只会悄悄放进一条 5000 字的评论。这是本项目允许建值对象的判据(轻量 DDD 第二节:只给<b>真正带业务规则</b>
 * 的概念做,裸 String 不丢人)。
 *
 * <p><b>刻意不做任何转义或富文本处理。</b>存进去什么样,取出来什么样。渲染用户内容是 XSS 的主要入口,
 * 而这个平台本来就在分发会被 Agent 执行的东西,不再增加攻击面;转义是展示时机的事,由前端按纯文本渲染完成。
 * 在存储侧做转义反而有害:同一份内容会被反复转义,而且没法判断某个 {@code &lt;} 是用户真的打了尖括号还是
 * 上一轮转义的残留。
 *
 * <p>长度按<b>去掉首尾空白之后</b>计算 —— 否则一串空格就能把人卡在上限外,而用户看着自己没超也解释不通。
 */
public record CommentContent(String value) {

    /** 与 {@code skill_comment.content} 的列注释一致。列是 TEXT,上限由应用层守。 */
    public static final int MAX_LENGTH = 2000;

    public CommentContent {
        if (value == null) {
            throw new CommentContentInvalidException("评论正文不能为空");
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new CommentContentInvalidException("评论正文不能为空");
        }
        if (value.length() > MAX_LENGTH) {
            throw new CommentContentInvalidException(
                    "评论正文不得超过 " + MAX_LENGTH + " 字符,实际为 " + value.length());
        }
    }
}
