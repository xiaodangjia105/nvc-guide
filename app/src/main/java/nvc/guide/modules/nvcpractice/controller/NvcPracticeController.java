package nvc.guide.modules.nvcpractice.controller;

import nvc.guide.common.annotation.RateLimit;
import nvc.guide.common.result.PageResult;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcpractice.dto.*;
import nvc.guide.modules.nvcpractice.model.*;
import nvc.guide.modules.nvcpractice.service.NvcPracticeFacade;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/nvc/practice")
@Slf4j
@RequiredArgsConstructor
@Validated
public class NvcPracticeController {

  private final NvcPracticeFacade practiceFacade;

  /**
   * 创建练习会话
   */
  @RateLimit(count = 10)
  @PostMapping("/sessions")
  public Result<PracticeSessionResponse> createSession(
      @RequestParam Long userId,
      @Valid @RequestBody CreatePracticeSessionRequest req) {
    return Result.success(practiceFacade.createSession(userId, req));
  }

  /**
   * 获取用户的练习会话列表
   */
  @GetMapping("/sessions")
  public Result<PageResult<PracticeSessionResponse>> getUserSessions(
      @RequestParam Long userId,
      @RequestParam(required = false) NvcSessionPhase phase,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    var pageResult = practiceFacade.getUserSessions(userId, phase, PageRequest.of(page, size));
    return Result.success(PageResult.of(pageResult));
  }

  /**
   * 获取单个会话详情
   */
  @GetMapping("/sessions/{sessionId}")
  public Result<PracticeSessionResponse> getSession(@PathVariable Long sessionId) {
    return Result.success(practiceFacade.getSession(sessionId));
  }

  /**
   * 发送消息（非流式）
   */
  @RateLimit(count = 30)
  @PostMapping("/sessions/{sessionId}/messages")
  public Result<DialogueResponse> sendMessage(
      @PathVariable Long sessionId,
      @Valid @RequestBody SendMessageRequest request) {
    return Result.success(practiceFacade.sendMessage(sessionId, request.content()));
  }

  /**
   * 发送消息（流式 SSE）
   */
  @RateLimit(count = 30)
  @PostMapping(
      value = "/sessions/{sessionId}/messages/stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<String>> sendMessageStream(
      @PathVariable Long sessionId,
      @Valid @RequestBody SendMessageRequest request) {
    return practiceFacade.sendMessageStream(sessionId, request.content())
        .onErrorResume(e -> {
          log.error("Practice stream error: sessionId={}", sessionId, e);
          return Flux.just(ServerSentEvent.<String>builder()
              .event("error")
              .data("对话出错: " + e.getMessage())
              .build());
        });
  }

  /**
   * 获取对话历史
   */
  @GetMapping("/sessions/{sessionId}/messages")
  public Result<PageResult<MessageResponse>> getMessages(
      @PathVariable Long sessionId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
    var pageResult = practiceFacade.getMessages(sessionId, PageRequest.of(page, size));
    return Result.success(PageResult.of(pageResult));
  }

  /**
   * 获取最新实时评估结果
   */
  @GetMapping("/sessions/{sessionId}/evaluation")
  public Result<NvcEvaluationEntity> getLatestEvaluation(@PathVariable Long sessionId) {
    return Result.success(practiceFacade.getLatestEvaluation(sessionId));
  }

  /**
   * 获取 NVC 四要素摘要
   */
  @GetMapping("/sessions/{sessionId}/summary")
  public Result<NvcSummaryEntity> getSummary(@PathVariable Long sessionId) {
    return Result.success(practiceFacade.getSummary(sessionId));
  }

  /**
   * 结束会话（含最终评估）
   */
  @RateLimit(count = 5)
  @PostMapping("/sessions/{sessionId}/complete")
  public Result<PracticeSessionResponse> completeSession(@PathVariable Long sessionId) {
    return Result.success(practiceFacade.completeSession(sessionId));
  }

  /**
   * 获取结构化练习的步骤进度
   */
  @GetMapping("/sessions/{sessionId}/step-progress")
  public Result<StepProgressDTO> getStepProgress(@PathVariable Long sessionId) {
    return Result.success(practiceFacade.getStepProgress(sessionId));
  }

  /**
   * 手动推进到下一步
   */
  @RateLimit(count = 10)
  @PostMapping("/sessions/{sessionId}/advance-step")
  public Result<StepProgressDTO> advanceStep(@PathVariable Long sessionId) {
    return Result.success(practiceFacade.advanceStep(sessionId));
  }

  /**
   * 重置步骤（重新开始）
   */
  @RateLimit(count = 5)
  @PostMapping("/sessions/{sessionId}/reset-step")
  public Result<StepProgressDTO> resetStep(@PathVariable Long sessionId) {
    return Result.success(practiceFacade.resetStep(sessionId));
  }

  /**
   * 获取推荐练习场景（基于用户薄弱维度）
   */
  @GetMapping("/recommendations")
  public Result<List<NvcScenarioEntity>> getRecommendations(
      @RequestParam Long userId,
      @RequestParam(defaultValue = "5") int limit) {
    return Result.success(practiceFacade.getRecommendations(userId, limit));
  }
}
