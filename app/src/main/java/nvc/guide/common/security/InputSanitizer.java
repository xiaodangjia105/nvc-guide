package nvc.guide.common.security;

import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 输入安全校验器
 * 统一校验用户输入的长度和安全性
 */
@Component
@Slf4j
public class InputSanitizer {

    /** 练习对话最大长度 */
    private static final int MAX_PRACTICE_MESSAGE_LENGTH = 2000;

    /** 主 Agent 对话最大长度 */
    private static final int MAX_ASSISTANT_MESSAGE_LENGTH = 4000;

    /** Prompt 注入检测器 */
    private final PromptInjectionDetector injectionDetector;

    public InputSanitizer(PromptInjectionDetector injectionDetector) {
        this.injectionDetector = injectionDetector;
    }

    /**
     * 校验练习对话消息
     */
    public void validatePracticeMessage(String message) {
        validateLength(message, MAX_PRACTICE_MESSAGE_LENGTH, "练习消息");
        checkInjection(message, "练习对话");
    }

    /**
     * 校验主 Agent 对话消息
     */
    public void validateAssistantMessage(String message) {
        validateLength(message, MAX_ASSISTANT_MESSAGE_LENGTH, "助手消息");
        checkInjection(message, "主Agent对话");
    }

    /**
     * 通用长度校验
     */
    public void validateLength(String message, int maxLength, String label) {
        if (message == null || message.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不能为空");
        }
        if (message.length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                label + "长度不能超过 " + maxLength + " 个字符");
        }
    }

    /**
     * Prompt 注入检测
     */
    private void checkInjection(String message, String context) {
        if (injectionDetector.detect(message)) {
            log.warn("Prompt injection detected in {}: {}", context,
                message.substring(0, Math.min(100, message.length())));
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                "输入包含不安全内容，请修改后重试");
        }
    }
}
