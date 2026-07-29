package nvc.guide.modules.nvcassistant.service.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcprofile.service.NvcProfileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 系统提示词构建器
 *
 * <p>职责：
 * <ul>
 *   <li>加载并缓存系统提示词模板（nvc-assistant-system-v2.st）</li>
 *   <li>注入用户档案、上下文摘要、当前时间等动态信息</li>
 *   <li>输出最终的系统提示词字符串</li>
 * </ul>
 *
 * <p>使用 double-checked locking 缓存模板，避免重复读取文件。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PromptBuilder {

    private final NvcProfileService profileService;

    @Value("classpath:prompts/nvc-assistant-system-v2.st")
    private Resource systemPromptResource;

    /** 缓存的系统提示词模板 */
    private volatile String cachedTemplate;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 构建最终的系统提示词
     *
     * @param userId         用户 ID（用于加载用户档案）
     * @param contextSummary 上下文摘要（压缩后的内容，无压缩时为 null 或空字符串）
     * @return 完整的系统提示词
     */
    public String buildSystemPrompt(Long userId, String contextSummary) {
        String template = getOrLoadTemplate();

        // 注入用户档案
        String profileSummary = profileService.getUserProfilePrompt(userId);
        template = template.replace("{userProfileSummary}", profileSummary != null ? profileSummary : "暂无档案");

        // 注入上下文摘要
        template = template.replace("{contextSummary}", contextSummary != null ? contextSummary : "");

        // 注入当前时间
        template = template.replace("{currentTime}", LocalDateTime.now().format(TIME_FORMATTER));

        return template;
    }

    /**
     * 获取或加载系统提示词模板（double-checked locking 缓存）
     */
    private String getOrLoadTemplate() {
        if (cachedTemplate != null) {
            return cachedTemplate;
        }
        synchronized (this) {
            if (cachedTemplate != null) {
                return cachedTemplate;
            }
            try {
                cachedTemplate = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
                log.info("[PromptBuilder] System prompt template loaded, length={}", cachedTemplate.length());
            } catch (IOException e) {
                log.error("[PromptBuilder] Failed to load system prompt template", e);
                cachedTemplate = "你是 NVC 非暴力沟通练习平台的 AI 助手。";
            }
            return cachedTemplate;
        }
    }
}
