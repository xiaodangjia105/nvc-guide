package nvc.guide.modules.nvcvoice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.model.AsyncTaskStatus;
import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.service.NvcAgentOrchestrator;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import nvc.guide.modules.nvcscenario.repository.NvcScenarioRepository;
import nvc.guide.modules.nvcvoice.config.NvcVoiceProperties;
import nvc.guide.modules.nvcvoice.dto.CreateVoiceSessionRequest;
import nvc.guide.modules.nvcvoice.dto.VoiceEvaluationStatusDTO;
import nvc.guide.modules.nvcvoice.dto.VoiceMessageDTO;
import nvc.guide.modules.nvcvoice.dto.VoiceSessionResponse;
import nvc.guide.modules.nvcvoice.listener.NvcVoiceEvaluateStreamProducer;
import nvc.guide.modules.nvcvoice.model.NvcVoiceEvaluationEntity;
import nvc.guide.modules.nvcvoice.model.NvcVoiceMessageEntity;
import nvc.guide.modules.nvcvoice.model.NvcVoiceSessionEntity;
import nvc.guide.modules.nvcvoice.model.NvcVoiceSessionPhase;
import nvc.guide.modules.nvcvoice.model.NvcVoiceSessionStatus;
import nvc.guide.modules.nvcvoice.repository.NvcVoiceEvaluationRepository;
import nvc.guide.modules.nvcvoice.repository.NvcVoiceMessageRepository;
import nvc.guide.modules.nvcvoice.repository.NvcVoiceSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcVoiceService 语音练习核心服务")
class NvcVoiceServiceTest {

  @Mock private NvcVoiceSessionRepository sessionRepository;
  @Mock private NvcVoiceMessageRepository messageRepository;
  @Mock private NvcVoiceEvaluationRepository evaluationRepository;
  @Mock private RedissonClient redissonClient;
  @Mock private NvcVoiceEvaluateStreamProducer evaluateStreamProducer;
  @Mock private NvcAgentOrchestrator orchestrator;
  @Mock private NvcScenarioRepository scenarioRepository;

  private NvcVoiceProperties properties;
  private NvcVoiceService service;

  @BeforeEach
  void setUp() {
    properties = new NvcVoiceProperties();
    service = new NvcVoiceService(
        sessionRepository, messageRepository, evaluationRepository,
        redissonClient, properties, evaluateStreamProducer,
        orchestrator, scenarioRepository);
  }

  // ==================== Helper Methods ====================

  private NvcVoiceSessionEntity buildSession(Long id, NvcVoiceSessionStatus status) {
    return NvcVoiceSessionEntity.builder()
        .id(id)
        .userId(100L)
        .practiceMode(NvcPracticeMode.SCENARIO)
        .scenarioId(1L)
        .difficulty(NvcDifficulty.MEDIUM)
        .llmProvider("dashscope")
        .currentPhase(NvcVoiceSessionPhase.INTRO)
        .status(status)
        .plannedDuration(20)
        .startTime(LocalDateTime.now().minusMinutes(10))
        .build();
  }

  private NvcVoiceSessionEntity buildSession(Long id, NvcVoiceSessionStatus status, NvcPracticeMode mode) {
    NvcVoiceSessionEntity s = buildSession(id, status);
    s.setPracticeMode(mode);
    return s;
  }

  /**
   * Sets up Redis to return the given entity on cache GET.
   * Returns the bucket mock so tests can verify DELETE (cache invalidation).
   */
  @SuppressWarnings("unchecked")
  private RBucket<NvcVoiceSessionEntity> mockCacheHit(Long sessionId, NvcVoiceSessionEntity entity) {
    RBucket<NvcVoiceSessionEntity> bucket = mock(RBucket.class);
    doReturn(bucket).when(redissonClient).getBucket("nvc:voice:session:" + sessionId);
    doReturn(entity).when(bucket).get();
    return bucket;
  }

  /**
   * Sets up Redis cache miss for the given session.
   * Returns the bucket mock.
   */
  @SuppressWarnings("unchecked")
  private RBucket<NvcVoiceSessionEntity> mockCacheMiss(Long sessionId) {
    RBucket<NvcVoiceSessionEntity> bucket = mock(RBucket.class);
    doReturn(bucket).when(redissonClient).getBucket("nvc:voice:session:" + sessionId);
    doReturn(null).when(bucket).get();
    return bucket;
  }

  /**
   * Sets up a Redis bucket for the given session WITHOUT stubbing get().
   * Use when only cache invalidation (delete) needs to be verified,
   * and the session is looked up via sessionRepository directly.
   */
  @SuppressWarnings("unchecked")
  private RBucket<NvcVoiceSessionEntity> mockBucketOnly(Long sessionId) {
    RBucket<NvcVoiceSessionEntity> bucket = mock(RBucket.class);
    doReturn(bucket).when(redissonClient).getBucket("nvc:voice:session:" + sessionId);
    return bucket;
  }

  // ==================== createSession ====================

  @Nested
  @DisplayName("createSession 创建会话")
  class CreateSessionTests {

    @Test
    @DisplayName("Happy path - SCENARIO 模式创建会话")
    void createSession_scenarioMode_success() {
      CreateVoiceSessionRequest request = new CreateVoiceSessionRequest(
          100L, NvcPracticeMode.SCENARIO, 1L, NvcDifficulty.MEDIUM, "dashscope");

      NvcVoiceSessionEntity saved = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      saved.setPracticeMode(NvcPracticeMode.SCENARIO);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(saved);

      // For cacheSession call - createSession creates a NEW bucket for caching
      @SuppressWarnings("unchecked")
      RBucket<NvcVoiceSessionEntity> cacheBucket = mock(RBucket.class);
      doReturn(cacheBucket).when(redissonClient).getBucket("nvc:voice:session:1");

      VoiceSessionResponse response = service.createSession(request);

      assertNotNull(response);
      assertEquals(1L, response.id());
      assertEquals(100L, response.userId());
      assertEquals(NvcPracticeMode.SCENARIO, response.practiceMode());
      assertEquals(NvcVoiceSessionStatus.IN_PROGRESS, response.status());
      assertEquals(NvcVoiceSessionPhase.INTRO, response.currentPhase());
      assertEquals("/ws/nvc-voice/1", response.webSocketUrl());

      ArgumentCaptor<NvcVoiceSessionEntity> captor = ArgumentCaptor.forClass(NvcVoiceSessionEntity.class);
      verify(sessionRepository).save(captor.capture());
      assertEquals(NvcPracticeMode.SCENARIO, captor.getValue().getPracticeMode());
      verify(cacheBucket).set(eq(saved), any());
    }

    @Test
    @DisplayName("FREE_DIALOG 模式创建会话")
    void createSession_freeDialogMode_success() {
      CreateVoiceSessionRequest request = new CreateVoiceSessionRequest(
          200L, NvcPracticeMode.FREE_DIALOG, null, NvcDifficulty.EASY, null);

      NvcVoiceSessionEntity saved = buildSession(2L, NvcVoiceSessionStatus.IN_PROGRESS, NvcPracticeMode.FREE_DIALOG);
      saved.setUserId(200L);
      saved.setScenarioId(null);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(saved);

      @SuppressWarnings("unchecked")
      RBucket<NvcVoiceSessionEntity> cacheBucket = mock(RBucket.class);
      doReturn(cacheBucket).when(redissonClient).getBucket("nvc:voice:session:2");

      VoiceSessionResponse response = service.createSession(request);

      assertEquals(NvcPracticeMode.FREE_DIALOG, response.practiceMode());
      assertNull(response.scenarioId());
      // null llmProvider should default to "dashscope"
      ArgumentCaptor<NvcVoiceSessionEntity> captor = ArgumentCaptor.forClass(NvcVoiceSessionEntity.class);
      verify(sessionRepository).save(captor.capture());
      assertEquals("dashscope", captor.getValue().getLlmProvider());
    }

    @Test
    @DisplayName("STRUCTURED_FOUR_STEP 模式创建会话")
    void createSession_structuredMode_success() {
      CreateVoiceSessionRequest request = new CreateVoiceSessionRequest(
          300L, NvcPracticeMode.STRUCTURED_FOUR_STEP, null, NvcDifficulty.HARD, "openai");

      NvcVoiceSessionEntity saved = buildSession(3L, NvcVoiceSessionStatus.IN_PROGRESS, NvcPracticeMode.STRUCTURED_FOUR_STEP);
      saved.setUserId(300L);
      saved.setLlmProvider("openai");
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(saved);

      @SuppressWarnings("unchecked")
      RBucket<NvcVoiceSessionEntity> cacheBucket = mock(RBucket.class);
      doReturn(cacheBucket).when(redissonClient).getBucket("nvc:voice:session:3");

      VoiceSessionResponse response = service.createSession(request);

      assertEquals(NvcPracticeMode.STRUCTURED_FOUR_STEP, response.practiceMode());
      assertEquals("openai", response.llmProvider());
    }

    @Test
    @DisplayName("空白 llmProvider 应默认为 dashscope")
    void createSession_blankLlmProvider_defaultsToDashscope() {
      CreateVoiceSessionRequest request = new CreateVoiceSessionRequest(
          100L, NvcPracticeMode.SCENARIO, 1L, NvcDifficulty.MEDIUM, "   ");

      NvcVoiceSessionEntity saved = buildSession(4L, NvcVoiceSessionStatus.IN_PROGRESS);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(saved);

      @SuppressWarnings("unchecked")
      RBucket<NvcVoiceSessionEntity> cacheBucket = mock(RBucket.class);
      doReturn(cacheBucket).when(redissonClient).getBucket("nvc:voice:session:4");

      service.createSession(request);

      ArgumentCaptor<NvcVoiceSessionEntity> captor = ArgumentCaptor.forClass(NvcVoiceSessionEntity.class);
      verify(sessionRepository).save(captor.capture());
      assertEquals("dashscope", captor.getValue().getLlmProvider());
    }

    @Test
    @DisplayName("创建后应缓存会话到 Redis")
    void createSession_cachesInRedis() {
      CreateVoiceSessionRequest request = new CreateVoiceSessionRequest(
          100L, NvcPracticeMode.SCENARIO, 1L, NvcDifficulty.MEDIUM, "dashscope");

      NvcVoiceSessionEntity saved = buildSession(5L, NvcVoiceSessionStatus.IN_PROGRESS);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(saved);

      @SuppressWarnings("unchecked")
      RBucket<NvcVoiceSessionEntity> cacheBucket = mock(RBucket.class);
      doReturn(cacheBucket).when(redissonClient).getBucket("nvc:voice:session:5");

      service.createSession(request);

      verify(cacheBucket).set(eq(saved), any());
    }
  }

  // ==================== endSession ====================

  @Nested
  @DisplayName("endSession 结束会话")
  class EndSessionTests {

    @Test
    @DisplayName("Happy path - 结束 IN_PROGRESS 会话")
    void endSession_inProgress_success() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      mockCacheHit(1L, session);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(session);

      VoiceSessionResponse response = service.endSession(1L);

      assertEquals(NvcVoiceSessionStatus.COMPLETED, session.getStatus());
      assertEquals(NvcVoiceSessionPhase.WRAP_UP, session.getCurrentPhase());
      assertNotNull(session.getEndTime());
      assertNotNull(session.getActualDuration());
      assertEquals(AsyncTaskStatus.PENDING, session.getEvaluateStatus());
      verify(evaluateStreamProducer).sendEvaluateTask("1");
      assertNotNull(response);
    }

    @Test
    @DisplayName("结束 PAUSED 会话也应成功")
    void endSession_paused_success() {
      NvcVoiceSessionEntity session = buildSession(2L, NvcVoiceSessionStatus.PAUSED);
      mockCacheHit(2L, session);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(session);

      VoiceSessionResponse response = service.endSession(2L);

      assertEquals(NvcVoiceSessionStatus.COMPLETED, session.getStatus());
      verify(evaluateStreamProducer).sendEvaluateTask("2");
    }

    @Test
    @DisplayName("幂等 - 对已 COMPLETED 的会话再次调用应直接返回，不触发评估")
    void endSession_alreadyCompleted_idempotent() {
      NvcVoiceSessionEntity session = buildSession(3L, NvcVoiceSessionStatus.COMPLETED);
      session.setEndTime(LocalDateTime.now());
      mockCacheHit(3L, session);

      VoiceSessionResponse response = service.endSession(3L);

      assertEquals(NvcVoiceSessionStatus.COMPLETED, session.getStatus());
      verify(evaluateStreamProducer, never()).sendEvaluateTask(anyString());
      verify(sessionRepository, never()).save(any());
      assertNotNull(response);
    }

    @Test
    @DisplayName("会话不存在应抛出 BusinessException")
    void endSession_notFound_throwsException() {
      mockCacheMiss(999L);
      when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

      BusinessException ex = assertThrows(BusinessException.class,
          () -> service.endSession(999L));
      assertTrue(ex.getMessage().contains("999"));
    }

    @Test
    @DisplayName("结束会话应计算 actualDuration")
    void endSession_calculatesDuration() {
      NvcVoiceSessionEntity session = buildSession(4L, NvcVoiceSessionStatus.IN_PROGRESS);
      session.setStartTime(LocalDateTime.now().minusMinutes(5));
      mockCacheHit(4L, session);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(session);

      service.endSession(4L);

      ArgumentCaptor<NvcVoiceSessionEntity> captor = ArgumentCaptor.forClass(NvcVoiceSessionEntity.class);
      verify(sessionRepository).save(captor.capture());
      Integer duration = captor.getValue().getActualDuration();
      assertNotNull(duration);
      assertTrue(duration >= 290 && duration <= 310, "Duration should be ~300 seconds");
    }

    @Test
    @DisplayName("startTime 为 null 时 actualDuration 应为 0")
    void endSession_noStartTime_durationZero() {
      NvcVoiceSessionEntity session = buildSession(5L, NvcVoiceSessionStatus.IN_PROGRESS);
      session.setStartTime(null);
      mockCacheHit(5L, session);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(session);

      service.endSession(5L);

      ArgumentCaptor<NvcVoiceSessionEntity> captor = ArgumentCaptor.forClass(NvcVoiceSessionEntity.class);
      verify(sessionRepository).save(captor.capture());
      assertEquals(0, captor.getValue().getActualDuration());
    }

    @Test
    @DisplayName("结束会话应清除 Redis 缓存")
    void endSession_invalidatesCache() {
      NvcVoiceSessionEntity session = buildSession(6L, NvcVoiceSessionStatus.IN_PROGRESS);
      RBucket<NvcVoiceSessionEntity> cacheBucket = mockCacheHit(6L, session);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(session);

      service.endSession(6L);

      verify(cacheBucket).delete();
    }
  }

  // ==================== endSessionIfInProgress ====================

  @Nested
  @DisplayName("endSessionIfInProgress 条件结束会话")
  class EndSessionIfInProgressTests {

    @Test
    @DisplayName("IN_PROGRESS 会话应被结束")
    void endSessionIfInProgress_inProgress_endsSession() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

      // endSession will be called internally, so set up cache for that call
      mockCacheHit(1L, session);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(session);

      service.endSessionIfInProgress(1L);

      assertEquals(NvcVoiceSessionStatus.COMPLETED, session.getStatus());
      verify(evaluateStreamProducer).sendEvaluateTask("1");
    }

    @Test
    @DisplayName("PAUSED 会话应被跳过（不结束）")
    void endSessionIfInProgress_paused_skips() {
      NvcVoiceSessionEntity session = buildSession(2L, NvcVoiceSessionStatus.PAUSED);
      when(sessionRepository.findById(2L)).thenReturn(Optional.of(session));

      service.endSessionIfInProgress(2L);

      assertEquals(NvcVoiceSessionStatus.PAUSED, session.getStatus());
      verify(evaluateStreamProducer, never()).sendEvaluateTask(anyString());
    }

    @Test
    @DisplayName("COMPLETED 会话应被跳过")
    void endSessionIfInProgress_completed_skips() {
      NvcVoiceSessionEntity session = buildSession(3L, NvcVoiceSessionStatus.COMPLETED);
      when(sessionRepository.findById(3L)).thenReturn(Optional.of(session));

      service.endSessionIfInProgress(3L);

      verify(evaluateStreamProducer, never()).sendEvaluateTask(anyString());
    }

    @Test
    @DisplayName("会话不存在应安全返回（不抛异常）")
    void endSessionIfInProgress_notFound_silentlyReturns() {
      when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

      assertDoesNotThrow(() -> service.endSessionIfInProgress(999L));
      verify(evaluateStreamProducer, never()).sendEvaluateTask(anyString());
    }
  }

  // ==================== saveMessage ====================

  @Nested
  @DisplayName("saveMessage 保存消息")
  class SaveMessageTests {

    @Test
    @DisplayName("Happy path - 同时有 userText 和 aiText")
    void saveMessage_bothTexts_saved() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      mockCacheHit(1L, session);
      when(messageRepository.findTop20BySessionIdOrderBySequenceNumDesc(1L)).thenReturn(List.of());
      when(messageRepository.findMaxSequenceNumBySessionId(1L)).thenReturn(Optional.of(5));
      when(messageRepository.save(any(NvcVoiceMessageEntity.class))).thenAnswer(inv -> inv.getArgument(0));

      service.saveMessage(1L, "用户说的话", "AI的回复", "DIALOGUE_GUIDE");

      ArgumentCaptor<NvcVoiceMessageEntity> captor = ArgumentCaptor.forClass(NvcVoiceMessageEntity.class);
      verify(messageRepository).save(captor.capture());
      NvcVoiceMessageEntity saved = captor.getValue();
      assertEquals("用户说的话", saved.getUserRecognizedText());
      assertEquals("AI的回复", saved.getAiGeneratedText());
      assertEquals(6, saved.getSequenceNum());
      assertEquals("DIALOGUE_GUIDE", saved.getAgentScene());
    }

    @Test
    @DisplayName("aiText 为 null 时不应保存消息")
    void saveMessage_nullAiText_noSave() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      mockCacheHit(1L, session);
      // userText is non-null, so fillLatestUnansweredQuestion is called
      when(messageRepository.findTop20BySessionIdOrderBySequenceNumDesc(1L)).thenReturn(List.of());

      service.saveMessage(1L, "用户说的话", null, "DIALOGUE_GUIDE");

      verify(messageRepository, never()).save(any(NvcVoiceMessageEntity.class));
    }

    @Test
    @DisplayName("aiText 为空字符串时不应保存消息")
    void saveMessage_emptyAiText_noSave() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      mockCacheHit(1L, session);
      when(messageRepository.findTop20BySessionIdOrderBySequenceNumDesc(1L)).thenReturn(List.of());

      service.saveMessage(1L, "用户说的话", "   ", "DIALOGUE_GUIDE");

      verify(messageRepository, never()).save(any(NvcVoiceMessageEntity.class));
    }

    @Test
    @DisplayName("userText 为 null 时应正常保存（userRecognizedText 为 null）")
    void saveMessage_nullUserText_savedWithNullUserText() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      mockCacheHit(1L, session);
      // userText is null -> short-circuit, fillLatestUnansweredQuestion NOT called
      when(messageRepository.findMaxSequenceNumBySessionId(1L)).thenReturn(Optional.empty());
      when(messageRepository.save(any(NvcVoiceMessageEntity.class))).thenAnswer(inv -> inv.getArgument(0));

      service.saveMessage(1L, null, "AI的回复", "DIALOGUE_GUIDE");

      ArgumentCaptor<NvcVoiceMessageEntity> captor = ArgumentCaptor.forClass(NvcVoiceMessageEntity.class);
      verify(messageRepository).save(captor.capture());
      assertNull(captor.getValue().getUserRecognizedText());
      assertEquals("AI的回复", captor.getValue().getAiGeneratedText());
      assertEquals(1, captor.getValue().getSequenceNum());
    }

    @Test
    @DisplayName("会话不存在时应安全返回（不抛异常）")
    void saveMessage_sessionNotFound_silentlyReturns() {
      mockCacheMiss(999L);
      when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

      assertDoesNotThrow(() -> service.saveMessage(999L, "user", "ai", null));
      verify(messageRepository, never()).save(any(NvcVoiceMessageEntity.class));
    }

    @Test
    @DisplayName("userText 应回填到上一条未回答的 AI 消息")
    void saveMessage_backfillUserTextToLatestUnanswered() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      mockCacheHit(1L, session);

      // 上一条 AI 消息没有 userRecognizedText
      NvcVoiceMessageEntity prevAiMessage = NvcVoiceMessageEntity.builder()
          .id(10L).sessionId(1L).aiGeneratedText("你好吗？").userRecognizedText(null).sequenceNum(3).build();
      when(messageRepository.findTop20BySessionIdOrderBySequenceNumDesc(1L))
          .thenReturn(List.of(prevAiMessage));
      when(messageRepository.save(any(NvcVoiceMessageEntity.class))).thenAnswer(inv -> inv.getArgument(0));

      // 新消息有 userText + aiText
      service.saveMessage(1L, "我还好", "那太好了", "DIALOGUE_GUIDE");

      // prevAiMessage 应被回填
      assertEquals("我还好", prevAiMessage.getUserRecognizedText());

      // 验证两次 save：一次回填 prevAiMessage，一次保存新消息
      ArgumentCaptor<NvcVoiceMessageEntity> captor = ArgumentCaptor.forClass(NvcVoiceMessageEntity.class);
      verify(messageRepository, times(2)).save(captor.capture());
      List<NvcVoiceMessageEntity> allSaved = captor.getAllValues();

      // 第一次 save 是回填
      assertEquals("我还好", allSaved.get(0).getUserRecognizedText());

      // 第二次 save 是新消息，userRecognizedText 为 null（因为 answer 已 attached）
      NvcVoiceMessageEntity newMessage = allSaved.get(1);
      assertEquals("那太好了", newMessage.getAiGeneratedText());
      assertNull(newMessage.getUserRecognizedText());
    }

    @Test
    @DisplayName("序列号从 1 开始（无历史消息）")
    void saveMessage_firstMessage_sequenceNumOne() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      mockCacheHit(1L, session);
      // userText is null -> short-circuit, no findTop20 call
      when(messageRepository.findMaxSequenceNumBySessionId(1L)).thenReturn(Optional.empty());
      when(messageRepository.save(any(NvcVoiceMessageEntity.class))).thenAnswer(inv -> inv.getArgument(0));

      service.saveMessage(1L, null, "第一条消息", null);

      ArgumentCaptor<NvcVoiceMessageEntity> captor = ArgumentCaptor.forClass(NvcVoiceMessageEntity.class);
      verify(messageRepository).save(captor.capture());
      assertEquals(1, captor.getValue().getSequenceNum());
    }
  }

  // ==================== listSessions ====================

  @Nested
  @DisplayName("listSessions 列出会话")
  class ListSessionsTests {

    @Test
    @DisplayName("同时指定 userId 和 status")
    void listSessions_userIdAndStatus_filtersByBoth() {
      NvcVoiceSessionEntity s = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      when(sessionRepository.findByUserIdAndStatus(100L, NvcVoiceSessionStatus.IN_PROGRESS))
          .thenReturn(List.of(s));

      List<VoiceSessionResponse> result = service.listSessions(100L, NvcVoiceSessionStatus.IN_PROGRESS);

      assertEquals(1, result.size());
      assertEquals(100L, result.get(0).userId());
      verify(sessionRepository).findByUserIdAndStatus(100L, NvcVoiceSessionStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("仅指定 userId")
    void listSessions_userOnly_filtersByUser() {
      NvcVoiceSessionEntity s = buildSession(1L, NvcVoiceSessionStatus.COMPLETED);
      when(sessionRepository.findByUserId(100L)).thenReturn(List.of(s));

      List<VoiceSessionResponse> result = service.listSessions(100L, null);

      assertEquals(1, result.size());
      verify(sessionRepository).findByUserId(100L);
    }

    @Test
    @DisplayName("仅指定 status")
    void listSessions_statusOnly_filtersByStatus() {
      NvcVoiceSessionEntity s = buildSession(1L, NvcVoiceSessionStatus.PAUSED);
      when(sessionRepository.findByStatus(NvcVoiceSessionStatus.PAUSED)).thenReturn(List.of(s));

      List<VoiceSessionResponse> result = service.listSessions(null, NvcVoiceSessionStatus.PAUSED);

      assertEquals(1, result.size());
      verify(sessionRepository).findByStatus(NvcVoiceSessionStatus.PAUSED);
    }

    @Test
    @DisplayName("都不指定时返回全部")
    void listSessions_noFilters_returnsAll() {
      NvcVoiceSessionEntity s1 = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      NvcVoiceSessionEntity s2 = buildSession(2L, NvcVoiceSessionStatus.COMPLETED);
      when(sessionRepository.findAll()).thenReturn(List.of(s1, s2));

      List<VoiceSessionResponse> result = service.listSessions(null, null);

      assertEquals(2, result.size());
      verify(sessionRepository).findAll();
    }

    @Test
    @DisplayName("结果应映射为 VoiceSessionResponse")
    void listSessions_mapsToResponse() {
      NvcVoiceSessionEntity s = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      s.setScenarioId(42L);
      when(sessionRepository.findByUserId(100L)).thenReturn(List.of(s));

      List<VoiceSessionResponse> result = service.listSessions(100L, null);

      VoiceSessionResponse resp = result.get(0);
      assertEquals(1L, resp.id());
      assertEquals(100L, resp.userId());
      assertEquals(NvcPracticeMode.SCENARIO, resp.practiceMode());
      assertEquals(42L, resp.scenarioId());
      assertEquals("/ws/nvc-voice/1", resp.webSocketUrl());
    }
  }

  // ==================== deleteSession ====================

  @Nested
  @DisplayName("deleteSession 删除会话")
  class DeleteSessionTests {

    @Test
    @DisplayName("Happy path - 级联删除消息和评估")
    void deleteSession_cascadingCleanup() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.COMPLETED);
      RBucket<NvcVoiceSessionEntity> cacheBucket = mockCacheHit(1L, session);

      NvcVoiceMessageEntity msg1 = NvcVoiceMessageEntity.builder().id(10L).sessionId(1L).sequenceNum(1).build();
      NvcVoiceMessageEntity msg2 = NvcVoiceMessageEntity.builder().id(11L).sessionId(1L).sequenceNum(2).build();
      when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L)).thenReturn(List.of(msg1, msg2));

      NvcVoiceEvaluationEntity eval = NvcVoiceEvaluationEntity.builder().id(1L).sessionId(1L).build();
      when(evaluationRepository.findBySessionId(1L)).thenReturn(Optional.of(eval));

      service.deleteSession(1L);

      verify(messageRepository).deleteAll(List.of(msg1, msg2));
      verify(evaluationRepository).delete(eval);
      verify(sessionRepository).delete(session);
      verify(cacheBucket).delete();
    }

    @Test
    @DisplayName("无评估数据时只删消息和会话")
    void deleteSession_noEvaluation_stillDeletes() {
      NvcVoiceSessionEntity session = buildSession(2L, NvcVoiceSessionStatus.COMPLETED);
      mockCacheHit(2L, session);
      when(messageRepository.findBySessionIdOrderBySequenceNumAsc(2L)).thenReturn(List.of());
      when(evaluationRepository.findBySessionId(2L)).thenReturn(Optional.empty());

      service.deleteSession(2L);

      verify(messageRepository).deleteAll(List.of());
      verify(evaluationRepository, never()).delete(any());
      verify(sessionRepository).delete(session);
    }

    @Test
    @DisplayName("会话不存在时安全返回（不抛异常）")
    void deleteSession_notFound_silentlyReturns() {
      mockCacheMiss(999L);
      when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

      assertDoesNotThrow(() -> service.deleteSession(999L));
      verify(sessionRepository, never()).delete(any());
    }
  }

  // ==================== getSession / getSessionResponse ====================

  @Nested
  @DisplayName("getSession 获取会话")
  class GetSessionTests {

    @Test
    @DisplayName("Redis 缓存命中时直接返回缓存")
    void getSession_cacheHit_returnsCached() {
      NvcVoiceSessionEntity cached = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      mockCacheHit(1L, cached);

      NvcVoiceSessionEntity result = service.getSession(1L);

      assertSame(cached, result);
      verify(sessionRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("Redis 缓存未命中时查数据库")
    void getSession_cacheMiss_queriesDb() {
      mockCacheMiss(1L);
      NvcVoiceSessionEntity dbEntity = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      when(sessionRepository.findById(1L)).thenReturn(Optional.of(dbEntity));

      NvcVoiceSessionEntity result = service.getSession(1L);

      assertSame(dbEntity, result);
    }

    @Test
    @DisplayName("数据库也不存在时返回 null")
    void getSession_notFound_returnsNull() {
      mockCacheMiss(999L);
      when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

      NvcVoiceSessionEntity result = service.getSession(999L);

      assertNull(result);
    }

    @Test
    @DisplayName("sessionId 为 null 时返回 null")
    void getSession_nullId_returnsNull() {
      NvcVoiceSessionEntity result = service.getSession(null);
      assertNull(result);
    }

    @Test
    @DisplayName("getSessionResponse - 会话不存在应抛 BusinessException")
    void getSessionResponse_notFound_throwsException() {
      mockCacheMiss(999L);
      when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

      assertThrows(BusinessException.class, () -> service.getSessionResponse(999L));
    }

    @Test
    @DisplayName("getSessionResponse - 会话存在应返回响应")
    void getSessionResponse_found_returnsResponse() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      mockCacheHit(1L, session);

      VoiceSessionResponse response = service.getSessionResponse(1L);

      assertNotNull(response);
      assertEquals(1L, response.id());
    }
  }

  // ==================== pauseSession / resumeSession ====================

  @Nested
  @DisplayName("pauseSession 暂停会话")
  class PauseSessionTests {

    @Test
    @DisplayName("Happy path - 暂停 IN_PROGRESS 会话")
    void pauseSession_inProgress_success() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      mockCacheHit(1L, session);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(session);

      VoiceSessionResponse response = service.pauseSession(1L, "用户暂停");

      assertEquals(NvcVoiceSessionStatus.PAUSED, session.getStatus());
      assertNotNull(session.getPausedAt());
      assertNotNull(response);
    }

    @Test
    @DisplayName("暂停 COMPLETED 会话应抛异常（非法状态转换）")
    void pauseSession_completed_throwsException() {
      NvcVoiceSessionEntity session = buildSession(2L, NvcVoiceSessionStatus.COMPLETED);
      mockCacheHit(2L, session);

      assertThrows(BusinessException.class, () -> service.pauseSession(2L, "test"));
    }

    @Test
    @DisplayName("暂停 PAUSED 会话应抛异常（非法状态转换）")
    void pauseSession_alreadyPaused_throwsException() {
      NvcVoiceSessionEntity session = buildSession(3L, NvcVoiceSessionStatus.PAUSED);
      mockCacheHit(3L, session);

      assertThrows(BusinessException.class, () -> service.pauseSession(3L, "test"));
    }
  }

  @Nested
  @DisplayName("resumeSession 恢复会话")
  class ResumeSessionTests {

    @Test
    @DisplayName("Happy path - 恢复 PAUSED 会话")
    void resumeSession_paused_success() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.PAUSED);
      mockCacheHit(1L, session);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(session);

      VoiceSessionResponse response = service.resumeSession(1L);

      assertEquals(NvcVoiceSessionStatus.IN_PROGRESS, session.getStatus());
      assertNotNull(session.getResumedAt());
      assertNotNull(response);
    }

    @Test
    @DisplayName("恢复 IN_PROGRESS 会话应抛异常")
    void resumeSession_inProgress_throwsException() {
      NvcVoiceSessionEntity session = buildSession(2L, NvcVoiceSessionStatus.IN_PROGRESS);
      mockCacheHit(2L, session);

      assertThrows(BusinessException.class, () -> service.resumeSession(2L));
    }

    @Test
    @DisplayName("恢复 COMPLETED 会话应抛异常")
    void resumeSession_completed_throwsException() {
      NvcVoiceSessionEntity session = buildSession(3L, NvcVoiceSessionStatus.COMPLETED);
      mockCacheHit(3L, session);

      assertThrows(BusinessException.class, () -> service.resumeSession(3L));
    }
  }

  // ==================== getMessages ====================

  @Nested
  @DisplayName("getMessages 获取消息历史")
  class GetMessagesTests {

    @Test
    @DisplayName("返回消息列表并映射为 DTO")
    void getMessages_returnsMappedDTOs() {
      NvcVoiceMessageEntity m1 = NvcVoiceMessageEntity.builder()
          .id(1L).sessionId(1L).messageType("DIALOGUE").agentScene("DIALOGUE_GUIDE")
          .userRecognizedText("你好").aiGeneratedText("你好呀").sequenceNum(1)
          .timestamp(LocalDateTime.now()).build();
      NvcVoiceMessageEntity m2 = NvcVoiceMessageEntity.builder()
          .id(2L).sessionId(1L).messageType("DIALOGUE").agentScene("DIALOGUE_GUIDE")
          .userRecognizedText(null).aiGeneratedText("请说说你的感受").sequenceNum(2)
          .timestamp(LocalDateTime.now()).build();

      when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L)).thenReturn(List.of(m1, m2));

      List<VoiceMessageDTO> result = service.getMessages(1L);

      assertEquals(2, result.size());
      assertEquals(1L, result.get(0).id());
      assertEquals("你好", result.get(0).userRecognizedText());
      assertEquals("你好呀", result.get(0).aiGeneratedText());
      assertEquals(2, result.get(1).sequenceNum());
      assertNull(result.get(1).userRecognizedText());
    }

    @Test
    @DisplayName("无消息时返回空列表")
    void getMessages_noMessages_returnsEmpty() {
      when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L)).thenReturn(List.of());

      List<VoiceMessageDTO> result = service.getMessages(1L);

      assertTrue(result.isEmpty());
    }
  }

  // ==================== buildVoiceContext ====================

  @Nested
  @DisplayName("buildVoiceContext 构建上下文")
  class BuildVoiceContextTests {

    @Test
    @DisplayName("Happy path - 有 scenarioId 时获取场景描述")
    void buildVoiceContext_withScenario_success() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      session.setScenarioId(42L);
      mockCacheHit(1L, session);

      when(messageRepository.countBySessionId(1L)).thenReturn(5L);

      NvcScenarioEntity scenario = NvcScenarioEntity.builder()
          .id(42L).title("邻里噪音").description("邻居经常在深夜制造噪音").build();
      when(scenarioRepository.findById(42L)).thenReturn(Optional.of(scenario));

      PracticeContext context = service.buildVoiceContext(1L);

      assertNotNull(context);
      assertEquals(5, context.getRoundCount());
      assertTrue(context.getScenarioDescription().contains("邻里噪音"));
      assertTrue(context.getScenarioDescription().contains("深夜"));
    }

    @Test
    @DisplayName("无 scenarioId 时 scenarioDescription 为 null")
    void buildVoiceContext_noScenario_nullDescription() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      session.setScenarioId(null);
      mockCacheHit(1L, session);
      when(messageRepository.countBySessionId(1L)).thenReturn(0L);

      PracticeContext context = service.buildVoiceContext(1L);

      assertNull(context.getScenarioDescription());
      assertEquals(0, context.getRoundCount());
    }

    @Test
    @DisplayName("scenarioId 对应的场景不存在时 description 为 null")
    void buildVoiceContext_scenarioNotFound_nullDescription() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      session.setScenarioId(999L);
      mockCacheHit(1L, session);
      when(messageRepository.countBySessionId(1L)).thenReturn(3L);
      when(scenarioRepository.findById(999L)).thenReturn(Optional.empty());

      PracticeContext context = service.buildVoiceContext(1L);

      assertNull(context.getScenarioDescription());
      assertEquals(3, context.getRoundCount());
    }

    @Test
    @DisplayName("会话不存在应抛 BusinessException")
    void buildVoiceContext_sessionNotFound_throwsException() {
      mockCacheMiss(999L);
      when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

      assertThrows(BusinessException.class, () -> service.buildVoiceContext(999L));
    }
  }

  // ==================== decideNextAgent / executeAgent ====================

  @Nested
  @DisplayName("Agent 调度委托")
  class AgentDelegationTests {

    @Test
    @DisplayName("decideNextAgent 委托给 orchestrator")
    void decideNextAgent_delegates() {
      PracticeContext context = PracticeContext.builder().roundCount(2).build();
      AgentDecision decision = new AgentDecision(NvcAgentScene.DIALOGUE_GUIDE, "test", null);
      when(orchestrator.decideNextAgent(context)).thenReturn(decision);

      AgentDecision result = service.decideNextAgent(context);

      assertSame(decision, result);
      verify(orchestrator).decideNextAgent(context);
    }

    @Test
    @DisplayName("executeAgent 委托给 orchestrator")
    void executeAgent_delegates() {
      PracticeContext context = PracticeContext.builder().roundCount(2).build();
      AgentDecision decision = new AgentDecision(NvcAgentScene.DIALOGUE_GUIDE, "test", null);
      when(orchestrator.executeAgent(decision, context, "用户输入")).thenReturn("AI回复");

      String result = service.executeAgent(decision, context, "用户输入");

      assertEquals("AI回复", result);
      verify(orchestrator).executeAgent(decision, context, "用户输入");
    }
  }

  // ==================== triggerEvaluation ====================

  @Nested
  @DisplayName("triggerEvaluation 触发评估")
  class TriggerEvaluationTests {

    @Test
    @DisplayName("Happy path - 触发评估并设置状态为 PENDING")
    void triggerEvaluation_success() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.COMPLETED);
      mockCacheHit(1L, session);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(session);

      service.triggerEvaluation(1L);

      assertEquals(AsyncTaskStatus.PENDING, session.getEvaluateStatus());
      verify(evaluateStreamProducer).sendEvaluateTask("1");
    }

    @Test
    @DisplayName("会话不存在时安全返回")
    void triggerEvaluation_notFound_silentlyReturns() {
      mockCacheMiss(999L);
      when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

      assertDoesNotThrow(() -> service.triggerEvaluation(999L));
      verify(evaluateStreamProducer, never()).sendEvaluateTask(anyString());
    }
  }

  // ==================== getEvaluationStatus ====================

  @Nested
  @DisplayName("getEvaluationStatus 获取评估状态")
  class GetEvaluationStatusTests {

    @Test
    @DisplayName("有评估结果时返回完整 DTO")
    void getEvaluationStatus_withEvaluation_returnsFullDTO() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.COMPLETED);
      session.setEvaluateStatus(AsyncTaskStatus.COMPLETED);
      mockCacheHit(1L, session);

      NvcVoiceEvaluationEntity eval = NvcVoiceEvaluationEntity.builder()
          .id(1L).sessionId(1L)
          .observationScore(85).feelingScore(70).needScore(90).requestScore(75)
          .empathyScore(80).overallScore(80).fluencyScore(88)
          .observationDetail("观察力强").overallFeedback("不错")
          .build();
      when(evaluationRepository.findBySessionId(1L)).thenReturn(Optional.of(eval));

      VoiceEvaluationStatusDTO result = service.getEvaluationStatus(1L);

      assertEquals(AsyncTaskStatus.COMPLETED, result.status());
      assertNull(result.error());
      assertNotNull(result.evaluation());
      assertEquals(85, result.evaluation().observationScore());
      assertEquals(80, result.evaluation().overallScore());
    }

    @Test
    @DisplayName("无评估结果时 evaluation 为 null")
    void getEvaluationStatus_noEvaluation_evaluationNull() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.COMPLETED);
      session.setEvaluateStatus(AsyncTaskStatus.PENDING);
      mockCacheHit(1L, session);
      when(evaluationRepository.findBySessionId(1L)).thenReturn(Optional.empty());

      VoiceEvaluationStatusDTO result = service.getEvaluationStatus(1L);

      assertEquals(AsyncTaskStatus.PENDING, result.status());
      assertNull(result.evaluation());
    }

    @Test
    @DisplayName("会话不存在应抛 BusinessException")
    void getEvaluationStatus_notFound_throwsException() {
      mockCacheMiss(999L);
      when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

      assertThrows(BusinessException.class, () -> service.getEvaluationStatus(999L));
    }
  }

  // ==================== updateEvaluateStatus ====================

  @Nested
  @DisplayName("updateEvaluateStatus 更新评估状态")
  class UpdateEvaluateStatusTests {

    @Test
    @DisplayName("Happy path - 更新评估状态和错误信息")
    void updateEvaluateStatus_success() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.COMPLETED);
      when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(session);

      // updateEvaluateStatus uses sessionRepository.findById, not getSession (no cache read)
      // but it calls invalidateSessionCache, so we need the bucket for delete verification
      RBucket<NvcVoiceSessionEntity> cacheBucket = mockBucketOnly(1L);

      service.updateEvaluateStatus(1L, AsyncTaskStatus.FAILED, "评估超时");

      assertEquals(AsyncTaskStatus.FAILED, session.getEvaluateStatus());
      assertEquals("评估超时", session.getEvaluateError());
      verify(cacheBucket).delete(); // cache invalidated
    }

    @Test
    @DisplayName("会话不存在时安全返回")
    void updateEvaluateStatus_notFound_silentlyReturns() {
      when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

      assertDoesNotThrow(() ->
          service.updateEvaluateStatus(999L, AsyncTaskStatus.FAILED, "error"));
    }
  }

  // ==================== cleanupStaleSessions ====================

  @Nested
  @DisplayName("cleanupStaleSessions 清理过期会话")
  class CleanupStaleSessionsTests {

    @Test
    @DisplayName("清理超过 2 小时的 IN_PROGRESS/PAUSED 会话")
    void cleanupStaleSessions_cleansStaleSessions() {
      NvcVoiceSessionEntity staleSession = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      when(sessionRepository.findByStatusInAndUpdatedAtBefore(
          eq(List.of(NvcVoiceSessionStatus.IN_PROGRESS, NvcVoiceSessionStatus.PAUSED)),
          any(LocalDateTime.class)))
          .thenReturn(List.of(staleSession));

      // endSession internal mocks
      mockCacheHit(1L, staleSession);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(staleSession);

      // No stuck evaluations
      when(sessionRepository.findByEvaluateStatusAndUpdatedAtBefore(
          eq(AsyncTaskStatus.PROCESSING), any(LocalDateTime.class)))
          .thenReturn(List.of());

      service.cleanupStaleSessions();

      assertEquals(NvcVoiceSessionStatus.COMPLETED, staleSession.getStatus());
      verify(evaluateStreamProducer).sendEvaluateTask("1");
    }

    @Test
    @DisplayName("清理超过 30 分钟的 PROCESSING 评估")
    void cleanupStaleSessions_cleansStuckEvaluations() {
      // No stale sessions
      when(sessionRepository.findByStatusInAndUpdatedAtBefore(
          anyList(), any(LocalDateTime.class)))
          .thenReturn(List.of());

      NvcVoiceSessionEntity stuckSession = buildSession(2L, NvcVoiceSessionStatus.COMPLETED);
      stuckSession.setEvaluateStatus(AsyncTaskStatus.PROCESSING);
      when(sessionRepository.findByEvaluateStatusAndUpdatedAtBefore(
          eq(AsyncTaskStatus.PROCESSING), any(LocalDateTime.class)))
          .thenReturn(List.of(stuckSession));
      when(sessionRepository.findById(2L)).thenReturn(Optional.of(stuckSession));
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(stuckSession);

      // updateEvaluateStatus uses sessionRepository.findById (no cache read)
      // but calls invalidateSessionCache which needs the bucket for delete
      mockBucketOnly(2L);

      service.cleanupStaleSessions();

      assertEquals(AsyncTaskStatus.FAILED, stuckSession.getEvaluateStatus());
      assertEquals("评估超时", stuckSession.getEvaluateError());
    }

    @Test
    @DisplayName("无过期数据时不做任何操作")
    void cleanupStaleSessions_nothingToClean() {
      when(sessionRepository.findByStatusInAndUpdatedAtBefore(
          anyList(), any(LocalDateTime.class)))
          .thenReturn(List.of());
      when(sessionRepository.findByEvaluateStatusAndUpdatedAtBefore(
          eq(AsyncTaskStatus.PROCESSING), any(LocalDateTime.class)))
          .thenReturn(List.of());

      assertDoesNotThrow(() -> service.cleanupStaleSessions());

      verify(sessionRepository, never()).save(any());
      verify(evaluateStreamProducer, never()).sendEvaluateTask(anyString());
    }
  }

  // ==================== getNextSequenceNum (via saveMessage) ====================

  @Nested
  @DisplayName("getNextSequenceNum 序列号生成（间接测试）")
  class SequenceNumTests {

    @Test
    @DisplayName("已有消息时序列号为 MAX + 1")
    void sequenceNum_existingMessages_maxPlusOne() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      mockCacheHit(1L, session);
      // userText is null -> short-circuit, no findTop20 call
      when(messageRepository.findMaxSequenceNumBySessionId(1L)).thenReturn(Optional.of(10));
      when(messageRepository.save(any(NvcVoiceMessageEntity.class))).thenAnswer(inv -> inv.getArgument(0));

      service.saveMessage(1L, null, "消息", null);

      ArgumentCaptor<NvcVoiceMessageEntity> captor = ArgumentCaptor.forClass(NvcVoiceMessageEntity.class);
      verify(messageRepository).save(captor.capture());
      assertEquals(11, captor.getValue().getSequenceNum());
    }

    @Test
    @DisplayName("无历史消息时序列号为 1")
    void sequenceNum_noHistory_startsAtOne() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      mockCacheHit(1L, session);
      // userText is null -> short-circuit, no findTop20 call
      when(messageRepository.findMaxSequenceNumBySessionId(1L)).thenReturn(Optional.empty());
      when(messageRepository.save(any(NvcVoiceMessageEntity.class))).thenAnswer(inv -> inv.getArgument(0));

      service.saveMessage(1L, null, "消息", null);

      ArgumentCaptor<NvcVoiceMessageEntity> captor = ArgumentCaptor.forClass(NvcVoiceMessageEntity.class);
      verify(messageRepository).save(captor.capture());
      assertEquals(1, captor.getValue().getSequenceNum());
    }
  }

  // ==================== 状态转换验证 ====================

  @Nested
  @DisplayName("状态转换验证")
  class TransitionValidationTests {

    @Test
    @DisplayName("IN_PROGRESS -> COMPLETED 合法")
    void transition_inProgressToCompleted_valid() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      mockCacheHit(1L, session);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(session);

      assertDoesNotThrow(() -> service.endSession(1L));
    }

    @Test
    @DisplayName("IN_PROGRESS -> PAUSED 合法")
    void transition_inProgressToPaused_valid() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.IN_PROGRESS);
      mockCacheHit(1L, session);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(session);

      assertDoesNotThrow(() -> service.pauseSession(1L, "test"));
    }

    @Test
    @DisplayName("PAUSED -> IN_PROGRESS 合法")
    void transition_pausedToInProgress_valid() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.PAUSED);
      mockCacheHit(1L, session);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(session);

      assertDoesNotThrow(() -> service.resumeSession(1L));
    }

    @Test
    @DisplayName("PAUSED -> COMPLETED 合法")
    void transition_pausedToCompleted_valid() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.PAUSED);
      mockCacheHit(1L, session);
      when(sessionRepository.save(any(NvcVoiceSessionEntity.class))).thenReturn(session);

      assertDoesNotThrow(() -> service.endSession(1L));
    }

    @Test
    @DisplayName("COMPLETED -> 任何状态都不合法")
    void transition_completedToAny_invalid() {
      NvcVoiceSessionEntity session = buildSession(1L, NvcVoiceSessionStatus.COMPLETED);
      session.setEndTime(LocalDateTime.now());
      mockCacheHit(1L, session);

      // endSession is idempotent for COMPLETED, so it returns without error
      // But pauseSession and resumeSession should throw
      assertThrows(BusinessException.class, () -> service.pauseSession(1L, "test"));

      NvcVoiceSessionEntity session2 = buildSession(2L, NvcVoiceSessionStatus.COMPLETED);
      mockCacheHit(2L, session2);
      assertThrows(BusinessException.class, () -> service.resumeSession(2L));
    }
  }
}
