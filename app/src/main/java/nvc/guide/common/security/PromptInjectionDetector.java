package nvc.guide.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt 注入检测器
 * 检测用户输入中可能的 Prompt 注入攻击
 */
@Component
@Slf4j
public class PromptInjectionDetector {

    /**
     * 注入模式列表
     * 注意：这些模式需要平衡安全性和误报率
     * 过于宽松会漏检，过于严格会误杀正常 NVC 对话
     */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        // 系统级指令覆盖（英文）
        Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+instructions"),
        Pattern.compile("(?i)ignore\\s+(all\\s+)?prior\\s+instructions"),
        Pattern.compile("(?i)disregard\\s+(all\\s+)?previous\\s+instructions"),
        Pattern.compile("(?i)you\\s+are\\s+now\\s+a\\s+"),
        Pattern.compile("(?i)new\\s+instructions?\\s*:\\s*"),
        Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
        Pattern.compile("(?i)forget\\s+(everything|all|your\\s+instructions)"),
        Pattern.compile("(?i)override\\s+(your|system|all)\\s+instructions"),

        // 角色劫持（英文）
        Pattern.compile("(?i)act\\s+as\\s+(?:a\\s+)?(?:different|new|another)"),
        Pattern.compile("(?i)pretend\\s+you\\s+are"),
        Pattern.compile("(?i)from\\s+now\\s+on\\s+you\\s+are"),

        // 中文注入模式
        Pattern.compile("(?i)忽略(之前|上面|所有|全部)(的)?(指令|提示|规则|要求)"),
        Pattern.compile("(?i)从现在开始你是"),
        Pattern.compile("(?i)假装你是"),
        Pattern.compile("(?i)新的指令[：:]"),
        Pattern.compile("(?i)系统提示[：:]你是一个"),

        // 指令泄露尝试
        Pattern.compile("(?i)print\\s+(your|the)\\s+(system|initial|original)\\s+prompt"),
        Pattern.compile("(?i)repeat\\s+(your|the)\\s+(system|initial)\\s+(prompt|instructions)"),
        Pattern.compile("(?i)show\\s+me\\s+(your|the)\\s+(system|initial)\\s+(prompt|instructions)")
    );

    /**
     * 检测输入是否包含 Prompt 注入
     * @return true 表示检测到注入
     */
    public boolean detect(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        // 长度超过阈值才检测（短消息不太可能是注入）
        if (input.length() < 10) {
            return false;
        }

        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.debug("Injection pattern matched: {}", pattern.pattern());
                return true;
            }
        }

        return false;
    }
}
