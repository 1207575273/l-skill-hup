package com.landray.skillhub.domain.model;

/**
 * skill 的上下架状态。
 *
 * <p>业务建模文档 4.8:下架后不出现在列表,已生成的安装单仍可用但脚本会提示。**不提供物理删除**——
 * 一旦被人装过就有了外部引用,物理删除会让已流传的安装命令报出难以理解的错误。
 */
public enum SkillStatus {
    /** 正常展示,出现在列表与搜索中。 */
    PUBLISHED,
    /** 已下架:不出现在列表 / 搜索,但仍可通过已知的直达链接(详情页、安装单)访问。 */
    UNLISTED
}
