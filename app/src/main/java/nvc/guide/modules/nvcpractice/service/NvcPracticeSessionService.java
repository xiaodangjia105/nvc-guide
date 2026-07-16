package nvc.guide.modules.nvcpractice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcpractice.dto.CreatePracticeSessionRequest;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeSessionRepository;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import nvc.guide.modules.nvcscenario.repository.NvcScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcPracticeSessionService {

  private final NvcPracticeSessionRepository sessionRepository;
  private final NvcScenarioRepository scenarioRepository;
  private final RedisService redisService;
  private final ObjectMapper objectMapper;

  private static final String CACHE_KEY_PREFIX = "nvc:practice:session:";
  private static final Duration CACHE_TTL = Duration.ofHours(24);

  /**
   * 创建练习会话
   */
  public NvcPracticeSessionEntity createSession(Long userId,
      CreatePracticeSessionRequest request) {
    Long scenarioId = request.scenarioId();
    if (request.practiceMode() == NvcPracticeMode.SCENARIO
        && scenarioId == null) {
      scenarioId = pickRandomScenario(request.difficulty());
    }

    NvcPracticeSessionEntity session = NvcPracticeSessionEntity.builder()
        .userId(userId)
        .practiceMode(request.practiceMode())
        .scenarioId(scenarioId)
        .difficulty(request.difficulty() != null
            ? request.difficulty() : NvcDifficulty.MEDIUM)
        .currentPhase(NvcSessionPhase.CREATED)
        .currentStep(request.practiceMode()
            == NvcPracticeMode.STRUCTURED_FOUR_STEP
            ? NvcPracticeStep.OBSERVE : null)
        .build();

    NvcPracticeSessionEntity saved = sessionRepository.save(session);
    log.info(
        "NVC practice session created: sessionId={}, mode={}, userId={}",
        saved.getId(), request.practiceMode(), userId);

    cacheSession(saved);
    return saved;
  }

  /**
   * 获取会话（始终返回 JPA 托管实体）
   * 缓存仅用于快速判断会话是否存在，避免缓存反序列化后的
   * 非托管实体在 save() 时触发 merge() 导致生命周期回调异常。
   */
  public NvcPracticeSessionEntity getSession(Long sessionId) {
    String cacheKey = CACHE_KEY_PREFIX + sessionId;

    String cached = redisService.get(cacheKey);
    if (cached == null) {
      // 缓存未命中，说明会话不存在（或缓存过期），直接查 DB
      NvcPracticeSessionEntity session = sessionRepository
          .findById(sessionId)
          .orElseThrow(() -> new BusinessException(
              ErrorCode.NVC_SESSION_NOT_FOUND,
              "Practice session not found: " + sessionId));
      cacheSession(session);
      return session;
    }

    // 缓存命中，仍从 DB 加载托管实体
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
      return sessionRepository
          .findByUserIdAndCurrentPhaseOrderByCreatedAtDesc(
              userId, phase);
    }
    return sessionRepository
        .findByUserIdOrderByCreatedAtDesc(userId);
  }

  /**
   * 更新会话阶段
   */
  public NvcPracticeSessionEntity updatePhase(
      Long sessionId, NvcSessionPhase newPhase) {
    NvcPracticeSessionEntity session = getSession(sessionId);
    session.setCurrentPhase(newPhase);

    if (newPhase == NvcSessionPhase.IN_PROGRESS
        && session.getStartedAt() == null) {
      session.setStartedAt(LocalDateTime.now());
    }
    if (newPhase == NvcSessionPhase.COMPLETED) {
      session.setCompletedAt(LocalDateTime.now());
    }

    NvcPracticeSessionEntity saved = sessionRepository.save(session);
    cacheSession(saved);
    return saved;
  }

  /**
   * 更新当前步骤（结构化四步模式）
   */
  public NvcPracticeSessionEntity updateStep(
      Long sessionId, NvcPracticeStep step) {
    NvcPracticeSessionEntity session = getSession(sessionId);
    session.setCurrentStep(step);
    NvcPracticeSessionEntity saved = sessionRepository.save(session);
    cacheSession(saved);
    return saved;
  }

  /**
   * 更新当前 Agent 场景
   */
  public NvcPracticeSessionEntity updateAgentScene(
      Long sessionId, NvcAgentScene scene) {
    NvcPracticeSessionEntity session = getSession(sessionId);
    session.setAgentScene(scene);
    NvcPracticeSessionEntity saved = sessionRepository.save(session);
    cacheSession(saved);
    return saved;
  }

  /**
   * 结束会话
   */
  public NvcPracticeSessionEntity completeSession(Long sessionId) {
    return updatePhase(sessionId, NvcSessionPhase.COMPLETED);
  }

  /**
   * 从 DB 中按难度随机分配一个场景
   */
  private Long pickRandomScenario(NvcDifficulty difficulty) {
    NvcDifficulty d = difficulty != null
        ? difficulty : NvcDifficulty.MEDIUM;
    List<NvcScenarioEntity> scenarios =
        scenarioRepository.findByDifficulty(d);
    if (scenarios.isEmpty()) {
      scenarios = scenarioRepository.findAll();
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

  private void cacheSession(NvcPracticeSessionEntity session) {
    try {
      String json = objectMapper.writeValueAsString(session);
      redisService.set(
          CACHE_KEY_PREFIX + session.getId(), json, CACHE_TTL);
    } catch (Exception e) {
      log.warn("Failed to cache session: {}",
          session.getId(), e);
    }
  }
}
