package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.test.IntegrationTestBase;
import nvc.guide.modules.nvcpractice.dto.CreatePracticeSessionRequest;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * NvcPracticeSessionService 集成测试
 * 使用 Testcontainers 提供真实 PostgreSQL + Redis
 */
@DisplayName("NvcPracticeSessionService 集成测试")
@Tag("integration")
@Transactional
class NvcPracticeSessionServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private NvcPracticeSessionService sessionService;

    @Test
    @DisplayName("完整会话生命周期：创建 -> 进行中 -> 暂停 -> 进行中 -> 完成 -> 评估")
    void fullSessionLifecycle() {
        // 1. 创建会话
        CreatePracticeSessionRequest request = new CreatePracticeSessionRequest(
            NvcPracticeMode.FREE_DIALOG, null, NvcDifficulty.MEDIUM);
        NvcPracticeSessionEntity session = sessionService.createSession(100L, request);

        assertNotNull(session.getId());
        assertEquals(NvcSessionPhase.CREATED, session.getCurrentPhase());
        assertEquals(NvcPracticeMode.FREE_DIALOG, session.getPracticeMode());
        assertEquals(NvcDifficulty.MEDIUM, session.getDifficulty());

        // 2. CREATED -> IN_PROGRESS
        session = sessionService.updatePhase(session.getId(), NvcSessionPhase.IN_PROGRESS);
        assertEquals(NvcSessionPhase.IN_PROGRESS, session.getCurrentPhase());
        assertNotNull(session.getStartedAt());

        // 3. IN_PROGRESS -> PAUSED
        session = sessionService.updatePhase(session.getId(), NvcSessionPhase.PAUSED);
        assertEquals(NvcSessionPhase.PAUSED, session.getCurrentPhase());

        // 4. PAUSED -> IN_PROGRESS (恢复)
        session = sessionService.updatePhase(session.getId(), NvcSessionPhase.IN_PROGRESS);
        assertEquals(NvcSessionPhase.IN_PROGRESS, session.getCurrentPhase());

        // 5. IN_PROGRESS -> COMPLETED
        session = sessionService.completeSession(session.getId());
        assertEquals(NvcSessionPhase.COMPLETED, session.getCurrentPhase());
        assertNotNull(session.getCompletedAt());
    }

    @Test
    @DisplayName("结构化四步模式：初始步骤为 OBSERVE，可更新步骤")
    void structuredFourStepMode_initialStepAndAdvance() {
        CreatePracticeSessionRequest request = new CreatePracticeSessionRequest(
            NvcPracticeMode.STRUCTURED_FOUR_STEP, null, NvcDifficulty.HARD);
        NvcPracticeSessionEntity session = sessionService.createSession(100L, request);

        assertEquals(NvcPracticeStep.OBSERVE, session.getCurrentStep());

        // 更新步骤
        session = sessionService.updateStep(session.getId(), NvcPracticeStep.FEELING);
        assertEquals(NvcPracticeStep.FEELING, session.getCurrentStep());

        session = sessionService.updateStep(session.getId(), NvcPracticeStep.NEED);
        assertEquals(NvcPracticeStep.NEED, session.getCurrentStep());
    }

    @Test
    @DisplayName("getSession 缓存一致性：DB 更新后 getSession 返回最新数据")
    void getSession_cacheConsistency() {
        CreatePracticeSessionRequest request = new CreatePracticeSessionRequest(
            NvcPracticeMode.FREE_DIALOG, null, null);
        NvcPracticeSessionEntity created = sessionService.createSession(100L, request);

        // 更新阶段
        sessionService.updatePhase(created.getId(), NvcSessionPhase.IN_PROGRESS);

        // getSession 应返回最新状态
        NvcPracticeSessionEntity fetched = sessionService.getSession(created.getId());
        assertEquals(NvcSessionPhase.IN_PROGRESS, fetched.getCurrentPhase());
    }

    @Test
    @DisplayName("getUserSessions 按阶段过滤")
    void getUserSessions_filterByPhase() {
        CreatePracticeSessionRequest request = new CreatePracticeSessionRequest(
            NvcPracticeMode.FREE_DIALOG, null, null);

        // 创建两个会话，一个完成，一个进行中
        NvcPracticeSessionEntity s1 = sessionService.createSession(200L, request);
        NvcPracticeSessionEntity s2 = sessionService.createSession(200L, request);
        sessionService.updatePhase(s1.getId(), NvcSessionPhase.IN_PROGRESS);
        sessionService.completeSession(s2.getId());

        // 按阶段查询
        assertEquals(1, sessionService.getUserSessions(200L, NvcSessionPhase.IN_PROGRESS).size());
        assertEquals(1, sessionService.getUserSessions(200L, NvcSessionPhase.COMPLETED).size());
        assertEquals(2, sessionService.getUserSessions(200L, null).size());
    }

    @Test
    @DisplayName("completeSession 幂等性：重复调用不报错")
    void completeSession_idempotent() {
        CreatePracticeSessionRequest request = new CreatePracticeSessionRequest(
            NvcPracticeMode.FREE_DIALOG, null, null);
        NvcPracticeSessionEntity session = sessionService.createSession(100L, request);

        // 第一次完成
        sessionService.updatePhase(session.getId(), NvcSessionPhase.IN_PROGRESS);
        NvcPracticeSessionEntity completed = sessionService.completeSession(session.getId());
        assertEquals(NvcSessionPhase.COMPLETED, completed.getCurrentPhase());

        // 第二次完成（幂等）
        NvcPracticeSessionEntity again = sessionService.completeSession(session.getId());
        assertEquals(NvcSessionPhase.COMPLETED, again.getCurrentPhase());
    }
}
