package nvc.guide.modules.nvcpractice.service;

import nvc.guide.modules.nvcpractice.dto.CreatePracticeSessionRequest;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcscenario.service.NvcScenarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NvcPracticeFacadeTest {

    @Mock private NvcPracticeSessionService sessionService;
    @Mock private NvcPracticeDialogueService dialogueService;
    @Mock private NvcStructuredPracticeService structuredPracticeService;
    @Mock private NvcEvaluationService evaluationService;
    @Mock private NvcSummaryService summaryService;
    @Mock private NvcScenarioService scenarioService;
    @Mock private NvcAgentOrchestrator agentOrchestrator;

    @InjectMocks
    private NvcPracticeFacade facade;

    @Test
    void createSession_delegatesToSessionService() {
        NvcPracticeSessionEntity mockSession = NvcPracticeSessionEntity.builder()
            .id(1L)
            .userId(100L)
            .practiceMode(NvcPracticeMode.FREE_DIALOG)
            .currentPhase(NvcSessionPhase.CREATED)
            .build();
        when(sessionService.createSession(eq(100L), any())).thenReturn(mockSession);

        var result = facade.createSession(100L, new CreatePracticeSessionRequest(
            NvcPracticeMode.FREE_DIALOG, null, null));

        assertNotNull(result);
        assertEquals(1L, result.id());
        verify(sessionService).createSession(eq(100L), any());
    }

    @Test
    void getLatestEvaluation_delegatesToEvaluationService() {
        when(evaluationService.getLatestRealtimeEvaluation(1L))
            .thenReturn(java.util.Optional.empty());

        var result = facade.getLatestEvaluation(1L);

        assertNull(result);
        verify(evaluationService).getLatestRealtimeEvaluation(1L);
    }

    @Test
    void getSummary_delegatesToSummaryService() {
        when(summaryService.getSummary(1L)).thenReturn(null);

        var result = facade.getSummary(1L);

        assertNull(result);
        verify(summaryService).getSummary(1L);
    }

    @Test
    void getRecommendations_delegatesToAgentOrchestrator() {
        when(agentOrchestrator.recommendScenarios(100L, 5))
            .thenReturn(java.util.Collections.emptyList());

        var result = facade.getRecommendations(100L, 5);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(agentOrchestrator).recommendScenarios(100L, 5);
    }
}
