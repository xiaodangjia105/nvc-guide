package nvc.guide.modules.nvcscenario.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.common.ai.StructuredOutputInvoker;
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
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcScenarioService {

    private final NvcScenarioRepository scenarioRepository;
    private final LlmProviderRegistry llmProviderRegistry;
    private final StructuredOutputInvoker structuredOutputInvoker;
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
