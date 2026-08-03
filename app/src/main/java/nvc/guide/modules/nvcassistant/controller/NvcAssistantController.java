package nvc.guide.modules.nvcassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcassistant.dto.AssistantRequest;
import nvc.guide.modules.nvcassistant.dto.AssistantResponse;
import nvc.guide.modules.nvcassistant.dto.ConversationResponse;
import nvc.guide.modules.nvcassistant.dto.MessageResponse;
import nvc.guide.modules.nvcassistant.dto.ToolCallRecord;
import nvc.guide.modules.nvcassistant.model.NvcAssistantMessageEntity;
import nvc.guide.modules.nvcassistant.service.NvcAssistantMessageService;
import nvc.guide.modules.nvcassistant.service.NvcAssistantService;
import nvc.guide.modules.nvcassistant.service.agent.AgentEvent;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 主 Agent REST 控制器
 */
@RestController
@RequestMapping("/api/nvc/assistant")
@Slf4j
@RequiredArgsConstructor
public class NvcAssistantController {

    private final NvcAssistantService assistantService;
    private final NvcAssistantMessageService messageService;
    private final ObjectMapper objectMapper;

    /**
     * 非流式对话（同步等待完成）
     */
    @PostMapping("/chat")
    public Result<AssistantResponse> chat(
            @RequestParam Long userId,
            @Validated @RequestBody AssistantRequest request) {
        NvcAssistantService.ChatStreamResult result = assistantService.chatStreamRaw(userId, request);

        // 收集所有事件，提取最终内容
        StringBuilder content = new StringBuilder();
        List<ToolCallRecord> toolCalls = new ArrayList<>();

        result.eventStream().toIterable().forEach(event -> {
            switch (event.type()) {
                case CONTENT -> content.append(event.data());
                case TOOLCALL_END -> {
                    Map<String, Object> meta = event.metadata();
                    toolCalls.add(ToolCallRecord.builder()
                        .toolName(event.data())
                        .success(Boolean.TRUE.equals(meta.get("success")))
                        .result(meta.getOrDefault("result", "").toString())
                        .durationMs(meta.get("durationMs") instanceof Number n ? n.longValue() : 0)
                        .build());
                }
                default -> { /* ignore other events */ }
            }
        });

        return Result.success(AssistantResponse.builder()
            .conversationId(result.conversationId())
            .content(content.toString())
            .toolCalls(toolCalls)
            .done(true)
            .build());
    }

    /**
     * 流式 SSE 对话
     *
     * <p>SSE 事件类型：
     * <ul>
     *   <li>thinking — 思考中</li>
     *   <li>tool_call — 工具调用开始（含工具名和参数）</li>
     *   <li>tool_result — 工具调用结束（含结果和耗时）</li>
     *   <li>content — 回复内容</li>
     *   <li>done — 完成（含 conversationId）</li>
     *   <li>error — 错误</li>
     * </ul>
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestParam Long userId,
            @Validated @RequestBody AssistantRequest request) {

        NvcAssistantService.ChatStreamResult result = assistantService.chatStreamRaw(userId, request);
        long convId = result.conversationId();

        return result.eventStream()
            .map(event -> toServerSentEvent(event, convId))
            .onErrorResume(e -> {
                // 客户端断开连接是正常情况，不需要记录错误日志
                if (isClientDisconnect(e)) {
                    log.info("[NvcAssistantController] Client disconnected: conversationId={}", convId);
                    return Flux.empty();
                }
                log.error("Stream error: conversationId={}", convId, e);
                return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("对话出错: " + e.getMessage())
                    .build());
            })
            .doOnComplete(result.onComplete());
    }

    /**
     * 判断是否是客户端断开连接
     *
     * <p>检查异常链中是否包含 IOException 或 ClientAbortException，
     * 兼容中英文操作系统和不同 Servlet 容器。
     */
    private boolean isClientDisconnect(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof org.apache.catalina.connector.ClientAbortException) {
                return true;
            }
            if (cause instanceof java.io.IOException) {
                String msg = cause.getMessage();
                if (msg != null && (msg.contains("已建立的连接")
                    || msg.contains("broken pipe")
                    || msg.contains("connection was aborted")
                    || msg.contains("Connection reset"))) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 获取用户对话列表
     */
    @GetMapping("/conversations")
    public Result<List<ConversationResponse>> listConversations(@RequestParam Long userId) {
        return Result.success(messageService.listConversations(userId));
    }

    /**
     * 获取对话消息列表
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<List<MessageResponse>> getMessages(
            @RequestParam Long userId,
            @PathVariable Long conversationId) {
        messageService.getConversationOrThrow(conversationId, userId);

        List<MessageResponse> messages = messageService.getMessages(conversationId).stream()
            .map(this::toMessageResponse)
            .toList();

        return Result.success(messages);
    }

    /**
     * 删除对话
     */
    @DeleteMapping("/conversations/{conversationId}")
    public Result<Void> deleteConversation(
            @RequestParam Long userId,
            @PathVariable Long conversationId) {
        messageService.deleteConversation(conversationId, userId);
        return Result.success(null);
    }

    /**
     * 重新生成最后一条回复
     */
    @PostMapping("/conversations/{conversationId}/regenerate")
    public Result<AssistantResponse> regenerate(
            @RequestParam Long userId,
            @PathVariable Long conversationId) {
        messageService.getConversationOrThrow(conversationId, userId);

        String lastUserMessage = messageService.getLastUserMessageContent(conversationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ASSISTANT_MESSAGE_NOT_FOUND, "对话中没有用户消息"));

        AssistantRequest request = new AssistantRequest();
        request.setConversationId(conversationId);
        request.setMessage(lastUserMessage);

        // 重新生成（非流式简化版）
        // TODO: 后续可改为流式重新生成
        return Result.success(AssistantResponse.builder()
            .conversationId(conversationId)
            .content("重新生成功能暂未适配新架构")
            .toolCalls(List.of())
            .done(true)
            .build());
    }

    // ==================== 内部方法 ====================

    /**
     * AgentEvent → SSE 事件转换
     */
    private ServerSentEvent<String> toServerSentEvent(AgentEvent event, long conversationId) {
        String eventType = mapEventType(event.type());
        String data = formatEventData(event, conversationId);

        return ServerSentEvent.<String>builder()
            .event(eventType)
            .data(data)
            .build();
    }

    /**
     * 映射 AgentEventType 到前端期望的 SSE event name
     */
    private String mapEventType(AgentEvent.AgentEventType type) {
        return switch (type) {
            case THINKING -> "thinking";
            case TOOLCALL_START -> "tool_call";
            case TOOLCALL_END -> "tool_result";
            case CONTENT -> "content";
            case DONE -> "done";
            case ERROR -> "error";
        };
    }

    /**
     * 格式化事件数据
     *
     * <p>CONTENT 事件需要转义换行符，因为 SSE 协议使用 \n 作为行分隔符。
     * 前端收到后会反转义：data.replace(/\\n/g, '\n')
     */
    private String formatEventData(AgentEvent event, long conversationId) {
        return switch (event.type()) {
            case THINKING, ERROR -> event.data();
            case CONTENT -> escapeNewlines(event.data());
            case TOOLCALL_START -> toJson(Map.of(
                "toolName", event.data(),
                "arguments", event.metadata().getOrDefault("arguments", "{}")
            ));
            case TOOLCALL_END -> toJson(Map.of(
                "toolName", event.data(),
                "success", event.metadata().getOrDefault("success", false),
                "result", event.metadata().getOrDefault("result", ""),
                "durationMs", event.metadata().getOrDefault("durationMs", 0)
            ));
            case DONE -> toJson(Map.of("conversationId", conversationId));
        };
    }

    /**
     * 转义换行符为 \\n，避免 SSE 协议解析时被当作行分隔符
     */
    private String escapeNewlines(String text) {
        if (text == null) return "";
        return text.replace("\n", "\\n").replace("\r", "\\r");
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Entity → DTO 转换
     */
    private MessageResponse toMessageResponse(NvcAssistantMessageEntity entity) {
        return MessageResponse.builder()
            .id(entity.getId())
            .role(entity.getRole().name())
            .content(entity.getContent())
            .toolCalls(List.of())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
