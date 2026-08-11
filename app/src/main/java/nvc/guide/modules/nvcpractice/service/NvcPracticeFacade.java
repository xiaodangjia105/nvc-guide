package nvc.guide.modules.nvcpractice.service;

import nvc.guide.modules.nvcpractice.dto.CreatePracticeSessionRequest;
import nvc.guide.modules.nvcpractice.dto.DialogueResponse;
import nvc.guide.modules.nvcpractice.dto.MessageResponse;
import nvc.guide.modules.nvcpractice.dto.PracticeSessionResponse;
import nvc.guide.modules.nvcpractice.dto.StepProgressDTO;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.model.NvcSummaryEntity;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import nvc.guide.modules.nvcscenario.service.NvcScenarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Facade for NVC practice operations.
 * Orchestrates multiple practice-related services so the Controller
 * can depend on a single entry point.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcPracticeFacade {

  private final NvcPracticeSessionService sessionService;
  private final NvcPracticeDialogueService dialogueService;
  private final NvcStructuredPracticeService structuredPracticeService;
  private final NvcEvaluationService evaluationService;
  private final NvcSummaryService summaryService;
  private final NvcScenarioService scenarioService;
  private final NvcAgentOrchestrator agentOrchestrator;

  // ── Session management ──────────────────────────────────────────────

  public PracticeSessionResponse createSession(
      Long userId, CreatePracticeSessionRequest req) {
    NvcPracticeSessionEntity session = sessionService.createSession(userId, req);
    return toSessionResponse(session);
  }

  public Page<PracticeSessionResponse> getUserSessions(
      Long userId, NvcSessionPhase phase, Pageable pageable) {
    return sessionService.getUserSessions(userId, phase, pageable)
        .map(this::toSessionResponse);
  }

  public PracticeSessionResponse getSession(Long sessionId) {
    NvcPracticeSessionEntity session = sessionService.getSession(sessionId);
    return toSessionResponse(session);
  }

  public PracticeSessionResponse completeSession(Long sessionId) {
    NvcPracticeSessionService.CompleteResult result =
        sessionService.completeAndEvaluate(sessionId);
    return toSessionResponse(result.session(), result.evaluationFailed());
  }

  // ── Dialogue ────────────────────────────────────────────────────────

  public DialogueResponse sendMessage(Long sessionId, String content) {
    return dialogueService.sendMessage(sessionId, content);
  }

  public Flux<ServerSentEvent<String>> sendMessageStream(
      Long sessionId, String content) {
    return dialogueService.sendMessageStream(sessionId, content);
  }

  public Page<MessageResponse> getMessages(Long sessionId, Pageable pageable) {
    return dialogueService.getMessages(sessionId, pageable);
  }

  // ── Evaluation & Summary ────────────────────────────────────────────

  public NvcEvaluationEntity getLatestEvaluation(Long sessionId) {
    return evaluationService.getLatestRealtimeEvaluation(sessionId).orElse(null);
  }

  public NvcSummaryEntity getSummary(Long sessionId) {
    return summaryService.getSummary(sessionId);
  }

  // ── Structured Practice ─────────────────────────────────────────────

  public StepProgressDTO getStepProgress(Long sessionId) {
    return structuredPracticeService.getStepProgress(sessionId);
  }

  public StepProgressDTO advanceStep(Long sessionId) {
    return structuredPracticeService.advanceStep(sessionId);
  }

  public StepProgressDTO resetStep(Long sessionId) {
    return structuredPracticeService.resetStep(sessionId);
  }

  // ── Recommendations ─────────────────────────────────────────────────

  public List<NvcScenarioEntity> getRecommendations(Long userId, int limit) {
    return agentOrchestrator.recommendScenarios(userId, limit);
  }

  // ── Response mapping ────────────────────────────────────────────────

  public PracticeSessionResponse toSessionResponse(
      NvcPracticeSessionEntity session) {
    return toSessionResponse(session, false);
  }

  public PracticeSessionResponse toSessionResponse(
      NvcPracticeSessionEntity session, boolean evaluationFailed) {
    Long scenarioId = session.getScenarioId();
    String scenarioTitle = null;
    String scenarioDescription = null;
    if (scenarioId != null) {
      NvcScenarioEntity scenario = scenarioService.findById(scenarioId);
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
}
