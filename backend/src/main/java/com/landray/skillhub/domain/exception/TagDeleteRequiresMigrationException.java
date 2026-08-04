package com.landray.skillhub.domain.exception;

import java.util.List;
import java.util.Map;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 删一个仍被引用的 CURATED 词条却没给 {@code migrateToTagId}(业务建模文档 6.2)。
 *
 * <p>为什么只拦 CURATED:FREE 是用户随手打的,零引用时本来就会被自动清理,删掉不留痕迹是它的正常归宿。
 * CURATED 是导航结构的一部分,删掉意味着一批 skill 同时失去分类——那不该是一次点击的副作用,必须显式说明
 * 它们改挂到哪里去。
 */
public class TagDeleteRequiresMigrationException extends BusinessException {

    private static final String CODE = "TAG_DELETE_REQUIRES_MIGRATION";
    private static final int HTTP_STATUS_CONFLICT = 409;

    public TagDeleteRequiresMigrationException(long referenceCount, List<String> affectedSkills) {
        super(
                CODE,
                "该分类还挂着 " + referenceCount + " 个 skill,删除时必须指定 migrateToTagId 把它们迁到别的分类",
                HTTP_STATUS_CONFLICT,
                Map.of("referenceCount", referenceCount, "affectedSkills", affectedSkills));
    }
}
