package com.landray.skillhub.shared.exception;

/**
 * 业务异常基类。
 *
 * <p>由 domain / application 层抛出,统一由 interfaces 层的 RestControllerAdvice 映射成 HTTP 响应。 禁止在 Controller
 * 里 try-catch 业务异常。
 *
 * <p>本类被 domain 依赖,因此必须保持纯 Java、零框架注解。
 */
public abstract class BusinessException extends RuntimeException {

    private final String code;

    protected BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** 业务错误码,前端据此做差异化处理,不要依赖 message 文本。 */
    public String code() {
        return code;
    }
}
