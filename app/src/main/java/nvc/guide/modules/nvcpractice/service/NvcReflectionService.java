package nvc.guide.modules.nvcpractice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.*;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeReflectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 反思服务
 * 负责：存储反思结果、查询历史反思、格式化上下文记忆
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcReflectionService {

    private final NvcPracticeReflectionRepository reflectionRepository;
    private final NvcAgentOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    /**
     * 执行反思并存储结果
     * 在练习完成后调用
     *
     * <p>注意：LLM 调用在事务外执行，避免占用 DB 连接。
     * 只有数据库操作在事务内执行。
     */
    public NvcPracticeReflectionEntity reflectAndSave(PracticeContext context) {
        try {
            // LLM 调用在事务外
            String reflectJson = orchestrator.reflect(context);
            // 数据库操作在事务内
            return parseAndSaveInTransaction(context, reflectJson);
        } catch (Exception e) {
            log.error("Reflection failed for session {}: {}",
                context.getSession().getId(), e.getMessage());
            // 反思失败不阻塞主流程，保存默认结果
            return saveDefaultReflectionInTransaction(context);
        }
    }

    @Transactional
    protected NvcPracticeReflectionEntity parseAndSaveInTransaction(PracticeContext context, String reflectJson) {
        return parseAndSave(context, reflectJson);
    }

    @Transactional
    protected NvcPracticeReflectionEntity saveDefaultReflectionInTransaction(PracticeContext context) {
        return saveDefaultReflection(context);
    }

    /**
     * 获取用户最近的反思记忆（用于上下文注入）
     * @param limit 最多返回几条
     */
    @Transactional(readOnly = true)
    public String getRecentMemory(Long userId, int limit) {
        List<NvcPracticeReflectionEntity> reflections =
            reflectionRepository.findRecentByUserId(userId);

        if (reflections.isEmpty()) {
            return null;
        }

        List<String> memoryParts = new ArrayList<>();
        int count = 0;
        for (NvcPracticeReflectionEntity r : reflections) {
            if (count >= limit) break;
            memoryParts.add(formatReflectionMemory(r));
            count++;
        }

        return String.join("\n---\n", memoryParts);
    }

    /**
     * 获取用户最新反思（用于自适应难度）
     */
    @Transactional(readOnly = true)
    public NvcPracticeReflectionEntity getLatestReflection(Long userId) {
        return reflectionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
            .orElse(null);
    }

    /**
     * 解析反思 JSON 并保存
     */
    private NvcPracticeReflectionEntity parseAndSave(
            PracticeContext context, String reflectJson) {
        try {
            JsonNode root = objectMapper.readTree(reflectJson);

            List<String> weakElements = new ArrayList<>();
            if (root.has("weak_elements")) {
                for (JsonNode node : root.get("weak_elements")) {
                    weakElements.add(node.asText());
                }
            }

            String suggestedDiff = root.has("suggested_difficulty")
                ? root.get("suggested_difficulty").asText() : "MEDIUM";
            NvcDifficulty difficulty;
            try {
                difficulty = NvcDifficulty.valueOf(suggestedDiff);
            } catch (IllegalArgumentException e) {
                difficulty = NvcDifficulty.MEDIUM;
            }

            NvcEvaluationEntity eval = context.getLastEvaluation();

            NvcPracticeReflectionEntity entity = NvcPracticeReflectionEntity.builder()
                .userId(context.getSession().getUserId())
                .sessionId(context.getSession().getId())
                .weakElements(objectMapper.writeValueAsString(weakElements))
                .suggestedDifficulty(difficulty)
                .suggestedScenarioType(root.has("suggested_scenario_type")
                    ? root.get("suggested_scenario_type").asText() : null)
                .strategyNote(root.has("strategy_note")
                    ? root.get("strategy_note").asText() : null)
                .observationScore(eval != null ? eval.getObservationScore() : null)
                .feelingScore(eval != null ? eval.getFeelingScore() : null)
                .needScore(eval != null ? eval.getNeedScore() : null)
                .requestScore(eval != null ? eval.getRequestScore() : null)
                .overallScore(eval != null ? eval.getOverallScore() : null)
                .build();

            NvcPracticeReflectionEntity saved = reflectionRepository.save(entity);
            log.info("Reflection saved: sessionId={}, weakElements={}, suggestedDiff={}",
                context.getSession().getId(), weakElements, difficulty);
            return saved;

        } catch (Exception e) {
            log.error("Failed to parse reflection JSON: {}", e.getMessage());
            return saveDefaultReflection(context);
        }
    }

    /**
     * 保存默认反思结果（解析失败时）
     */
    private NvcPracticeReflectionEntity saveDefaultReflection(PracticeContext context) {
        NvcEvaluationEntity eval = context.getLastEvaluation();
        NvcPracticeReflectionEntity entity = NvcPracticeReflectionEntity.builder()
            .userId(context.getSession().getUserId())
            .sessionId(context.getSession().getId())
            .weakElements("[]")
            .suggestedDifficulty(NvcDifficulty.MEDIUM)
            .strategyNote("反思数据不可用，使用默认策略")
            .observationScore(eval != null ? eval.getObservationScore() : null)
            .feelingScore(eval != null ? eval.getFeelingScore() : null)
            .needScore(eval != null ? eval.getNeedScore() : null)
            .requestScore(eval != null ? eval.getRequestScore() : null)
            .overallScore(eval != null ? eval.getOverallScore() : null)
            .build();
        return reflectionRepository.save(entity);
    }

    /**
     * 格式化单条反思为记忆文本
     */
    private String formatReflectionMemory(NvcPracticeReflectionEntity r) {
        StringBuilder sb = new StringBuilder();
        sb.append("练习反思（").append(r.getCreatedAt().toLocalDate()).append("）：\n");

        if (r.getObservationScore() != null) {
            sb.append("  评分：观察").append(r.getObservationScore())
              .append(" 感受").append(r.getFeelingScore())
              .append(" 需求").append(r.getNeedScore())
              .append(" 请求").append(r.getRequestScore())
              .append(" 综合").append(r.getOverallScore()).append("\n");
        }

        if (r.getWeakElements() != null && !r.getWeakElements().equals("[]")) {
            sb.append("  薄弱环节：").append(r.getWeakElements()).append("\n");
        }

        if (r.getStrategyNote() != null) {
            sb.append("  策略建议：").append(r.getStrategyNote());
        }

        return sb.toString();
    }
}
