package nvc.guide.modules.nvcpractice.fallback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.dto.NvcEvaluationResult;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationType;
import nvc.guide.modules.nvcpractice.repository.NvcEvaluationRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 评估降级服务
 *
 * <p>LLM 评估异常时，用关键词匹配给出粗略评分。
 * 降级评估结果标记 degraded=true，服务恢复后可重新评估。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EvaluationFallbackService {

    private final KeywordScorer keywordScorer;
    private final NvcEvaluationRepository evaluationRepository;

    /**
     * 关键词匹配评估
     *
     * @param userMessage 用户消息
     * @param currentStep 当前步骤
     * @return 降级评估结果
     */
    public NvcEvaluationResult evaluateByKeyWords(String userMessage, String currentStep) {
        Map<String, Double> scores = keywordScorer.score(userMessage, currentStep);

        log.info("[EvalFallback] Keyword evaluation: obs={}, feel={}, need={}, req={}",
            scores.get("observation"), scores.get("feeling"),
            scores.get("need"), scores.get("request"));

        return new NvcEvaluationResult(
            scores.get("observation").intValue(),
            scores.get("feeling").intValue(),
            scores.get("need").intValue(),
            scores.get("request").intValue(),
            null,  // empathyScore
            (int) Math.round(
                (scores.get("observation") + scores.get("feeling")
                + scores.get("need") + scores.get("request")) / 4),
            "【降级评估】此评分为关键词匹配生成，仅供参考",
            "【降级评估】此评分为关键词匹配生成，仅供参考",
            "【降级评估】此评分为关键词匹配生成，仅供参考",
            "【降级评估】此评分为关键词匹配生成，仅供参考",
            null,  // empathyDetail
            "降级评估无法分析 strengths",
            "降级评估无法分析 improvements",
            null,  // referenceExpressions
            "【降级评估】此评分为关键词匹配生成，仅供参考，服务恢复后可重新评估"
        );
    }

    /**
     * 标记为降级评估
     *
     * @param entity 评估实体
     */
    public void markAsDegraded(NvcEvaluationEntity entity) {
        entity.setDegraded(true);
        evaluationRepository.save(entity);
        log.info("[EvalFallback] Marked evaluation as degraded: id={}", entity.getId());
    }
}
