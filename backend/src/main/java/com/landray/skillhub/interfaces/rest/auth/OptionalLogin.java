package com.landray.skillhub.interfaces.rest.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller 类或方法上,声明该端点**匿名可用,但登录后行为不同**。
 *
 * <p>拦截器的既有形态是二态的:标了 {@link RequireLogin} 的拦,没标的直接 {@code return true} ——
 * 而那个早返回发生在解析会话之前,所以**未标注的端点即使请求带了合法 token,也拿不到当前用户**。
 * 这对"匿名可用"的只读端点毫无影响(它们本来就不关心你是谁),但上传链路放开匿名之后出现了第三种需求:
 *
 * <ul>
 *   <li>{@code POST /skills/precheck} 的 {@code canPublishNewVersion} 取决于"当前是谁"(是不是这个 skill
 *       的 owner 或 ADMIN)。只要不解析会话,它对已登录的 owner 也会恒为 false,等于这个字段废掉。
 *   <li>{@code POST /skills} 需要据此决定归属:登录了就归你,没登录就落到 system 名下等人认领。
 * </ul>
 *
 * <p><b>为什么新加一个注解,而不是让拦截器对所有请求都无条件解析会话</b>:无条件解析意味着每个带 token 的
 * 请求(前端登录后所有 API 调用都带)都多一次会话查询,而绝大多数端点并不需要知道你是谁。多一个注解则把
 * 代价精确地付在需要它的地方,并且让"这个端点登录后表现不一样"这件事写在端点上、看得见 ——
 * 与既有的 {@link RequireLogin} / {@link RequireAdmin} 是同一种表达方式。
 *
 * <p><b>语义严格弱于 {@link RequireLogin}</b>:token 缺失、过期、被撤销、所属账号被禁用,一律当作匿名继续放行,
 * 不报 401。调用方从 {@code CurrentUserAccessor.current()} 拿 {@code Optional},必须自己处理空的那一支。
 * 与 {@link RequireLogin} 同时标注时以后者为准(更严的赢)。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface OptionalLogin {}
