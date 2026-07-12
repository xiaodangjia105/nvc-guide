package nvc.guide.modules.nvcvoice.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.common.ai.StructuredOutputInvoker;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcvoice.dto.VoiceEvaluationDetailDTO;
import nvc.guide.modules.nvcvoice.model.NvcVoiceEvaluationEntity;
import nvc.guide.modules.nvcvoice.model.NvcVoiceMessageEntity;
import nvc.guide.modules.nvcvoice.model.NvcVoiceSessionEntity;
import nvc.guide.modules.nvcvoice.repository.NvcVoiceEvaluationRepository;
import nvc.guide.modules.nvcvoice.repository.NvcVoiceMessageRepository;
import nvc.guide.modules.nvcvoice.repository.NvcVoiceSessionRepository;

/**
 * NVC 语音评估服务
 *
 * 基于 NVC 四维度评分：
 * - 观察（Observation）
 * - 感受（Feeling）
 * - 需求（Need）
 * - 请求（Request）
 * - 共情（Empathy）
 * - 流畅度（Fluency）
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcVoiceEvaluationService {

  private final LlmProviderRegistry llmProviderRegistry;
  private final StructuredOutputInvoker structuredOutputInvoker;
  private final NvcVoiceEvaluationRepository evaluationRepository;
  private final NvcVoiceMessageRepository messageRepository;
  private final NvcVoiceSessionRepository sessionRepository;
  private final NvcVoicePromptService promptService;

  /**
   * 生成语音评估（由异步消费者调用）
   */
  public void generateEvaluation(Long sessionId) {
    try {
      log.info("开始生成 NVC 语音评估: sessionId={}", sessionId);

      NvcVoiceSessionEntity session = sessionRepository.findById(sessionId)
          .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId));

      List<NvcVoiceMessageEntity> messages =
          messageRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);

      if (messages.isEmpty()) {
        log.warn("NVC 语音会话无对话记录，生成空评估: sessionId={}", sessionId);
        saveEmptyEvaluation(sessionId);
        return;
      }

      // 构建对话历史文本
      String conversationText = buildConversationText(messages);

      // 调用 LLM 评估
      String provider = session.getLlmProvider();
      ChatClient chatClient = llmProviderRegistry.getChatClientOrDefault(provider);

      BeanOutputConverter<NvcVoiceEvaluationResult> outputConverter =
          new BeanOutputConverter<>(NvcVoiceEvaluationResult.class);

      String evaluationPrompt = buildEvaluationPrompt(conversationText, outputConverter);

      NvcVoiceEvaluationResult evalResult = structuredOutputInvoker.invoke(
          chatClient,
          evaluationPrompt,
          "请对以上对话进行评估",
          outputConverter,
          ErrorCode.NVC_EVALUATION_FAILED,
          "NVC 语音评估失败",
          "NvcVoiceEvaluation",
          log
      );

      if (evalResult == null) {
        log.warn("评估结果为空，使用默认值: sessionId={}", sessionId);
        evalResult = getDefaultEvaluationResult();
      }

      // 保存评估
      saveEvaluation(sessionId, evalResult);

      log.info("NVC 语音评估生成完成: sessionId={}, overallScore={}",
          sessionId, evalResult.overallScore());

    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("NVC 语音评估生成失败: sessionId={}", sessionId, e);
      throw new BusinessException(ErrorCode.NVC_EVALUATION_FAILED,
          "评估生成失败: " + e.getMessage());
    }
  }

  /**
   * 获取评估结果
   */
  public VoiceEvaluationDetailDTO getEvaluation(Long sessionId) {
    NvcVoiceEvaluationEntity evaluation = evaluationRepository.findBySessionId(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
            "评估结果不存在: " + sessionId));

    return toDetailDTO(evaluation);
  }

  // ==================== 内部方法 ====================

  private String buildConversationText(List<NvcVoiceMessageEntity> messages) {
    StringBuilder sb = new StringBuilder();
    for (NvcVoiceMessageEntity msg : messages) {
      if (msg.getUserRecognizedText() != null) {
        sb.append("用户：").append(msg.getUserRecognizedText()).append("\n");
      }
      if (msg.getAiGeneratedText() != null) {
        sb.append("AI：").append(msg.getAiGeneratedText()).append("\n");
      }
    }
    return sb.toString();
  }

  private String buildEvaluationPrompt(
      String conversationText,
      BeanOutputConverter<NvcVoiceEvaluationResult> outputConverter) {
    return """
        你是一位 NVC（非暴力沟通）语音练习评估专家。
        请对以下语音对话进行 NVC 四维度评估。

        【对话内容】
        %s

        【评估维度】
        1. 观察（Observation）：是否客观描述事实，不带评判
        2. 感受（Feeling）：是否清晰表达情感
        3. 需求（Need）：是否识别和表达内在需求
        4. 请求（Request）：是否提出具体、可行的请求
        5. 共情（Empathy）：是否理解对方的感受和需求
        6. 表达流畅度（Fluency）：语音表达是否自然流畅

        %s
        """.formatted(conversationText, outputConverter.getFormat());
  }

  private NvcVoiceEvaluationResult getDefaultEvaluationResult() {
    return new NvcVoiceEvaluationResult(
        60, 60, 60, 60, 60, 60, 60,
        "评估数据不足", "评估数据不足", "评估数据不足", "评估数据不足",
        "对话内容较少，建议多练习",
        "[]", "[]"
    );
  }

  @Transactional
  public void saveEvaluation(Long sessionId, NvcVoiceEvaluationResult result) {
    NvcVoiceEvaluationEntity entity = NvcVoiceEvaluationEntity.builder()
        .sessionId(sessionId)
        .observationScore(result.observationScore())
        .feelingScore(result.feelingScore())
        .needScore(result.needScore())
        .requestScore(result.requestScore())
        .empathyScore(result.empathyScore())
        .overallScore(result.overallScore())
        .fluencyScore(result.fluencyScore())
        .observationDetail(result.observationDetail())
        .feelingDetail(result.feelingDetail())
        .needDetail(result.needDetail())
        .requestDetail(result.requestDetail())
        .overallFeedback(result.overallFeedback())
        .strengthsJson(result.strengthsJson())
        .improvementsJson(result.improvementsJson())
        .build();

    evaluationRepository.save(entity);
  }

  @Transactional
  public void saveEmptyEvaluation(Long sessionId) {
    NvcVoiceEvaluationEntity entity = NvcVoiceEvaluationEntity.builder()
        .sessionId(sessionId)
        .observationScore(0)
        .feelingScore(0)
        .needScore(0)
        .requestScore(0)
        .empathyScore(0)
        .overallScore(0)
        .fluencyScore(0)
        .observationDetail("无对话记录")
        .feelingDetail("无对话记录")
        .needDetail("无对话记录")
        .requestDetail("无对话记录")
        .overallFeedback("会话无对话记录，无法评估")
        .strengthsJson("[]")
        .improvementsJson("[]")
        .build();

    evaluationRepository.save(entity);
  }

  private VoiceEvaluationDetailDTO toDetailDTO(NvcVoiceEvaluationEntity entity) {
    return new VoiceEvaluationDetailDTO(
        entity.getId(),
        entity.getSessionId(),
        entity.getObservationScore(),
        entity.getFeelingScore(),
        entity.getNeedScore(),
        entity.getRequestScore(),
        entity.getEmpathyScore(),
        entity.getOverallScore(),
        entity.getFluencyScore(),
        entity.getObservationDetail(),
        entity.getFeelingDetail(),
        entity.getNeedDetail(),
        entity.getRequestDetail(),
        entity.getOverallFeedback(),
        entity.getStrengthsJson(),
        entity.getImprovementsJson(),
        entity.getCreatedAt()
    );
  }

  // ==================== 内部类型 ====================

  public record NvcVoiceEvaluationResult(
      Integer observationScore,
      Integer feelingScore,
      Integer needScore,
      Integer requestScore,
      Integer empathyScore,
      Integer overallScore,
      Integer fluencyScore,
      String observationDetail,
      String feelingDetail,
      String needDetail,
      String requestDetail,
      String overallFeedback,
      String strengthsJson,
      String improvementsJson
  ) {
    public NvcVoiceEvaluationResult() {
      this(60, 60, 60, 60, 60, 60, 60,
          "评估中", "评估中", "评估中", "评估中", "评估中", "[]", "[]");
    }
  }
}
