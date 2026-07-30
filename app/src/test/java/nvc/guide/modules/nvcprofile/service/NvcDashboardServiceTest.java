package nvc.guide.modules.nvcprofile.service;

import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeSessionRepository;
import nvc.guide.modules.nvcprofile.repository.NvcUserAbilityScoreRepository;
import nvc.guide.modules.nvcprofile.repository.NvcUserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcDashboardService 测试")
class NvcDashboardServiceTest {

    @Mock
    private NvcPracticeSessionRepository sessionRepository;
    @Mock
    private NvcUserAbilityScoreRepository abilityScoreRepository;
    @Mock
    private NvcUserProfileRepository profileRepository;

    private NvcDashboardService service;

    @BeforeEach
    void setUp() {
        service = new NvcDashboardService(sessionRepository, abilityScoreRepository, profileRepository);
    }

    @Nested
    @DisplayName("getUserStats()")
    class GetUserStatsTests {

        @Test
        @DisplayName("正确聚合总练习次数、已完成次数、评分记录数")
        void aggregatesAllStats() {
            when(sessionRepository.countByUserId(1L)).thenReturn(10L);
            when(sessionRepository.countByUserIdAndCurrentPhase(1L, NvcSessionPhase.COMPLETED)).thenReturn(7L);
            when(abilityScoreRepository.countByUserId(1L)).thenReturn(5L);

            Map<String, Object> stats = service.getUserStats(1L);

            assertEquals(10L, stats.get("totalSessions"));
            assertEquals(7L, stats.get("completedSessions"));
            assertEquals(5L, stats.get("totalScores"));
        }

        @Test
        @DisplayName("无数据时返回全零")
        void returnsZerosWhenNoData() {
            when(sessionRepository.countByUserId(999L)).thenReturn(0L);
            when(sessionRepository.countByUserIdAndCurrentPhase(999L, NvcSessionPhase.COMPLETED)).thenReturn(0L);
            when(abilityScoreRepository.countByUserId(999L)).thenReturn(0L);

            Map<String, Object> stats = service.getUserStats(999L);

            assertEquals(0L, stats.get("totalSessions"));
            assertEquals(0L, stats.get("completedSessions"));
            assertEquals(0L, stats.get("totalScores"));
        }

        @Test
        @DisplayName("验证调用了正确的 repository 方法")
        void delegatesToCorrectRepositories() {
            when(sessionRepository.countByUserId(1L)).thenReturn(3L);
            when(sessionRepository.countByUserIdAndCurrentPhase(1L, NvcSessionPhase.COMPLETED)).thenReturn(2L);
            when(abilityScoreRepository.countByUserId(1L)).thenReturn(1L);

            service.getUserStats(1L);

            verify(sessionRepository).countByUserId(1L);
            verify(sessionRepository).countByUserIdAndCurrentPhase(1L, NvcSessionPhase.COMPLETED);
            verify(abilityScoreRepository).countByUserId(1L);
        }
    }
}
