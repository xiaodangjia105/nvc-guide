package nvc.guide.modules.nvcassistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcassistant.dto.AssistantRequest;
import nvc.guide.modules.nvcassistant.dto.AssistantResponse;
import nvc.guide.modules.nvcassistant.dto.ToolCallRecord;
import nvc.guide.modules.nvcassistant.model.NvcAssistantConversationEntity;
import nvc.guide.modules.nvcassistant.model.NvcAssistantMessageEntity;
import nvc.guide.modules.nvcassistant.model.NvcAssistantMessageRole;
import nvc.guide.modules.nvcpractice.tool.NvcToolRegistry;
import nvc.guide.modules.nvcprofile.service.NvcProfileService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 主 Agent 核心对话服务
 * 支持非流式和流式 SSE 对话，通过 Spring AI 自动处理工具调用
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcAssistantService {

    private static final int HISTORY_WINDOW_SIZE = 20;

    private final LlmProviderRegistry llmProviderRegistry;
    private final NvcToolRegistry toolRegistry;
    private final NvcProfileService profileService;
    private final NvcAssistantMessageService messageService;
    private final ObjectMapper objectMapper;

    @Value("classpath:prompts/nvc-assistant-system.st")
    private Resource systemPromptResource;

    /** 缓存的系统 Prompt 模板 */
    private volatile String cachedSystemPromptTemplate;

    /**
     * 非流式对话
     */
    @Transactional
    public AssistantResponse chat(Long userId, AssistantRequest request) {
        // 1. 获取或创建对话
        NvcAssistantConversationEntity conversation = getOrCreateConversation(userId, request.getConversationId());

        // 2. 保存用户消息
        int seq = messageService.getMessageCount(conversation.getId());
        NvcAssistantMessageEntity userMsg = messageService.buildUserMessage(
            conversation.getId(), userId, request.getMessage(), seq++);
        messageService.saveMessage(userMsg);

        // 3. 构建上下文
        List<Message> messages = buildContextMessages(conversation.getId(), userId);

        // 4. 调用 LLM（Spring AI 自动处理工具调用）
        String content;
        try {
            ChatClient client = llmProviderRegistry.getDefaultChatClient();
            content = client.prompt()
                .options(OpenAiChatOptions.builder()
                    .temperature(0.7)
                    .maxTokens(2000)
                    .topP(0.9)
                    .build())
                .messages(messages)
                .toolCallbacks(toolRegistry.toFunctionCallbacks())
                .toolContext(Map.of("nvc.userId", userId))
                .call()
                .content();
        } catch (Exception e) {
            log.error("LLM call failed: conversationId={}", conversation.getId(), e);
            throw new BusinessException(ErrorCode.ASSISTANT_CHAT_FAILED, "对话请求失败: " + e.getMessage());
        }

        if (content == null) {
            content = "";
        }

        // 5. 保存助手回复
        NvcAssistantMessageEntity assistantMsg = messageService.buildAssistantMessage(
            conversation.getId(), userId, content, null, seq++);
        assistantMsg = messageService.saveMessage(assistantMsg);

        // 6. 自动标题（第一轮时）
        if (seq <= 2) {
            String title = generateTitle(request.getMessage());
            messageService.updateConversationTitle(conversation.getId(), title);
        }

        log.info("Assistant chat completed: conversationId={}, userId={}", conversation.getId(), userId);

        return AssistantResponse.builder()
            .conversationId(conversation.getId())
            .messageId(assistantMsg.getId())
            .content(content)
            .toolCalls(List.of())
            .done(true)
            .build();
    }

    /**
     * 流式 SSE 对话
     * 返回 {@link ChatStreamResult}，包含对话 ID、原始内容流和保存回调。
     * Controller 负责 SSE 事件格式化。
     *
     * 注意：不在方法上加 @Transactional，而是在保存消息时使用独立事务
     */
    public ChatStreamResult chatStreamRaw(Long userId, AssistantRequest request) {
        // 1. 获取或创建对话（在独立事务中保存用户消息）
        NvcAssistantConversationEntity conversation = getOrCreateConversation(userId, request.getConversationId());
        long convId = conversation.getId();

        // 2. 保存用户消息
        int seq = messageService.getMessageCount(convId);
        NvcAssistantMessageEntity userMsg = messageService.buildUserMessage(
            convId, userId, request.getMessage(), seq++);
        messageService.saveMessage(userMsg);

        // 3. 构建上下文
        List<Message> messages = buildContextMessages(convId, userId);

        // 4. 流式调用——返回原始内容 chunk
        ChatClient client = llmProviderRegistry.getDefaultChatClient();
        AtomicReference<StringBuilder> contentAccumulator = new AtomicReference<>(new StringBuilder());

        Flux<String> contentStream = client.prompt()
            .options(OpenAiChatOptions.builder()
                .temperature(0.7)
                .maxTokens(2000)
                .topP(0.9)
                .build())
            .messages(messages)
            .toolCallbacks(toolRegistry.toFunctionCallbacks())
            .toolContext(Map.of("nvc.userId", userId))
            .stream()
            .content()
            .filter(s -> s != null && !s.isEmpty())
            .doOnNext(chunk -> contentAccumulator.get().append(chunk));

        // 5. 保存回调：流结束后保存完整回复
        Runnable saveCallback = () -> {
            try {
                String fullContent = contentAccumulator.get().toString();
                if (!fullContent.isEmpty()) {
                    int finalSeq = messageService.getMessageCount(convId);
                    NvcAssistantMessageEntity assistantMsg = messageService.buildAssistantMessage(
                        convId, userId, fullContent, null, finalSeq);
                    messageService.saveMessage(assistantMsg);
                    log.info("Saved streaming assistant reply: conversationId={}, length={}", convId, fullContent.length());
                }
            } catch (Exception e) {
                log.error("Failed to save streaming assistant reply: conversationId={}", convId, e);
            }
        };

        return new ChatStreamResult(convId, contentStream, saveCallback);
    }

    /**
     * 流式对话结果封装
     */
    public record ChatStreamResult(long conversationId, Flux<String> contentStream, Runnable onComplete) {}

    /**
     * @deprecated 使用 {@link #chatStreamRaw(Long, AssistantRequest)}，由 Controller 负责 SSE 格式化
     */
    @Deprecated
    public Flux<String> chatStream(Long userId, AssistantRequest request) {
        ChatStreamResult result = chatStreamRaw(userId, request);

        Flux<String> thinking = Flux.just(formatSseEvent("thinking", "正在思考..."));
        Flux<String> content = result.contentStream()
            .map(chunk -> formatSseEvent("content", chunk));
        Flux<String> done = Flux.just(formatSseEvent("done", "{\"conversationId\":" + result.conversationId() + "}"))
            .doOnComplete(result.onComplete());

        return Flux.concat(thinking, content, done);
    }

    // ==================== 内部方法 ====================

    /**
     * 获取或创建对话
     */
    private NvcAssistantConversationEntity getOrCreateConversation(Long userId, Long conversationId) {
        if (conversationId != null) {
            return messageService.getConversationOrThrow(conversationId, userId);
        }
        return messageService.createConversation(userId);
    }

    /**
     * 构建上下文消息列表（系统 Prompt + 最近 20 轮历史）
     */
    private List<Message> buildContextMessages(Long conversationId, Long userId) {
        List<Message> messages = new ArrayList<>();

        // 1. 系统 Prompt（注入用户档案）
        String systemPrompt = loadSystemPrompt(userId);
        messages.add(new SystemMessage(systemPrompt));

        // 2. 最近 20 轮历史
        List<NvcAssistantMessageEntity> history = messageService.getRecentMessages(conversationId);
        for (NvcAssistantMessageEntity msg : history) {
            switch (msg.getRole()) {
                case USER -> messages.add(new UserMessage(msg.getContent()));
                case ASSISTANT -> messages.add(new AssistantMessage(msg.getContent()));
                case SYSTEM -> messages.add(new SystemMessage(msg.getContent()));
                // TOOL 消息在当前简化模型中不直接加入历史
            }
        }

        return messages;
    }

    /**
     * 加载系统 Prompt 并注入用户档案（带缓存）
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

    /**
     * 生成对话标题（取用户消息前 50 字符）
     */
    private String generateTitle(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "新对话";
        }
        String title = userMessage.replaceAll("[\\r\\n]+", " ").trim();
        return title.length() > 50 ? title.substring(0, 50) + "..." : title;
    }

    /**
     * 格式化 SSE 事件
     * 格式: event: {type}\ndata: {data}\n\n
     */
    private String formatSseEvent(String event, String data) {
        return "event: " + event + "\ndata: " + data + "\n\n";
    }
}
