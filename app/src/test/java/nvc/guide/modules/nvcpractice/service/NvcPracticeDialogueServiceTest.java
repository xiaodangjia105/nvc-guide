package nvc.guide.modules.nvcpractice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.DialogueResponse;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import nvc.guide.modules.nvcscenario.service.NvcScenarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcPracticeDialogueService 测试")
class NvcPracticeDialogueServiceTest {

  @Mock
  private NvcPracticeSessionService sessionService;
  @Mock
  private NvcPracticeMessageRepository messageRepository;
  @Mock
  private NvcAgentOrchestrator orchestrator;
  @Mock
  private ObjectMapper objectMapper;
  @Mock
  private NvcEvaluationService evaluationService;
  @Mock
  private NvcSummaryService summaryService;
  @Mock
  private NvcScenarioService scenarioService;
  @Mock
  private NvcStructuredPracticeService structuredPracticeService;

  private NvcPracticeDialogueService dialogueService;

  @BeforeEach
  void setUp() {
    dialogueService = new NvcPracticeDialogueService(
        sessionService, messageRepository, orchestrator,
        objectMapper, evaluationService, summaryService, scenarioService,
        structuredPracticeService);
  }

  private NvcPracticeSessionEntity buildSession(
      NvcPracticeMode mode, NvcPracticeStep step) {
    return NvcPracticeSessionEntity.builder()
        .id(1L)
        .userId(100L)
        .practiceMode(mode)
        .currentPhase(NvcSessionPhase.IN_PROGRESS)
        .currentStep(step)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  // ========== sendMessage() step advancement ==========

  @Nested
  @DisplayName("sendMessage() 结构化四步推进")
  class SendMessageStepAdvancementTests {

    @Test
    @DisplayName("STEP_ADVANCE action 触发步骤推进")
    void stepAdvanceAction_triggersAdvance() {
      // Arrange
      NvcPracticeSessionEntity session =
          buildSession(NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.OBSERVE);
      when(sessionService.getSession(1L)).thenReturn(session);
      when(messageRepository.countBySessionId(1L)).thenReturn(0);
      when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      AgentDecision decision = new AgentDecision(
          NvcAgentScene.STEP_OBSERVE_COACH, "测试", "STEP_ADVANCE");
      when(orchestrator.buildPracticeContext(1L, 100L))
          .thenReturn(PracticeContext.builder().build());
      when(orchestrator.decideNextAgent(any())).thenReturn(decision);
      when(orchestrator.executeAgent(any(), any(), any())).thenReturn("AI回复");

      NvcPracticeSessionEntity advancedSession =
          buildSession(NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.FEELING);
      when(structuredPracticeService.advanceStep(1L)).thenReturn(null);
      when(sessionService.getSession(1L))
          .thenReturn(session)      // 第一次调用返回原 session
          .thenReturn(advancedSession); // 推进后返回新 session

      // Act
      DialogueResponse result = dialogueService.sendMessage(1L, "用户消息");

      // Assert
      assertNotNull(result);
      verify(structuredPracticeService).advanceStep(1L);
      assertEquals("FEELING", result.currentStep());
    }

    @Test
    @DisplayName("非 STEP_ADVANCE action 不触发步骤推进")
    void nonStepAdvanceAction_doesNotTriggerAdvance() {
      // Arrange
      NvcPracticeSessionEntity session =
          buildSession(NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.OBSERVE);
      when(sessionService.getSession(1L)).thenReturn(session);
      when(messageRepository.countBySessionId(1L)).thenReturn(0);
      when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      AgentDecision decision = new AgentDecision(
          NvcAgentScene.STEP_OBSERVE_COACH, "测试", "CONTINUE");
      when(orchestrator.buildPracticeContext(1L, 100L))
          .thenReturn(PracticeContext.builder().build());
      when(orchestrator.decideNextAgent(any())).thenReturn(decision);
      when(orchestrator.executeAgent(any(), any(), any())).thenReturn("AI回复");

      // Act
      DialogueResponse result = dialogueService.sendMessage(1L, "用户消息");

      // Assert
      assertNotNull(result);
      verify(structuredPracticeService, never()).advanceStep(anyLong());
      assertEquals("OBSERVE", result.currentStep());
    }

    @Test
    @DisplayName("非结构化四步模式不触发步骤推进")
    void nonStructuredMode_doesNotTriggerAdvance() {
      // Arrange
      NvcPracticeSessionEntity session =
          buildSession(NvcPracticeMode.FREE_DIALOG, null);
      when(sessionService.getSession(1L)).thenReturn(session);
      when(messageRepository.countBySessionId(1L)).thenReturn(0);
      when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      AgentDecision decision = new AgentDecision(
          NvcAgentScene.DIALOGUE_GUIDE, "测试", "STEP_ADVANCE");
      when(orchestrator.buildPracticeContext(1L, 100L))
          .thenReturn(PracticeContext.builder().build());
      when(orchestrator.decideNextAgent(any())).thenReturn(decision);
      when(orchestrator.executeAgent(any(), any(), any())).thenReturn("AI回复");

      // Act
      DialogueResponse result = dialogueService.sendMessage(1L, "用户消息");

      // Assert
      assertNotNull(result);
      verify(structuredPracticeService, never()).advanceStep(anyLong());
    }

    @Test
    @DisplayName("action 为 null 时不触发步骤推进")
    void nullAction_doesNotTriggerAdvance() {
      // Arrange
      NvcPracticeSessionEntity session =
          buildSession(NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.OBSERVE);
      when(sessionService.getSession(1L)).thenReturn(session);
      when(messageRepository.countBySessionId(1L)).thenReturn(0);
      when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      AgentDecision decision = new AgentDecision(
          NvcAgentScene.STEP_OBSERVE_COACH, "测试", null);
      when(orchestrator.buildPracticeContext(1L, 100L))
          .thenReturn(PracticeContext.builder().build());
      when(orchestrator.decideNextAgent(any())).thenReturn(decision);
      when(orchestrator.executeAgent(any(), any(), any())).thenReturn("AI回复");

      // Act
      DialogueResponse result = dialogueService.sendMessage(1L, "用户消息");

      // Assert
      assertNotNull(result);
      verify(structuredPracticeService, never()).advanceStep(anyLong());
    }
  }

  // ========== sendMessageStream() step advancement ==========

  @Nested
  @DisplayName("sendMessageStream() 结构化四步推进")
  class SendMessageStreamStepAdvancementTests {

    @Test
    @DisplayName("STEP_ADVANCE action 触发步骤推进（流式 doOnComplete 回调）")
    void streamStepAdvanceAction_triggersAdvance() {
      // Arrange
      NvcPracticeSessionEntity session =
          buildSession(NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.OBSERVE);
      when(sessionService.getSession(1L)).thenReturn(session);
      when(messageRepository.countBySessionId(1L)).thenReturn(0);
      when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      AgentDecision decision = new AgentDecision(
          NvcAgentScene.STEP_OBSERVE_COACH, "测试", "STEP_ADVANCE");
      when(orchestrator.buildPracticeContext(1L, 100L))
          .thenReturn(PracticeContext.builder().build());
      when(orchestrator.decideNextAgent(any())).thenReturn(decision);
      when(orchestrator.executeAgentStream(any(), any(), any()))
          .thenReturn(Flux.just("AI", "回复"));
      when(structuredPracticeService.advanceStep(1L)).thenReturn(null);

      // 让 objectMapper 序列化成功
      try {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
      } catch (Exception ignored) {
        // 不会发生
      }

      // Act — 订阅 Flux 并等待完成，触发 doOnComplete 回调
      dialogueService.sendMessageStream(1L, "用户消息")
          .collectList().block();

      // Assert — doOnComplete 中应调用 advanceStep
      verify(structuredPracticeService).advanceStep(1L);
      // 用户消息 + AI 回复至少保存两次
      verify(messageRepository, atLeast(2)).save(any());
    }

    @Test
    @DisplayName("非 STEP_ADVANCE action 不触发步骤推进（流式）")
    void streamNonStepAdvanceAction_doesNotTriggerAdvance() {
      // Arrange
      NvcPracticeSessionEntity session =
          buildSession(NvcPracticeMode.STRUCTURED_FOUR_STEP, NvcPracticeStep.OBSERVE);
      when(sessionService.getSession(1L)).thenReturn(session);
      when(messageRepository.countBySessionId(1L)).thenReturn(0);
      when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      AgentDecision decision = new AgentDecision(
          NvcAgentScene.STEP_OBSERVE_COACH, "测试", "CONTINUE");
      when(orchestrator.buildPracticeContext(1L, 100L))
          .thenReturn(PracticeContext.builder().build());
      when(orchestrator.decideNextAgent(any())).thenReturn(decision);
      when(orchestrator.executeAgentStream(any(), any(), any()))
          .thenReturn(Flux.just("AI", "回复"));

      try {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
      } catch (Exception ignored) {
      }

      // Act
      dialogueService.sendMessageStream(1L, "用户消息")
          .collectList().block();

      // Assert
      verify(structuredPracticeService, never()).advanceStep(anyLong());
    }

    @Test
    @DisplayName("非结构化四步模式不触发步骤推进（流式）")
    void streamNonStructuredMode_doesNotTriggerAdvance() {
      // Arrange
      NvcPracticeSessionEntity session =
          buildSession(NvcPracticeMode.FREE_DIALOG, null);
      when(sessionService.getSession(1L)).thenReturn(session);
      when(messageRepository.countBySessionId(1L)).thenReturn(0);
      when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      AgentDecision decision = new AgentDecision(
          NvcAgentScene.DIALOGUE_GUIDE, "测试", "STEP_ADVANCE");
      when(orchestrator.buildPracticeContext(1L, 100L))
          .thenReturn(PracticeContext.builder().build());
      when(orchestrator.decideNextAgent(any())).thenReturn(decision);
      when(orchestrator.executeAgentStream(any(), any(), any()))
          .thenReturn(Flux.just("AI", "回复"));

      try {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
      } catch (Exception ignored) {
      }

      // Act
      dialogueService.sendMessageStream(1L, "用户消息")
          .collectList().block();

      // Assert
      verify(structuredPracticeService, never()).advanceStep(anyLong());
    }
  }
}
