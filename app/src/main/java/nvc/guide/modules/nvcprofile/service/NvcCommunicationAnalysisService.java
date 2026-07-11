package nvc.guide.modules.nvcprofile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.common.ai.StructuredOutputInvoker;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcprofile.dto.CommunicationAnalysisRequest;
import nvc.guide.modules.nvcprofile.dto.CommunicationAnalysisResult;
import nvc.guide.modules.nvcprofile.model.NvcCommunicationRecordEntity;
import nvc.guide.modules.nvcprofile.repository.NvcCommunicationRecordRepository;
import nvc.guide.modules.nvcscenario.model.NvcScenarioType;
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
public class NvcCommunicationAnalysisService {

    private final NvcCommunicationRecordRepository recordRepository;
    private final LlmProviderRegistry llmProviderRegistry;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final ObjectMapper objectMapper;

    private String analysisSystemPrompt;

    private String getAnalysisSystemPrompt() {
        if (analysisSystemPrompt == null) {
            try {
                ClassPathResource resource = new ClassPathResource("prompts/nvc-profile-analyze-system.st");
                analysisSystemPrompt = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                analysisSystemPrompt = getDefaultAnalysisPrompt();
            }
        }
        return analysisSystemPrompt;
    }

    /**
     * 分析沟通记录
     */
    public NvcCommunicationRecordEntity analyzeAndSave(Long userId, CommunicationAnalysisRequest request) {
        // 1. 调用 LLM 分析
        String analysisPrompt = buildAnalysisPrompt(request.rawContent(), request.scenarioType());
        ChatClient chatClient = llmProviderRegistry.getPlainChatClient(null);
        BeanOutputConverter<CommunicationAnalysisResult> converter =
            new BeanOutputConverter<>(CommunicationAnalysisResult.class);

        CommunicationAnalysisResult result = structuredOutputInvoker.invoke(
            chatClient,
            getAnalysisSystemPrompt(),
            analysisPrompt,
            converter,
            ErrorCode.NVC_EVALUATION_FAILED,
            "沟通记录分析失败: ",
            "CommunicationAnalysis",
            log
        );

        // 2. 保存记录
        NvcCommunicationRecordEntity record = NvcCommunicationRecordEntity.builder()
            .userId(userId)
            .title(request.title())
            .scenarioType(request.scenarioType())
            .rawContent(request.rawContent())
            .analysisResult(objectMapper.valueToTree(result).toString())
            .nvcSuggestion(result.nvcRewrite())
            .build();

        NvcCommunicationRecordEntity saved = recordRepository.save(record);
        log.info("Communication record analyzed and saved: userId={}, recordId={}", userId, saved.getId());
        return saved;
    }

    /**
     * 获取用户的沟通记录列表
     */
    public List<NvcCommunicationRecordEntity> getUserRecords(Long userId) {
        return recordRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private String buildAnalysisPrompt(String rawContent, NvcScenarioType scenarioType) {
        return """
            请分析以下真实沟通记录，评估其中的NVC四要素质量，并给出NVC改写建议。

            [沟通记录]
            %s

            场景类型：%s

            请分析：
            1. 这段沟通中哪些是"观察"（客观事实），哪些是"评论"（主观判断）
            2. 表达了哪些"感受"，哪些是"想法"伪装成感受
            3. 隐藏了哪些"需求"
            4. 有没有具体的"请求"，还是只有命令或指责
            5. 用NVC方式重新改写这段沟通
            """.formatted(rawContent, scenarioType != null ? scenarioType : "未知");
    }

    private String getDefaultAnalysisPrompt() {
        return """
            你是NVC（非暴力沟通）表达分析专家。请分析用户的沟通记录，评估其中的NVC四要素质量。

            请以JSON格式返回：
            {
              "observationAnalysis": "观察维度分析",
              "feelingAnalysis": "感受维度分析",
              "needAnalysis": "需求维度分析",
              "requestAnalysis": "请求维度分析",
              "observationScore": 0-100,
              "feelingScore": 0-100,
              "needScore": 0-100,
              "requestScore": 0-100,
              "overallScore": 0-100,
              "overallAssessment": "整体评估",
              "nvcRewrite": "用NVC方式改写的版本",
              "keyImprovements": ["关键改进建议1", "关键改进建议2"]
            }
            """;
    }
}
