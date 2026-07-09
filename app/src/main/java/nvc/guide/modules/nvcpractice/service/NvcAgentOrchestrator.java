package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.*;
import nvc.guide.modules.nvcpractice.repository.*;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import nvc.guide.modules.nvcscenario.repository.NvcScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * NVC Agent 调度中心
 * 负责：组装 PracticeContext、决定下一步 Agent、执行对话、反思分析
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcAgentOrchestrator {

  private final NvcAgentConfigService agentConfigService;
  private final NvcAgentChatService agentChatService;
  private final NvcPracticeSessionRepository sessionRepository;
  private final NvcPracticeMessageRepository messageRepository;
  private final NvcEvaluationRepository evaluationRepository;
  private final NvcScenarioRepository scenarioRepository;

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
   * 根据练习模式和当前状态做出决策
   */
  public AgentDecision decideNextAgent(PracticeContext context) {
    NvcPracticeSessionEntity session = context.getSession();

    // 场景生成阶段
    if (session.getCurrentPhase() == NvcSessionPhase.CREATED) {
      return new AgentDecision(
          NvcAgentScene.SCENARIO_GENERATOR,
          "练习刚开始，需要生成场景",
          null);
    }

    // 结构化四步模式
    if (session.getPracticeMode() == NvcPracticeMode.STRUCTURED_FOUR_STEP) {
      return decideStructuredStep(context);
    }

    // 场景驱动和自由对话模式
    return decideFreeDialog(context);
  }

  /**
   * 结构化四步模式的调度逻辑
   */
  private AgentDecision decideStructuredStep(PracticeContext context) {
    NvcPracticeStep currentStep = context.getSession().getCurrentStep();

    // 无评估结果，继续当前步骤
    if (context.getLastEvaluation() == null) {
      return new AgentDecision(
          stepToCoach(currentStep),
          "当前步骤 " + currentStep + " 尚未评估，继续练习",
          null);
    }

    int stepScore = getStepScore(context.getLastEvaluation(), currentStep);

    // 评分 >= 70，推进到下一步
    if (stepScore >= 70) {
      NvcPracticeStep nextStep = nextStep(currentStep);
      if (nextStep == NvcPracticeStep.COMPLETED) {
        return new AgentDecision(
            NvcAgentScene.DIALOGUE_GUIDE,
            "四步全部通过，进入综合练习",
            "COMPLETED_ALL_STEPS");
      }
      return new AgentDecision(
          stepToCoach(nextStep),
          currentStep + " 通过（" + stepScore + "分），推进到 " + nextStep,
          "STEP_ADVANCE");
    }

    // 评分 < 70，继续当前步骤
    return new AgentDecision(
        stepToCoach(currentStep),
        currentStep + " 未通过（" + stepScore + "分），继续练习",
        "STEP_RETRY");
  }

  /**
   * 自由对话/场景驱动模式的调度逻辑
   */
  private AgentDecision decideFreeDialog(PracticeContext context) {
    // 无评估结果
    if (context.getLastEvaluation() == null) {
      return new AgentDecision(
          NvcAgentScene.DIALOGUE_GUIDE,
          "对话进行中，使用引导官",
          null);
    }

    int overallScore = context.getLastEvaluation().getOverallScore();
    int roundCount = context.getRoundCount();

    // 评分 < 50，切换引导模式
    if (overallScore < 50) {
      return new AgentDecision(
          NvcAgentScene.DIALOGUE_GUIDE,
          "评分较低（" + overallScore + "），切换到引导模式",
          "LOW_SCORE_GUIDE");
    }

    // 评分 >= 60 且轮次足够，偶尔切换困难搭档
    if (overallScore >= 60 && roundCount > 3 && roundCount % 3 == 0) {
      return new AgentDecision(
          NvcAgentScene.DIFFICULT_PARTNER,
          "用户表现良好（" + overallScore + "），切换困难搭档提升难度",
          "DIFFICULT_UPGRADE");
    }

    // 检查薄弱要素
    NvcPracticeStep weakElement = findWeakestElement(context);
    if (weakElement != null && overallScore < 70) {
      return new AgentDecision(
          stepToCoach(weakElement),
          "薄弱要素 " + weakElement + " 需要针对性练习",
          "WEAK_ELEMENT_FOCUS");
    }

    // 默认继续对话引导
    return new AgentDecision(
        NvcAgentScene.DIALOGUE_GUIDE,
        "对话正常进行",
        null);
  }

  // ==================== EXECUTE：执行 Agent 对话 ====================

  /**
   * 执行 Agent 对话，获取 AI 回复
   */
  public String executeAgent(
      NvcAgentScene scene, PracticeContext context, String userMessage) {
    NvcAgentConfigEntity config = agentConfigService.getConfig(scene);

    // 检查 Agent 是否启用，禁用则 fallback
    if (!config.getIsEnabled()) {
      log.warn("Agent is disabled: {}, falling back to DIALOGUE_GUIDE", scene);
      config = agentConfigService.getConfig(NvcAgentScene.DIALOGUE_GUIDE);
    }

    return agentChatService.chat(config, context, userMessage);
  }

  // ==================== REFLECT：反思与策略调整 ====================

  /**
   * 练习结束后反思，分析表现并给出策略建议
   */
  public String reflect(PracticeContext context) {
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

  // ==================== 辅助方法 ====================

  private NvcAgentScene stepToCoach(NvcPracticeStep step) {
    return switch (step) {
      case OBSERVE -> NvcAgentScene.STEP_OBSERVE_COACH;
      case FEELING -> NvcAgentScene.STEP_FEELING_COACH;
      case NEED -> NvcAgentScene.STEP_NEED_COACH;
      case REQUEST -> NvcAgentScene.STEP_REQUEST_COACH;
      default -> NvcAgentScene.DIALOGUE_GUIDE;
    };
  }

  private NvcPracticeStep nextStep(NvcPracticeStep current) {
    return switch (current) {
      case OBSERVE -> NvcPracticeStep.FEELING;
      case FEELING -> NvcPracticeStep.NEED;
      case NEED -> NvcPracticeStep.REQUEST;
      case REQUEST -> NvcPracticeStep.COMPLETED;
      default -> NvcPracticeStep.COMPLETED;
    };
  }

  private int getStepScore(NvcEvaluationEntity eval, NvcPracticeStep step) {
    return switch (step) {
      case OBSERVE -> eval.getObservationScore();
      case FEELING -> eval.getFeelingScore();
      case NEED -> eval.getNeedScore();
      case REQUEST -> eval.getRequestScore();
      default -> eval.getOverallScore();
    };
  }

  private NvcPracticeStep findWeakestElement(PracticeContext context) {
    NvcEvaluationEntity eval = context.getLastEvaluation();
    if (eval == null) return null;

    int minScore = Integer.MAX_VALUE;
    NvcPracticeStep weakest = null;

    if (eval.getObservationScore() != null && eval.getObservationScore() < minScore) {
      minScore = eval.getObservationScore();
      weakest = NvcPracticeStep.OBSERVE;
    }
    if (eval.getFeelingScore() != null && eval.getFeelingScore() < minScore) {
      minScore = eval.getFeelingScore();
      weakest = NvcPracticeStep.FEELING;
    }
    if (eval.getNeedScore() != null && eval.getNeedScore() < minScore) {
      minScore = eval.getNeedScore();
      weakest = NvcPracticeStep.NEED;
    }
    if (eval.getRequestScore() != null && eval.getRequestScore() < minScore) {
      minScore = eval.getRequestScore();
      weakest = NvcPracticeStep.REQUEST;
    }

    return weakest;
  }
}
