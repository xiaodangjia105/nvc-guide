package nvc.guide.modules.nvcassistant.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcassistant.dto.AssistantRequest;
import nvc.guide.modules.nvcassistant.dto.AssistantResponse;
import nvc.guide.modules.nvcassistant.dto.ConversationResponse;
import nvc.guide.modules.nvcassistant.model.NvcAssistantMessageEntity;
import nvc.guide.modules.nvcassistant.service.NvcAssistantMessageService;
import nvc.guide.modules.nvcassistant.service.NvcAssistantService;
import org.springframework.http.MediaType;
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
            @RequestBody AssistantRequest request) {
        return Result.success(assistantService.chat(userId, request));
    }

    /**
     * 流式 SSE 对话
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(
            @RequestParam Long userId,
            @RequestBody AssistantRequest request) {
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
    public Result<List<NvcAssistantMessageEntity>> getMessages(
            @RequestParam Long userId,
            @PathVariable Long conversationId) {
        // 校验所有权
        messageService.getConversationOrThrow(conversationId, userId);
        return Result.success(messageService.getMessages(conversationId));
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
        // 获取对话的最后一条用户消息
        List<NvcAssistantMessageEntity> messages = messageService.getMessages(conversationId);
        String lastUserMessage = messages.stream()
            .filter(m -> m.getRole() == nvc.guide.modules.nvcassistant.model.NvcAssistantMessageRole.USER)
            .reduce((a, b) -> b) // 获取最后一条
            .map(NvcAssistantMessageEntity::getContent)
            .orElseThrow(() -> new nvc.guide.common.exception.BusinessException(
                nvc.guide.common.exception.ErrorCode.ASSISTANT_MESSAGE_NOT_FOUND,
                "对话中没有用户消息"));

        // 重新发送
        AssistantRequest request = new AssistantRequest();
        request.setConversationId(conversationId);
        request.setMessage(lastUserMessage);

        return Result.success(assistantService.chat(userId, request));
    }
}
