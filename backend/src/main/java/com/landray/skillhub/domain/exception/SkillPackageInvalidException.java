package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * skill 安装包未通过服务端校验(业务建模文档 5.2 / 5.3),HTTP 400。
 *
 * <p>由 {@code code} 参数区分具体是哪一类违规(超出解包上限的六项之一、路径不安全、含符号链接、结构不合规、
 * frontmatter 非法……),而不是每类违规各建一个异常类——这类违规有十种以上,枚举成类只会让
 * {@link com.landray.skillhub.domain.service.SkillPackageValidator} 的每个校验点都要多一次 import,
 * 与 {@code shared.exception} 下 {@code ResourceNotFoundException} / {@code ResourceConflictException}
 * 按 code 参数化是同一个理由。测试按 {@code code()} 区分断言,不靠 Java 类型系统区分。
 */
public class SkillPackageInvalidException extends BusinessException {

    public SkillPackageInvalidException(String code, String message) {
        super(code, message);
    }
}
