package com.landray.skillhub.domain.model;

/**
 * skill_version 的认证状态,三态而不是布尔。
 *
 * <p>业务建模文档 3.6:{@code REVOKED} 比 {@code NONE} 是强得多的负面信号——"没人看过"和"看过之后发现有问题"
 * 是完全不同的决策依据,不能用同一个"没有徽章"表达。认证挂在版本上,发新版本徽章立刻失效。
 */
public enum Certification {
    /** 没审过,不显示任何徽章。 */
    NONE,
    /** 审过,通过,展示绿色"平台认证"。 */
    CERTIFIED,
    /** 审过,但事后撤销,展示醒目警示 + 撤销理由。 */
    REVOKED
}
