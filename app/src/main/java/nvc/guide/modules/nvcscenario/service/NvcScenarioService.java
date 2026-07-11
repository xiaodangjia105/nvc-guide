package nvc.guide.modules.nvcscenario.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcscenario.dto.ScenarioDTO;
import nvc.guide.modules.nvcscenario.dto.ScenarioGenerateRequest;
import nvc.guide.modules.nvcscenario.dto.ScenarioQueryRequest;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import nvc.guide.modules.nvcscenario.model.NvcScenarioType;
import nvc.guide.modules.nvcscenario.repository.NvcScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcScenarioService {

    private final NvcScenarioRepository scenarioRepository;
    private final LlmProviderRegistry llmProviderRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 查询场景列表（按类型和难度筛选）
     */
    public List<NvcScenarioEntity> queryScenarios(ScenarioQueryRequest query) {
        if (query.scenarioType() != null && query.difficulty() != null) {
            return scenarioRepository.findByScenarioTypeAndDifficulty(
                query.scenarioType(), query.difficulty());
        }
        if (query.scenarioType() != null) {
            return scenarioRepository.findByScenarioType(query.scenarioType());
        }
        if (query.difficulty() != null) {
            return scenarioRepository.findByDifficulty(query.difficulty());
        }
        return scenarioRepository.findByIsSystemTrueOrderByUsageCountDesc();
    }

    /**
     * 获取单个场景
     */
    public NvcScenarioEntity getScenario(Long id) {
        return scenarioRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NVC_SCENARIO_NOT_FOUND,
                "Scenario not found: " + id));
    }

    /**
     * AI 生成新场景
     */
    public NvcScenarioEntity generateScenario(ScenarioGenerateRequest request) {
        String systemPrompt = loadSystemPrompt();
        String userPrompt = buildGeneratePrompt(request);

        ChatClient chatClient = llmProviderRegistry.getPlainChatClient(null);
        String result = chatClient.prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .call()
            .content();

        try {
            var node = objectMapper.readTree(result);
            NvcScenarioEntity scenario = NvcScenarioEntity.builder()
                .title(node.has("title") ? node.get("title").asText() : "AI 生成场景")
                .description(node.has("description") ? node.get("description").asText() : result)
                .scenarioType(request.scenarioType() != null ? request.scenarioType() : NvcScenarioType.WORKPLACE)
                .difficulty(request.difficulty() != null ? request.difficulty() : NvcDifficulty.MEDIUM)
                .context(node.has("context") ? node.get("context").asText() : null)
                .focusElements(node.has("focus_elements") ? node.get("focus_elements").toString() : null)
                .tags(node.has("tags") ? node.get("tags").toString() : null)
                .isSystem(false)
                .usageCount(0)
                .build();

            NvcScenarioEntity saved = scenarioRepository.save(scenario);
            log.info("AI scenario generated: id={}, title={}", saved.getId(), saved.getTitle());
            return saved;

        } catch (Exception e) {
            log.error("Failed to parse AI generated scenario: {}", result, e);
            throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT,
                "场景生成失败，请重试");
        }
    }

    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/nvc-scenario-generate-system.st");
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "你是NVC练习场景设计师，请根据用户需求生成NVC练习场景，以JSON格式返回。";
        }
    }

    private String buildGeneratePrompt(ScenarioGenerateRequest request) {
        StringBuilder sb = new StringBuilder("请生成一个NVC练习场景。\n\n");

        if (request.scenarioType() != null) {
            sb.append("场景类型：").append(request.scenarioType()).append("\n");
        }
        if (request.difficulty() != null) {
            sb.append("难度：").append(request.difficulty()).append("\n");
        }
        if (request.focusElements() != null && !request.focusElements().isEmpty()) {
            sb.append("重点练习要素：").append(String.join(", ", request.focusElements())).append("\n");
        }
        if (request.description() != null) {
            sb.append("用户描述：").append(request.description()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 场景使用次数 +1
     */
    public void incrementUsage(Long scenarioId) {
        scenarioRepository.findById(scenarioId).ifPresent(scenario -> {
            scenario.setUsageCount(scenario.getUsageCount() + 1);
            scenarioRepository.save(scenario);
        });
    }

    /**
     * 转换为 DTO
     */
    public ScenarioDTO toDTO(NvcScenarioEntity entity) {
        return new ScenarioDTO(
            entity.getId(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getScenarioType(),
            entity.getDifficulty(),
            entity.getFocusElements(),
            entity.getContext(),
            entity.getSampleDialogue(),
            entity.getTags(),
            entity.getIsSystem(),
            entity.getUsageCount()
        );
    }
}
