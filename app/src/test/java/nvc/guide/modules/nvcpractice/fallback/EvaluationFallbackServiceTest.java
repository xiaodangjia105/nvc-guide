package nvc.guide.modules.nvcpractice.fallback;

import nvc.guide.modules.nvcpractice.dto.NvcEvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EvaluationFallbackService 评估降级测试")
class EvaluationFallbackServiceTest {

    private final KeywordScorer keywordScorer = new KeywordScorer();

    @Test
    @DisplayName("含评判词的消息应降低观察分")
    void score_withJudgmentWords_shouldReduceObservationScore() {
        Map<String, Double> scores = keywordScorer.score("你总是这样不尊重人，太自私了", "OBSERVE");

        assertTrue(scores.get("observation") < 5, "观察分应低于基础分5");
    }

    @Test
    @DisplayName("含情绪词的消息应提高感受分")
    void score_withEmotionWords_shouldIncreaseFeelingScore() {
        Map<String, Double> scores = keywordScorer.score("我感到失落和焦虑，内心很不安", "FEELING");

        assertTrue(scores.get("feeling") >= 6, "感受分应高于基础分5");
    }

    @Test
    @DisplayName("含需求词的消息应提高需求分")
    void score_withNeedWords_shouldIncreaseNeedScore() {
        Map<String, Double> scores = keywordScorer.score("我需要被尊重，渴望公平的对待", "NEED");

        assertTrue(scores.get("need") >= 6, "需求分应高于基础分5");
    }

    @Test
    @DisplayName("含正向请求的消息应提高请求分")
    void score_withPositiveRequest_shouldIncreaseRequestScore() {
        Map<String, Double> scores = keywordScorer.score("你能不能具体说一下？比如哪些地方需要改进？", "REQUEST");

        assertTrue(scores.get("request") >= 6, "请求分应高于基础分5");
    }

    @Test
    @DisplayName("分数应钳制在 1-10 范围内")
    void score_shouldClampBetween1And10() {
        // 极端负向
        Map<String, Double> lowScores = keywordScorer.score("总是从来每次永远自私懒不负责任", "OBSERVE");
        assertTrue(lowScores.get("observation") >= 1, "分数不应低于1");

        // 极端正向
        Map<String, Double> highScores = keywordScorer.score("看到听到注意到发现观察到记录到具体来说实际上数据显示", "OBSERVE");
        assertTrue(highScores.get("observation") <= 10, "分数不应高于10");
    }

    @Test
    @DisplayName("空消息应返回基础分")
    void score_emptyMessage_shouldReturnBaseScore() {
        Map<String, Double> scores = keywordScorer.score("", "OBSERVE");

        assertEquals(5.0, scores.get("observation"));
        assertEquals(5.0, scores.get("feeling"));
        assertEquals(5.0, scores.get("need"));
        assertEquals(5.0, scores.get("request"));
    }
}
