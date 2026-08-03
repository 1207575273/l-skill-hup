package com.landray.skillhub.interfaces.rest.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller 类或方法上,声明该端点必须是 ADMIN 才能访问(隐含要求登录,不需要再叠加
 * {@link RequireLogin})。用于业务建模文档权限矩阵 5.16 里"建号 / 禁用用户 / 踢下线"这一档。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequireAdmin {}
