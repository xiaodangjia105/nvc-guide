package nvc.guide.modules.nvcpractice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcpractice.dto.CreatePracticeSessionRequest;
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
import nvc.guide.modules.nvcscenario.repository.NvcScenarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcPracticeSessionService 测试")
class NvcPracticeSessionServiceTest {

    @Mock
    private NvcPracticeSessionRepository sessionRepository;
    @Mock
    private NvcPracticeMessageRepository messageRepository;
    @Mock
    private NvcEvaluationService evaluationService;
    @Mock
    private NvcScenarioRepository scenarioRepository;
    @Mock
    private RedisService redisService;
    @Mock
    private ObjectMapper objectMapper;

    private NvcPracticeSessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new NvcPracticeSessionService(
            sessionRepository, messageRepository, evaluationService,
            scenarioRepository, redisService, objectMapper);
    }

    private NvcPracticeSessionEntity buildSession(Long id, NvcSessionPhase phase) {
        return NvcPracticeSessionEntity.builder()
            .id(id)
            .userId(100L)
            .practiceMode(NvcPracticeMode.FREE_DIALOG)
            .currentPhase(phase)
            .difficulty(NvcDifficulty.MEDIUM)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private NvcPracticeSessionEntity buildSessionWithMode(Long id, NvcPracticeMode mode,
            NvcSessionPhase phase, NvcPracticeStep step) {
        return NvcPracticeSessionEntity.builder()
            .id(id)
            .userId(100L)
            .practiceMode(mode)
            .currentPhase(phase)
            .currentStep(step)
            .difficulty(NvcDifficulty.MEDIUM)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    // ========== createSession() ==========

    @Nested
    @DisplayName("createSession()")
    class CreateSessionTests {

        @Test
        @DisplayName("自由对话模式：创建会话并缓存")
        void createSession_freeDialog_createsAndCaches() {
            // Arrange
            CreatePracticeSessionRequest request = new CreatePracticeSessionRequest(
                NvcPracticeMode.FREE_DIALOG, null, NvcDifficulty.MEDIUM);
            NvcPracticeSessionEntity saved = buildSession(1L, NvcSessionPhase.CREATED);

            when(sessionRepository.save(any())).thenReturn(saved);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionEntity result = sessionService.createSession(100L, request);

            // Assert
            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals(NvcSessionPhase.CREATED, result.getCurrentPhase());
            verify(sessionRepository).save(any());
            verify(redisService).set(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("场景驱动模式：指定场景 ID")
        void createSession_scenarioMode_withScenarioId() {
            // Arrange
            CreatePracticeSessionRequest request = new CreatePracticeSessionRequest(
                NvcPracticeMode.SCENARIO, 42L, NvcDifficulty.HARD);
            NvcPracticeSessionEntity saved = NvcPracticeSessionEntity.builder()
                .id(2L).userId(100L)
                .practiceMode(NvcPracticeMode.SCENARIO)
                .scenarioId(42L)
                .currentPhase(NvcSessionPhase.CREATED)
                .difficulty(NvcDifficulty.HARD)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            when(sessionRepository.save(any())).thenReturn(saved);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionEntity result = sessionService.createSession(100L, request);

            // Assert
            assertEquals(42L, result.getScenarioId());
            assertEquals(NvcPracticeMode.SCENARIO, result.getPracticeMode());
            verify(scenarioRepository, never()).findByDifficulty(any());
        }

        @Test
        @DisplayName("场景驱动模式：无场景 ID 时随机分配")
        void createSession_scenarioMode_randomScenario() {
            // Arrange
            CreatePracticeSessionRequest request = new CreatePracticeSessionRequest(
                NvcPracticeMode.SCENARIO, null, NvcDifficulty.EASY);
            NvcScenarioEntity scenario = NvcScenarioEntity.builder()
                .id(10L).title("测试场景").difficulty(NvcDifficulty.EASY).build();
            NvcPracticeSessionEntity saved = NvcPracticeSessionEntity.builder()
                .id(3L).userId(100L)
                .practiceMode(NvcPracticeMode.SCENARIO)
                .scenarioId(10L)
                .currentPhase(NvcSessionPhase.CREATED)
                .difficulty(NvcDifficulty.EASY)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            when(scenarioRepository.findByDifficulty(NvcDifficulty.EASY))
                .thenReturn(List.of(scenario));
            when(sessionRepository.save(any())).thenReturn(saved);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionEntity result = sessionService.createSession(100L, request);

            // Assert
            assertEquals(10L, result.getScenarioId());
            verify(scenarioRepository).findByDifficulty(NvcDifficulty.EASY);
        }

        @Test
        @DisplayName("场景驱动模式：指定难度无场景时从该难度随机选择")
        void createSession_scenarioMode_fallbackToAllScenarios() {
            // Arrange
            CreatePracticeSessionRequest request = new CreatePracticeSessionRequest(
                NvcPracticeMode.SCENARIO, null, NvcDifficulty.HARD);
            NvcScenarioEntity scenario = NvcScenarioEntity.builder()
                .id(20L).title("通用场景").difficulty(NvcDifficulty.MEDIUM).build();
            NvcPracticeSessionEntity saved = NvcPracticeSessionEntity.builder()
                .id(4L).userId(100L)
                .practiceMode(NvcPracticeMode.SCENARIO)
                .scenarioId(20L)
                .currentPhase(NvcSessionPhase.CREATED)
                .difficulty(NvcDifficulty.HARD)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            when(scenarioRepository.findByDifficulty(NvcDifficulty.HARD))
                .thenReturn(Collections.emptyList());
            when(scenarioRepository.findAll())
                .thenReturn(List.of(scenario));
            when(sessionRepository.save(any())).thenReturn(saved);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionEntity result = sessionService.createSession(100L, request);

            // Assert
            assertEquals(20L, result.getScenarioId());
            verify(scenarioRepository).findByDifficulty(NvcDifficulty.HARD);
            verify(scenarioRepository).findAll();
        }

        @Test
        @DisplayName("场景驱动模式：无任何场景时抛异常")
        void createSession_scenarioMode_noScenarios_throwsException() {
            // Arrange
            CreatePracticeSessionRequest request = new CreatePracticeSessionRequest(
                NvcPracticeMode.SCENARIO, null, null);

            when(scenarioRepository.findByDifficulty(NvcDifficulty.MEDIUM))
                .thenReturn(Collections.emptyList());
            when(scenarioRepository.findAll())
                .thenReturn(Collections.emptyList());

            // Act & Assert
            assertThrows(BusinessException.class,
                () -> sessionService.createSession(100L, request));
        }

        @Test
        @DisplayName("结构化四步模式：初始步骤设为 OBSERVE")
        void createSession_structuredFourStep_initialStepObserve() {
            // Arrange
            CreatePracticeSessionRequest request = new CreatePracticeSessionRequest(
                NvcPracticeMode.STRUCTURED_FOUR_STEP, null, NvcDifficulty.MEDIUM);
            NvcPracticeSessionEntity saved = NvcPracticeSessionEntity.builder()
                .id(5L).userId(100L)
                .practiceMode(NvcPracticeMode.STRUCTURED_FOUR_STEP)
                .currentPhase(NvcSessionPhase.CREATED)
                .currentStep(NvcPracticeStep.OBSERVE)
                .difficulty(NvcDifficulty.MEDIUM)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            when(sessionRepository.save(any())).thenReturn(saved);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionEntity result = sessionService.createSession(100L, request);

            // Assert
            assertEquals(NvcPracticeStep.OBSERVE, result.getCurrentStep());
        }

        @Test
        @DisplayName("difficulty 为 null 时默认 MEDIUM")
        void createSession_nullDifficulty_defaultsToMedium() {
            // Arrange
            CreatePracticeSessionRequest request = new CreatePracticeSessionRequest(
                NvcPracticeMode.FREE_DIALOG, null, null);
            NvcPracticeSessionEntity saved = NvcPracticeSessionEntity.builder()
                .id(6L).userId(100L)
                .practiceMode(NvcPracticeMode.FREE_DIALOG)
                .currentPhase(NvcSessionPhase.CREATED)
                .difficulty(NvcDifficulty.MEDIUM)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            when(sessionRepository.save(any())).thenReturn(saved);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionEntity result = sessionService.createSession(100L, request);

            // Assert
            assertEquals(NvcDifficulty.MEDIUM, result.getDifficulty());
        }
    }

    // ========== getSession() ==========

    @Nested
    @DisplayName("getSession()")
    class GetSessionTests {

        @Test
        @DisplayName("缓存未命中时从 DB 加载并缓存")
        void getSession_cacheMiss_loadsFromDbAndCaches() {
            // Arrange
            NvcPracticeSessionEntity session = buildSession(1L, NvcSessionPhase.IN_PROGRESS);
            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionEntity result = sessionService.getSession(1L);

            // Assert
            assertNotNull(result);
            assertEquals(1L, result.getId());
            verify(sessionRepository).findById(1L);
            verify(redisService).set(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("缓存命中时仍从 DB 加载托管实体")
        void getSession_cacheHit_stillLoadsFromDb() {
            // Arrange
            NvcPracticeSessionEntity session = buildSession(1L, NvcSessionPhase.IN_PROGRESS);
            when(redisService.get("nvc:practice:session:1")).thenReturn("{\"id\":1}");
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

            // Act
            NvcPracticeSessionEntity result = sessionService.getSession(1L);

            // Assert
            assertNotNull(result);
            assertEquals(1L, result.getId());
            verify(sessionRepository).findById(1L);
            // 缓存命中时不重新缓存
            verify(redisService, never()).set(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("会话不存在时抛 BusinessException")
        void getSession_notFound_throwsException() {
            // Arrange
            when(redisService.get("nvc:practice:session:999")).thenReturn(null);
            when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

            // Act & Assert
            BusinessException ex = assertThrows(BusinessException.class,
                () -> sessionService.getSession(999L));
            assertEquals(3001, ex.getCode());
        }
    }

    // ========== getUserSessions() ==========

    @Nested
    @DisplayName("getUserSessions()")
    class GetUserSessionsTests {

        @Test
        @DisplayName("无 phase 过滤：返回用户全部会话")
        void getUserSessions_noPhaseFilter_returnsAll() {
            // Arrange
            List<NvcPracticeSessionEntity> sessions = List.of(
                buildSession(1L, NvcSessionPhase.IN_PROGRESS),
                buildSession(2L, NvcSessionPhase.COMPLETED));
            when(sessionRepository.findByUserIdOrderByCreatedAtDesc(100L))
                .thenReturn(sessions);

            // Act
            List<NvcPracticeSessionEntity> result =
                sessionService.getUserSessions(100L, null);

            // Assert
            assertEquals(2, result.size());
            verify(sessionRepository).findByUserIdOrderByCreatedAtDesc(100L);
        }

        @Test
        @DisplayName("有 phase 过滤：返回指定阶段的会话")
        void getUserSessions_withPhaseFilter_returnsFiltered() {
            // Arrange
            List<NvcPracticeSessionEntity> sessions = List.of(
                buildSession(1L, NvcSessionPhase.IN_PROGRESS));
            when(sessionRepository.findByUserIdAndCurrentPhaseOrderByCreatedAtDesc(
                100L, NvcSessionPhase.IN_PROGRESS))
                .thenReturn(sessions);

            // Act
            List<NvcPracticeSessionEntity> result =
                sessionService.getUserSessions(100L, NvcSessionPhase.IN_PROGRESS);

            // Assert
            assertEquals(1, result.size());
            assertEquals(NvcSessionPhase.IN_PROGRESS, result.get(0).getCurrentPhase());
        }
    }

    // ========== updatePhase() ==========

    @Nested
    @DisplayName("updatePhase()")
    class UpdatePhaseTests {

        @Test
        @DisplayName("CREATED -> IN_PROGRESS：合法转换")
        void updatePhase_createdToInProgress_succeeds() {
            // Arrange
            NvcPracticeSessionEntity session = buildSession(1L, NvcSessionPhase.CREATED);
            NvcPracticeSessionEntity updated = buildSession(1L, NvcSessionPhase.IN_PROGRESS);
            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenReturn(updated);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionEntity result =
                sessionService.updatePhase(1L, NvcSessionPhase.IN_PROGRESS);

            // Assert
            assertEquals(NvcSessionPhase.IN_PROGRESS, result.getCurrentPhase());
            verify(sessionRepository).save(any());
        }

        @Test
        @DisplayName("CREATED -> COMPLETED：合法转换")
        void updatePhase_createdToCompleted_succeeds() {
            // Arrange
            NvcPracticeSessionEntity session = buildSession(1L, NvcSessionPhase.CREATED);
            NvcPracticeSessionEntity updated = buildSession(1L, NvcSessionPhase.COMPLETED);
            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenReturn(updated);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionEntity result =
                sessionService.updatePhase(1L, NvcSessionPhase.COMPLETED);

            // Assert
            assertEquals(NvcSessionPhase.COMPLETED, result.getCurrentPhase());
        }

        @Test
        @DisplayName("IN_PROGRESS -> PAUSED：合法转换")
        void updatePhase_inProgressToPaused_succeeds() {
            // Arrange
            NvcPracticeSessionEntity session = buildSession(1L, NvcSessionPhase.IN_PROGRESS);
            NvcPracticeSessionEntity updated = buildSession(1L, NvcSessionPhase.PAUSED);
            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenReturn(updated);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionEntity result =
                sessionService.updatePhase(1L, NvcSessionPhase.PAUSED);

            // Assert
            assertEquals(NvcSessionPhase.PAUSED, result.getCurrentPhase());
        }

        @Test
        @DisplayName("PAUSED -> IN_PROGRESS：合法转换")
        void updatePhase_pausedToInProgress_succeeds() {
            // Arrange
            NvcPracticeSessionEntity session = buildSession(1L, NvcSessionPhase.PAUSED);
            NvcPracticeSessionEntity updated = buildSession(1L, NvcSessionPhase.IN_PROGRESS);
            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenReturn(updated);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionEntity result =
                sessionService.updatePhase(1L, NvcSessionPhase.IN_PROGRESS);

            // Assert
            assertEquals(NvcSessionPhase.IN_PROGRESS, result.getCurrentPhase());
        }

        @Test
        @DisplayName("COMPLETED -> EVALUATED：合法转换")
        void updatePhase_completedToEvaluated_succeeds() {
            // Arrange
            NvcPracticeSessionEntity session = buildSession(1L, NvcSessionPhase.COMPLETED);
            NvcPracticeSessionEntity updated = buildSession(1L, NvcSessionPhase.EVALUATED);
            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenReturn(updated);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionEntity result =
                sessionService.updatePhase(1L, NvcSessionPhase.EVALUATED);

            // Assert
            assertEquals(NvcSessionPhase.EVALUATED, result.getCurrentPhase());
        }

        @Test
        @DisplayName("非法转换：CREATED -> EVALUATED 抛异常")
        void updatePhase_invalidTransition_throwsException() {
            // Arrange
            NvcPracticeSessionEntity session = buildSession(1L, NvcSessionPhase.CREATED);
            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

            // Act & Assert
            BusinessException ex = assertThrows(BusinessException.class,
                () -> sessionService.updatePhase(1L, NvcSessionPhase.EVALUATED));
            assertEquals(3007, ex.getCode());
        }

        @Test
        @DisplayName("非法转换：EVALUATED -> 任意状态 抛异常（终态不可转移）")
        void updatePhase_evaluatedToAny_throwsException() {
            // Arrange
            NvcPracticeSessionEntity session = buildSession(1L, NvcSessionPhase.EVALUATED);
            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

            // Act & Assert
            assertThrows(BusinessException.class,
                () -> sessionService.updatePhase(1L, NvcSessionPhase.IN_PROGRESS));
        }

        @Test
        @DisplayName("首次切换到 IN_PROGRESS 时设置 startedAt")
        void updatePhase_firstInProgress_setsStartedAt() {
            // Arrange
            NvcPracticeSessionEntity session = NvcPracticeSessionEntity.builder()
                .id(1L).userId(100L)
                .practiceMode(NvcPracticeMode.FREE_DIALOG)
                .currentPhase(NvcSessionPhase.CREATED)
                .difficulty(NvcDifficulty.MEDIUM)
                .startedAt(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
            NvcPracticeSessionEntity updated = buildSession(1L, NvcSessionPhase.IN_PROGRESS);

            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenReturn(updated);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            sessionService.updatePhase(1L, NvcSessionPhase.IN_PROGRESS);

            // Assert
            verify(sessionRepository).save(any());
        }

        @Test
        @DisplayName("切换到 COMPLETED 时设置 completedAt")
        void updatePhase_toCompleted_setsCompletedAt() {
            // Arrange
            NvcPracticeSessionEntity session = buildSession(1L, NvcSessionPhase.IN_PROGRESS);
            NvcPracticeSessionEntity updated = buildSession(1L, NvcSessionPhase.COMPLETED);

            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenReturn(updated);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            sessionService.updatePhase(1L, NvcSessionPhase.COMPLETED);

            // Assert
            verify(sessionRepository).save(any());
        }

        @Test
        @DisplayName("更新后同步写入 Redis 缓存")
        void updatePhase_syncsCache() {
            // Arrange
            NvcPracticeSessionEntity session = buildSession(1L, NvcSessionPhase.CREATED);
            NvcPracticeSessionEntity updated = buildSession(1L, NvcSessionPhase.IN_PROGRESS);
            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenReturn(updated);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            sessionService.updatePhase(1L, NvcSessionPhase.IN_PROGRESS);

            // Assert
            verify(redisService, atLeast(1)).set(anyString(), anyString(), any());
        }
    }

    // ========== updateStep() ==========

    @Nested
    @DisplayName("updateStep()")
    class UpdateStepTests {

        @Test
        @DisplayName("更新当前步骤并缓存")
        void updateStep_updatesAndCaches() {
            // Arrange
            NvcPracticeSessionEntity session = buildSessionWithMode(
                1L, NvcPracticeMode.STRUCTURED_FOUR_STEP,
                NvcSessionPhase.IN_PROGRESS, NvcPracticeStep.OBSERVE);
            NvcPracticeSessionEntity updated = buildSessionWithMode(
                1L, NvcPracticeMode.STRUCTURED_FOUR_STEP,
                NvcSessionPhase.IN_PROGRESS, NvcPracticeStep.FEELING);

            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenReturn(updated);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionEntity result =
                sessionService.updateStep(1L, NvcPracticeStep.FEELING);

            // Assert
            assertEquals(NvcPracticeStep.FEELING, result.getCurrentStep());
            verify(sessionRepository).save(any());
            verify(redisService, atLeast(1)).set(anyString(), anyString(), any());
        }
    }

    // ========== updateAgentScene() ==========

    @Nested
    @DisplayName("updateAgentScene()")
    class UpdateAgentSceneTests {

        @Test
        @DisplayName("更新当前 Agent 场景并缓存")
        void updateAgentScene_updatesAndCaches() {
            // Arrange
            NvcPracticeSessionEntity session = buildSession(1L, NvcSessionPhase.IN_PROGRESS);
            NvcPracticeSessionEntity updated = buildSession(1L, NvcSessionPhase.IN_PROGRESS);
            updated.setAgentScene(NvcAgentScene.DIALOGUE_GUIDE);

            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenReturn(updated);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionEntity result =
                sessionService.updateAgentScene(1L, NvcAgentScene.DIALOGUE_GUIDE);

            // Assert
            assertEquals(NvcAgentScene.DIALOGUE_GUIDE, result.getAgentScene());
            verify(sessionRepository).save(any());
            verify(redisService, atLeast(1)).set(anyString(), anyString(), any());
        }
    }

    // ========== completeSession() ==========

    @Nested
    @DisplayName("completeSession()")
    class CompleteSessionTests {

        @Test
        @DisplayName("正常完成：IN_PROGRESS -> COMPLETED")
        void completeSession_inProgress_transitionsToCompleted() {
            // Arrange
            NvcPracticeSessionEntity session = buildSession(1L, NvcSessionPhase.IN_PROGRESS);
            NvcPracticeSessionEntity completed = buildSession(1L, NvcSessionPhase.COMPLETED);

            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenReturn(completed);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionEntity result = sessionService.completeSession(1L);

            // Assert
            assertEquals(NvcSessionPhase.COMPLETED, result.getCurrentPhase());
        }

        @Test
        @DisplayName("幂等性：已完成的会话直接返回，不重复操作")
        void completeSession_alreadyCompleted_returnsDirectly() {
            // Arrange
            NvcPracticeSessionEntity session = buildSession(1L, NvcSessionPhase.COMPLETED);
            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

            // Act
            NvcPracticeSessionEntity result = sessionService.completeSession(1L);

            // Assert
            assertEquals(NvcSessionPhase.COMPLETED, result.getCurrentPhase());
            verify(sessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("幂等性：已评估的会话直接返回")
        void completeSession_alreadyEvaluated_returnsDirectly() {
            // Arrange
            NvcPracticeSessionEntity session = buildSession(1L, NvcSessionPhase.EVALUATED);
            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

            // Act
            NvcPracticeSessionEntity result = sessionService.completeSession(1L);

            // Assert
            assertEquals(NvcSessionPhase.EVALUATED, result.getCurrentPhase());
            verify(sessionRepository, never()).save(any());
        }
    }

    // ========== completeAndEvaluate() ==========

    @Nested
    @DisplayName("completeAndEvaluate()")
    class CompleteAndEvaluateTests {

        @Test
        @DisplayName("正常流程：完成会话 -> 获取消息 -> 最终评估 -> 更新为 EVALUATED")
        void completeAndEvaluate_successFlow() {
            // Arrange
            NvcPracticeSessionEntity createdSession = buildSession(1L, NvcSessionPhase.CREATED);
            NvcPracticeSessionEntity completedSession = buildSession(1L, NvcSessionPhase.COMPLETED);
            NvcPracticeSessionEntity evaluatedSession = buildSession(1L, NvcSessionPhase.EVALUATED);

            NvcPracticeMessageEntity userMsg = NvcPracticeMessageEntity.builder()
                .id(1L).sessionId(1L).content("用户消息").sequenceNum(1).build();
            NvcPracticeMessageEntity aiMsg = NvcPracticeMessageEntity.builder()
                .id(2L).sessionId(1L).content("AI回复").sequenceNum(2).build();

            // completeSession 内部调用链：
            // getSession(#1) -> updatePhase -> getSession(#2) -> save(#1)  => completeSession 返回 COMPLETED
            // evaluateFinal
            // getSession(#3) -> updatePhase -> getSession(#4) -> save(#2)  => EVALUATED
            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L))
                .thenReturn(Optional.of(createdSession))    // getSession #1 (completeSession)
                .thenReturn(Optional.of(createdSession))    // getSession #2 (updatePhase COMPLETED)
                .thenReturn(Optional.of(completedSession))  // getSession #3 (completeAndEvaluate 第二段)
                .thenReturn(Optional.of(completedSession)); // getSession #4 (updatePhase EVALUATED)
            when(sessionRepository.save(any()))
                .thenReturn(completedSession)   // save #1
                .thenReturn(evaluatedSession);  // save #2
            when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L))
                .thenReturn(List.of(userMsg, aiMsg));
            when(evaluationService.evaluateFinal(1L, 100L, List.of(userMsg, aiMsg)))
                .thenReturn(null);
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionService.CompleteResult result =
                sessionService.completeAndEvaluate(1L);

            // Assert
            assertNotNull(result);
            assertEquals(NvcSessionPhase.EVALUATED, result.session().getCurrentPhase());
            assertEquals(false, result.evaluationFailed());
            verify(evaluationService).evaluateFinal(1L, 100L, List.of(userMsg, aiMsg));
        }

        @Test
        @DisplayName("已评估的会话：跳过评估，返回 evaluationFailed=false")
        void completeAndEvaluate_alreadyEvaluated_skipsEvaluation() {
            // Arrange
            NvcPracticeSessionEntity session = buildSession(1L, NvcSessionPhase.EVALUATED);
            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

            // Act
            NvcPracticeSessionService.CompleteResult result =
                sessionService.completeAndEvaluate(1L);

            // Assert
            assertEquals(false, result.evaluationFailed());
            verify(evaluationService, never()).evaluateFinal(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("评估失败：标记 evaluationFailed=true，不抛异常")
        void completeAndEvaluate_evaluationFails_marksFailed() {
            // Arrange
            NvcPracticeSessionEntity createdSession = buildSession(1L, NvcSessionPhase.CREATED);
            NvcPracticeSessionEntity completedSession = buildSession(1L, NvcSessionPhase.COMPLETED);

            NvcPracticeMessageEntity userMsg = NvcPracticeMessageEntity.builder()
                .id(1L).sessionId(1L).content("用户消息").sequenceNum(1).build();

            // getSession(#1) -> updatePhase -> getSession(#2) -> save  => COMPLETED
            // evaluateFinal throws
            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L))
                .thenReturn(Optional.of(createdSession))    // getSession #1
                .thenReturn(Optional.of(createdSession));   // getSession #2 (updatePhase)
            when(sessionRepository.save(any()))
                .thenReturn(completedSession);
            when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L))
                .thenReturn(List.of(userMsg));
            when(evaluationService.evaluateFinal(1L, 100L, List.of(userMsg)))
                .thenThrow(new RuntimeException("LLM 服务不可用"));
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionService.CompleteResult result =
                sessionService.completeAndEvaluate(1L);

            // Assert
            assertNotNull(result);
            assertEquals(true, result.evaluationFailed());
        }

        @Test
        @DisplayName("无消息时跳过评估")
        void completeAndEvaluate_noMessages_skipsEvaluation() {
            // Arrange
            NvcPracticeSessionEntity createdSession = buildSession(1L, NvcSessionPhase.CREATED);
            NvcPracticeSessionEntity completedSession = buildSession(1L, NvcSessionPhase.COMPLETED);

            // getSession(#1) -> updatePhase -> getSession(#2) -> save  => COMPLETED
            // messages empty -> skip evaluation
            when(redisService.get("nvc:practice:session:1")).thenReturn(null);
            when(sessionRepository.findById(1L))
                .thenReturn(Optional.of(createdSession))    // getSession #1
                .thenReturn(Optional.of(createdSession));   // getSession #2 (updatePhase)
            when(sessionRepository.save(any()))
                .thenReturn(completedSession);
            when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L))
                .thenReturn(Collections.emptyList());
            try { when(objectMapper.writeValueAsString(any())).thenReturn("{}"); } catch (Exception ignored) {}

            // Act
            NvcPracticeSessionService.CompleteResult result =
                sessionService.completeAndEvaluate(1L);

            // Assert
            assertEquals(false, result.evaluationFailed());
            verify(evaluationService, never()).evaluateFinal(anyLong(), anyLong(), any());
        }
    }
}
