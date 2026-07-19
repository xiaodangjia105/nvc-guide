package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import java.util.Map;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcMessageRole;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.repository.NvcEvaluationRepository;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeSessionRepository;
import nvc.guide.modules.nvcpractice.router.FreeDialogRouter;
import nvc.guide.modules.nvcpractice.router.ScenarioRouter;
import nvc.guide.modules.nvcpractice.router.StructuredRouter;
import nvc.guide.modules.nvcprofile.service.NvcProfileService;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import nvc.guide.modules.nvcscenario.repository.NvcScenarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcAgentOrchestrator 测试")
class NvcAgentOrchestratorTest {

  @Mock private NvcAgentConfigService agentConfigService;
  @Mock private NvcAgentChatService agentChatService;
  @Mock private NvcPracticeSessionRepository sessionRepository;
  @Mock private NvcPracticeMessageRepository messageRepository;
  @Mock private NvcEvaluationRepository evaluationRepository;
  @Mock private NvcScenarioRepository scenarioRepository;
  @Mock private NvcProfileService profileService;
  @Mock private NvcRagService ragService;
  @Mock private FreeDialogRouter freeDialogRouter;
  @Mock private ScenarioRouter scenarioRouter;
  @Mock private StructuredRouter structuredRouter;

  private NvcAgentOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    orchestrator = new NvcAgentOrchestrator(
        agentConfigService, agentChatService,
        sessionRepository, messageRepository,
        evaluationRepository, scenarioRepository,
        profileService, ragService,
        freeDialogRouter, scenarioRouter, structuredRouter);
  }

  private NvcPracticeSessionEntity buildSession(
      NvcSessionPhase phase, NvcPracticeMode mode, NvcPracticeStep step) {
    return NvcPracticeSessionEntity.builder()
        .id(1L)
        .userId(100L)
        .practiceMode(mode)
        .currentPhase(phase)
        .currentStep(step)
        .build();
  }

  private NvcEvaluationEntity buildEvaluation(
      int obs, int feel, int need, int req, int overall) {
    return NvcEvaluationEntity.builder()
        .id(1L)
        .sessionId(1L)
        .observationScore(obs)
        .feelingScore(feel)
        .needScore(need)
        .requestScore(req)
        .overallScore(overall)
        .build();
  }

  @Nested
  @DisplayName("buildPracticeContext()")
  class BuildPracticeContextTests {

    @Test
    @DisplayName("正常组装 PracticeContext")
    void buildContext_success() {
      NvcPracticeSessionEntity session = buildSession(
          NvcSessionPhase.IN_PROGRESS, NvcPracticeMode.FREE_DIALOG, null);
      when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

      NvcPracticeMessageEntity userMsg = NvcPracticeMessageEntity.builder()
          .id(1L).sessionId(1L).role(NvcMessageRole.USER).content("hello").sequenceNum(1).build();
      NvcPracticeMessageEntity aiMsg = NvcPracticeMessageEntity.builder()
          .id(2L).sessionId(1L).role(NvcMessageRole.ASSISTANT).content("hi").sequenceNum(2).build();
      when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L))
          .thenReturn(List.of(userMsg, aiMsg));

      NvcEvaluationEntity eval = buildEvaluation(70, 70, 70, 70, 70);
      when(evaluationRepository.findBySessionIdOrderByCreatedAtAsc(1L))
          .thenReturn(List.of(eval));

      PracticeContext ctx = orchestrator.buildPracticeContext(1L, 100L);

      assertEquals(session, ctx.getSession());
      assertEquals(2, ctx.getRecentMessages().size());
      assertEquals(eval, ctx.getLastEvaluation());
      assertEquals(1, ctx.getRoundCount());
      assertNull(ctx.getScenario());
    }

    @Test
    @DisplayName("session 不存在时抛出 BusinessException")
    void buildContext_sessionNotFound() {
      when(sessionRepository.findById(anyLong())).thenReturn(Optional.empty());

      assertThrows(BusinessException.class,
          () -> orchestrator.buildPracticeContext(999L, 100L));
    }

    @Test
    @DisplayName("有 scenarioId 时加载场景描述")
    void buildContext_withScenario() {
      NvcPracticeSessionEntity session = buildSession(
          NvcSessionPhase.IN_PROGRESS, NvcPracticeMode.SCENARIO, null);
      session.setScenarioId(42L);
      when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
      when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L))
          .thenReturn(List.of());
      when(evaluationRepository.findBySessionIdOrderByCreatedAtAsc(1L))
          .thenReturn(List.of());

      NvcScenarioEntity scenario = NvcScenarioEntity.builder()
          .id(42L).title("职场冲突").description("同事误解你的意图").build();
      when(scenarioRepository.findById(42L)).thenReturn(Optional.of(scenario));

      PracticeContext ctx = orchestrator.buildPracticeContext(1L, 100L);

      assertNotNull(ctx.getScenario());
      assertEquals("职场冲突\n同事误解你的意图", ctx.getScenarioDescription());
    }
  }

  @Nested
  @DisplayName("decideNextAgent()")
  class DecideNextAgentTests {

    @Test
    @DisplayName("FREE_DIALOG 模式委托给 FreeDialogRouter")
    void freeDialog_delegatesToRouter() {
      NvcPracticeSessionEntity session = buildSession(
          NvcSessionPhase.IN_PROGRESS, NvcPracticeMode.FREE_DIALOG, null);
      PracticeContext context = PracticeContext.builder()
          .session(session).roundCount(0).build();

      AgentDecision expected = new AgentDecision(
          NvcAgentScene.DIALOGUE_GUIDE, "free dialog", null);
      when(freeDialogRouter.route(context)).thenReturn(expected);

      AgentDecision decision = orchestrator.decideNextAgent(context);

      assertEquals(expected, decision);
      assertEquals(NvcAgentScene.DIALOGUE_GUIDE, decision.scene());
    }

    @Nested
    @DisplayName("结构化四步模式")
    class StructuredFourStepTests {

      @Test
      @DisplayName("STRUCTURED_FOUR_STEP 委托给 StructuredRouter")
      void structured_delegatesToRouter() {
        NvcPracticeSessionEntity session = buildSession(
            NvcSessionPhase.IN_PROGRESS,
            NvcPracticeMode.STRUCTURED_FOUR_STEP,
            NvcPracticeStep.OBSERVE);
        PracticeContext context = PracticeContext.builder()
            .session(session).lastEvaluation(null).build();

        AgentDecision expected = new AgentDecision(
            NvcAgentScene.DIALOGUE_GUIDE, "structured", null);
        when(structuredRouter.route(context)).thenReturn(expected);

        AgentDecision decision = orchestrator.decideNextAgent(context);

        assertEquals(expected, decision);
        assertEquals(NvcAgentScene.DIALOGUE_GUIDE, decision.scene());
      }
    }

    @Nested
    @DisplayName("场景驱动模式")
    class ScenarioTests {

      @Test
      @DisplayName("SCENARIO 模式委托给 ScenarioRouter")
      void scenario_delegatesToRouter() {
        NvcPracticeSessionEntity session = buildSession(
            NvcSessionPhase.IN_PROGRESS,
            NvcPracticeMode.SCENARIO, null);
        PracticeContext context = PracticeContext.builder()
            .session(session).lastEvaluation(null).build();

        AgentDecision expected = new AgentDecision(
            NvcAgentScene.DIFFICULT_PARTNER, "scenario", null);
        when(scenarioRouter.route(context)).thenReturn(expected);

        AgentDecision decision = orchestrator.decideNextAgent(context);

        assertEquals(expected, decision);
        assertEquals(NvcAgentScene.DIFFICULT_PARTNER, decision.scene());
      }
    }
  }

  @Nested
  @DisplayName("executeAgent()")
  class ExecuteAgentTests {

    @Test
    @DisplayName("调用配置的 Agent 并返回回复")
    void executeAgent_callsChatService() {
      NvcAgentConfigEntity config = NvcAgentConfigEntity.builder()
          .id(1L)
          .agentScene(NvcAgentScene.DIALOGUE_GUIDE)
          .systemPrompt("test")
          .modelProvider("mimo")
          .isEnabled(true)
          .build();
      when(agentConfigService.getConfig(NvcAgentScene.DIALOGUE_GUIDE)).thenReturn(config);

      PracticeContext context = PracticeContext.builder()
          .session(buildSession(NvcSessionPhase.IN_PROGRESS,
              NvcPracticeMode.FREE_DIALOG, null))
          .roundCount(1).build();

      when(agentChatService.chat(config, context, "你好", Map.of()))
          .thenReturn("你好！我是引导官。");

      AgentDecision decision = new AgentDecision(NvcAgentScene.DIALOGUE_GUIDE, "test", null);
      String result = orchestrator.executeAgent(decision, context, "你好");

      assertEquals("你好！我是引导官。", result);
    }

    @Test
    @DisplayName("Agent 禁用时 fallback 到 DIALOGUE_GUIDE")
    void disabledAgent_fallbackToDialogueGuide() {
      NvcAgentConfigEntity disabledConfig = NvcAgentConfigEntity.builder()
          .id(2L)
          .agentScene(NvcAgentScene.DIFFICULT_PARTNER)
          .isEnabled(false)
          .build();
      NvcAgentConfigEntity fallbackConfig = NvcAgentConfigEntity.builder()
          .id(1L)
          .agentScene(NvcAgentScene.DIALOGUE_GUIDE)
          .systemPrompt("fallback")
          .modelProvider("mimo")
          .isEnabled(true)
          .build();
      when(agentConfigService.getConfig(NvcAgentScene.DIFFICULT_PARTNER))
          .thenReturn(disabledConfig);
      when(agentConfigService.getConfig(NvcAgentScene.DIALOGUE_GUIDE))
          .thenReturn(fallbackConfig);

      PracticeContext context = PracticeContext.builder()
          .session(buildSession(NvcSessionPhase.IN_PROGRESS,
              NvcPracticeMode.FREE_DIALOG, null))
          .roundCount(1).build();

      when(agentChatService.chat(fallbackConfig, context, "hello", Map.of()))
          .thenReturn("fallback response");

      AgentDecision decision = new AgentDecision(NvcAgentScene.DIFFICULT_PARTNER, "test", "DIFFICULT_UPGRADE");
      String result = orchestrator.executeAgent(decision, context, "hello");

      assertEquals("fallback response", result);
    }
  }

  @Nested
  @DisplayName("reflect()")
  class ReflectTests {

    @Test
    @DisplayName("reflect() 使用评估官配置并包含评分数据")
    void reflect_usesEvaluatorConfig_withScores() {
      NvcPracticeSessionEntity session = buildSession(
          NvcSessionPhase.IN_PROGRESS,
          NvcPracticeMode.FREE_DIALOG, null);
      NvcEvaluationEntity eval = buildEvaluation(60, 50, 70, 65, 60);
      PracticeContext context = PracticeContext.builder()
          .session(session).lastEvaluation(eval).roundCount(3).build();

      NvcAgentConfigEntity evaluatorConfig = NvcAgentConfigEntity.builder()
          .id(2L)
          .agentScene(NvcAgentScene.NVC_EXPRESSION_EVALUATOR)
          .isEnabled(true)
          .build();

      when(agentConfigService.getConfig(NvcAgentScene.NVC_EXPRESSION_EVALUATOR))
          .thenReturn(evaluatorConfig);
      when(agentChatService.chatPlain(any(), anyString(), anyString()))
          .thenReturn("{\"weak_elements\":[\"feeling\"]}");

      String result = orchestrator.reflect(context);

      assertNotNull(result);
      verify(agentConfigService).getConfig(NvcAgentScene.NVC_EXPRESSION_EVALUATOR);
      verify(agentChatService).chatPlain(eq(evaluatorConfig), anyString(), anyString());
    }
  }
}
