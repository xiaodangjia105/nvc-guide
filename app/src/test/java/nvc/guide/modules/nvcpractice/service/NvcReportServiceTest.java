package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.infrastructure.export.PdfExportService;
import nvc.guide.modules.nvcpractice.dto.NvcPracticeReport;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcMessageRole;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcReportService 测试")
class NvcReportServiceTest {

    @Mock
    private NvcPracticeSessionRepository sessionRepository;
    @Mock
    private NvcPracticeMessageRepository messageRepository;
    @Mock
    private NvcEvaluationService evaluationService;
    @Mock
    private PdfExportService pdfExportService;

    private NvcReportService reportService;

    private static final Long SESSION_ID = 1L;
    private static final Long USER_ID = 10L;

    @BeforeEach
    void setUp() {
        reportService = new NvcReportService(
            sessionRepository, messageRepository, evaluationService, pdfExportService);
    }

    // ---- helpers ----

    private NvcPracticeSessionEntity buildSession(NvcSessionPhase phase) {
        return NvcPracticeSessionEntity.builder()
            .id(SESSION_ID)
            .userId(USER_ID)
            .practiceMode(NvcPracticeMode.SCENARIO)
            .difficulty(NvcDifficulty.MEDIUM)
            .currentPhase(phase)
            .startedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .completedAt(phase == NvcSessionPhase.COMPLETED || phase == NvcSessionPhase.EVALUATED
                ? LocalDateTime.of(2026, 1, 1, 10, 30) : null)
            .build();
    }

    private List<NvcPracticeMessageEntity> buildMessages() {
        NvcPracticeMessageEntity userMsg = NvcPracticeMessageEntity.builder()
            .id(1L).sessionId(SESSION_ID).role(NvcMessageRole.USER)
            .content("I feel frustrated when you arrive late.").sequenceNum(0).build();
        NvcPracticeMessageEntity assistantMsg = NvcPracticeMessageEntity.builder()
            .id(2L).sessionId(SESSION_ID).role(NvcMessageRole.ASSISTANT)
            .content("I hear you. Can you tell me more?").sequenceNum(1).build();
        NvcPracticeMessageEntity userMsg2 = NvcPracticeMessageEntity.builder()
            .id(3L).sessionId(SESSION_ID).role(NvcMessageRole.USER)
            .content("I need reliability.").sequenceNum(2).build();
        return List.of(userMsg, assistantMsg, userMsg2);
    }

    private NvcEvaluationEntity buildEvaluation() {
        return NvcEvaluationEntity.builder()
            .id(100L)
            .sessionId(SESSION_ID)
            .userId(USER_ID)
            .observationScore(8)
            .feelingScore(7)
            .needScore(9)
            .requestScore(6)
            .empathyScore(8)
            .overallScore(7)
            .observationDetail("Good observation detail")
            .feelingDetail("Feeling detail")
            .needDetail("Need detail")
            .requestDetail("Request detail")
            .empathyDetail("Empathy detail")
            .strengths("Clear expression")
            .improvements("Be more specific")
            .referenceExpressions("When you..., I feel..., I need...")
            .summary("Good overall practice")
            .build();
    }

    // ---- Test 1 ----

    @Nested
    @DisplayName("generateReport - completed session with existing evaluation")
    class CompletedWithExistingEval {

        @Test
        @DisplayName("returns report with scores from existing final evaluation")
        void returnsReportWithExistingEvaluation() {
            NvcPracticeSessionEntity session = buildSession(NvcSessionPhase.COMPLETED);
            List<NvcPracticeMessageEntity> messages = buildMessages();
            NvcEvaluationEntity eval = buildEvaluation();

            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(messageRepository.findBySessionIdOrderBySequenceNumAsc(SESSION_ID)).thenReturn(messages);
            when(evaluationService.getFinalEvaluation(SESSION_ID)).thenReturn(Optional.of(eval));

            NvcPracticeReport report = reportService.generateReport(SESSION_ID);

            assertNotNull(report);
            assertEquals(SESSION_ID, report.sessionId());
            assertEquals(NvcPracticeMode.SCENARIO, report.practiceMode());
            assertEquals(NvcDifficulty.MEDIUM, report.difficulty());
            assertEquals(2, report.totalRounds());
            assertEquals(8, report.observationScore());
            assertEquals(7, report.feelingScore());
            assertEquals(9, report.needScore());
            assertEquals(6, report.requestScore());
            assertEquals(8, report.empathyScore());
            assertEquals(7, report.overallScore());
            assertEquals("Good observation detail", report.observationDetail());
            assertEquals("Good overall practice", report.summary());

            // evaluateFinal should NOT be called since evaluation already exists
            verify(evaluationService, never()).evaluateFinal(anyLong(), anyLong(), anyList());
        }
    }

    // ---- Test 2 ----

    @Nested
    @DisplayName("generateReport - incomplete session returns default report")
    class IncompleteSession {

        @Test
        @DisplayName("returns default report when session phase is IN_PROGRESS")
        void returnsDefaultReportWhenInProgress() {
            NvcPracticeSessionEntity session = buildSession(NvcSessionPhase.IN_PROGRESS);
            List<NvcPracticeMessageEntity> messages = buildMessages();

            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(messageRepository.findBySessionIdOrderBySequenceNumAsc(SESSION_ID)).thenReturn(messages);

            NvcPracticeReport report = reportService.generateReport(SESSION_ID);

            assertNotNull(report);
            assertEquals(0, report.observationScore());
            assertEquals(0, report.overallScore());
            assertEquals("评估暂未完成", report.observationDetail());
            assertEquals("评估暂未完成，请稍后刷新页面查看完整报告。", report.summary());

            // no evaluation should be triggered
            verify(evaluationService, never()).getFinalEvaluation(anyLong());
            verify(evaluationService, never()).evaluateFinal(anyLong(), anyLong(), anyList());
        }

        @Test
        @DisplayName("returns default report when session phase is CREATED")
        void returnsDefaultReportWhenCreated() {
            NvcPracticeSessionEntity session = buildSession(NvcSessionPhase.CREATED);

            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(messageRepository.findBySessionIdOrderBySequenceNumAsc(SESSION_ID)).thenReturn(List.of());

            NvcPracticeReport report = reportService.generateReport(SESSION_ID);

            assertNotNull(report);
            assertEquals(0, report.observationScore());
            assertEquals("评估暂未完成", report.observationDetail());
        }
    }

    // ---- Test 3 ----

    @Nested
    @DisplayName("generateReport - completed session but no evaluation triggers evaluateFinal")
    class CompletedNoExistingEval {

        @Test
        @DisplayName("calls evaluateFinal and returns report with resulting scores")
        void triggersEvaluateFinalWhenNoExistingEvaluation() {
            NvcPracticeSessionEntity session = buildSession(NvcSessionPhase.COMPLETED);
            List<NvcPracticeMessageEntity> messages = buildMessages();
            NvcEvaluationEntity eval = buildEvaluation();

            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(messageRepository.findBySessionIdOrderBySequenceNumAsc(SESSION_ID)).thenReturn(messages);
            when(evaluationService.getFinalEvaluation(SESSION_ID)).thenReturn(Optional.empty());
            when(evaluationService.evaluateFinal(SESSION_ID, USER_ID, messages)).thenReturn(eval);

            NvcPracticeReport report = reportService.generateReport(SESSION_ID);

            assertNotNull(report);
            assertEquals(8, report.observationScore());
            assertEquals(9, report.needScore());
            assertEquals("Clear expression", report.strengths());

            verify(evaluationService).evaluateFinal(SESSION_ID, USER_ID, messages);
        }
    }

    // ---- Test 4 ----

    @Nested
    @DisplayName("generateReport - evaluateFinal throws returns default report")
    class EvaluateFinalFails {

        @Test
        @DisplayName("returns default report when evaluateFinal throws exception")
        void returnsDefaultReportOnEvaluateFailure() {
            NvcPracticeSessionEntity session = buildSession(NvcSessionPhase.EVALUATED);
            List<NvcPracticeMessageEntity> messages = buildMessages();

            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(messageRepository.findBySessionIdOrderBySequenceNumAsc(SESSION_ID)).thenReturn(messages);
            when(evaluationService.getFinalEvaluation(SESSION_ID)).thenReturn(Optional.empty());
            when(evaluationService.evaluateFinal(SESSION_ID, USER_ID, messages))
                .thenThrow(new RuntimeException("LLM service unavailable"));

            NvcPracticeReport report = reportService.generateReport(SESSION_ID);

            assertNotNull(report);
            assertEquals(0, report.observationScore());
            assertEquals(0, report.overallScore());
            assertEquals("评估暂未完成", report.observationDetail());
            assertEquals("评估暂未完成，请稍后刷新页面查看完整报告。", report.summary());
        }
    }

    // ---- Test 5 ----

    @Nested
    @DisplayName("generateReport - non-existent session throws")
    class SessionNotFound {

        @Test
        @DisplayName("throws BusinessException when session does not exist")
        void throwsWhenSessionNotFound() {
            when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                () -> reportService.generateReport(999L));

            assertEquals(ErrorCode.NVC_SESSION_NOT_FOUND.getCode(), ex.getCode());
            assertEquals("Session not found: 999", ex.getMessage());

            // no further interactions expected
            verify(messageRepository, never()).findBySessionIdOrderBySequenceNumAsc(anyLong());
            verify(evaluationService, never()).getFinalEvaluation(anyLong());
        }
    }
}
