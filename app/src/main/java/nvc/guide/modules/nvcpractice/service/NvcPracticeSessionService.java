package nvc.guide.modules.nvcpractice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.common.event.PracticeCompletedEvent;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcpractice.dto.CreatePracticeSessionRequest;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeSessionRepository;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import nvc.guide.modules.nvcscenario.service.NvcScenarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class NvcPracticeSessionService {

  private final NvcPracticeSessionValidator validator;
  private final NvcPracticeSessionRepository sessionRepository;
  private final NvcPracticeMessageRepository messageRepository;
  private final NvcEvaluationService evaluationService;
  private final NvcScenarioService scenarioService;
  private final RedisService redisService;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;
  private final NvcReflectionService reflectionService;
  private final NvcAgentOrchestrator orchestrator;

  public NvcPracticeSessionService(
      NvcPracticeSessionRepository sessionRepository,
      NvcPracticeMessageRepository messageRepository,
      NvcEvaluationService evaluationService,
      NvcScenarioService scenarioService,
      RedisService redisService,
      ObjectMapper objectMapper,
      ApplicationEventPublisher eventPublisher,
      NvcPracticeSessionValidator validator,
      @Lazy NvcReflectionService reflectionService,
      @Lazy NvcAgentOrchestrator orchestrator) {
    this.sessionRepository = sessionRepository;
    this.messageRepository = messageRepository;
    this.evaluationService = evaluationService;
    this.scenarioService = scenarioService;
    this.redisService = redisService;
    this.objectMapper = objectMapper;
    this.eventPublisher = eventPublisher;
    this.validator = validator;
    this.reflectionService = reflectionService;
    this.orchestrator = orchestrator;
  }

  /**
   * 创建练习会话
   * 支持自适应难度：未指定难度时，基于上次反思建议自动调整
   */
  @Transactional
  public NvcPracticeSessionEntity createSession(Long userId,
      CreatePracticeSessionRequest request) {
    Long scenarioId = request.scenarioId();
    if (request.practiceMode() == NvcPracticeMode.SCENARIO
        && scenarioId == null) {
      scenarioId = pickRandomScenario(request.difficulty());
    }

    // 自适应难度：用户未指定时，使用反思建议的难度
    NvcDifficulty difficulty = request.difficulty();
    if (difficulty == null) {
      difficulty = suggestDifficulty(userId);
    }

    NvcPracticeSessionEntity session = NvcPracticeSessionEntity.builder()
        .userId(userId)
        .practiceMode(request.practiceMode())
        .scenarioId(scenarioId)
        .difficulty(difficulty)
        .currentPhase(NvcSessionPhase.CREATED)
        .currentStep(request.practiceMode()
            == NvcPracticeMode.STRUCTURED_FOUR_STEP
            ? NvcPracticeStep.OBSERVE : null)
        .build();

    NvcPracticeSessionEntity saved = sessionRepository.save(session);
    log.info(
        "NVC practice session created: sessionId={}, mode={}, userId={}",
        saved.getId(), request.practiceMode(), userId);

    // 场景驱动模式：创建会话时记录一次场景使用
    if (scenarioId != null) {
      scenarioService.incrementUsage(scenarioId);
    }

    return saved;
  }

  /**
   * 获取会话（始终返回 JPA 托管实体）
   *
   * <p>注意：此方法始终从 DB 加载，确保返回的是 JPA 托管实体。
   * 不使用 Redis 缓存实体，因为缓存反序列化后的非托管实体在 save() 时
   * 会触发 merge() 导致生命周期回调异常。
   */
  public NvcPracticeSessionEntity getSession(Long sessionId) {
    return sessionRepository
        .findById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.NVC_SESSION_NOT_FOUND,
            "Practice session not found: " + sessionId));
  }

  /**
   * 获取用户的练习会话列表
   */
  public List<NvcPracticeSessionEntity> getUserSessions(
      Long userId, NvcSessionPhase phase) {
    if (phase != null) {
      return sessionRepository.findByUserIdAndPhaseWithMessages(userId, phase);
    }
    return sessionRepository.findByUserIdWithMessages(userId);
  }

  /**
   * 获取用户的练习会话列表（分页）
   */
  public org.springframework.data.domain.Page<NvcPracticeSessionEntity> getUserSessions(
      Long userId, NvcSessionPhase phase, org.springframework.data.domain.Pageable pageable) {
    if (phase != null) {
      return sessionRepository
          .findByUserIdAndCurrentPhaseOrderByCreatedAtDesc(userId, phase, pageable);
    }
    return sessionRepository
        .findByUserIdOrderByCreatedAtDesc(userId, pageable);
  }

  /**
   * 更新会话阶段（含状态转换校验）
   */
  public NvcPracticeSessionEntity updatePhase(
      Long sessionId, NvcSessionPhase newPhase) {
    NvcPracticeSessionEntity session = getSession(sessionId);
    NvcSessionPhase currentPhase = session.getCurrentPhase();

    // 状态转换校验
    validator.validatePhaseTransition(currentPhase, newPhase, sessionId);

    session.setCurrentPhase(newPhase);

    if (newPhase == NvcSessionPhase.IN_PROGRESS
        && session.getStartedAt() == null) {
      session.setStartedAt(LocalDateTime.now());
    }
    if (newPhase == NvcSessionPhase.COMPLETED) {
      session.setCompletedAt(LocalDateTime.now());
    }

    return sessionRepository.save(session);
  }

  /**
   * 更新当前步骤（结构化四步模式）
   */
  public NvcPracticeSessionEntity updateStep(
      Long sessionId, NvcPracticeStep step) {
    NvcPracticeSessionEntity session = getSession(sessionId);
    session.setCurrentStep(step);
    return sessionRepository.save(session);
  }

  /**
   * 更新当前 Agent 场景
   */
  public NvcPracticeSessionEntity updateAgentScene(
      Long sessionId, NvcAgentScene scene) {
    NvcPracticeSessionEntity session = getSession(sessionId);
    session.setAgentScene(scene);
    return sessionRepository.save(session);
  }

  /**
   * 结束会话（含重复调用校验）
   * 使用分布式锁防止并发请求重复评估
   */
  public NvcPracticeSessionEntity completeSession(Long sessionId) {
    String lockKey = "nvc:practice:complete:" + sessionId;
    return redisService.executeWithLock(lockKey, 5, 10, TimeUnit.SECONDS, () -> {
      NvcPracticeSessionEntity session = getSession(sessionId);
      // 已完成或已评估则直接返回，防止重复调用
      if (session.getCurrentPhase() == NvcSessionPhase.COMPLETED
          || session.getCurrentPhase() == NvcSessionPhase.EVALUATED) {
        log.info("Session already completed/evaluated, skipping: sessionId={}, phase={}",
            sessionId, session.getCurrentPhase());
        return session;
      }
      return updatePhase(sessionId, NvcSessionPhase.COMPLETED);
    });
  }

  /**
   * 结束会话并执行最终评估
   * 从 Controller 上移的业务逻辑
   *
   * <p>设计：DB 操作在事务内，外部 API 调用（LLM、RAG）在事务外。
   * evaluationService.evaluateFinal 使用 NOT_SUPPORTED 传播，LLM 调用在事务外执行。
   *
   * @return 包含评估状态的会话实体
   */
  public CompleteResult completeAndEvaluate(Long sessionId) {
    // 阶段 1：事务内的 DB 操作
    CompleteResult result = completeAndEvaluateInTransaction(sessionId);

    // 阶段 2：事务外的外部 API 调用（反思、事件发布）
    Long userId = result.session().getUserId();

    // 练习后反思（异步，不阻塞主流程）
    try {
      PracticeContext context = orchestrator.buildPracticeContext(
          new nvc.guide.common.PracticeContext(sessionId, userId));
      reflectionService.reflectAndSave(context);
      log.info("Reflection completed: sessionId={}", sessionId);
    } catch (Exception e) {
      log.warn("Reflection failed (non-blocking): sessionId={}, error={}",
          sessionId, e.getMessage());
    }

    // 发布练习完成事件
    eventPublisher.publishEvent(
        new PracticeCompletedEvent(this, sessionId, userId, result.evaluationFailed()));

    return result;
  }

  /**
   * 事务内的 DB 操作部分
   */
  @Transactional
  protected CompleteResult completeAndEvaluateInTransaction(Long sessionId) {
    NvcPracticeSessionEntity session = completeSession(sessionId);
    Long userId = session.getUserId();

    // 已评估则跳过
    if (session.getCurrentPhase() == NvcSessionPhase.EVALUATED) {
      return new CompleteResult(session, false, false);
    }

    boolean evaluationFailed = false;
    boolean evaluationSkipped = false;
    try {
      List<NvcPracticeMessageEntity> messages =
          messageRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);
      if (!messages.isEmpty()) {
        evaluationService.evaluateFinal(sessionId, userId, messages);
        session = updatePhase(sessionId, NvcSessionPhase.EVALUATED);
        log.info("Final evaluation completed: sessionId={}", sessionId);
      } else {
        evaluationSkipped = true;
        log.info("No messages to evaluate, skipping final evaluation: sessionId={}", sessionId);
      }
    } catch (Exception e) {
      log.error("Final evaluation failed: sessionId={}", sessionId, e);
      evaluationFailed = true;
    }

    return new CompleteResult(session, evaluationFailed, evaluationSkipped);
  }

  /**
   * 结束会话并执行最终评估的结果
   */
  public record CompleteResult(
      NvcPracticeSessionEntity session,
      boolean evaluationFailed,
      boolean evaluationSkipped
  ) {}

  /**
   * 基于反思历史建议难度
   * 连续3次高分(>=80)→升级，连续3次低分(<40)→降级
   */
  private NvcDifficulty suggestDifficulty(Long userId) {
    try {
      var reflection = reflectionService.getLatestReflection(userId);
      if (reflection != null && reflection.getSuggestedDifficulty() != null) {
        log.info("Adaptive difficulty: userId={}, suggested={}",
            userId, reflection.getSuggestedDifficulty());
        return reflection.getSuggestedDifficulty();
      }
    } catch (Exception e) {
      log.debug("Failed to get difficulty suggestion: {}", e.getMessage());
    }
    return NvcDifficulty.MEDIUM;
  }

  /**
   * 从 DB 中按难度随机分配一个场景
   */
  private Long pickRandomScenario(NvcDifficulty difficulty) {
    NvcDifficulty d = difficulty != null
        ? difficulty : NvcDifficulty.MEDIUM;
    List<NvcScenarioEntity> scenarios =
        scenarioService.findByDifficulty(d);
    if (scenarios.isEmpty()) {
      scenarios = scenarioService.findAll();
    }
    if (scenarios.isEmpty()) {
      throw new BusinessException(
          ErrorCode.NVC_SCENARIO_NOT_FOUND,
          "No scenario available for difficulty: " + d);
    }
    int idx = ThreadLocalRandom.current()
        .nextInt(scenarios.size());
    NvcScenarioEntity picked = scenarios.get(idx);
    log.info(
        "Random scenario picked: id={}, title={}, difficulty={}",
        picked.getId(), picked.getTitle(), picked.getDifficulty());
    return picked.getId();
  }
}
