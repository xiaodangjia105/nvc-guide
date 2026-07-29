package nvc.guide.modules.nvcassistant.service.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.modules.nvcassistant.model.NvcAssistantMessageEntity;
import nvc.guide.modules.nvcassistant.model.NvcAssistantMessageRole;
import nvc.guide.modules.nvcassistant.service.NvcAssistantMessageService;
import nvc.guide.modules.nvcprofile.service.NvcProfileService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 上下文管理器 — 消息管理 + 压缩
 *
 * <p>当消息数超过阈值时，通过 LLM 摘要压缩早期消息，
 * 保留最近消息不变，摘要作为 SystemMessage 插入上下文最前面。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContextManager {

    private final NvcAssistantMessageService messageService;
    private final LlmProviderRegistry llmProviderRegistry;
    private final NvcProfileService profileService;

    @Value("classpath:prompts/nvc-assistant-system.st")
    private Resource systemPromptResource;

    /** 缓存的系统 Prompt 模板 */
    private volatile String cachedSystemPromptTemplate;

    /** 消息轮数阈值，超过此值触发压缩 */
    private static final int COMPRESSION_THRESHOLD = 20;
    /** 压缩后保留的最近消息数 */
    private static final int KEEP_RECENT_MESSAGES = 10;
    /** 降级时截断的早期消息数 */
    private static final int FALLBACK_EARLY_MESSAGES = 5;

    /**
     * 构建上下文消息列表
     *
     * <p>如果消息数超过阈值，自动压缩早期消息。
     * 返回的消息列表包含：系统 Prompt + 摘要（如有） + 最近消息
     *
     * @param conversationId 对话 ID
     * @param userId 用户 ID（用于加载用户档案）
     * @return 上下文消息列表
     */
    public List<Message> buildContext(Long conversationId, Long userId) {
        List<Message> messages = new ArrayList<>();

        // 1. 系统 Prompt（注入用户档案）
        String systemPrompt = loadSystemPrompt(userId);
        messages.add(new SystemMessage(systemPrompt));

        // 2. 获取所有消息
        List<NvcAssistantMessageEntity> allMessages = messageService.getMessages(conversationId);

        if (allMessages.size() <= COMPRESSION_THRESHOLD) {
            // 未超过阈值，直接返回
            for (NvcAssistantMessageEntity msg : allMessages) {
                Message converted = toMessage(msg);
                if (converted != null) {
                    messages.add(converted);
                }
            }
            log.debug("Context built without compression: conversationId={}, messageCount={}",
                conversationId, allMessages.size());
            return messages;
        }

        // 3. 超过阈值，需要压缩
        List<NvcAssistantMessageEntity> earlyMessages = allMessages.subList(0,
            allMessages.size() - KEEP_RECENT_MESSAGES);
        List<NvcAssistantMessageEntity> recentMessages = allMessages.subList(
            allMessages.size() - KEEP_RECENT_MESSAGES, allMessages.size());

        // 4. 生成早期消息的摘要
        String summary = generateSummary(earlyMessages);

        // 5. 构建上下文：摘要 + 最近消息
        messages.add(new SystemMessage("以下是之前对话的摘要：\n" + summary));
        for (NvcAssistantMessageEntity msg : recentMessages) {
            Message converted = toMessage(msg);
            if (converted != null) {
                messages.add(converted);
            }
        }

        log.info("Context compressed: conversationId={}, total={}, early={}, recent={}, summaryLength={}",
            conversationId, allMessages.size(), earlyMessages.size(), recentMessages.size(), summary.length());

        return messages;
    }

    /**
     * 生成对话摘要
     *
     * <p>调用 LLM 将早期消息压缩为不超过 500 字的摘要。
     * 如果 LLM 调用失败，降级为截断模式（取前 5 条消息）。
     */
    private String generateSummary(List<NvcAssistantMessageEntity> messages) {
        // 构建摘要请求内容
        StringBuilder conversationText = new StringBuilder();
        for (NvcAssistantMessageEntity msg : messages) {
            conversationText.append(formatRole(msg.getRole()));
            conversationText.append(": ");
            conversationText.append(msg.getContent());

            // 如果有工具调用记录，添加简要说明
            if (msg.getToolCallsJson() != null && !msg.getToolCallsJson().isEmpty()) {
                conversationText.append(" [调用了工具]");
            }
            conversationText.append("\n");
        }

        String prompt = """
            请将以下对话压缩为简洁的摘要，保留关键信息：
            1. 用户的主要问题和需求
            2. 调用了哪些工具，获得了什么关键结果
            3. 给出了什么建议或结论

            对话内容：
            %s

            请用中文输出摘要，不超过 500 字。""".formatted(conversationText);

        try {
            ChatClient client = llmProviderRegistry.getDefaultChatClient();
            String summary = client.prompt()
                .user(prompt)
                .call()
                .content();

            if (summary != null && !summary.isBlank()) {
                return summary.trim();
            }
            log.warn("LLM returned empty summary, falling back to truncation");
            return buildFallbackSummary(messages);
        } catch (Exception e) {
            log.error("Failed to generate summary, falling back to truncation", e);
            return buildFallbackSummary(messages);
        }
    }

    /**
     * 降级摘要：截断早期消息
     */
    private String buildFallbackSummary(List<NvcAssistantMessageEntity> messages) {
        StringBuilder fallback = new StringBuilder("（摘要生成失败，以下是早期对话的前几条消息）\n");
        int limit = Math.min(messages.size(), FALLBACK_EARLY_MESSAGES);
        for (int i = 0; i < limit; i++) {
            NvcAssistantMessageEntity msg = messages.get(i);
            fallback.append(formatRole(msg.getRole()));
            fallback.append(": ");
            fallback.append(truncateContent(msg.getContent(), 200));
            fallback.append("\n");
        }
        return fallback.toString();
    }

    /**
     * 格式化角色名称
     */
    private String formatRole(NvcAssistantMessageRole role) {
        return switch (role) {
            case USER -> "用户";
            case ASSISTANT -> "助手";
            case SYSTEM -> "系统";
            case TOOL -> "工具";
        };
    }

    /**
     * 截断过长的内容
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "...";
    }

    /**
     * 将实体转换为 Spring AI Message
     *
     * <p>TOOL 角色的消息不直接加入上下文（工具调用结果在 AgentLoop 中动态生成）
     */
    private Message toMessage(NvcAssistantMessageEntity entity) {
        return switch (entity.getRole()) {
            case USER -> new UserMessage(entity.getContent());
            case ASSISTANT -> new AssistantMessage(entity.getContent());
            case SYSTEM -> new SystemMessage(entity.getContent());
            case TOOL -> null; // TOOL 消息不加入上下文
        };
    }

    /**
     * 加载系统 Prompt 并注入用户档案
     */
    private String loadSystemPrompt(Long userId) {
        String template = getOrLoadSystemPromptTemplate();
        String profileSummary = profileService.getUserProfilePrompt(userId);
        return template.replace("{userProfileSummary}", profileSummary != null ? profileSummary : "暂无档案");
    }

    /**
     * 获取或加载系统 Prompt 模板（带缓存）
     */
    private String getOrLoadSystemPromptTemplate() {
        if (cachedSystemPromptTemplate != null) {
            return cachedSystemPromptTemplate;
        }
        synchronized (this) {
            if (cachedSystemPromptTemplate != null) {
                return cachedSystemPromptTemplate;
            }
            try {
                cachedSystemPromptTemplate = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Failed to load system prompt template", e);
                cachedSystemPromptTemplate = "你是 NVC 非暴力沟通练习平台的 AI 助手。";
            }
            return cachedSystemPromptTemplate;
        }
    }
}
