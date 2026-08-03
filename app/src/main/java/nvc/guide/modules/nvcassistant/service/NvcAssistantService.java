package nvc.guide.modules.nvcassistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcassistant.dto.AssistantRequest;
import nvc.guide.modules.nvcassistant.model.NvcAssistantConversationEntity;
import nvc.guide.modules.nvcassistant.model.NvcAssistantMessageEntity;
import nvc.guide.modules.nvcassistant.service.agent.AgentEvent;
import nvc.guide.modules.nvcassistant.service.agent.AgentLoop;
import nvc.guide.modules.nvcassistant.service.agent.ContextManager;
import nvc.guide.modules.nvcassistant.service.agent.PromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主 Agent 核心对话服务
 *
 * <p>使用 AgentLoop 实现多轮工具调用，替代 Spring AI 自动工具处理。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcAssistantService {

    private final NvcAssistantMessageService messageService;
    private final AgentLoop agentLoop;
    private final ContextManager contextManager;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    /**
     * 流式 SSE 对话（使用 AgentLoop）
     *
     * <p>返回 {@link ChatStreamResult}，包含对话 ID、SSE 事件流和保存回调。
     */
    public ChatStreamResult chatStreamRaw(Long userId, AssistantRequest request) {
        // 1. 获取或创建对话
        NvcAssistantConversationEntity conversation = getOrCreateConversation(userId, request.getConversationId());
        long convId = conversation.getId();

        // 2. 保存用户消息
        int seq = messageService.getMessageCount(convId);
        NvcAssistantMessageEntity userMsg = messageService.buildUserMessage(
            convId, userId, request.getMessage(), seq++);
        messageService.saveMessage(userMsg);

        // 3. 构建上下文（历史消息 + 摘要，不含系统 Prompt）
        ContextManager.ContextResult contextResult = contextManager.buildContext(convId, userId);

        // 4. 通过 PromptBuilder 构建系统提示词（注入用户档案 + 上下文摘要 + 当前时间）
        String systemPrompt = promptBuilder.buildSystemPrompt(userId, contextResult.summary());

        // 5. 组装最终上下文：系统 Prompt + 历史消息
        List<Message> contextMessages = new ArrayList<>();
        contextMessages.add(new SystemMessage(systemPrompt));
        contextMessages.addAll(contextResult.messages());

        // 6. 通过 AgentLoop 执行（返回 SSE 事件流）
        Flux<AgentEvent> eventStream = agentLoop.executeStream(userId, convId, contextMessages, request.getMessage());

        // 7. 用 AtomicInteger 实现可变序列号（工具消息需要递增 seq）
        final AtomicInteger seqCounter = new AtomicInteger(seq);
        final boolean isFirstRound = seq <= 1;

        // 收集工具调用记录（TOOLCALL_START + TOOLCALL_END 配对）
        final ConcurrentLinkedQueue<Map<String, Object>> toolCallRecords = new ConcurrentLinkedQueue<>();
        // 临时存储 TOOLCALL_START 事件，等待配对的 TOOLCALL_END
        final ConcurrentLinkedQueue<AgentEvent> pendingToolStarts = new ConcurrentLinkedQueue<>();

        Flux<AgentEvent> processedStream = eventStream
            .doOnNext(event -> {
                switch (event.type()) {
                    case TOOLCALL_START -> {
                        pendingToolStarts.add(event);
                        // 保存 TOOL 消息（工具调用开始）
                        saveToolMessage(convId, userId, event.data(),
                            "调用工具: " + event.data() + "(" + event.metadata().get("arguments") + ")",
                            seqCounter.getAndIncrement());
                    }
                    case TOOLCALL_END -> {
                        // 配对 TOOLCALL_START
                        AgentEvent startEvent = pendingToolStarts.poll();
                        String toolName = event.data();
                        boolean success = Boolean.TRUE.equals(event.metadata().get("success"));
                        String result = String.valueOf(event.metadata().getOrDefault("result", ""));
                        long durationMs = event.metadata().get("durationMs") instanceof Number n ? n.longValue() : 0;

                        // 记录工具调用
                        toolCallRecords.add(Map.of(
                            "toolName", toolName,
                            "success", success,
                            "result", truncate(result, 500),
                            "durationMs", durationMs
                        ));

                        // 保存 TOOL 消息（工具调用结果）
                        String resultSummary = success
                            ? "工具 " + toolName + " 执行成功: " + truncate(result, 200)
                            : "工具 " + toolName + " 执行失败: " + truncate(result, 200);
                        saveToolMessage(convId, userId, toolName, resultSummary, seqCounter.getAndIncrement());
                    }
                    case CONTENT -> {
                        // 保存助手回复（附带工具调用记录 JSON）
                        String toolCallsJson = null;
                        if (!toolCallRecords.isEmpty()) {
                            try {
                                toolCallsJson = objectMapper.writeValueAsString(new ArrayList<>(toolCallRecords));
                            } catch (Exception e) {
                                log.warn("Failed to serialize tool call records", e);
                            }
                        }
                        saveAssistantReply(convId, userId, event.data(), toolCallsJson, seqCounter.getAndIncrement());
                        toolCallRecords.clear();
                    }
                    default -> { /* THINKING, DONE, ERROR — 不保存 */ }
                }
            })
            .doOnComplete(() -> {
                // 自动标题（第一轮时）
                if (isFirstRound) {
                    String title = generateTitle(request.getMessage());
                    messageService.updateConversationTitle(convId, title);
                }
                log.info("[NvcAssistantService] Stream completed: conversationId={}, userId={}", convId, userId);
            });

        // 8. 保存回调（兼容旧接口）
        Runnable saveCallback = () -> {
            // 内容已在 doOnNext 中保存，这里只做日志
            log.debug("[NvcAssistantService] Save callback invoked: conversationId={}", convId);
        };

        return new ChatStreamResult(convId, processedStream, saveCallback);
    }

    /**
     * 流式对话结果封装
     */
    public record ChatStreamResult(long conversationId, Flux<AgentEvent> eventStream, Runnable onComplete) {}

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
     * 保存助手回复（附带工具调用记录）
     */
    private void saveAssistantReply(Long conversationId, Long userId, String content,
                                     String toolCallsJson, int seq) {
        try {
            if (content != null && !content.isEmpty()) {
                NvcAssistantMessageEntity assistantMsg = messageService.buildAssistantMessage(
                    conversationId, userId, content, toolCallsJson, seq);
                messageService.saveMessage(assistantMsg);
                log.info("[NvcAssistantService] Saved assistant reply: conversationId={}, length={}, toolCalls={}",
                    conversationId, content.length(),
                    toolCallsJson != null ? "yes" : "no");
            }
        } catch (Exception e) {
            log.error("[NvcAssistantService] Failed to save assistant reply: conversationId={}", conversationId, e);
        }
    }

    /**
     * 保存工具调用消息（TOOL 角色）
     */
    private void saveToolMessage(Long conversationId, Long userId, String toolName,
                                  String content, int seq) {
        try {
            NvcAssistantMessageEntity toolMsg = NvcAssistantMessageEntity.builder()
                .conversationId(conversationId)
                .userId(userId)
                .role(nvc.guide.modules.nvcassistant.model.NvcAssistantMessageRole.TOOL)
                .content(content)
                .toolName(toolName)
                .sequenceNum(seq)
                .build();
            messageService.saveMessage(toolMsg);
            log.debug("[NvcAssistantService] Saved tool message: conversationId={}, tool={}, seq={}",
                conversationId, toolName, seq);
        } catch (Exception e) {
            log.error("[NvcAssistantService] Failed to save tool message: conversationId={}, tool={}",
                conversationId, toolName, e);
        }
    }

    /**
     * 截断过长文本
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    /**
     * 生成对话标题
     */
    private String generateTitle(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "新对话";
        }
        String title = userMessage.replaceAll("[\\r\\n]+", " ").trim();
        return title.length() > 50 ? title.substring(0, 50) + "..." : title;
    }
}
