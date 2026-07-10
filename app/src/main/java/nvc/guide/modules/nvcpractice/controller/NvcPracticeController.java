package nvc.guide.modules.nvcpractice.controller;

import nvc.guide.common.annotation.RateLimit;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcpractice.dto.CreatePracticeSessionRequest;
import nvc.guide.modules.nvcpractice.dto.DialogueResponse;
import nvc.guide.modules.nvcpractice.dto.MessageResponse;
import nvc.guide.modules.nvcpractice.dto.PracticeSessionResponse;
import nvc.guide.modules.nvcpractice.dto.SendMessageRequest;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.service.NvcPracticeDialogueService;
import nvc.guide.modules.nvcpractice.service.NvcPracticeSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/nvc/practice")
@RequiredArgsConstructor
public class NvcPracticeController {

  private final NvcPracticeSessionService sessionService;
  private final NvcPracticeDialogueService dialogueService;

  /**
   * 创建练习会话
   */
  @RateLimit(count = 10)
  @PostMapping("/sessions")
  public Result<PracticeSessionResponse> createSession(
      @RequestParam Long userId,
      @Valid @RequestBody CreatePracticeSessionRequest req) {
    NvcPracticeSessionEntity session =
        sessionService.createSession(userId, req);
    return Result.success(toSessionResponse(session));
  }

  /**
   * 获取用户的练习会话列表
   */
  @GetMapping("/sessions")
  public Result<List<PracticeSessionResponse>> getUserSessions(
      @RequestParam Long userId,
      @RequestParam(required = false) NvcSessionPhase phase) {
    var sessions = sessionService
        .getUserSessions(userId, phase).stream()
        .map(this::toSessionResponse)
        .toList();
    return Result.success(sessions);
  }

  /**
   * 获取单个会话详情
   */
  @GetMapping("/sessions/{sessionId}")
  public Result<PracticeSessionResponse> getSession(
      @PathVariable Long sessionId) {
    NvcPracticeSessionEntity session =
        sessionService.getSession(sessionId);
    return Result.success(toSessionResponse(session));
  }

  /**
   * 发送消息（非流式）
   */
  @RateLimit(count = 30)
  @PostMapping("/sessions/{sessionId}/messages")
  public Result<DialogueResponse> sendMessage(
      @PathVariable Long sessionId,
      @RequestBody SendMessageRequest request) {
    DialogueResponse response =
        dialogueService.sendMessage(
            sessionId, request.content());
    return Result.success(response);
  }

  /**
   * 发送消息（流式 SSE）
   */
  @RateLimit(count = 30)
  @PostMapping(
      value = "/sessions/{sessionId}/messages/stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<String> sendMessageStream(
      @PathVariable Long sessionId,
      @RequestBody SendMessageRequest request) {
    return dialogueService.sendMessageStream(
        sessionId, request.content());
  }

  /**
   * 获取对话历史
   */
  @GetMapping("/sessions/{sessionId}/messages")
  public Result<List<MessageResponse>> getMessages(
      @PathVariable Long sessionId) {
    return Result.success(
        dialogueService.getMessages(sessionId));
  }

  /**
   * 结束会话
   */
  @PostMapping("/sessions/{sessionId}/complete")
  public Result<PracticeSessionResponse> completeSession(
      @PathVariable Long sessionId) {
    NvcPracticeSessionEntity session =
        sessionService.completeSession(sessionId);
    return Result.success(toSessionResponse(session));
  }

  private PracticeSessionResponse toSessionResponse(
      NvcPracticeSessionEntity session) {
    return new PracticeSessionResponse(
        session.getId(),
        session.getUserId(),
        session.getPracticeMode(),
        session.getCurrentPhase(),
        session.getCurrentStep(),
        session.getAgentScene() != null
            ? session.getAgentScene().name() : null,
        session.getDifficulty(),
        session.getStartedAt(),
        session.getCompletedAt(),
        session.getCreatedAt()
    );
  }
}
