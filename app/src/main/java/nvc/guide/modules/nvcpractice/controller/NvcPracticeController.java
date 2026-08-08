package nvc.guide.modules.nvcpractice.controller;

import nvc.guide.common.annotation.RateLimit;
import nvc.guide.common.result.PageResult;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcpractice.dto.CreatePracticeSessionRequest;
import nvc.guide.modules.nvcpractice.dto.DialogueResponse;
import nvc.guide.modules.nvcpractice.dto.MessageResponse;
import nvc.guide.modules.nvcpractice.dto.PracticeSessionResponse;
import nvc.guide.modules.nvcpractice.dto.SendMessageRequest;
import nvc.guide.modules.nvcpractice.dto.StepProgressDTO;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.service.NvcEvaluationService;
import nvc.guide.modules.nvcpractice.service.NvcPracticeSessionService.CompleteResult;
import nvc.guide.modules.nvcpractice.service.NvcPracticeDialogueService;
import nvc.guide.modules.nvcpractice.service.NvcPracticeSessionService;
import nvc.guide.modules.nvcpractice.service.NvcAgentOrchestrator;
import nvc.guide.modules.nvcpractice.service.NvcStructuredPracticeService;
import nvc.guide.modules.nvcpractice.service.NvcSummaryService;
import nvc.guide.modules.nvcpractice.model.NvcSummaryEntity;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import nvc.guide.modules.nvcscenario.repository.NvcScenarioRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
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
@Slf4j
@RequiredArgsConstructor
@Validated
public class NvcPracticeController {

  private final NvcPracticeSessionService sessionService;
  private final NvcPracticeDialogueService dialogueService;
  private final NvcStructuredPracticeService structuredPracticeService;
  private final NvcEvaluationService evaluationService;
  private final NvcSummaryService summaryService;
  private final NvcScenarioRepository scenarioRepository;
  private final NvcAgentOrchestrator agentOrchestrator;

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
  public Result<PageResult<PracticeSessionResponse>> getUserSessions(
      @RequestParam Long userId,
      @RequestParam(required = false) NvcSessionPhase phase,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    var pageResult = sessionService
        .getUserSessions(userId, phase, PageRequest.of(page, size))
        .map(this::toSessionResponse);
    return Result.success(PageResult.of(pageResult));
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
      @Valid @RequestBody SendMessageRequest request) {
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
  public Flux<ServerSentEvent<String>> sendMessageStream(
      @PathVariable Long sessionId,
      @Valid @RequestBody SendMessageRequest request) {
    return dialogueService.sendMessageStream(sessionId, request.content())
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
    var pageResult = dialogueService.getMessages(sessionId, PageRequest.of(page, size));
    return Result.success(PageResult.of(pageResult));
  }

  /**
   * 获取最新实时评估结果
   */
  @GetMapping("/sessions/{sessionId}/evaluation")
  public Result<NvcEvaluationEntity> getLatestEvaluation(
      @PathVariable Long sessionId) {
    NvcEvaluationEntity evaluation =
        evaluationService.getLatestRealtimeEvaluation(sessionId).orElse(null);
    return Result.success(evaluation);
  }

  /**
   * 获取 NVC 四要素摘要
   */
  @GetMapping("/sessions/{sessionId}/summary")
  public Result<NvcSummaryEntity> getSummary(
      @PathVariable Long sessionId) {
    NvcSummaryEntity summary = summaryService.getSummary(sessionId);
    return Result.success(summary);
  }

  /**
   * 结束会话（含最终评估）
   */
  @RateLimit(count = 5)
  @PostMapping("/sessions/{sessionId}/complete")
  public Result<PracticeSessionResponse> completeSession(
      @PathVariable Long sessionId) {
    CompleteResult result = sessionService.completeAndEvaluate(sessionId);
    // evaluationSkipped 表示没有消息需要评估，不是失败
    return Result.success(
        toSessionResponse(result.session(), result.evaluationFailed()));
  }

  /**
   * 获取结构化练习的步骤进度
   */
  @GetMapping("/sessions/{sessionId}/step-progress")
  public Result<StepProgressDTO> getStepProgress(
      @PathVariable Long sessionId) {
    StepProgressDTO progress =
        structuredPracticeService.getStepProgress(sessionId);
    return Result.success(progress);
  }

  /**
   * 手动推进到下一步
   */
  @RateLimit(count = 10)
  @PostMapping("/sessions/{sessionId}/advance-step")
  public Result<StepProgressDTO> advanceStep(
      @PathVariable Long sessionId) {
    StepProgressDTO progress =
        structuredPracticeService.advanceStep(sessionId);
    return Result.success(progress);
  }

  /**
   * 重置步骤（重新开始）
   */
  @RateLimit(count = 5)
  @PostMapping("/sessions/{sessionId}/reset-step")
  public Result<StepProgressDTO> resetStep(
      @PathVariable Long sessionId) {
    StepProgressDTO progress =
        structuredPracticeService.resetStep(sessionId);
    return Result.success(progress);
  }

  private PracticeSessionResponse toSessionResponse(
      NvcPracticeSessionEntity session) {
    return toSessionResponse(session, false);
  }

  private PracticeSessionResponse toSessionResponse(
      NvcPracticeSessionEntity session, boolean evaluationFailed) {
    // 加载场景信息（场景驱动模式）
    Long scenarioId = session.getScenarioId();
    String scenarioTitle = null;
    String scenarioDescription = null;
    if (scenarioId != null) {
      NvcScenarioEntity scenario =
          scenarioRepository.findById(scenarioId).orElse(null);
      if (scenario != null) {
        scenarioTitle = scenario.getTitle();
        scenarioDescription = scenario.getDescription();
      }
    }

    return new PracticeSessionResponse(
        session.getId(),
        session.getUserId(),
        session.getPracticeMode(),
        session.getCurrentPhase(),
        session.getCurrentStep(),
        session.getAgentScene() != null
            ? session.getAgentScene().name() : null,
        session.getDifficulty(),
        scenarioId,
        scenarioTitle,
        scenarioDescription,
        session.getStartedAt(),
        session.getCompletedAt(),
        session.getCreatedAt(),
        evaluationFailed
    );
  }

  /**
   * 获取推荐练习场景（基于用户薄弱维度）
   */
  @GetMapping("/recommendations")
  public Result<List<NvcScenarioEntity>> getRecommendations(
      @RequestParam Long userId,
      @RequestParam(defaultValue = "5") int limit) {
    List<NvcScenarioEntity> recommendations =
        agentOrchestrator.recommendScenarios(userId, limit);
    return Result.success(recommendations);
  }
}
