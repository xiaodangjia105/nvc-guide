package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.common.ai.StructuredOutputInvoker;
import nvc.guide.modules.nvcpractice.dto.NvcEvaluationResult;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationType;
import nvc.guide.modules.nvcpractice.model.NvcMessageRole;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.repository.NvcEvaluationRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcEvaluationService 测试")
class NvcEvaluationServiceTest {

    @Mock
    private LlmProviderRegistry llmProviderRegistry;
    @Mock
    private NvcEvaluationRepository evaluationRepository;
    @Mock
    private StructuredOutputInvoker structuredOutputInvoker;

    private NvcEvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        evaluationService = new NvcEvaluationService(
            llmProviderRegistry, evaluationRepository, structuredOutputInvoker);
    }

    private NvcEvaluationResult buildResult(int overall) {
        return new NvcEvaluationResult(
            75, 70, 80, 65, 72, overall,
            "观察描述", "感受描述", "需求描述", "请求描述", "共情描述",
            "表达清晰", "可以更具体", "试试这样说...", "综合评价"
        );
    }

    private NvcEvaluationEntity buildEntity(Long id, Long sessionId,
            NvcEvaluationType type, int overall) {
        return NvcEvaluationEntity.builder()
            .id(id)
            .sessionId(sessionId)
            .userId(100L)
            .observationScore(75)
            .feelingScore(70)
            .needScore(80)
            .requestScore(65)
            .empathyScore(72)
            .overallScore(overall)
            .observationDetail("观察描述")
            .feelingDetail("感受描述")
            .needDetail("需求描述")
            .requestDetail("请求描述")
            .empathyDetail("共情描述")
            .strengths("表达清晰")
            .improvements("可以更具体")
            .referenceExpressions("试试这样说...")
            .summary("综合评价")
            .evaluationType(type)
            .createdAt(LocalDateTime.now())
            .build();
    }

    // ========== evaluateRealtime() ==========

    @Nested
    @DisplayName("evaluateRealtime()")
    class EvaluateRealtimeTests {

        @Test
        @DisplayName("构建评估实体并保存")
        void evaluateRealtime_buildsAndSavesEntity() {
            // Arrange
            NvcEvaluationResult result = buildResult(73);
            NvcEvaluationEntity savedEntity = buildEntity(
                1L, 1L, NvcEvaluationType.REALTIME, 73);

            when(structuredOutputInvoker.invoke(
                any(), anyString(), anyString(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(result);
            when(evaluationRepository.save(any())).thenReturn(savedEntity);

            // Act
            NvcEvaluationEntity returned = evaluationService.evaluateRealtime(
                1L, 100L, "我观察到你今天迟到了", "我理解你的感受",
                NvcPracticeStep.OBSERVE);

            // Assert
            assertNotNull(returned);
            assertEquals(73, returned.getOverallScore());
            assertEquals(NvcEvaluationType.REALTIME, returned.getEvaluationType());
            assertEquals(1L, returned.getSessionId());
            assertEquals(100L, returned.getUserId());
            verify(evaluationRepository).save(any());
        }

        @Test
        @DisplayName("currentStep 为 null 时正常评估")
        void evaluateRealtime_nullStep_worksNormally() {
            // Arrange
            NvcEvaluationResult result = buildResult(68);
            NvcEvaluationEntity savedEntity = buildEntity(
                2L, 1L, NvcEvaluationType.REALTIME, 68);

            when(structuredOutputInvoker.invoke(
                any(), anyString(), anyString(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(result);
            when(evaluationRepository.save(any())).thenReturn(savedEntity);

            // Act
            NvcEvaluationEntity returned = evaluationService.evaluateRealtime(
                1L, 100L, "我觉得有点难过", null, null);

            // Assert
            assertNotNull(returned);
            assertEquals(68, returned.getOverallScore());
            verify(evaluationRepository).save(any());
        }

        @Test
        @DisplayName("评估实体包含所有评分字段")
        void evaluateRealtime_entityContainsAllScores() {
            // Arrange
            NvcEvaluationResult result = new NvcEvaluationResult(
                80, 75, 85, 70, 78, 77,
                "观察详情", "感受详情", "需求详情", "请求详情", "共情详情",
                "优势", "改进", "参考表达", "摘要");
            NvcEvaluationEntity savedEntity = NvcEvaluationEntity.builder()
                .id(3L).sessionId(2L).userId(200L)
                .observationScore(80).feelingScore(75).needScore(85)
                .requestScore(70).empathyScore(78).overallScore(77)
                .evaluationType(NvcEvaluationType.REALTIME)
                .createdAt(LocalDateTime.now())
                .build();

            when(structuredOutputInvoker.invoke(
                any(), anyString(), anyString(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(result);
            when(evaluationRepository.save(any())).thenReturn(savedEntity);

            // Act
            NvcEvaluationEntity returned = evaluationService.evaluateRealtime(
                2L, 200L, "用户消息", "AI上下文", NvcPracticeStep.FEELING);

            // Assert
            assertNotNull(returned);
            assertEquals(80, returned.getObservationScore());
            assertEquals(75, returned.getFeelingScore());
            assertEquals(85, returned.getNeedScore());
            assertEquals(70, returned.getRequestScore());
            assertEquals(78, returned.getEmpathyScore());
            assertEquals(77, returned.getOverallScore());
        }
    }

    // ========== getLatestRealtimeEvaluation() ==========

    @Nested
    @DisplayName("getLatestRealtimeEvaluation()")
    class GetLatestRealtimeEvaluationTests {

        @Test
        @DisplayName("存在实时评估时返回最新一条")
        void found_returnsLatestEvaluation() {
            // Arrange
            NvcEvaluationEntity entity = buildEntity(
                5L, 1L, NvcEvaluationType.REALTIME, 78);
            when(evaluationRepository
                .findFirstBySessionIdAndEvaluationTypeOrderByCreatedAtDesc(
                    1L, NvcEvaluationType.REALTIME))
                .thenReturn(Optional.of(entity));

            // Act
            NvcEvaluationEntity result =
                evaluationService.getLatestRealtimeEvaluation(1L);

            // Assert
            assertNotNull(result);
            assertEquals(78, result.getOverallScore());
            assertEquals(NvcEvaluationType.REALTIME, result.getEvaluationType());
        }

        @Test
        @DisplayName("不存在实时评估时返回 null")
        void notFound_returnsNull() {
            // Arrange
            when(evaluationRepository
                .findFirstBySessionIdAndEvaluationTypeOrderByCreatedAtDesc(
                    99L, NvcEvaluationType.REALTIME))
                .thenReturn(Optional.empty());

            // Act
            NvcEvaluationEntity result =
                evaluationService.getLatestRealtimeEvaluation(99L);

            // Assert
            assertNull(result);
        }
    }

    // ========== getFinalEvaluation() ==========

    @Nested
    @DisplayName("getFinalEvaluation()")
    class GetFinalEvaluationTests {

        @Test
        @DisplayName("存在最终评估时返回")
        void found_returnsFinalEvaluation() {
            // Arrange
            NvcEvaluationEntity entity = buildEntity(
                10L, 1L, NvcEvaluationType.FINAL, 82);
            when(evaluationRepository
                .findFirstBySessionIdAndEvaluationTypeOrderByCreatedAtDesc(
                    1L, NvcEvaluationType.FINAL))
                .thenReturn(Optional.of(entity));

            // Act
            NvcEvaluationEntity result =
                evaluationService.getFinalEvaluation(1L);

            // Assert
            assertNotNull(result);
            assertEquals(82, result.getOverallScore());
            assertEquals(NvcEvaluationType.FINAL, result.getEvaluationType());
        }

        @Test
        @DisplayName("不存在最终评估时返回 null")
        void notFound_returnsNull() {
            // Arrange
            when(evaluationRepository
                .findFirstBySessionIdAndEvaluationTypeOrderByCreatedAtDesc(
                    99L, NvcEvaluationType.FINAL))
                .thenReturn(Optional.empty());

            // Act
            NvcEvaluationEntity result =
                evaluationService.getFinalEvaluation(99L);

            // Assert
            assertNull(result);
        }
    }

    // ========== evaluateFinal() ==========

    @Nested
    @DisplayName("evaluateFinal()")
    class EvaluateFinalTests {

        @Test
        @DisplayName("构建最终评估实体并保存")
        void evaluateFinal_buildsAndSavesEntity() {
            // Arrange
            NvcPracticeMessageEntity userMsg = NvcPracticeMessageEntity.builder()
                .id(1L).sessionId(1L).role(NvcMessageRole.USER)
                .content("我观察到你迟到了").sequenceNum(1).build();
            NvcPracticeMessageEntity aiMsg = NvcPracticeMessageEntity.builder()
                .id(2L).sessionId(1L).role(NvcMessageRole.ASSISTANT)
                .content("谢谢你告诉我").sequenceNum(2).build();

            NvcEvaluationResult result = buildResult(80);
            NvcEvaluationEntity savedEntity = buildEntity(
                10L, 1L, NvcEvaluationType.FINAL, 80);

            when(structuredOutputInvoker.invoke(
                any(), anyString(), anyString(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(result);
            when(evaluationRepository.save(any())).thenReturn(savedEntity);

            // Act
            NvcEvaluationEntity returned = evaluationService.evaluateFinal(
                1L, 100L, List.of(userMsg, aiMsg));

            // Assert
            assertNotNull(returned);
            assertEquals(80, returned.getOverallScore());
            assertEquals(NvcEvaluationType.FINAL, returned.getEvaluationType());
            verify(evaluationRepository).save(any());
        }
    }
}
