package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcpractice.dto.AgentConfigDTO;
import nvc.guide.modules.nvcpractice.dto.AgentConfigUpdateRequest;
import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.repository.NvcAgentConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcAgentConfigService {

  private static final String CACHE_KEY_PREFIX = "nvc:agent-config:";
  private static final Duration CACHE_TTL = Duration.ofMinutes(5);

  private final NvcAgentConfigRepository agentConfigRepository;
  private final RedisService redisService;

  /**
   * 获取 Agent 配置（cache-aside 模式）
   * 优先从 Redis 读取，miss 则查 DB 并写入缓存
   */
  public NvcAgentConfigEntity getConfig(NvcAgentScene scene) {
    String cacheKey = CACHE_KEY_PREFIX + scene.name();
    return redisService.getOrLoad(cacheKey, CACHE_TTL, key -> {
      NvcAgentConfigEntity config = agentConfigRepository.findByAgentScene(scene)
          .orElseThrow(() -> new BusinessException(
              ErrorCode.NVC_AGENT_CONFIG_NOT_FOUND,
              "Agent config not found: " + scene.name()));
      log.debug("Loaded agent config from DB: scene={}", scene);
      return config;
    });
  }

  /**
   * 获取所有已启用的 Agent 配置
   */
  public List<NvcAgentConfigEntity> getAllEnabledConfigs() {
    return agentConfigRepository.findByIsEnabledTrue();
  }

  /**
   * 获取所有 Agent 配置（包括禁用的）
   */
  public List<NvcAgentConfigEntity> getAllConfigs() {
    return agentConfigRepository.findAll();
  }

  /**
   * 更新 Agent 配置（热更新）
   * 更新 DB + 清除缓存，下次调用自动生效
   */
  @Transactional
  public NvcAgentConfigEntity updateConfig(
      NvcAgentScene scene, AgentConfigUpdateRequest request) {
    NvcAgentConfigEntity config = agentConfigRepository.findByAgentScene(scene)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.NVC_AGENT_CONFIG_NOT_FOUND,
            "Agent config not found: " + scene.name()));

    if (request.systemPrompt() != null) {
      config.setSystemPrompt(request.systemPrompt());
    }
    if (request.modelProvider() != null) {
      config.setModelProvider(request.modelProvider());
    }
    if (request.modelName() != null) {
      config.setModelName(request.modelName());
    }
    if (request.temperature() != null) {
      config.setTemperature(request.temperature());
    }
    if (request.maxTokens() != null) {
      config.setMaxTokens(request.maxTokens());
    }
    if (request.topP() != null) {
      config.setTopP(request.topP());
    }
    if (request.isEnabled() != null) {
      config.setIsEnabled(request.isEnabled());
    }
    if (request.stepAdvanceThreshold() != null) {
      config.setStepAdvanceThreshold(request.stepAdvanceThreshold());
    }
    if (request.maxStepAttempts() != null) {
      config.setMaxStepAttempts(request.maxStepAttempts());
    }
    if (request.stepTimeoutMinutes() != null) {
      config.setStepTimeoutMinutes(request.stepTimeoutMinutes());
    }

    NvcAgentConfigEntity saved = agentConfigRepository.save(config);

    // 主动清除缓存
    String cacheKey = CACHE_KEY_PREFIX + scene.name();
    redisService.delete(cacheKey);
    log.info("Agent config updated and cache cleared: scene={}", scene);

    return saved;
  }

  /**
   * Entity -> DTO 转换
   */
  public AgentConfigDTO toDTO(NvcAgentConfigEntity entity) {
    return new AgentConfigDTO(
        entity.getAgentScene(),
        entity.getDisplayName(),
        entity.getDescription(),
        entity.getSystemPrompt(),
        entity.getModelProvider(),
        entity.getModelName(),
        entity.getTemperature(),
        entity.getMaxTokens(),
        entity.getTopP(),
        entity.getIsEnabled(),
        entity.getStepAdvanceThreshold(),
        entity.getMaxStepAttempts(),
        entity.getStepTimeoutMinutes()
    );
  }
}
