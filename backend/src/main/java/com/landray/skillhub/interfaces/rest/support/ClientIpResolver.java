package com.landray.skillhub.interfaces.rest.support;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 从请求里解析客户端 IP,供审计日志与下载计数去重使用。
 *
 * <p>{@code AuthController} 里已有一份同构的私有方法(登录审计用);这里没有把两处合并成一次改动
 * ——{@code AuthController} 不在本任务负责的文件范围内,为了不产生与其他并行任务的合并冲突,新增的两个
 * 使用方(安装脚本渲染的撤销认证审计、下载计数去重)改用这个独立的小工具类,{@code AuthController}
 * 那份留待后续统一收敛时再处理。
 *
 * <p>只用于审计与去重,<b>不是安全校验依据</b>——{@code X-Forwarded-For} 可被客户端伪造。
 */
public final class ClientIpResolver {

    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader(X_FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
