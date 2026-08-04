package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 删除时给的 {@code migrateToTagId} 不能用(业务建模文档 5.7:"校验目标存在且 kind 与被删标签一致")。
 *
 * <p><b>kind 必须一致这条不是洁癖。</b>把一个 CURATED 迁到 FREE 上,那批 skill 表面上"还有标签",实际上
 * 从导航里整体消失了,而操作者以为自己刚做的是一次无损搬迁。反过来把 FREE 迁到 CURATED,那批 skill 会凭空
 * 多出一个分类,还可能撞破每个 skill 至多 3 个 CURATED 的上限。
 */
public class TagMigrationTargetInvalidException extends BusinessException {

    private static final String CODE = "TAG_MIGRATION_TARGET_INVALID";

    public TagMigrationTargetInvalidException(String message) {
        super(CODE, message);
    }
}
