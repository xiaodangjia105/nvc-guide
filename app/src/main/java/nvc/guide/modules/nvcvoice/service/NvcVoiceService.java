package nvc.guide.modules.nvcvoice.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.common.model.AsyncTaskStatus;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.service.NvcAgentOrchestrator;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcvoice.config.NvcVoiceProperties;
import nvc.guide.modules.nvcvoice.dto.CreateVoiceSessionRequest;
import nvc.guide.modules.nvcvoice.dto.VoiceEvaluationStatusDTO;
import nvc.guide.modules.nvcvoice.dto.VoiceMessageDTO;
import nvc.guide.modules.nvcvoice.dto.VoiceSessionResponse;
import nvc.guide.modules.nvcvoice.listener.NvcVoiceEvaluateStreamProducer;
import nvc.guide.modules.nvcvoice.model.NvcVoiceMessageEntity;
import nvc.guide.modules.nvcvoice.model.NvcVoiceSessionEntity;
import nvc.guide.modules.nvcvoice.model.NvcVoiceSessionPhase;
import nvc.guide.modules.nvcvoice.model.NvcVoiceSessionStatus;
import nvc.guide.modules.nvcvoice.repository.NvcVoiceEvaluationRepository;
import nvc.guide.modules.nvcvoice.repository.NvcVoiceMessageRepository;
import nvc.guide.modules.nvcvoice.repository.NvcVoiceSessionRepository;

/**
 * NVC 语音练习核心服务
 *
 * 负责：
 * - 会话生命周期管理（创建/结束/暂停/恢复）
 * - 消息持久化
 * - Agent 调度集成
 * - 评估触发
 * - Redis 缓存
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NvcVoiceService {

  private final NvcVoiceSessionRepository sessionRepository;
  private final NvcVoiceMessageRepository messageRepository;
  private final NvcVoiceEvaluationRepository evaluationRepository;
  private final RedissonClient redissonClient;
  private final NvcVoiceProperties properties;
  private final NvcVoiceEvaluateStreamProducer evaluateStreamProducer;
  private final NvcAgentOrchestrator orchestrator;

  private static final String SESSION_CACHE_KEY_PREFIX = "nvc:voice:session:";
  private static final int CACHE_TTL_HOURS = 1;

  /** 合法状态转换表 */
  private static final Map<NvcVoiceSessionStatus, Set<NvcVoiceSessionStatus>> VALID_TRANSITIONS = Map.of(
      NvcVoiceSessionStatus.IN_PROGRESS, Set.of(NvcVoiceSessionStatus.PAUSED, NvcVoiceSessionStatus.COMPLETED),
      NvcVoiceSessionStatus.PAUSED,      Set.of(NvcVoiceSessionStatus.IN_PROGRESS, NvcVoiceSessionStatus.COMPLETED),
      NvcVoiceSessionStatus.COMPLETED,   Set.of()  // 终态，不可转换
  );

  /**
   * 校验状态转换是否合法
   *
   * @param current 当前状态
   * @param target  目标状态
   * @throws BusinessException 如果转换不合法
   */
  private void validateTransition(NvcVoiceSessionStatus current, NvcVoiceSessionStatus target) {
    Set<NvcVoiceSessionStatus> allowed = VALID_TRANSITIONS.getOrDefault(current, Set.of());
    if (!allowed.contains(target)) {
      throw new BusinessException(ErrorCode.INVALID_OPERATION,
          "非法状态转换: " + current + " → " + target);
    }
  }

  // ==================== 会话生命周期 ====================

  /**
   * 创建语音练习会话
   */
  @Transactional
  public VoiceSessionResponse createSession(CreateVoiceSessionRequest request) {
    String llmProvider = (request.llmProvider() != null && !request.llmProvider().isBlank())
        ? request.llmProvider()
        : "dashscope";

    NvcVoiceSessionEntity session = NvcVoiceSessionEntity.builder()
        .userId(request.userId())
        .practiceMode(request.practiceMode())
        .scenarioId(request.scenarioId())
        .difficulty(request.difficulty())
        .llmProvider(llmProvider)
        .currentPhase(NvcVoiceSessionPhase.INTRO)
        .status(NvcVoiceSessionStatus.IN_PROGRESS)
        .plannedDuration(properties.getPractice().getSuggestedDuration())
        .build();

    NvcVoiceSessionEntity saved = sessionRepository.save(session);
    cacheSession(saved);

    log.info("Created NVC voice session: {}, mode: {}, scenario: {}",
        saved.getId(), saved.getPracticeMode(), saved.getScenarioId());

    return buildSessionResponse(saved);
  }

  /**
   * 结束会话
   */
  @Transactional
  public VoiceSessionResponse endSession(Long sessionId) {
    NvcVoiceSessionEntity session = getSession(sessionId);
    if (session == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
    }

    if (session.getStatus() == NvcVoiceSessionStatus.COMPLETED) {
      return buildSessionResponse(session);  // 幂等
    }

    validateTransition(session.getStatus(), NvcVoiceSessionStatus.COMPLETED);

    session.setEndTime(LocalDateTime.now());
    session.setCurrentPhase(NvcVoiceSessionPhase.WRAP_UP);
    session.setStatus(NvcVoiceSessionStatus.COMPLETED);
    session.setActualDuration(
        (int) Duration.between(session.getStartTime(), LocalDateTime.now()).toSeconds());
    session.setEvaluateStatus(AsyncTaskStatus.PENDING);

    sessionRepository.save(session);
    invalidateSessionCache(sessionId);

    // 触发异步评估
    evaluateStreamProducer.sendEvaluateTask(sessionId.toString());

    log.info("Ended NVC voice session: {}, duration: {} seconds",
        session.getId(), session.getActualDuration());

    return buildSessionResponse(session);
  }

  /**
   * 仅当会话处于 IN_PROGRESS 状态时结束（WebSocket 断连兜底）
   */
  @Transactional
  public void endSessionIfInProgress(Long sessionId) {
    NvcVoiceSessionEntity session = sessionRepository.findById(sessionId).orElse(null);
    if (session == null || session.getStatus() != NvcVoiceSessionStatus.IN_PROGRESS) {
      return;
    }
    log.info("Auto-ending IN_PROGRESS session {} after WebSocket disconnect", sessionId);
    endSession(sessionId);
  }

  /**
   * 暂停会话
   */
  @Transactional
  public VoiceSessionResponse pauseSession(Long sessionId, String reason) {
    NvcVoiceSessionEntity session = getSession(sessionId);
    if (session == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
    }

    validateTransition(session.getStatus(), NvcVoiceSessionStatus.PAUSED);

    session.setStatus(NvcVoiceSessionStatus.PAUSED);
    session.setPausedAt(LocalDateTime.now());
    sessionRepository.save(session);
    cacheSession(session);

    log.info("Paused NVC voice session: {}, reason: {}", sessionId, reason);
    return buildSessionResponse(session);
  }

  /**
   * 恢复会话
   */
  @Transactional
  public VoiceSessionResponse resumeSession(Long sessionId) {
    NvcVoiceSessionEntity session = getSession(sessionId);
    if (session == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
    }

    validateTransition(session.getStatus(), NvcVoiceSessionStatus.IN_PROGRESS);

    session.setStatus(NvcVoiceSessionStatus.IN_PROGRESS);
    session.setResumedAt(LocalDateTime.now());
    sessionRepository.save(session);
    cacheSession(session);

    log.info("Resumed NVC voice session: {}", sessionId);
    return buildSessionResponse(session);
  }

  /**
   * 获取会话（Redis 缓存优先）
   */
  public VoiceSessionResponse getSessionResponse(Long sessionId) {
    NvcVoiceSessionEntity session = getSession(sessionId);
    if (session == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
    }
    return buildSessionResponse(session);
  }

  /**
   * 获取会话实体（Redis 缓存优先）
   */
  public NvcVoiceSessionEntity getSession(Long sessionId) {
    if (sessionId == null) {
      return null;
    }

    String cacheKey = getSessionCacheKey(sessionId);
    RBucket<NvcVoiceSessionEntity> bucket = redissonClient.getBucket(cacheKey);
    NvcVoiceSessionEntity cached = bucket.get();

    if (cached != null) {
      return cached;
    }

    return sessionRepository.findById(sessionId).orElse(null);
  }

  /**
   * 列出会话
   */
  public List<VoiceSessionResponse> listSessions(Long userId, NvcVoiceSessionStatus status) {
    List<NvcVoiceSessionEntity> sessions;
    if (userId != null && status != null) {
      sessions = sessionRepository.findByUserIdAndStatus(userId, status);
    } else if (userId != null) {
      sessions = sessionRepository.findByUserId(userId);
    } else if (status != null) {
      sessions = sessionRepository.findByStatus(status);
    } else {
      sessions = sessionRepository.findAll();
    }
    return sessions.stream().map(this::buildSessionResponse).collect(Collectors.toList());
  }

  /**
   * 删除会话
   */
  @Transactional
  public void deleteSession(Long sessionId) {
    NvcVoiceSessionEntity session = getSession(sessionId);
    if (session == null) {
      return;
    }

    // 删除关联数据
    messageRepository.deleteAll(messageRepository.findBySessionIdOrderBySequenceNumAsc(sessionId));
    evaluationRepository.findBySessionId(sessionId).ifPresent(evaluationRepository::delete);
    sessionRepository.delete(session);
    invalidateSessionCache(sessionId);

    log.info("Deleted NVC voice session: {}", sessionId);
  }

  // ==================== 消息管理 ====================

  /**
   * 保存对话消息
   */
  @Transactional
  public void saveMessage(Long sessionId, String userText, String aiText, String agentScene) {
    NvcVoiceSessionEntity session = getSession(sessionId);
    if (session == null) {
      log.warn("Cannot save message - session not found: {}", sessionId);
      return;
    }

    String normalizedUserText = trimToNull(userText);
    String normalizedAiText = trimToNull(aiText);

    // 尝试回填用户文本到上一条 AI 消息
    boolean answerAttached = normalizedUserText != null
        && fillLatestUnansweredQuestion(sessionId, normalizedUserText);

    if (normalizedAiText == null) {
      return;
    }

    NvcVoiceMessageEntity message = NvcVoiceMessageEntity.builder()
        .sessionId(sessionId)
        .messageType("DIALOGUE")
        .agentScene(agentScene)
        .userRecognizedText(
            normalizedUserText != null && !answerAttached ? normalizedUserText : null)
        .aiGeneratedText(normalizedAiText)
        .sequenceNum(getNextSequenceNum(sessionId))
        .build();

    messageRepository.save(message);
    log.debug("Saved message for session: {}, agent: {}, sequence: {}",
        sessionId, agentScene, message.getSequenceNum());
  }

  /**
   * 获取对话历史
   */
  public List<VoiceMessageDTO> getMessages(Long sessionId) {
    return messageRepository.findBySessionIdOrderBySequenceNumAsc(sessionId).stream()
        .map(this::toMessageDTO)
        .collect(Collectors.toList());
  }

  // ==================== Agent 调度 ====================

  /**
   * 构建语音练习上下文（供 Pipeline 调用）
   */
  public PracticeContext buildVoiceContext(Long sessionId) {
    NvcVoiceSessionEntity session = getSession(sessionId);
    if (session == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
    }

    // 构建 PracticeContext
    // 注意：PracticeContext 需要 NvcPracticeSessionEntity，这里暂时使用简化构建
    // TODO: 后续完善，将 NvcVoiceSessionEntity 转换为 NvcPracticeSessionEntity
    int messageCount = (int) messageRepository.countBySessionId(sessionId);
    return PracticeContext.builder()
        .roundCount(messageCount)
        .scenarioDescription(null) // TODO: 从场景库获取
        .build();
  }

  /**
   * 决定下一个 Agent
   */
  public AgentDecision decideNextAgent(PracticeContext context) {
    return orchestrator.decideNextAgent(context);
  }

  /**
   * 执行 Agent
   */
  public String executeAgent(AgentDecision decision, PracticeContext context, String userText) {
    return orchestrator.executeAgent(decision, context, userText);
  }

  // ==================== 评估 ====================

  /**
   * 触发评估
   */
  public void triggerEvaluation(Long sessionId) {
    NvcVoiceSessionEntity session = getSession(sessionId);
    if (session == null) {
      return;
    }
    session.setEvaluateStatus(AsyncTaskStatus.PENDING);
    sessionRepository.save(session);
    evaluateStreamProducer.sendEvaluateTask(sessionId.toString());
  }

  /**
   * 获取评估状态
   */
  public VoiceEvaluationStatusDTO getEvaluationStatus(Long sessionId) {
    NvcVoiceSessionEntity session = getSession(sessionId);
    if (session == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
    }

    return evaluationRepository.findBySessionId(sessionId)
        .map(e -> new VoiceEvaluationStatusDTO(
            session.getEvaluateStatus(),
            session.getEvaluateError(),
            toEvaluationDetailDTO(e)))
        .orElse(new VoiceEvaluationStatusDTO(
            session.getEvaluateStatus(),
            session.getEvaluateError(),
            null));
  }

  /**
   * 更新评估状态
   */
  @Transactional
  public void updateEvaluateStatus(Long sessionId, AsyncTaskStatus status, String error) {
    NvcVoiceSessionEntity session = sessionRepository.findById(sessionId).orElse(null);
    if (session == null) {
      return;
    }
    session.setEvaluateStatus(status);
    session.setEvaluateError(error);
    sessionRepository.save(session);
    invalidateSessionCache(sessionId);
  }

  // ==================== 维护 ====================

  /**
   * 清理过期会话（每 5 分钟执行）
   */
  @Scheduled(fixedRate = 300000)
  public void cleanupStaleSessions() {
    LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2);
    LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);

    // 清理超过 2 小时的 IN_PROGRESS 会话
    List<NvcVoiceSessionEntity> staleSessions = sessionRepository
        .findByStatusInAndUpdatedAtBefore(
            List.of(NvcVoiceSessionStatus.IN_PROGRESS, NvcVoiceSessionStatus.PAUSED),
            twoHoursAgo);

    for (NvcVoiceSessionEntity session : staleSessions) {
      log.warn("Cleaning up stale session: {}", session.getId());
      endSession(session.getId());
    }

    // 清理超过 30 分钟的 PROCESSING 评估
    List<NvcVoiceSessionEntity> stuckEvaluations = sessionRepository
        .findByEvaluateStatusAndUpdatedAtBefore(AsyncTaskStatus.PROCESSING, thirtyMinutesAgo);

    for (NvcVoiceSessionEntity session : stuckEvaluations) {
      log.warn("Cleaning up stuck evaluation for session: {}", session.getId());
      updateEvaluateStatus(session.getId(), AsyncTaskStatus.FAILED, "评估超时");
    }
  }

  // ==================== 内部方法 ====================

  private boolean fillLatestUnansweredQuestion(Long sessionId, String userText) {
    return messageRepository.findTop20BySessionIdOrderBySequenceNumDesc(sessionId).stream()
        .filter(m -> m.getUserRecognizedText() == null && m.getAiGeneratedText() != null)
        .findFirst()
        .map(message -> {
          message.setUserRecognizedText(userText);
          messageRepository.save(message);
          log.debug("Filled answer for voice message: sessionId={}, sequence={}",
              sessionId, message.getSequenceNum());
          return true;
        })
        .orElse(false);
  }

  private int getNextSequenceNum(Long sessionId) {
    return (int) messageRepository.countBySessionId(sessionId) + 1;
  }

  private String trimToNull(String text) {
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String getSessionCacheKey(Long sessionId) {
    return SESSION_CACHE_KEY_PREFIX + sessionId;
  }

  private void cacheSession(NvcVoiceSessionEntity session) {
    String cacheKey = getSessionCacheKey(session.getId());
    RBucket<NvcVoiceSessionEntity> bucket = redissonClient.getBucket(cacheKey);
    bucket.set(session, Duration.ofHours(CACHE_TTL_HOURS));
  }

  private void invalidateSessionCache(Long sessionId) {
    String cacheKey = getSessionCacheKey(sessionId);
    redissonClient.getBucket(cacheKey).delete();
  }

  private VoiceSessionResponse buildSessionResponse(NvcVoiceSessionEntity session) {
    return new VoiceSessionResponse(
        session.getId(),
        session.getUserId(),
        session.getPracticeMode(),
        session.getScenarioId(),
        session.getAgentScene(),
        session.getDifficulty(),
        session.getCurrentPhase(),
        session.getStatus(),
        session.getLlmProvider(),
        session.getPlannedDuration(),
        session.getActualDuration(),
        session.getStartTime(),
        session.getEndTime(),
        session.getPausedAt(),
        session.getResumedAt(),
        session.getEvaluateStatus(),
        "/ws/nvc-voice/" + session.getId()
    );
  }

  private VoiceMessageDTO toMessageDTO(NvcVoiceMessageEntity message) {
    return new VoiceMessageDTO(
        message.getId(),
        message.getSessionId(),
        message.getMessageType(),
        message.getAgentScene(),
        message.getUserRecognizedText(),
        message.getAiGeneratedText(),
        message.getSequenceNum(),
        message.getTimestamp()
    );
  }

  private nvc.guide.modules.nvcvoice.dto.VoiceEvaluationDetailDTO toEvaluationDetailDTO(
      nvc.guide.modules.nvcvoice.model.NvcVoiceEvaluationEntity e) {
    return new nvc.guide.modules.nvcvoice.dto.VoiceEvaluationDetailDTO(
        e.getId(),
        e.getSessionId(),
        e.getObservationScore(),
        e.getFeelingScore(),
        e.getNeedScore(),
        e.getRequestScore(),
        e.getEmpathyScore(),
        e.getOverallScore(),
        e.getFluencyScore(),
        e.getObservationDetail(),
        e.getFeelingDetail(),
        e.getNeedDetail(),
        e.getRequestDetail(),
        e.getOverallFeedback(),
        e.getStrengthsJson(),
        e.getImprovementsJson(),
        e.getCreatedAt()
    );
  }
}
