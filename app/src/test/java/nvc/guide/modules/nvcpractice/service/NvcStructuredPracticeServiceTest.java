package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcpractice.dto.StepProgressDTO;
import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationType;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.repository.NvcEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcStructuredPracticeService 测试")
class NvcStructuredPracticeServiceTest {

  @Mock
  private NvcPracticeSessionService sessionService;
  @Mock
  private NvcEvaluationRepository evaluationRepository;
  @Mock
  private NvcAgentConfigService agentConfigService;

  private NvcStructuredPracticeService service;

  @BeforeEach
  void setUp() {
    service = new NvcStructuredPracticeService(
        sessionService, evaluationRepository, agentConfigService);
  }

  private NvcPracticeSessionEntity buildSession(
      Long id, NvcPracticeMode mode, NvcPracticeStep step) {
    return NvcPracticeSessionEntity.builder()
        .id(id)
        .userId(1L)
        .practiceMode(mode)
        .currentStep(step)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  private NvcAgentConfigEntity buildConfig(NvcAgentScene scene) {
    return NvcAgentConfigEntity.builder()
        .id(1L)
        .agentScene(scene)
        .displayName("测试 Agent")
        .systemPrompt("你是测试 Agent")
        .modelProvider("mimo")
        .modelName("test-model")
        .temperature(0.7)
        .maxTokens(2000)
        .stepAdvanceThreshold(70)
        .maxStepAttempts(10)
        .stepTimeoutMinutes(30)
        .isEnabled(true)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  // ========== getStepProgress() ==========

  @Nested
  @DisplayName("getStepProgress()")
  class GetStepProgressTests {

    @Test
    @DisplayName("非结构化四步模式返回 null")
    void nonStructuredMode_returnsNull() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.FREE_DIALOG, null);
      when(sessionService.getSession(1L)).thenReturn(session);

      StepProgressDTO result = service.getStepProgress(1L);

      assertNull(result);
    }

    @Test
    @DisplayName("已完成的会话返回 COMPLETED 状态")
    void completedSession_returnsCompletedDTO() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.COMPLETED);
      when(sessionService.getSession(1L)).thenReturn(session);

      StepProgressDTO result = service.getStepProgress(1L);

      assertNotNull(result);
      assertEquals(NvcPracticeStep.COMPLETED, result.currentStep());
      assertEquals(4, result.stepIndex());
      assertEquals(4, result.totalSteps());
      assertFalse(result.canAdvance());
    }

    @Test
    @DisplayName("currentStep 为 null 时返回 COMPLETED 状态")
    void nullStep_returnsCompletedDTO() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, null);
      when(sessionService.getSession(1L)).thenReturn(session);

      StepProgressDTO result = service.getStepProgress(1L);

      assertNotNull(result);
      assertEquals(NvcPracticeStep.COMPLETED, result.currentStep());
    }

    @Test
    @DisplayName("有评估记录时返回对应分数和步骤信息")
    void withEvaluation_returnsScoreAndStepInfo() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.OBSERVE);
      when(sessionService.getSession(1L)).thenReturn(session);

      NvcEvaluationEntity eval = NvcEvaluationEntity.builder()
          .id(1L)
          .sessionId(1L)
          .userId(1L)
          .observationScore(85)
          .evaluationType(NvcEvaluationType.REALTIME)
          .build();
      when(evaluationRepository.findFirstBySessionIdAndEvaluationTypeOrderByCreatedAtDesc(
          1L, NvcEvaluationType.REALTIME)).thenReturn(Optional.of(eval));

      NvcAgentConfigEntity config = buildConfig(NvcAgentScene.STEP_OBSERVE_COACH);
      when(agentConfigService.getConfig(NvcAgentScene.STEP_OBSERVE_COACH))
          .thenReturn(config);

      StepProgressDTO result = service.getStepProgress(1L);

      assertNotNull(result);
      assertEquals(NvcPracticeStep.OBSERVE, result.currentStep());
      assertEquals(0, result.stepIndex());
      assertEquals(85, result.currentScore());
      assertTrue(result.canAdvance());
      assertEquals(10, result.maxAttempts());
      assertEquals(30, result.timeoutMinutes());
    }

    @Test
    @DisplayName("无评估记录时分数为 null，不可推进")
    void noEvaluation_scoreNullCannotAdvance() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.FEELING);
      when(sessionService.getSession(1L)).thenReturn(session);

      when(evaluationRepository.findFirstBySessionIdAndEvaluationTypeOrderByCreatedAtDesc(
          1L, NvcEvaluationType.REALTIME)).thenReturn(Optional.empty());

      NvcAgentConfigEntity config = buildConfig(NvcAgentScene.STEP_FEELING_COACH);
      when(agentConfigService.getConfig(NvcAgentScene.STEP_FEELING_COACH))
          .thenReturn(config);

      StepProgressDTO result = service.getStepProgress(1L);

      assertNotNull(result);
      assertEquals(NvcPracticeStep.FEELING, result.currentStep());
      assertEquals(1, result.stepIndex());
      assertNull(result.currentScore());
      assertFalse(result.canAdvance());
    }

    @Test
    @DisplayName("分数低于阈值时不可推进")
    void scoreBelowThreshold_cannotAdvance() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.NEED);
      when(sessionService.getSession(1L)).thenReturn(session);

      NvcEvaluationEntity eval = NvcEvaluationEntity.builder()
          .id(1L)
          .sessionId(1L)
          .userId(1L)
          .needScore(50)
          .evaluationType(NvcEvaluationType.REALTIME)
          .build();
      when(evaluationRepository.findFirstBySessionIdAndEvaluationTypeOrderByCreatedAtDesc(
          1L, NvcEvaluationType.REALTIME)).thenReturn(Optional.of(eval));

      NvcAgentConfigEntity config = buildConfig(NvcAgentScene.STEP_NEED_COACH);
      when(agentConfigService.getConfig(NvcAgentScene.STEP_NEED_COACH))
          .thenReturn(config);

      StepProgressDTO result = service.getStepProgress(1L);

      assertNotNull(result);
      assertEquals(50, result.currentScore());
      assertFalse(result.canAdvance());
    }

    @Test
    @DisplayName("阈值为 null 时默认使用 70")
    void nullThreshold_usesDefault70() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.REQUEST);
      when(sessionService.getSession(1L)).thenReturn(session);

      NvcEvaluationEntity eval = NvcEvaluationEntity.builder()
          .id(1L)
          .sessionId(1L)
          .userId(1L)
          .requestScore(75)
          .evaluationType(NvcEvaluationType.REALTIME)
          .build();
      when(evaluationRepository.findFirstBySessionIdAndEvaluationTypeOrderByCreatedAtDesc(
          1L, NvcEvaluationType.REALTIME)).thenReturn(Optional.of(eval));

      NvcAgentConfigEntity config = buildConfig(NvcAgentScene.STEP_REQUEST_COACH);
      config.setStepAdvanceThreshold(null);
      when(agentConfigService.getConfig(NvcAgentScene.STEP_REQUEST_COACH))
          .thenReturn(config);

      StepProgressDTO result = service.getStepProgress(1L);

      assertNotNull(result);
      assertEquals(75, result.currentScore());
      assertTrue(result.canAdvance());
    }

    @Test
    @DisplayName("评估中该步骤分数为 null 时返回 0")
    void nullScoreInEvaluation_returnsZero() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.OBSERVE);
      when(sessionService.getSession(1L)).thenReturn(session);

      NvcEvaluationEntity eval = NvcEvaluationEntity.builder()
          .id(1L)
          .sessionId(1L)
          .userId(1L)
          .observationScore(null)
          .evaluationType(NvcEvaluationType.REALTIME)
          .build();
      when(evaluationRepository.findFirstBySessionIdAndEvaluationTypeOrderByCreatedAtDesc(
          1L, NvcEvaluationType.REALTIME)).thenReturn(Optional.of(eval));

      NvcAgentConfigEntity config = buildConfig(NvcAgentScene.STEP_OBSERVE_COACH);
      when(agentConfigService.getConfig(NvcAgentScene.STEP_OBSERVE_COACH))
          .thenReturn(config);

      StepProgressDTO result = service.getStepProgress(1L);

      assertNotNull(result);
      assertEquals(0, result.currentScore());
    }
  }

  // ========== advanceStep() ==========

  @Nested
  @DisplayName("advanceStep()")
  class AdvanceStepTests {

    @Test
    @DisplayName("非结构化四步模式抛出 BAD_REQUEST")
    void nonStructuredMode_throwsBadRequest() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.FREE_DIALOG, NvcPracticeStep.OBSERVE);
      when(sessionService.getSession(1L)).thenReturn(session);

      BusinessException ex = assertThrows(BusinessException.class,
          () -> service.advanceStep(1L));

      assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("已完成的会话抛出 BAD_REQUEST")
    void completedSession_throwsBadRequest() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.COMPLETED);
      when(sessionService.getSession(1L)).thenReturn(session);

      BusinessException ex = assertThrows(BusinessException.class,
          () -> service.advanceStep(1L));

      assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("currentStep 为 null 时抛出 BAD_REQUEST")
    void nullCurrentStep_throwsBadRequest() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, null);
      when(sessionService.getSession(1L)).thenReturn(session);

      BusinessException ex = assertThrows(BusinessException.class,
          () -> service.advanceStep(1L));

      assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("OBSERVE -> FEELING 正常推进")
    void advanceObserveToFeeling() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.OBSERVE);
      when(sessionService.getSession(1L)).thenReturn(session);

      NvcAgentConfigEntity config = buildConfig(NvcAgentScene.STEP_FEELING_COACH);
      when(agentConfigService.getConfig(NvcAgentScene.STEP_FEELING_COACH))
          .thenReturn(config);
      when(sessionService.updateStep(1L, NvcPracticeStep.FEELING))
          .thenReturn(session);

      StepProgressDTO result = service.advanceStep(1L);

      assertNotNull(result);
      assertEquals(NvcPracticeStep.FEELING, result.currentStep());
      assertEquals(1, result.stepIndex());
      verify(sessionService).updateStep(1L, NvcPracticeStep.FEELING);
    }

    @Test
    @DisplayName("FEELING -> NEED 正常推进")
    void advanceFeelingToNeed() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.FEELING);
      when(sessionService.getSession(1L)).thenReturn(session);

      NvcAgentConfigEntity config = buildConfig(NvcAgentScene.STEP_NEED_COACH);
      when(agentConfigService.getConfig(NvcAgentScene.STEP_NEED_COACH))
          .thenReturn(config);
      when(sessionService.updateStep(1L, NvcPracticeStep.NEED))
          .thenReturn(session);

      StepProgressDTO result = service.advanceStep(1L);

      assertEquals(NvcPracticeStep.NEED, result.currentStep());
      assertEquals(2, result.stepIndex());
    }

    @Test
    @DisplayName("NEED -> REQUEST 正常推进")
    void advanceNeedToRequest() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.NEED);
      when(sessionService.getSession(1L)).thenReturn(session);

      NvcAgentConfigEntity config = buildConfig(NvcAgentScene.STEP_REQUEST_COACH);
      when(agentConfigService.getConfig(NvcAgentScene.STEP_REQUEST_COACH))
          .thenReturn(config);
      when(sessionService.updateStep(1L, NvcPracticeStep.REQUEST))
          .thenReturn(session);

      StepProgressDTO result = service.advanceStep(1L);

      assertEquals(NvcPracticeStep.REQUEST, result.currentStep());
      assertEquals(3, result.stepIndex());
    }

    @Test
    @DisplayName("REQUEST -> COMPLETED 返回完成状态")
    void advanceRequestToCompleted() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.REQUEST);
      when(sessionService.getSession(1L)).thenReturn(session);
      when(sessionService.updateStep(1L, NvcPracticeStep.COMPLETED))
          .thenReturn(session);

      StepProgressDTO result = service.advanceStep(1L);

      assertNotNull(result);
      assertEquals(NvcPracticeStep.COMPLETED, result.currentStep());
      assertEquals(4, result.stepIndex());
      assertEquals(4, result.totalSteps());
      assertFalse(result.canAdvance());
    }
  }

  // ========== canAdvance() ==========

  @Nested
  @DisplayName("canAdvance()")
  class CanAdvanceTests {

    @Test
    @DisplayName("currentStep 为 null 时返回 false")
    void nullStep_returnsFalse() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, null);
      when(sessionService.getSession(1L)).thenReturn(session);

      assertFalse(service.canAdvance(1L, 80));
    }

    @Test
    @DisplayName("currentStep 为 COMPLETED 时返回 false")
    void completedStep_returnsFalse() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.COMPLETED);
      when(sessionService.getSession(1L)).thenReturn(session);

      assertFalse(service.canAdvance(1L, 80));
    }

    @Test
    @DisplayName("分数为 null 时返回 false")
    void nullScore_returnsFalse() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.OBSERVE);
      when(sessionService.getSession(1L)).thenReturn(session);

      NvcAgentConfigEntity config = buildConfig(NvcAgentScene.STEP_OBSERVE_COACH);
      when(agentConfigService.getConfig(NvcAgentScene.STEP_OBSERVE_COACH))
          .thenReturn(config);

      assertFalse(service.canAdvance(1L, null));
    }

    @Test
    @DisplayName("分数低于阈值返回 false")
    void scoreBelowThreshold_returnsFalse() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.OBSERVE);
      when(sessionService.getSession(1L)).thenReturn(session);

      NvcAgentConfigEntity config = buildConfig(NvcAgentScene.STEP_OBSERVE_COACH);
      when(agentConfigService.getConfig(NvcAgentScene.STEP_OBSERVE_COACH))
          .thenReturn(config);

      assertFalse(service.canAdvance(1L, 69));
    }

    @Test
    @DisplayName("分数等于阈值返回 true")
    void scoreEqualsThreshold_returnsTrue() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.OBSERVE);
      when(sessionService.getSession(1L)).thenReturn(session);

      NvcAgentConfigEntity config = buildConfig(NvcAgentScene.STEP_OBSERVE_COACH);
      when(agentConfigService.getConfig(NvcAgentScene.STEP_OBSERVE_COACH))
          .thenReturn(config);

      assertTrue(service.canAdvance(1L, 70));
    }

    @Test
    @DisplayName("分数高于阈值返回 true")
    void scoreAboveThreshold_returnsTrue() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.NEED);
      when(sessionService.getSession(1L)).thenReturn(session);

      NvcAgentConfigEntity config = buildConfig(NvcAgentScene.STEP_NEED_COACH);
      when(agentConfigService.getConfig(NvcAgentScene.STEP_NEED_COACH))
          .thenReturn(config);

      assertTrue(service.canAdvance(1L, 90));
    }

    @Test
    @DisplayName("阈值为 null 时默认使用 70")
    void nullThreshold_usesDefault70() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.FEELING);
      when(sessionService.getSession(1L)).thenReturn(session);

      NvcAgentConfigEntity config = buildConfig(NvcAgentScene.STEP_FEELING_COACH);
      config.setStepAdvanceThreshold(null);
      when(agentConfigService.getConfig(NvcAgentScene.STEP_FEELING_COACH))
          .thenReturn(config);

      assertFalse(service.canAdvance(1L, 69));
      assertTrue(service.canAdvance(1L, 70));
    }
  }

  // ========== resetStep() ==========

  @Nested
  @DisplayName("resetStep()")
  class ResetStepTests {

    @Test
    @DisplayName("非结构化四步模式抛出 BAD_REQUEST")
    void nonStructuredMode_throwsBadRequest() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.SCENARIO, NvcPracticeStep.FEELING);
      when(sessionService.getSession(1L)).thenReturn(session);

      BusinessException ex = assertThrows(BusinessException.class,
          () -> service.resetStep(1L));

      assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("重置后回到 OBSERVE 步骤")
    void reset_returnsToObserve() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.REQUEST);
      when(sessionService.getSession(1L)).thenReturn(session);
      when(sessionService.updateStep(1L, NvcPracticeStep.OBSERVE))
          .thenReturn(session);

      NvcAgentConfigEntity config = buildConfig(NvcAgentScene.STEP_OBSERVE_COACH);
      when(agentConfigService.getConfig(NvcAgentScene.STEP_OBSERVE_COACH))
          .thenReturn(config);

      StepProgressDTO result = service.resetStep(1L);

      assertNotNull(result);
      assertEquals(NvcPracticeStep.OBSERVE, result.currentStep());
      assertEquals(0, result.stepIndex());
      assertEquals(4, result.totalSteps());
      assertFalse(result.canAdvance());
      verify(sessionService).updateStep(1L, NvcPracticeStep.OBSERVE);
    }

    @Test
    @DisplayName("已完成的会话也可以重置")
    void completedSession_canReset() {
      NvcPracticeSessionEntity session = buildSession(
          1L, NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.COMPLETED);
      when(sessionService.getSession(1L)).thenReturn(session);
      when(sessionService.updateStep(1L, NvcPracticeStep.OBSERVE))
          .thenReturn(session);

      NvcAgentConfigEntity config = buildConfig(NvcAgentScene.STEP_OBSERVE_COACH);
      when(agentConfigService.getConfig(NvcAgentScene.STEP_OBSERVE_COACH))
          .thenReturn(config);

      StepProgressDTO result = service.resetStep(1L);

      assertNotNull(result);
      assertEquals(NvcPracticeStep.OBSERVE, result.currentStep());
    }
  }
}
