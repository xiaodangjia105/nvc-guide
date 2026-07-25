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
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(
            @RequestParam Long userId,
            @Validated @RequestBody AssistantRequest request) {
        return assistantService.chatStream(userId, request);
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
