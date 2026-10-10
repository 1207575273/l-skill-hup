package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 已登录的上传请求里还带了"声明归属"。
 *
 * <p>声明归属只为匿名上传而存在:匿名上传的包落在内置 system 名下(无主待认领),那两个自称字段是旁人判断
 * "这大概是谁的包"的唯一线索。而已登录上传的归属人就是上传者本人,已经有账号背书 —— 此时再存一份自称归属,
 * 恰好制造了这个设计要消除的那种歧义:同一个包上挂着两份互相独立、可能矛盾的归属信息。
 *
 * <p><b>为什么报错而不是静默忽略</b>:静默丢弃用户填过的输入,会变成"我明明填了,保存后却没了"这类最难复现的
 * 问题报告。前端在已登录时根本不会展示这两个字段,所以正常链路碰不到这个异常;碰到了就说明调用方理解有误,
 * 响亮地说出来比替它猜要好。
 */
public class DeclaredOwnerNotAllowedException extends BusinessException {

    private static final String CODE = "DECLARED_OWNER_NOT_ALLOWED";

    public DeclaredOwnerNotAllowedException() {
        super(CODE, "已登录上传时归属人就是你本人,不需要再填声明归属");
    }
}
