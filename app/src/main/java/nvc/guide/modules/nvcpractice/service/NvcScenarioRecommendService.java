package nvc.guide.modules.nvcpractice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.modules.nvcprofile.dto.AbilityRadarDTO;
import nvc.guide.modules.nvcprofile.service.NvcProfileService;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import nvc.guide.modules.nvcscenario.repository.NvcScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * NVC 场景推荐服务
 * 基于用户能力雷达图的薄弱维度，推荐最相关的练习场景
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcScenarioRecommendService {

    private final NvcProfileService nvcProfileService;
    private final NvcScenarioRepository nvcScenarioRepository;
    private final ObjectMapper objectMapper;

    /**
     * 默认推荐数量
     */
    private static final int DEFAULT_LIMIT = 5;

    /**
     * 推荐练习场景（基于用户薄弱维度加权排序）
     *
     * @param userId 用户 ID
     * @param limit  返回数量
     * @return 按薄弱维度匹配度降序排列的场景列表
     */
    public List<NvcScenarioEntity> recommend(Long userId, int limit) {
        // 1. 获取用户能力雷达
        AbilityRadarDTO radar = nvcProfileService.getAbilityRadar(userId);
        if (radar == null) {
            log.debug("No ability radar found for userId={}, returning empty list", userId);
            return List.of();
        }

        // 2. 计算各维度权重：weight = (100 - score) / 100.0
        Map<String, Double> weightMap = Map.of(
            "OBSERVATION", calcWeight(radar.observation()),
            "FEELING", calcWeight(radar.feeling()),
            "NEED", calcWeight(radar.need()),
            "REQUEST", calcWeight(radar.request())
        );
        log.debug("Dimension weights for userId={}: {}", userId, weightMap);

        // 3. 加载所有场景并计算场景得分
        List<NvcScenarioEntity> scenarios = nvcScenarioRepository.findAll();
        if (scenarios.isEmpty()) {
            return List.of();
        }

        // 4. 按场景得分降序排序，取 Top limit
        return scenarios.stream()
            .sorted(Comparator.comparingDouble(
                (NvcScenarioEntity s) -> calcSceneScore(s, weightMap)).reversed())
            .limit(limit)
            .toList();
    }

    /**
     * 推荐练习场景（默认返回 Top 5）
     *
     * @param userId 用户 ID
     * @return 按薄弱维度匹配度降序排列的场景列表
     */
    public List<NvcScenarioEntity> recommend(Long userId) {
        return recommend(userId, DEFAULT_LIMIT);
    }

    /**
     * 计算维度权重：分数越低，权重越高
     */
    private double calcWeight(Integer score) {
        if (score == null) {
            return 1.0;
        }
        return (100 - score) / 100.0;
    }

    /**
     * 计算场景得分：focusElements 中匹配的维度权重之和
     */
    private double calcSceneScore(NvcScenarioEntity scenario, Map<String, Double> weightMap) {
        String focusElements = scenario.getFocusElements();
        if (focusElements == null || focusElements.isBlank()) {
            return 0.0;
        }

        try {
            List<String> focuses = objectMapper.readValue(focusElements, new TypeReference<>() {});
            if (focuses == null || focuses.isEmpty()) {
                return 0.0;
            }

            return focuses.stream()
                .mapToDouble(f -> weightMap.getOrDefault(f.toUpperCase(), 0.0))
                .sum();
        } catch (Exception e) {
            log.warn("Failed to parse focusElements for scenario {}: {}",
                scenario.getId(), e.getMessage());
            return 0.0;
        }
    }
}
