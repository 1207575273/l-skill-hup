package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 匿名上传试图新建 FREE 标签。
 *
 * <p>FREE 标签是**全局命名空间**:任何人新造一个词,它就会出现在所有人的筛选面板上,并且需要 ADMIN 事后
 * 治理(合并、改 kind、删除,见 {@code TagGovernanceApplicationService})。放开匿名新建,等于把一张全局词表的
 * 写权限交给未经身份认定的请求 —— 灌词的成本为零,清理的成本全落在 ADMIN 身上。
 *
 * <p>匿名上传仍然可以选 CURATED 标签:那些是受治理的预置词条,选择它们不会新增任何东西。要打自由标签,
 * 先登录(或上传后认领),届时标签的创建者是一个可追溯的账号。
 */
public class AnonymousFreeTagNotAllowedException extends BusinessException {

    private static final String CODE = "ANONYMOUS_FREE_TAG_NOT_ALLOWED";

    public AnonymousFreeTagNotAllowedException() {
        super(CODE, "匿名上传不能新建自由标签,请改选已有标签,或登录后再打自由标签");
    }
}
