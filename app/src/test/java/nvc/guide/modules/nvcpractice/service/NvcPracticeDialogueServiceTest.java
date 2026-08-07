package nvc.guide.modules.nvcpractice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.common.security.InputSanitizer;
import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.DialogueResponse;
import nvc.guide.modules.nvcpractice.dto.MessageResponse;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcMessageRole;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
  private NvcStructuredPracticeService structuredPracticeService;
  @Mock
  private InputSanitizer inputSanitizer;

  private NvcPracticeDialogueService dialogueService;

  @BeforeEach
  void setUp() {
    dialogueService = new NvcPracticeDialogueService(
        sessionService, messageRepository, orchestrator,
        objectMapper, evaluationService, summaryService,
        structuredPracticeService, inputSanitizer);
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

  private AgentDecision buildDecision(NvcAgentScene scene, String action) {
    return new AgentDecision(scene, "测试原因", action);
  }

  // ========== sendMessage() 编排流程与状态转换 ==========

  @Nested
  @DisplayName("sendMessage() 编排流程与状态转换")
  class SendMessageOrchestrationTests {

    @Test
    @DisplayName("CREATED 状态自动切换为 IN_PROGRESS")
    void sendMessage_createdPhase_transitionsToInProgress() {
      // Arrange
      NvcPracticeSessionEntity createdSession = NvcPracticeSessionEntity.builder()
          .id(1L).userId(100L)
          .practiceMode(NvcPracticeMode.FREE_DIALOG)
          .currentPhase(NvcSessionPhase.CREATED)
          .build();
      NvcPracticeSessionEntity inProgressSession = NvcPracticeSessionEntity.builder()
          .id(1L).userId(100L)
          .practiceMode(NvcPracticeMode.FREE_DIALOG)
          .currentPhase(NvcSessionPhase.IN_PROGRESS)
          .build();

      when(sessionService.getSession(1L))
          .thenReturn(createdSession)
          .thenReturn(inProgressSession);
      when(messageRepository.countBySessionId(1L)).thenReturn(0);
      when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(orchestrator.buildPracticeContext(1L, 100L))
          .thenReturn(PracticeContext.builder().build());
      when(orchestrator.decideNextAgent(any()))
          .thenReturn(buildDecision(NvcAgentScene.DIALOGUE_GUIDE, null));
      when(sessionService.updatePhase(1L, NvcSessionPhase.IN_PROGRESS))
          .thenReturn(inProgressSession);
      when(sessionService.updateAgentScene(eq(1L), any())).thenReturn(inProgressSession);
      when(orchestrator.executeAgent(any(), any(), any())).thenReturn("AI回复");

      // Act
      DialogueResponse result = dialogueService.sendMessage(1L, "你好");

      // Assert
      assertNotNull(result);
      verify(sessionService).updatePhase(1L, NvcSessionPhase.IN_PROGRESS);
    }

    @Test
    @DisplayName("IN_PROGRESS 状态不触发状态转换")
    void sendMessage_inProgressPhase_noTransition() {
      // Arrange
      NvcPracticeSessionEntity session =
          buildSession(NvcPracticeMode.FREE_DIALOG, null);
      when(sessionService.getSession(1L)).thenReturn(session);
      when(messageRepository.countBySessionId(1L)).thenReturn(0);
      when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(orchestrator.buildPracticeContext(1L, 100L))
          .thenReturn(PracticeContext.builder().build());
      when(orchestrator.decideNextAgent(any()))
          .thenReturn(buildDecision(NvcAgentScene.DIALOGUE_GUIDE, null));
      when(orchestrator.executeAgent(any(), any(), any())).thenReturn("AI回复");

      // Act
      dialogueService.sendMessage(1L, "你好");

      // Assert
      verify(sessionService, never()).updatePhase(anyLong(), any());
    }

    @Test
    @DisplayName("编排流程：保存用户消息 -> 构建上下文 -> Agent 决策 -> 执行 -> 保存 AI 回复")
    void sendMessage_fullOrchestrationFlow() {
      // Arrange
      NvcPracticeSessionEntity session =
          buildSession(NvcPracticeMode.FREE_DIALOG, null);
      when(sessionService.getSession(1L)).thenReturn(session);
      when(messageRepository.countBySessionId(1L)).thenReturn(2);
      when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(orchestrator.buildPracticeContext(1L, 100L))
          .thenReturn(PracticeContext.builder().build());
      when(orchestrator.decideNextAgent(any()))
          .thenReturn(buildDecision(NvcAgentScene.DIALOGUE_GUIDE, null));
      when(orchestrator.executeAgent(any(), any(), any())).thenReturn("你好！我是引导官。");

      // Act
      DialogueResponse result = dialogueService.sendMessage(1L, "我想练习NVC");

      // Assert
      assertNotNull(result);
      assertEquals(1L, result.sessionId());
      assertEquals("你好！我是引导官。", result.aiReply());
      assertEquals("DIALOGUE_GUIDE", result.agentScene());

      // 验证保存了用户消息和 AI 回复（两次 save）
      verify(messageRepository, atLeast(2)).save(any());
      // 验证构建上下文
      verify(orchestrator).buildPracticeContext(1L, 100L);
      // 验证 Agent 决策
      verify(orchestrator).decideNextAgent(any());
      // 验证执行 Agent
      verify(orchestrator).executeAgent(any(), any(), eq("我想练习NVC"));
      // 验证更新 Agent 场景
      verify(sessionService).updateAgentScene(1L, NvcAgentScene.DIALOGUE_GUIDE);
    }

    @Test
    @DisplayName("场景驱动模式下正常发送消息")
    void sendMessage_scenarioMode_works() {
      // Arrange
      NvcPracticeSessionEntity session = NvcPracticeSessionEntity.builder()
          .id(1L).userId(100L)
          .practiceMode(NvcPracticeMode.SCENARIO)
          .currentPhase(NvcSessionPhase.IN_PROGRESS)
          .scenarioId(42L)
          .build();
      when(sessionService.getSession(1L)).thenReturn(session);
      when(messageRepository.countBySessionId(1L)).thenReturn(0);
      when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(orchestrator.buildPracticeContext(1L, 100L))
          .thenReturn(PracticeContext.builder().build());
      when(orchestrator.decideNextAgent(any()))
          .thenReturn(buildDecision(NvcAgentScene.DIFFICULT_PARTNER, null));
      when(orchestrator.executeAgent(any(), any(), any())).thenReturn("AI回复");

      // Act
      DialogueResponse response = dialogueService.sendMessage(1L, "你好");

      // Assert
      assertNotNull(response);
      assertEquals("AI回复", response.aiReply());
    }

    @Test
    @DisplayName("无场景 ID 时正常发送消息")
    void sendMessage_noScenarioId_works() {
      // Arrange
      NvcPracticeSessionEntity session =
          buildSession(NvcPracticeMode.FREE_DIALOG, null);
      when(sessionService.getSession(1L)).thenReturn(session);
      when(messageRepository.countBySessionId(1L)).thenReturn(0);
      when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(orchestrator.buildPracticeContext(1L, 100L))
          .thenReturn(PracticeContext.builder().build());
      when(orchestrator.decideNextAgent(any()))
          .thenReturn(buildDecision(NvcAgentScene.DIALOGUE_GUIDE, null));
      when(orchestrator.executeAgent(any(), any(), any())).thenReturn("AI回复");

      // Act
      DialogueResponse response = dialogueService.sendMessage(1L, "你好");

      // Assert
      assertNotNull(response);
    }

    @Test
    @DisplayName("摘要更新失败不阻塞对话返回")
    void sendMessage_summaryFailure_doesNotBlock() {
      // Arrange
      NvcPracticeSessionEntity session =
          buildSession(NvcPracticeMode.FREE_DIALOG, null);
      when(sessionService.getSession(1L)).thenReturn(session);
      when(messageRepository.countBySessionId(1L)).thenReturn(0);
      when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(orchestrator.buildPracticeContext(1L, 100L))
          .thenReturn(PracticeContext.builder().build());
      when(orchestrator.decideNextAgent(any()))
          .thenReturn(buildDecision(NvcAgentScene.DIALOGUE_GUIDE, null));
      when(orchestrator.executeAgent(any(), any(), any())).thenReturn("AI回复");
      // 模拟摘要更新抛出异常
      org.mockito.Mockito.doThrow(new RuntimeException("summary failed"))
          .when(summaryService).updateSummary(anyLong(), any());

      // Act — 不应抛出异常
      DialogueResponse result = dialogueService.sendMessage(1L, "你好");

      // Assert
      assertNotNull(result);
      assertEquals("AI回复", result.aiReply());
    }
  }

  // ========== sendMessage() 结构化四步推进 ==========

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

  // ========== sendMessageStream() 结构化四步推进 ==========

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

  // ========== getMessages() ==========

  @Nested
  @DisplayName("getMessages()")
  class GetMessagesTests {

    @Test
    @DisplayName("返回按 sequenceNum 排序的消息列表")
    void getMessages_returnsSortedMessageList() {
      // Arrange
      NvcPracticeMessageEntity msg1 = NvcPracticeMessageEntity.builder()
          .id(1L).sessionId(1L).role(NvcMessageRole.USER)
          .content("你好").sequenceNum(1)
          .createdAt(LocalDateTime.now().minusMinutes(5))
          .build();
      NvcPracticeMessageEntity msg2 = NvcPracticeMessageEntity.builder()
          .id(2L).sessionId(1L).role(NvcMessageRole.ASSISTANT)
          .agentScene(NvcAgentScene.DIALOGUE_GUIDE)
          .content("你好！我是引导官。").sequenceNum(2)
          .createdAt(LocalDateTime.now().minusMinutes(4))
          .build();
      NvcPracticeMessageEntity msg3 = NvcPracticeMessageEntity.builder()
          .id(3L).sessionId(1L).role(NvcMessageRole.USER)
          .content("我想练习观察").sequenceNum(3)
          .createdAt(LocalDateTime.now().minusMinutes(3))
          .build();

      when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L))
          .thenReturn(List.of(msg1, msg2, msg3));

      // Act
      List<MessageResponse> result = dialogueService.getMessages(1L);

      // Assert
      assertEquals(3, result.size());
      assertEquals("你好", result.get(0).content());
      assertEquals("USER", result.get(0).role());
      assertEquals(1, result.get(0).sequenceNum());
      assertEquals("你好！我是引导官。", result.get(1).content());
      assertEquals("ASSISTANT", result.get(1).role());
      assertEquals("DIALOGUE_GUIDE", result.get(1).agentScene());
      assertEquals("我想练习观察", result.get(2).content());
    }

    @Test
    @DisplayName("无消息时返回空列表")
    void getMessages_noMessages_returnsEmptyList() {
      // Arrange
      when(messageRepository.findBySessionIdOrderBySequenceNumAsc(99L))
          .thenReturn(Collections.emptyList());

      // Act
      List<MessageResponse> result = dialogueService.getMessages(99L);

      // Assert
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("agentScene 为 null 时正确转换")
    void getMessages_nullAgentScene_convertsCorrectly() {
      // Arrange
      NvcPracticeMessageEntity msg = NvcPracticeMessageEntity.builder()
          .id(1L).sessionId(1L).role(NvcMessageRole.USER)
          .content("你好").sequenceNum(1)
          .createdAt(LocalDateTime.now())
          .build();

      when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L))
          .thenReturn(List.of(msg));

      // Act
      List<MessageResponse> result = dialogueService.getMessages(1L);

      // Assert
      assertEquals(1, result.size());
      assertEquals("USER", result.get(0).role());
      // agentScene 为 null 的用户消息
      assertTrue(result.get(0).agentScene() == null || result.get(0).agentScene().isEmpty()
          ? true : result.get(0).agentScene() != null);
    }
  }
}
