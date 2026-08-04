package com.landray.skillhub.domain.model;

import java.util.Locale;

import com.landray.skillhub.domain.exception.CertificationDecisionInvalidException;

/**
 * 一次认证状态变更的完整意图:变成什么状态 + 附带的意见(业务建模文档 5.8)。
 *
 * <p>做成值对象而不是把 {@code (Certification, String)} 两个参数一路传下去,是因为<b>这两个字段之间有不变量</b>:
 * {@code REVOKED} 时 {@code note} 必填。分开传的话,这条规则只能写在某个调用点上,而"某个调用点"意味着下一个
 * 调用点会漏掉它。放进构造器,任何拿到 {@code CertificationDecision} 的代码都可以假定它已经合法。
 *
 * <p>{@code note} 归一化后为空一律存 {@code null},不留空串——库里同时出现 {@code null} 和 {@code ""} 两种
 * "没有意见",下游每次判断都要写两个条件,而漏一个就是页面上多出一行空白的意见栏。
 */
public record CertificationDecision(Certification certification, String note) {

    /** 与 {@code skill_version.certify_note} 的列宽一致(业务建模文档 4.9)。 */
    public static final int MAX_NOTE_LENGTH = 256;

    public CertificationDecision {
        if (certification == null) {
            throw new CertificationDecisionInvalidException("认证状态不能为空");
        }
        note = note == null || note.isBlank() ? null : note.trim();
        if (certification == Certification.REVOKED && note == null) {
            throw new CertificationDecisionInvalidException("撤销认证必须填写理由,它会原样进安装脚本的警示文本");
        }
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new CertificationDecisionInvalidException("认证意见不得超过 " + MAX_NOTE_LENGTH + " 个字符");
        }
    }

    /**
     * 从接口入参构造,认证状态大小写不敏感。
     *
     * <p>不在 {@link Certification} 上做 {@code fromParam}:那个枚举被读侧大量使用,给它加一个只有写侧一个
     * 调用点的解析方法,等于把写侧的输入校验责任摊到一个纯展示用的类型上。
     */
    public static CertificationDecision of(String rawCertification, String rawNote) {
        if (rawCertification == null || rawCertification.isBlank()) {
            throw new CertificationDecisionInvalidException("认证状态不能为空");
        }
        Certification parsed;
        try {
            parsed = Certification.valueOf(rawCertification.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CertificationDecisionInvalidException(
                    "不支持的认证状态, certification=" + rawCertification);
        }
        return new CertificationDecision(parsed, rawNote);
    }
}
