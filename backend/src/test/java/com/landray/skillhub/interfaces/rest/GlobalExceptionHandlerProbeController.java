package com.landray.skillhub.interfaces.rest;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.landray.skillhub.shared.exception.ResourceConflictException;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;

/**
 * 仅供 {@link GlobalExceptionHandlerTest} 使用的假控制器。
 *
 * <p>本仓库目前还没有落地任何业务 Controller(Skill 相关领域模型还在搭建中),要验证
 * {@link GlobalExceptionHandler} 的异常映射链路只能自备一个会按需抛出各类异常的探针。放在 src/test 下、独立成
 * 顶层类(不是测试类的嵌套类),以便 {@code @WebMvcTest(controllers = ...)} 稳定发现它。
 */
@RestController
public class GlobalExceptionHandlerProbeController {

    @GetMapping("/test-support/not-found")
    public void triggerNotFound() {
        throw new ResourceNotFoundException("SKILL_NOT_FOUND", "skill 不存在", Map.of("skillId", 99));
    }

    @GetMapping("/test-support/conflict")
    public void triggerConflict() {
        throw new ResourceConflictException("SKILL_NAME_DUPLICATED", "已存在同名 skill", Map.of("skillId", 42));
    }

    @PostMapping("/test-support/validate")
    public void triggerValidationFailure(@Valid @RequestBody ProbeRequest request) {
        // 校验在到达方法体前就会失败,方法体不会被执行。
    }

    @GetMapping("/test-support/boom")
    public void triggerUnexpectedException() {
        throw new IllegalStateException("探针内部状态异常,细节不许泄漏给客户端");
    }

    @GetMapping("/test-support/validate-param")
    public void triggerParamValidationFailure(@RequestParam @NotBlank String value) {
        // 校验在到达方法体前就会失败,方法体不会被执行。
    }

    public record ProbeRequest(@NotBlank String name) {}
}
