package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.modules.knowledgebase.model.KnowledgeBaseType;
import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.NvcChatRequest;
import nvc.guide.modules.nvcpractice.dto.RagResult;
import nvc.guide.modules.nvcprofile.dto.AbilityRadarDTO;
import nvc.guide.modules.nvcprofile.model.NvcLevel;
import nvc.guide.modules.nvcprofile.model.NvcUserProfileEntity;
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
  @Mock private NvcScenarioRecommendService recommendService;
  @Mock private NvcReflectionService reflectionService;
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
        profileService, ragService, recommendService,
        reflectionService,
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

      when(agentChatService.chat(any(NvcChatRequest.class)))
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

      when(agentChatService.chat(any(NvcChatRequest.class)))
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

    @Test
    @DisplayName("无评估数据时返回默认 JSON")
    void reflect_noEvaluation_returnsDefaultJson() {
      NvcPracticeSessionEntity session = buildSession(
          NvcSessionPhase.IN_PROGRESS,
          NvcPracticeMode.FREE_DIALOG, null);
      PracticeContext context = PracticeContext.builder()
          .session(session).lastEvaluation(null).roundCount(0).build();

      String result = orchestrator.reflect(context);

      assertNotNull(result);
      assertTrue(result.contains("weak_elements"));
      assertTrue(result.contains("No evaluation data available"));
    }
  }

  // ========== executeAgentStream() ==========

  @Nested
  @DisplayName("executeAgentStream()")
  class ExecuteAgentStreamTests {

    @Test
    @DisplayName("流式执行返回 Flux<String>")
    void executeAgentStream_returnsFlux() {
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

      when(agentChatService.chatStream(any(NvcChatRequest.class)))
          .thenReturn(reactor.core.publisher.Flux.just("你好", "！", "我是引导官。"));

      AgentDecision decision = new AgentDecision(NvcAgentScene.DIALOGUE_GUIDE, "test", null);
      java.util.List<String> result = orchestrator.executeAgentStream(decision, context, "你好")
          .collectList().block();

      assertNotNull(result);
      assertEquals(3, result.size());
      assertEquals("你好", result.get(0));
    }

    @Test
    @DisplayName("Agent 禁用时 fallback 到 DIALOGUE_GUIDE（流式）")
    void executeAgentStream_disabledAgent_fallback() {
      NvcAgentConfigEntity disabledConfig = NvcAgentConfigEntity.builder()
          .id(2L).agentScene(NvcAgentScene.DIFFICULT_PARTNER).isEnabled(false).build();
      NvcAgentConfigEntity fallbackConfig = NvcAgentConfigEntity.builder()
          .id(1L).agentScene(NvcAgentScene.DIALOGUE_GUIDE)
          .systemPrompt("fallback").modelProvider("mimo").isEnabled(true).build();
      when(agentConfigService.getConfig(NvcAgentScene.DIFFICULT_PARTNER))
          .thenReturn(disabledConfig);
      when(agentConfigService.getConfig(NvcAgentScene.DIALOGUE_GUIDE))
          .thenReturn(fallbackConfig);

      PracticeContext context = PracticeContext.builder()
          .session(buildSession(NvcSessionPhase.IN_PROGRESS,
              NvcPracticeMode.FREE_DIALOG, null))
          .roundCount(1).build();

      when(agentChatService.chatStream(any(NvcChatRequest.class)))
          .thenReturn(reactor.core.publisher.Flux.just("fallback"));

      AgentDecision decision = new AgentDecision(
          NvcAgentScene.DIFFICULT_PARTNER, "test", null);
      java.util.List<String> result = orchestrator.executeAgentStream(decision, context, "hello")
          .collectList().block();

      assertNotNull(result);
      assertEquals("fallback", result.get(0));
    }
  }

  // ========== buildPracticeContext() RAG 路径 ==========

  @Nested
  @DisplayName("buildPracticeContext() RAG 路径")
  class BuildPracticeContextRagTests {

    private NvcUserProfileEntity buildProfile(Long userId) {
      return NvcUserProfileEntity.builder()
          .id(1L).userId(userId)
          .nvcLevel(NvcLevel.BEGINNER)
          .totalPracticeCount(5)
          .build();
    }

    @Test
    @DisplayName("场景驱动模式：RAG 查询使用场景描述")
    void buildContext_scenarioMode_ragUsesScenarioDescription() {
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

      NvcUserProfileEntity profile = buildProfile(100L);
      when(profileService.getOrCreateProfile(100L)).thenReturn(profile);
      when(profileService.getAbilityRadar(100L)).thenReturn(null);

      RagResult ragResult = new RagResult("职场沟通技巧", Map.of("source", "test"), 0.9);
      when(ragService.retrieve(eq("职场冲突\n同事误解你的意图"), any(), eq(3)))
          .thenReturn(List.of(ragResult));
      when(ragService.formatForPrompt(any())).thenReturn("RAG上下文");

      PracticeContext ctx = orchestrator.buildPracticeContext(1L, 100L);

      assertNotNull(ctx.getRagContext());
      assertEquals("RAG上下文", ctx.getRagContext());
      verify(ragService).retrieve(eq("职场冲突\n同事误解你的意图"), any(), eq(3));
    }

    @Test
    @DisplayName("自由对话模式：RAG 查询使用用户最新消息")
    void buildContext_freeDialog_ragUsesLastUserMessage() {
      NvcPracticeSessionEntity session = buildSession(
          NvcSessionPhase.IN_PROGRESS, NvcPracticeMode.FREE_DIALOG, null);
      when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

      NvcPracticeMessageEntity userMsg = NvcPracticeMessageEntity.builder()
          .id(1L).sessionId(1L).role(NvcMessageRole.USER)
          .content("如何表达我的感受？").sequenceNum(1).build();
      when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L))
          .thenReturn(List.of(userMsg));
      when(evaluationRepository.findBySessionIdOrderByCreatedAtAsc(1L))
          .thenReturn(List.of());

      NvcUserProfileEntity profile = buildProfile(100L);
      when(profileService.getOrCreateProfile(100L)).thenReturn(profile);
      when(profileService.getAbilityRadar(100L)).thenReturn(null);

      RagResult ragResult = new RagResult("感受表达", Map.of("source", "test"), 0.8);
      when(ragService.retrieve(eq("如何表达我的感受？"), any(), eq(3)))
          .thenReturn(List.of(ragResult));
      when(ragService.formatForPrompt(any())).thenReturn("RAG上下文");

      PracticeContext ctx = orchestrator.buildPracticeContext(1L, 100L);

      assertNotNull(ctx.getRagContext());
      verify(ragService).retrieve(eq("如何表达我的感受？"), any(), eq(3));
    }

    @Test
    @DisplayName("结构化四步模式：RAG 查询使用步骤名称")
    void buildContext_structuredMode_ragUsesStepName() {
      NvcPracticeSessionEntity session = buildSession(
          NvcSessionPhase.IN_PROGRESS,
          NvcPracticeMode.STRUCTURED_FOUR_STEP,
          NvcPracticeStep.FEELING);
      when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
      when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L))
          .thenReturn(List.of());
      when(evaluationRepository.findBySessionIdOrderByCreatedAtAsc(1L))
          .thenReturn(List.of());

      NvcUserProfileEntity profile = buildProfile(100L);
      when(profileService.getOrCreateProfile(100L)).thenReturn(profile);
      when(profileService.getAbilityRadar(100L)).thenReturn(null);

      RagResult ragResult = new RagResult("感受练习", Map.of("source", "test"), 0.8);
      when(ragService.retrieve(eq("NVC FEELING 练习"), any(), eq(3)))
          .thenReturn(List.of(ragResult));
      when(ragService.formatForPrompt(any())).thenReturn("RAG上下文");

      PracticeContext ctx = orchestrator.buildPracticeContext(1L, 100L);

      assertNotNull(ctx.getRagContext());
      verify(ragService).retrieve(eq("NVC FEELING 练习"), any(), eq(3));
    }

    @Test
    @DisplayName("无 RAG 查询条件时 ragContext 为 null")
    void buildContext_noRagQuery_ragContextIsNull() {
      NvcPracticeSessionEntity session = buildSession(
          NvcSessionPhase.IN_PROGRESS,
          NvcPracticeMode.STRUCTURED_FOUR_STEP, null);
      when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
      when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L))
          .thenReturn(List.of());
      when(evaluationRepository.findBySessionIdOrderByCreatedAtAsc(1L))
          .thenReturn(List.of());

      NvcUserProfileEntity profile = buildProfile(100L);
      when(profileService.getOrCreateProfile(100L)).thenReturn(profile);

      PracticeContext ctx = orchestrator.buildPracticeContext(1L, 100L);

      assertNull(ctx.getRagContext());
    }

    @Test
    @DisplayName("有薄弱要素时使用个性化检索")
    void buildContext_withWeakElement_usesPersonalizedRag() {
      NvcPracticeSessionEntity session = buildSession(
          NvcSessionPhase.IN_PROGRESS, NvcPracticeMode.FREE_DIALOG, null);
      when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

      NvcPracticeMessageEntity userMsg = NvcPracticeMessageEntity.builder()
          .id(1L).sessionId(1L).role(NvcMessageRole.USER)
          .content("我不知道怎么表达需求").sequenceNum(1).build();
      when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L))
          .thenReturn(List.of(userMsg));
      when(evaluationRepository.findBySessionIdOrderByCreatedAtAsc(1L))
          .thenReturn(List.of());

      NvcUserProfileEntity profile = buildProfile(100L);
      when(profileService.getOrCreateProfile(100L)).thenReturn(profile);

      AbilityRadarDTO radar = new AbilityRadarDTO(
          80, 60, 40, 70, 65, 62, "BEGINNER");  // need=40 is weakest
      when(profileService.getAbilityRadar(100L)).thenReturn(radar);

      RagResult ragResult = new RagResult("需求表达", Map.of("source", "test"), 0.9);
      when(ragService.retrievePersonalized(
          eq("我不知道怎么表达需求"), eq("need"), eq(3)))
          .thenReturn(List.of(ragResult));
      when(ragService.formatForPrompt(any())).thenReturn("个性化RAG");

      PracticeContext ctx = orchestrator.buildPracticeContext(1L, 100L);

      assertEquals("个性化RAG", ctx.getRagContext());
      verify(ragService).retrievePersonalized(
          eq("我不知道怎么表达需求"), eq("need"), eq(3));
    }

    @Test
    @DisplayName("无薄弱要素（全 0 分）时使用标准检索")
    void buildContext_allZeroScores_usesStandardRag() {
      NvcPracticeSessionEntity session = buildSession(
          NvcSessionPhase.IN_PROGRESS, NvcPracticeMode.FREE_DIALOG, null);
      when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

      NvcPracticeMessageEntity userMsg = NvcPracticeMessageEntity.builder()
          .id(1L).sessionId(1L).role(NvcMessageRole.USER)
          .content("你好").sequenceNum(1).build();
      when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L))
          .thenReturn(List.of(userMsg));
      when(evaluationRepository.findBySessionIdOrderByCreatedAtAsc(1L))
          .thenReturn(List.of());

      NvcUserProfileEntity profile = buildProfile(100L);
      when(profileService.getOrCreateProfile(100L)).thenReturn(profile);

      AbilityRadarDTO radar = new AbilityRadarDTO(0, 0, 0, 0, 0, 0, "BEGINNER");
      when(profileService.getAbilityRadar(100L)).thenReturn(radar);

      RagResult ragResult = new RagResult("NVC基础", Map.of("source", "test"), 0.8);
      when(ragService.retrieve(eq("你好"), any(), eq(3)))
          .thenReturn(List.of(ragResult));
      when(ragService.formatForPrompt(any())).thenReturn("标准RAG");

      PracticeContext ctx = orchestrator.buildPracticeContext(1L, 100L);

      assertEquals("标准RAG", ctx.getRagContext());
      verify(ragService).retrieve(eq("你好"), any(), eq(3));
    }
  }
}
