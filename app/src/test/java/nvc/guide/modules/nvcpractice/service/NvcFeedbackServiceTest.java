package nvc.guide.modules.nvcpractice.service;

import nvc.guide.modules.nvcpractice.dto.FeedbackStatsResponse;
import nvc.guide.modules.nvcpractice.dto.SubmitFeedbackRequest;
import nvc.guide.modules.nvcpractice.model.*;
import nvc.guide.modules.nvcpractice.repository.NvcFeedbackRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcFeedbackService 测试")
class NvcFeedbackServiceTest {

    @Mock
    private NvcFeedbackRepository feedbackRepository;

    private NvcFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new NvcFeedbackService(feedbackRepository);
    }

    private NvcFeedbackEntity buildFeedback(Long id, Long userId, int rating) {
        return NvcFeedbackEntity.builder()
            .id(id)
            .userId(userId)
            .sessionId(1L)
            .messageId(100L)
            .messageSource(NvcFeedbackSource.PRACTICE)
            .agentScene(NvcAgentScene.DIALOGUE_GUIDE)
            .rating(rating)
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Nested
    @DisplayName("submitFeedback()")
    class SubmitFeedbackTests {

        @Test
        @DisplayName("首次提交反馈创建新记录")
        void createsNewFeedback() {
            when(feedbackRepository.findBySessionIdAndMessageIdAndMessageSource(
                any(), any(), any())).thenReturn(Optional.empty());
            when(feedbackRepository.save(any())).thenAnswer(i -> {
                NvcFeedbackEntity e = i.getArgument(0);
                e.setId(1L);
                return e;
            });

            SubmitFeedbackRequest request = new SubmitFeedbackRequest(
                1L, 100L, NvcFeedbackSource.PRACTICE,
                NvcAgentScene.DIALOGUE_GUIDE, 5, null);

            NvcFeedbackEntity result = service.submitFeedback(1L, request);

            assertNotNull(result);
            assertEquals(5, result.getRating());
            verify(feedbackRepository).save(any());
        }

        @Test
        @DisplayName("重复提交更新已有反馈")
        void updatesExistingFeedback() {
            NvcFeedbackEntity existing = buildFeedback(1L, 1L, 1);
            when(feedbackRepository.findBySessionIdAndMessageIdAndMessageSource(
                1L, 100L, NvcFeedbackSource.PRACTICE))
                .thenReturn(Optional.of(existing));
            when(feedbackRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            SubmitFeedbackRequest request = new SubmitFeedbackRequest(
                1L, 100L, NvcFeedbackSource.PRACTICE,
                NvcAgentScene.DIALOGUE_GUIDE, 5, "改好评了");

            NvcFeedbackEntity result = service.submitFeedback(1L, request);

            assertEquals(5, result.getRating());
            assertEquals("改好评了", result.getComment());
            verify(feedbackRepository).save(existing);
        }
    }

    @Nested
    @DisplayName("getFeedbackStats()")
    class GetStatsTests {

        @Test
        @DisplayName("正确计算好评率")
        void calculatesThumbsUpRate() {
            LocalDateTime from = LocalDateTime.now().minusDays(7);
            LocalDateTime to = LocalDateTime.now();

            when(feedbackRepository.countTotalAndThumbsUp(from, to))
                .thenReturn(new Object[]{10L, 7L});
            when(feedbackRepository.countByAgentScene(from, to))
                .thenReturn(List.of(
                    new Object[]{"DIALOGUE_GUIDE", 5L, 4L},
                    new Object[]{"NVC_KNOWLEDGE_ADVISOR", 5L, 3L}
                ));

            FeedbackStatsResponse stats = service.getFeedbackStats(from, to);

            assertEquals(10, stats.totalFeedbackCount());
            assertEquals(0.7, stats.overallThumbsUpRate(), 0.01);
            assertEquals(2, stats.perSceneStats().size());
        }

        @Test
        @DisplayName("无反馈时返回零值")
        void returnsZerosWhenNoFeedback() {
            LocalDateTime from = LocalDateTime.now().minusDays(7);
            LocalDateTime to = LocalDateTime.now();

            when(feedbackRepository.countTotalAndThumbsUp(from, to))
                .thenReturn(new Object[]{0L, null});
            when(feedbackRepository.countByAgentScene(from, to))
                .thenReturn(List.of());

            FeedbackStatsResponse stats = service.getFeedbackStats(from, to);

            assertEquals(0, stats.totalFeedbackCount());
            assertEquals(0.0, stats.overallThumbsUpRate());
        }
    }

    @Nested
    @DisplayName("getRecentNegativeFeedback()")
    class GetNegativeTests {

        @Test
        @DisplayName("返回指定数量的差评")
        void returnsLimitedNegativeFeedback() {
            // Mock 返回限制数量的结果（模拟数据库分页）
            List<NvcFeedbackEntity> negatives = List.of(
                buildFeedback(1L, 1L, 1),
                buildFeedback(2L, 2L, 1)
            );
            when(feedbackRepository.findByRatingOrderByCreatedAtDesc(eq(1), any()))
                .thenReturn(negatives);

            List<NvcFeedbackEntity> result = service.getRecentNegativeFeedback(2);

            assertEquals(2, result.size());
        }
    }
}
