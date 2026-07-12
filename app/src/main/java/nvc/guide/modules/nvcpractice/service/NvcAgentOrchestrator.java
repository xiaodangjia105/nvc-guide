package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcpractice.dto.AgentDecision;
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
import nvc.guide.modules.nvcpractice.router.ModeRouter;
import nvc.guide.modules.nvcpractice.router.ScenarioRouter;
import nvc.guide.modules.nvcpractice.router.StructuredRouter;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import nvc.guide.modules.nvcscenario.repository.NvcScenarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * NVC Agent 调度中心
 * 负责：组装 PracticeContext、决定下一步 Agent、执行对话、反思分析
 *
 * 使用 ModeRouter 策略模式，每种练习模式有独立的路由逻辑
 */
@Service
@Slf4j
public class NvcAgentOrchestrator {

  private final NvcAgentConfigService agentConfigService;
  private final NvcAgentChatService agentChatService;
  private final NvcPracticeSessionRepository sessionRepository;
  private final NvcPracticeMessageRepository messageRepository;
  private final NvcEvaluationRepository evaluationRepository;
  private final NvcScenarioRepository scenarioRepository;
  private final Map<NvcPracticeMode, ModeRouter> routers;

  public NvcAgentOrchestrator(
      NvcAgentConfigService agentConfigService,
      NvcAgentChatService agentChatService,
      NvcPracticeSessionRepository sessionRepository,
      NvcPracticeMessageRepository messageRepository,
      NvcEvaluationRepository evaluationRepository,
      NvcScenarioRepository scenarioRepository,
      FreeDialogRouter freeDialogRouter,
      ScenarioRouter scenarioRouter,
      StructuredRouter structuredRouter) {
    this.agentConfigService = agentConfigService;
    this.agentChatService = agentChatService;
    this.sessionRepository = sessionRepository;
    this.messageRepository = messageRepository;
    this.evaluationRepository = evaluationRepository;
    this.scenarioRepository = scenarioRepository;

    this.routers = new EnumMap<>(NvcPracticeMode.class);
    this.routers.put(NvcPracticeMode.FREE_DIALOG, freeDialogRouter);
    this.routers.put(NvcPracticeMode.SCENARIO, scenarioRouter);
    this.routers.put(NvcPracticeMode.STRUCTURED_FOUR_STEP, structuredRouter);
  }

  private static final int RECENT_MESSAGES_WINDOW = 20;

  // ==================== Context 组装 ====================

  /**
   * 从 DB 组装 PracticeContext
   * 外部调用者只需传 sessionId 和 userId
   */
  public PracticeContext buildPracticeContext(Long sessionId, Long userId) {
    NvcPracticeSessionEntity session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.NVC_SESSION_NOT_FOUND,
            "Session not found: " + sessionId));

    // 最近 N 条消息（正序）
    List<NvcPracticeMessageEntity> allMessages =
        messageRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);
    List<NvcPracticeMessageEntity> recentMessages = allMessages.size() <= RECENT_MESSAGES_WINDOW
        ? allMessages
        : allMessages.subList(
            allMessages.size() - RECENT_MESSAGES_WINDOW, allMessages.size());

    // 最新评估
    List<NvcEvaluationEntity> evaluations =
        evaluationRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    NvcEvaluationEntity lastEvaluation = evaluations.isEmpty()
        ? null : evaluations.get(evaluations.size() - 1);

    // 轮次计算（用户消息数）
    long userMessageCount = allMessages.stream()
        .filter(m -> m.getRole() == NvcMessageRole.USER)
        .count();

    // 场景描述
    String scenarioDescription = null;
    NvcScenarioEntity scenario = null;
    if (session.getScenarioId() != null) {
      scenario = scenarioRepository.findById(session.getScenarioId()).orElse(null);
      if (scenario != null) {
        scenarioDescription = scenario.getTitle() + "\n" + scenario.getDescription();
      }
    }

    return PracticeContext.builder()
        .session(session)
        .recentMessages(recentMessages)
        .lastEvaluation(lastEvaluation)
        .roundCount((int) userMessageCount)
        .scenarioDescription(scenarioDescription)
        .scenario(scenario)
        .build();
  }

  // ==================== PLAN：分析当前状态，规划下一步 ====================

  /**
   * 决定下一步使用哪个 Agent
   * 通过 ModeRouter 策略模式，根据练习模式选择对应的路由逻辑
   */
  public AgentDecision decideNextAgent(PracticeContext context) {
    NvcPracticeMode mode = context.getSession().getPracticeMode();
    ModeRouter router = routers.get(mode);

    if (router == null) {
      log.warn("No router found for mode: {}, falling back to FREE_DIALOG", mode);
      router = routers.get(NvcPracticeMode.FREE_DIALOG);
    }

    AgentDecision decision = router.route(context);
    log.info("Agent decision: scene={}, reason={}, vars={}",
        decision.scene(), decision.reason(), decision.promptVariables());
    return decision;
  }

  // ==================== EXECUTE：执行 Agent 对话 ====================

  /**
   * 执行 Agent 对话，获取 AI 回复
   */
  public String executeAgent(
      AgentDecision decision, PracticeContext context, String userMessage) {
    NvcAgentConfigEntity config = agentConfigService.getConfig(decision.scene());

    // 检查 Agent 是否启用，禁用则 fallback
    if (!config.getIsEnabled()) {
      log.warn("Agent is disabled: {}, falling back to DIALOGUE_GUIDE", decision.scene());
      config = agentConfigService.getConfig(NvcAgentScene.DIALOGUE_GUIDE);
    }

    return agentChatService.chat(config, context, userMessage, decision.promptVariables());
  }

  /**
   * 流式执行 Agent 对话
   */
  public Flux<String> executeAgentStream(
      AgentDecision decision, PracticeContext context, String userMessage) {
    NvcAgentConfigEntity config = agentConfigService.getConfig(decision.scene());
    if (!config.getIsEnabled()) {
      config = agentConfigService.getConfig(NvcAgentScene.DIALOGUE_GUIDE);
    }
    return agentChatService.chatStream(config, context, userMessage, decision.promptVariables());
  }

  // ==================== REFLECT：反思与策略调整 ====================

  /**
   * 练习结束后反思，分析表现并给出策略建议
   */
  public String reflect(PracticeContext context) {
    if (context.getLastEvaluation() == null) {
      log.warn("Cannot reflect: no evaluation found for session {}",
          context.getSession().getId());
      return "{\"weak_elements\":[],\"suggested_difficulty\":\"MEDIUM\",\"suggested_scenario_type\":\"any\",\"strategy_note\":\"No evaluation data available\"}";
    }

    NvcAgentConfigEntity evaluatorConfig =
        agentConfigService.getConfig(NvcAgentScene.NVC_EXPRESSION_EVALUATOR);

    NvcEvaluationEntity eval = context.getLastEvaluation();
    String reflectPrompt = """
        基于以下练习记录，请分析用户的表现并给出下次练习的策略建议：

        练习模式：%s
        练习轮次：%d
        最终评分：观察%d 感受%d 需求%d 请求%d 综合%d

        请输出JSON格式：
        {
          "weak_elements": ["薄弱要素列表"],
          "suggested_difficulty": "EASY/MEDIUM/HARD",
          "suggested_scenario_type": "建议的场景类型",
          "strategy_note": "策略建议说明"
        }
        """.formatted(
        context.getSession().getPracticeMode(),
        context.getRoundCount(),
        eval.getObservationScore(),
        eval.getFeelingScore(),
        eval.getNeedScore(),
        eval.getRequestScore(),
        eval.getOverallScore());

    return agentChatService.chatPlain(evaluatorConfig, reflectPrompt, "");
  }

}
