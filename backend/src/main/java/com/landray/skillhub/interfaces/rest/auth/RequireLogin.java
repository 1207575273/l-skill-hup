package com.landray.skillhub.interfaces.rest.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller 类或方法上,声明该端点必须登录才能访问。
 *
 * <p>认证机制"默认放行,由端点自己声明要求"(业务建模文档权限矩阵 5.16):浏览列表 / 搜索 / 详情 / 下载 /
 * 生成安装单匿名可用,登录墙会显著抬高"甩个链接给同事"的成本。所以拦截器默认不拦任何请求,只有显式标了这个
 * 注解(或 {@link RequireAdmin},它隐含要求登录)的方法才会被要求带合法 {@code Authorization: Bearer <token>}。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequireLogin {}
