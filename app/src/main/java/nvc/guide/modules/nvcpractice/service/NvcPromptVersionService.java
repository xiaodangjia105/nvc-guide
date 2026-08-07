package nvc.guide.modules.nvcpractice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcpractice.dto.CreatePromptVersionRequest;
import nvc.guide.modules.nvcpractice.dto.PromptVersionResponse;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcPromptVersionEntity;
import nvc.guide.modules.nvcpractice.repository.NvcPromptVersionRepository;
import nvc.guide.infrastructure.redis.RedisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcPromptVersionService {

    private static final String CACHE_KEY_PREFIX = "nvc:agent-config:";

    private final NvcPromptVersionRepository versionRepository;
    private final RedisService redisService;

    /**
     * 获取某个场景的所有版本
     */
    @Transactional(readOnly = true)
    public List<PromptVersionResponse> getVersions(NvcAgentScene scene) {
        return versionRepository.findByAgentSceneOrderByVersionDesc(scene)
            .stream().map(this::toResponse).toList();
    }

    /**
     * 创建新版本（不自动激活）
     */
    @Transactional
    public PromptVersionResponse createVersion(
            NvcAgentScene scene, CreatePromptVersionRequest request) {
        Integer maxVersion = versionRepository.findMaxVersion(scene);
        int newVersion = (maxVersion != null ? maxVersion : 0) + 1;

        NvcPromptVersionEntity version = NvcPromptVersionEntity.builder()
            .agentScene(scene)
            .version(newVersion)
            .systemPrompt(request.systemPrompt())
            .isActive(false)
            .trafficPercentage(request.trafficPercentage() != null
                ? request.trafficPercentage() : 0)
            .changeNote(request.changeNote())
            .build();

        NvcPromptVersionEntity saved = versionRepository.save(version);
        log.info("Prompt version created: scene={}, version={}", scene, newVersion);
        return toResponse(saved);
    }

    /**
     * 激活版本（设为活跃，清缓存）
     */
    @Transactional
    public PromptVersionResponse activateVersion(NvcAgentScene scene, Integer version) {
        NvcPromptVersionEntity entity = versionRepository
            .findByAgentSceneAndVersion(scene, version)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NVC_AGENT_CONFIG_NOT_FOUND,
                "Prompt version not found: " + scene + " v" + version));

        entity.setIsActive(true);
        entity.setTrafficPercentage(100);
        entity.setActivatedAt(LocalDateTime.now());

        // 将同场景其他活跃版本设为非活跃
        List<NvcPromptVersionEntity> activeVersions =
            versionRepository.findByAgentSceneAndIsActiveTrue(scene);
        for (NvcPromptVersionEntity v : activeVersions) {
            if (!v.getId().equals(entity.getId())) {
                v.setIsActive(false);
                v.setTrafficPercentage(0);
            }
        }

        NvcPromptVersionEntity saved = versionRepository.save(entity);
        clearAgentConfigCache(scene);
        log.info("Prompt version activated: scene={}, version={}", scene, version);
        return toResponse(saved);
    }

    /**
     * A/B 测试：设置流量分配
     * 例如 v1=90, v2=10 表示 90% 流量走 v1，10% 走 v2
     */
    @Transactional
    public void setTrafficSplit(NvcAgentScene scene, Integer version1, Integer pct1,
                                 Integer version2, Integer pct2) {
        if (pct1 + pct2 != 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "流量百分比之和必须为 100");
        }

        NvcPromptVersionEntity v1 = versionRepository
            .findByAgentSceneAndVersion(scene, version1)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NVC_AGENT_CONFIG_NOT_FOUND,
                "Version not found: " + scene + " v" + version1));

        NvcPromptVersionEntity v2 = versionRepository
            .findByAgentSceneAndVersion(scene, version2)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NVC_AGENT_CONFIG_NOT_FOUND,
                "Version not found: " + scene + " v" + version2));

        v1.setIsActive(true);
        v1.setTrafficPercentage(pct1);
        v2.setIsActive(true);
        v2.setTrafficPercentage(pct2);

        versionRepository.save(v1);
        versionRepository.save(v2);
        clearAgentConfigCache(scene);
        log.info("A/B traffic split: scene={}, v{}={}%, v{}={}%",
            scene, version1, pct1, version2, pct2);
    }

    /**
     * A/B 路由：根据 userId 选择版本
     * 无活跃版本时返回 null（调用方应 fallback 到 NvcAgentConfigEntity）
     */
    @Transactional(readOnly = true)
    public NvcPromptVersionEntity selectVersion(NvcAgentScene scene, Long userId) {
        List<NvcPromptVersionEntity> activeVersions =
            versionRepository.findByAgentSceneAndIsActiveTrue(scene);

        if (activeVersions.isEmpty()) {
            return null; // 无版本化 Prompt，使用默认配置
        }

        if (activeVersions.size() == 1) {
            return activeVersions.get(0);
        }

        // A/B 路由：userId 取模
        int bucket = (int) (Math.abs(userId) % 100);
        int cumulative = 0;
        for (NvcPromptVersionEntity v : activeVersions) {
            cumulative += v.getTrafficPercentage();
            if (bucket < cumulative) {
                return v;
            }
        }

        // fallback: 返回第一个
        return activeVersions.get(0);
    }

    /**
     * 原子增加调用计数
     */
    @Transactional
    public void incrementCallCount(Long versionId) {
        versionRepository.incrementCallCount(versionId);
    }

    private void clearAgentConfigCache(NvcAgentScene scene) {
        redisService.delete(CACHE_KEY_PREFIX + scene.name());
    }

    private PromptVersionResponse toResponse(NvcPromptVersionEntity entity) {
        return new PromptVersionResponse(
            entity.getId(),
            entity.getAgentScene(),
            entity.getVersion(),
            entity.getSystemPrompt(),
            entity.getIsActive(),
            entity.getTrafficPercentage(),
            entity.getChangeNote(),
            entity.getTotalCalls(),
            entity.getAvgEvaluationScore(),
            entity.getAvgTokenUsage(),
            entity.getAvgLatencyMs(),
            entity.getCreatedAt(),
            entity.getActivatedAt()
        );
    }
}
