package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.common.ai.StructuredOutputInvoker;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcpractice.dto.NvcEvaluationResult;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationType;
import nvc.guide.modules.nvcpractice.model.NvcMessageRole;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.repository.NvcEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcEvaluationService {

    private final LlmProviderRegistry llmProviderRegistry;
    private final NvcEvaluationRepository evaluationRepository;
    private final StructuredOutputInvoker structuredOutputInvoker;

    // ==================== 实时评估 ====================

    /**
     * 实时评估：评估用户单轮回复的 NVC 表达质量
     */
    public NvcEvaluationEntity evaluateRealtime(Long sessionId, Long userId,
                                                 String userMessage, String aiContext,
                                                 NvcPracticeStep currentStep) {
        String systemPrompt = loadPrompt("prompts/nvc-evaluation-system.st");
        String userPrompt = buildRealtimeUserPrompt(userMessage, aiContext, currentStep);

        NvcEvaluationResult result = invokeEvaluation(systemPrompt, userPrompt);

        NvcEvaluationEntity entity = buildEvaluationEntity(
            sessionId, userId, result, NvcEvaluationType.REALTIME);
        NvcEvaluationEntity saved = evaluationRepository.save(entity);
        log.info("Realtime evaluation saved: sessionId={}, overallScore={}",
            sessionId, result.overallScore());
        return saved;
    }

    // ==================== 最终评估 ====================

    /**
     * 最终评估：对整个练习对话进行综合评估
     */
    public NvcEvaluationEntity evaluateFinal(Long sessionId, Long userId,
                                              List<NvcPracticeMessageEntity> messages) {
        String systemPrompt = loadPrompt("prompts/nvc-evaluation-summary-system.st");
        String userPrompt = buildFinalUserPrompt(messages);

        NvcEvaluationResult result = invokeEvaluation(systemPrompt, userPrompt);

        NvcEvaluationEntity entity = buildEvaluationEntity(
            sessionId, userId, result, NvcEvaluationType.FINAL);
        NvcEvaluationEntity saved = evaluationRepository.save(entity);
        log.info("Final evaluation saved: sessionId={}, overallScore={}",
            sessionId, result.overallScore());
        return saved;
    }

    // ==================== 查询 ====================

    /**
     * 获取会话的最新实时评估
     */
    public NvcEvaluationEntity getLatestRealtimeEvaluation(Long sessionId) {
        return evaluationRepository
            .findFirstBySessionIdAndEvaluationTypeOrderByCreatedAtDesc(sessionId, NvcEvaluationType.REALTIME)
            .orElse(null);
    }

    /**
     * 获取会话的最终评估
     */
    public NvcEvaluationEntity getFinalEvaluation(Long sessionId) {
        return evaluationRepository
            .findFirstBySessionIdAndEvaluationTypeOrderByCreatedAtDesc(sessionId, NvcEvaluationType.FINAL)
            .orElse(null);
    }

    // ==================== 内部方法 ====================

    /**
     * 调用 LLM 进行结构化评估
     */
    private NvcEvaluationResult invokeEvaluation(String systemPrompt, String userPrompt) {
        ChatClient chatClient = llmProviderRegistry.getPlainChatClient(null);
        BeanOutputConverter<NvcEvaluationResult> converter =
            new BeanOutputConverter<>(NvcEvaluationResult.class);

        return structuredOutputInvoker.invoke(
            chatClient,
            systemPrompt,
            userPrompt,
            converter,
            ErrorCode.NVC_EVALUATION_FAILED,
            "NVC评估失败: ",
            "NvcEvaluation",
            log
        );
    }

    /**
     * 构建实时评估的用户提示词
     */
    private String buildRealtimeUserPrompt(String userMessage, String aiContext,
                                            NvcPracticeStep currentStep) {
        StringBuilder conversation = new StringBuilder();
        if (aiContext != null && !aiContext.isBlank()) {
            conversation.append("[AI 上一轮回复]\n").append(aiContext).append("\n\n");
        }
        conversation.append("[用户最新表达]\n").append(userMessage);

        if (currentStep != null) {
            conversation.append("\n\n[当前练习步骤]\n").append(currentStep);
        }

        return loadPrompt("prompts/nvc-evaluation-user.st")
            .replace("{conversation}", conversation.toString())
            .replace("{userProfile}", "（暂无）");
    }

    /**
     * 构建最终评估的用户提示词
     */
    private String buildFinalUserPrompt(List<NvcPracticeMessageEntity> messages) {
        StringBuilder dialogue = new StringBuilder();
        for (NvcPracticeMessageEntity msg : messages) {
            String role = msg.getRole() == NvcMessageRole.USER ? "用户" : "AI";
            dialogue.append(String.format("[%s] %s\n", role, msg.getContent()));
        }

        return loadPrompt("prompts/nvc-evaluation-summary-user.st")
            .replace("{batchResults}", "（首轮综合评估，无各轮实时评估数据）")
            .replace("{conversation}", dialogue.toString());
    }

    /**
     * 构建评估实体
     */
    private NvcEvaluationEntity buildEvaluationEntity(Long sessionId, Long userId,
                                                        NvcEvaluationResult result,
                                                        NvcEvaluationType type) {
        return NvcEvaluationEntity.builder()
            .sessionId(sessionId)
            .userId(userId)
            .observationScore(result.observationScore())
            .feelingScore(result.feelingScore())
            .needScore(result.needScore())
            .requestScore(result.requestScore())
            .empathyScore(result.empathyScore())
            .overallScore(result.overallScore())
            .observationDetail(result.observationDetail())
            .feelingDetail(result.feelingDetail())
            .needDetail(result.needDetail())
            .requestDetail(result.requestDetail())
            .empathyDetail(result.empathyDetail())
            .strengths(result.strengths())
            .improvements(result.improvements())
            .referenceExpressions(result.referenceExpressions())
            .summary(result.summary())
            .evaluationType(type)
            .build();
    }

    /**
     * 加载 prompt 模板文件
     */
    private String loadPrompt(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load prompt: {}", path, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                "评估提示词加载失败: " + path);
        }
    }
}
