package nvc.guide.modules.nvcassistant.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcassistant.dto.AssistantRequest;
import nvc.guide.modules.nvcassistant.dto.AssistantResponse;
import nvc.guide.modules.nvcassistant.dto.ConversationResponse;
import nvc.guide.modules.nvcassistant.dto.MessageResponse;
import nvc.guide.modules.nvcassistant.model.NvcAssistantMessageEntity;
import nvc.guide.modules.nvcassistant.service.NvcAssistantMessageService;
import nvc.guide.modules.nvcassistant.service.NvcAssistantService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

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

    /**
     * 非流式对话
     */
    @PostMapping("/chat")
    public Result<AssistantResponse> chat(
            @RequestParam Long userId,
            @Validated @RequestBody AssistantRequest request) {
        return Result.success(assistantService.chat(userId, request));
    }

    /**
     * 流式 SSE 对话
     * 使用 ServerSentEvent 确保正确的 SSE 帧格式，避免浏览器/代理缓冲
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestParam Long userId,
            @Validated @RequestBody AssistantRequest request) {

        NvcAssistantService.ChatStreamResult result = assistantService.chatStreamRaw(userId, request);
        long convId = result.conversationId();

        // thinking 事件
        Flux<ServerSentEvent<String>> thinking = Flux.just(
            ServerSentEvent.<String>builder()
                .event("thinking")
                .data("正在思考...")
                .build()
        );

        // 内容流——出错时发送 error 事件而不是抛异常
        Flux<ServerSentEvent<String>> contentStream = result.contentStream()
            .map(chunk -> ServerSentEvent.<String>builder()
                .event("content")
                .data(chunk)
                .build()
            )
            .onErrorResume(e -> {
                log.error("Stream error: conversationId={}", convId, e);
                return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("对话出错: " + e.getMessage())
                    .build());
            });

        // done 事件 + 保存回调
        Flux<ServerSentEvent<String>> done = Flux.just(
            ServerSentEvent.<String>builder()
                .event("done")
                .data("{\"conversationId\":" + convId + "}")
                .build()
        ).doOnComplete(result.onComplete());

        return Flux.concat(thinking, contentStream, done);
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
        // 校验所有权
        messageService.getConversationOrThrow(conversationId, userId);

        // 转换为 DTO
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
        // 校验所有权
        messageService.getConversationOrThrow(conversationId, userId);

        // 获取最后一条用户消息
        String lastUserMessage = messageService.getLastUserMessageContent(conversationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ASSISTANT_MESSAGE_NOT_FOUND, "对话中没有用户消息"));

        // 重新发送
        AssistantRequest request = new AssistantRequest();
        request.setConversationId(conversationId);
        request.setMessage(lastUserMessage);

        return Result.success(assistantService.chat(userId, request));
    }

    // ==================== 内部方法 ====================

    /**
     * Entity → DTO 转换
     */
    private MessageResponse toMessageResponse(NvcAssistantMessageEntity entity) {
        return MessageResponse.builder()
            .id(entity.getId())
            .role(entity.getRole().name())
            .content(entity.getContent())
            .toolCalls(List.of()) // 简化：不返回工具调用详情
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
