package nvc.guide.modules.nvcassistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.ai.LlmProviderRegistry;
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
        ChatClient client = llmProviderRegistry.getDefaultChatClient();
        String content = client.prompt()
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

        if (content == null) {
            content = "";
        }

        // 5. 工具调用记录（简化：不提取详细记录，由 Spring AI 自动处理）
        List<ToolCallRecord> toolCalls = List.of();
        String toolCallsJson = null;

        // 6. 保存助手回复
        NvcAssistantMessageEntity assistantMsg = messageService.buildAssistantMessage(
            conversation.getId(), userId, content, toolCallsJson, seq++);
        assistantMsg = messageService.saveMessage(assistantMsg);

        // 7. 自动标题（第一轮时）
        if (seq <= 2) {
            String title = generateTitle(request.getMessage());
            messageService.updateConversationTitle(conversation.getId(), title);
        }

        log.info("Assistant chat completed: conversationId={}, userId={}, toolCalls={}",
            conversation.getId(), userId, toolCalls.size());

        return AssistantResponse.builder()
            .conversationId(conversation.getId())
            .messageId(assistantMsg.getId())
            .content(content)
            .toolCalls(toolCalls)
            .done(true)
            .build();
    }

    /**
     * 流式 SSE 对话
     * 返回 Flux<String>，每个元素是一个 SSE 事件行
     */
    @Transactional
    public Flux<String> chatStream(Long userId, AssistantRequest request) {
        // 1. 获取或创建对话
        NvcAssistantConversationEntity conversation = getOrCreateConversation(userId, request.getConversationId());

        // 2. 保存用户消息
        int seq = messageService.getMessageCount(conversation.getId());
        NvcAssistantMessageEntity userMsg = messageService.buildUserMessage(
            conversation.getId(), userId, request.getMessage(), seq++);
        messageService.saveMessage(userMsg);

        // 3. 构建上下文
        List<Message> messages = buildContextMessages(conversation.getId(), userId);

        // 4. 获取 ChatClient 和工具
        ChatClient client = llmProviderRegistry.getDefaultChatClient();
        long convId = conversation.getId();
        long userIdFinal = userId;

        // 5. 流式调用
        return Flux.defer(() -> {
            // 先发送 thinking 事件
            Flux<String> thinking = Flux.just(formatSseEvent("thinking", "正在思考..."));

            // 构建流式请求
            var spec = client.prompt()
                .options(OpenAiChatOptions.builder()
                    .temperature(0.7)
                    .maxTokens(2000)
                    .topP(0.9)
                    .build())
                .messages(messages)
                .toolCallbacks(toolRegistry.toFunctionCallbacks())
                .toolContext(Map.of("nvc.userId", userIdFinal));

            // 流式获取内容
            Flux<String> contentStream = spec.stream()
                .content()
                .filter(s -> s != null && !s.isEmpty())
                .map(chunk -> formatSseEvent("content", chunk));

            // 完成事件
            Flux<String> doneEvent = Flux.just(
                formatSseEvent("done", "{\"conversationId\":" + convId + "}"));

            return Flux.concat(thinking, contentStream, doneEvent);
        });
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
     * 加载系统 Prompt 并注入用户档案
     */
    private String loadSystemPrompt(Long userId) {
        String template;
        try {
            template = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load system prompt template", e);
            template = "你是 NVC 非暴力沟通练习平台的 AI 助手。";
        }

        String profileSummary = profileService.getUserProfilePrompt(userId);
        return template.replace("{userProfileSummary}", profileSummary);
    }

    /**
     * 从 AssistantMessage 中提取工具调用记录
     */
    private List<ToolCallRecord> extractToolCalls(AssistantMessage output) {
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }

        return toolCalls.stream()
            .map(tc -> ToolCallRecord.builder()
                .toolName(tc.name())
                .arguments(tc.arguments())
                .result("已执行")
                .success(true)
                .build())
            .toList();
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
     * 序列化工具调用记录为 JSON
     */
    private String serializeToolCalls(List<ToolCallRecord> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(toolCalls);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize tool calls", e);
            return null;
        }
    }

    /**
     * 格式化 SSE 事件
     */
    private String formatSseEvent(String event, String data) {
        return "event: " + event + "\ndata: " + data + "\n\n";
    }
}
