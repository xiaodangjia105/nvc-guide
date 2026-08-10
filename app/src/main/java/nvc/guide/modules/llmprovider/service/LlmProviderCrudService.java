package nvc.guide.modules.llmprovider.service;

import nvc.guide.common.ai.ApiPathResolver;
import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.common.config.LlmProviderProperties;
import nvc.guide.common.config.LlmProviderProperties.ProviderConfig;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.llmprovider.dto.CreateProviderRequest;
import nvc.guide.modules.llmprovider.dto.DefaultProviderDTO;
import nvc.guide.modules.llmprovider.dto.ProviderDTO;
import nvc.guide.modules.llmprovider.dto.ProviderTestResult;
import nvc.guide.modules.llmprovider.dto.UpdateProviderRequest;
import nvc.guide.modules.llmprovider.model.LlmGlobalSettingEntity;
import nvc.guide.modules.llmprovider.model.LlmProviderEntity;
import nvc.guide.modules.llmprovider.repository.LlmGlobalSettingRepository;
import nvc.guide.modules.llmprovider.repository.LlmProviderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Provider CRUD 操作服务。
 * <p>
 * 职责：Provider 的增删改查、默认 Provider 管理、连接测试。
 * 支持数据库模式（Database-backed）和遗留配置文件模式（Legacy）。
 */
@Service
@Slf4j
public class LlmProviderCrudService {

  private final LlmProviderProperties properties;
  private final LlmProviderRegistry registry;
  private final LlmProviderRepository providerRepository;
  private final LlmGlobalSettingRepository globalSettingRepository;
  private final ApiKeyEncryptionService encryptionService;
  private final ConfigFilePersistenceService configFilePersistence;
  private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

  private static final Map<String, String> RECOMMENDED_EMBEDDING_MODELS = Map.of(
      "dashscope", "text-embedding-v3",
      "glm", "embedding-3",
      "zhipu", "embedding-3",
      "baidu", "Embedding-V1",
      "minimax", "embo-01"
  );

  public LlmProviderCrudService(
      LlmProviderProperties properties,
      LlmProviderRegistry registry,
      LlmProviderRepository providerRepository,
      LlmGlobalSettingRepository globalSettingRepository,
      ApiKeyEncryptionService encryptionService,
      ConfigFilePersistenceService configFilePersistence) {
    this.properties = properties;
    this.registry = registry;
    this.providerRepository = providerRepository;
    this.globalSettingRepository = globalSettingRepository;
    this.encryptionService = encryptionService;
    this.configFilePersistence = configFilePersistence;
  }

  // ===== Read operations (read lock) =====

  public List<ProviderDTO> listProviders() {
    rwLock.readLock().lock();
    try {
      if (!isDatabaseBacked()) {
        Map<String, ProviderConfig> providers = properties.getProviders();
        if (providers == null) return List.of();
        return providers.entrySet().stream()
            .map(e -> ProviderDTO.builder()
                .id(e.getKey())
                .baseUrl(e.getValue().getBaseUrl())
                .maskedApiKey(maskApiKey(e.getValue().getApiKey()))
                .model(e.getValue().getModel())
                .embeddingModel(e.getValue().getEmbeddingModel())
                .embeddingDimensions(resolveEmbeddingDimensions(e.getValue().getEmbeddingDimensions()))
                .supportsEmbedding(Boolean.TRUE.equals(e.getValue().getSupportsEmbedding())
                    || trimOrNull(e.getValue().getEmbeddingModel()) != null)
                .temperature(e.getValue().getTemperature())
                .defaultChatProvider(e.getKey().equals(properties.getDefaultProvider()))
                .defaultEmbeddingProvider(e.getKey().equals(properties.getDefaultEmbeddingProvider()))
                .build())
            .toList();
      }
      LlmGlobalSettingEntity setting = getGlobalSettingOrThrow();
      return providerRepository.findAll().stream()
          .map(provider -> ProviderDTO.builder()
              .id(provider.getId())
              .baseUrl(provider.getBaseUrl())
              .maskedApiKey(maskApiKey(decryptApiKey(provider)))
              .model(provider.getModel())
              .embeddingModel(provider.getEmbeddingModel())
              .embeddingDimensions(resolveEmbeddingDimensions(provider.getEmbeddingDimensions()))
              .supportsEmbedding(provider.isSupportsEmbedding())
              .temperature(provider.getTemperature())
              .defaultChatProvider(provider.getId().equals(setting.getDefaultChatProviderId()))
              .defaultEmbeddingProvider(provider.getId().equals(setting.getDefaultEmbeddingProviderId()))
              .build())
          .toList();
    } finally {
      rwLock.readLock().unlock();
    }
  }

  public ProviderDTO getProvider(String id) {
    rwLock.readLock().lock();
    try {
      if (!isDatabaseBacked()) {
        ProviderConfig config = getLegacyProviderConfigOrThrow(id);
        return ProviderDTO.builder()
            .id(id)
            .baseUrl(config.getBaseUrl())
            .maskedApiKey(maskApiKey(config.getApiKey()))
            .model(config.getModel())
            .embeddingModel(config.getEmbeddingModel())
            .embeddingDimensions(resolveEmbeddingDimensions(config.getEmbeddingDimensions()))
            .supportsEmbedding(Boolean.TRUE.equals(config.getSupportsEmbedding())
                || trimOrNull(config.getEmbeddingModel()) != null)
            .temperature(config.getTemperature())
            .defaultChatProvider(id.equals(properties.getDefaultProvider()))
            .defaultEmbeddingProvider(id.equals(properties.getDefaultEmbeddingProvider()))
            .build();
      }
      LlmGlobalSettingEntity setting = getGlobalSettingOrThrow();
      LlmProviderEntity provider = getProviderEntityOrThrow(id);
      return ProviderDTO.builder()
          .id(id)
          .baseUrl(provider.getBaseUrl())
          .maskedApiKey(maskApiKey(decryptApiKey(provider)))
          .model(provider.getModel())
          .embeddingModel(provider.getEmbeddingModel())
          .embeddingDimensions(resolveEmbeddingDimensions(provider.getEmbeddingDimensions()))
          .supportsEmbedding(provider.isSupportsEmbedding())
          .temperature(provider.getTemperature())
          .defaultChatProvider(id.equals(setting.getDefaultChatProviderId()))
          .defaultEmbeddingProvider(id.equals(setting.getDefaultEmbeddingProviderId()))
          .build();
    } finally {
      rwLock.readLock().unlock();
    }
  }

  public DefaultProviderDTO getDefaultProvider() {
    rwLock.readLock().lock();
    try {
      if (!isDatabaseBacked()) {
        return new DefaultProviderDTO(properties.getDefaultProvider(), properties.getDefaultEmbeddingProvider());
      }
      LlmGlobalSettingEntity setting = getGlobalSettingOrThrow();
      return new DefaultProviderDTO(
          setting.getDefaultChatProviderId(),
          setting.getDefaultEmbeddingProviderId());
    } finally {
      rwLock.readLock().unlock();
    }
  }

  public ProviderTestResult testProvider(String id) {
    rwLock.readLock().lock();
    try {
      ProviderRuntimeConfig config = isDatabaseBacked()
          ? getProviderRuntimeConfigOrThrow(id)
          : toRuntimeConfig(getLegacyProviderConfigOrThrow(id));
      return doTestProvider(config, id);
    } finally {
      rwLock.readLock().unlock();
    }
  }

  // ===== Write operations (write lock) =====

  @Transactional
  public void createProvider(CreateProviderRequest request) {
    rwLock.writeLock().lock();
    try {
      if (!isDatabaseBacked()) {
        createProviderLegacy(request);
        return;
      }
      String providerId = trimOrNull(request.id());
      if (providerRepository.existsById(providerId)) {
        throw new BusinessException(ErrorCode.PROVIDER_ALREADY_EXISTS,
            "Provider '" + request.id() + "' 已存在");
      }
      String baseUrl = requireNonBlank(request.baseUrl(), "baseUrl");
      String model = requireNonBlank(request.model(), "model");
      String apiKey = requireNonBlank(request.apiKey(), "apiKey");
      String embeddingModel = trimOrNull(request.embeddingModel());
      Integer embeddingDimensions = resolveEmbeddingDimensions(request.embeddingDimensions());
      boolean supportsEmbedding = request.supportsEmbedding() != null
          ? request.supportsEmbedding()
          : embeddingModel != null;
      validateEmbeddingConfig(providerId, supportsEmbedding, embeddingModel, embeddingDimensions);

      ApiKeyEncryptionService.EncryptedValue encrypted = encryptionService.encrypt(apiKey);
      providerRepository.save(LlmProviderEntity.builder()
          .id(providerId)
          .baseUrl(baseUrl)
          .apiKeyNonce(encrypted.nonce())
          .apiKeyCiphertext(encrypted.ciphertext())
          .model(model)
          .embeddingModel(embeddingModel)
          .embeddingDimensions(embeddingDimensions)
          .supportsEmbedding(supportsEmbedding)
          .temperature(request.temperature())
          .enabled(true)
          .builtin(false)
          .build());
      registry.reload();
      log.info("Created provider: id={}, baseUrl={}, model={}", providerId, baseUrl, model);
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  @Transactional
  public void updateProvider(String id, UpdateProviderRequest request) {
    rwLock.writeLock().lock();
    try {
      if (!isDatabaseBacked()) {
        updateProviderLegacy(id, request);
        return;
      }
      LlmProviderEntity provider = getProviderEntityOrThrow(id);

      String trimmedBaseUrl = trimOrNull(request.baseUrl());
      if (request.baseUrl() != null && trimmedBaseUrl == null) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "baseUrl 不能为空字符串");
      }
      String trimmedModel = trimOrNull(request.model());
      if (request.model() != null && trimmedModel == null) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "model 不能为空字符串");
      }
      String trimmedApiKey = trimOrNull(request.apiKey());
      if (request.apiKey() != null && trimmedApiKey == null) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "apiKey 不能为空字符串");
      }

      if (trimmedBaseUrl != null) provider.setBaseUrl(trimmedBaseUrl);
      if (trimmedModel != null) provider.setModel(trimmedModel);
      if (request.embeddingModel() != null) {
        provider.setEmbeddingModel(trimOrNull(request.embeddingModel()));
      }
      if (request.embeddingDimensions() != null) {
        provider.setEmbeddingDimensions(resolveEmbeddingDimensions(request.embeddingDimensions()));
      }
      if (request.supportsEmbedding() != null) {
        provider.setSupportsEmbedding(request.supportsEmbedding());
      }
      validateEmbeddingConfig(
          id,
          provider.isSupportsEmbedding(),
          provider.getEmbeddingModel(),
          resolveEmbeddingDimensions(provider.getEmbeddingDimensions()));
      if (request.temperature() != null) {
        provider.setTemperature(request.temperature());
      }
      if (trimmedApiKey != null) {
        ApiKeyEncryptionService.EncryptedValue encrypted = encryptionService.encrypt(trimmedApiKey);
        provider.setApiKeyNonce(encrypted.nonce());
        provider.setApiKeyCiphertext(encrypted.ciphertext());
      }

      providerRepository.save(provider);
      registry.reload();
      log.info("Updated provider: id={}", id);
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  @Transactional
  public void deleteProvider(String id) {
    rwLock.writeLock().lock();
    try {
      if (!isDatabaseBacked()) {
        deleteProviderLegacy(id);
        return;
      }
      LlmGlobalSettingEntity setting = getGlobalSettingOrThrow();
      if (id.equals(setting.getDefaultChatProviderId()) || id.equals(setting.getDefaultEmbeddingProviderId())) {
        throw new BusinessException(ErrorCode.PROVIDER_DEFAULT_CANNOT_DELETE,
            "默认 Provider '" + id + "' 不可删除，请先切换默认 Provider");
      }
      getProviderEntityOrThrow(id);

      providerRepository.deleteById(id);
      registry.reload();
      log.info("Deleted provider: id={}", id);
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  @Transactional
  public void updateDefaultProvider(DefaultProviderDTO request) {
    rwLock.writeLock().lock();
    try {
      if (!isDatabaseBacked()) {
        updateDefaultProviderLegacy(request);
        return;
      }
      String providerId = trimOrNull(request.defaultProvider());
      if (providerId == null) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "defaultProvider 不能为空");
      }
      getProviderEntityOrThrow(providerId);
      LlmGlobalSettingEntity setting = getGlobalSettingOrThrow();
      setting.setDefaultChatProviderId(providerId);
      globalSettingRepository.save(setting);
      registry.reload();
      log.info("Updated default provider: {}", providerId);
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  @Transactional
  public void updateDefaultEmbeddingProvider(DefaultProviderDTO request) {
    rwLock.writeLock().lock();
    try {
      if (!isDatabaseBacked()) {
        updateDefaultEmbeddingProviderLegacy(request);
        return;
      }
      String providerId = trimOrNull(request.defaultEmbeddingProvider());
      if (providerId == null) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "defaultEmbeddingProvider 不能为空");
      }
      LlmProviderEntity provider = getProviderEntityOrThrow(providerId);
      String embeddingModel = trimOrNull(provider.getEmbeddingModel());
      if (!provider.isSupportsEmbedding() || embeddingModel == null) {
        throw new BusinessException(ErrorCode.BAD_REQUEST,
            "Provider '" + providerId + "' 不支持 Embedding，不能设为默认向量服务");
      }
      validateEmbeddingConfig(
          providerId,
          true,
          embeddingModel,
          resolveEmbeddingDimensions(provider.getEmbeddingDimensions()));
      LlmGlobalSettingEntity setting = getGlobalSettingOrThrow();
      setting.setDefaultEmbeddingProviderId(providerId);
      globalSettingRepository.save(setting);
      registry.reload();
      log.info("Updated default embedding provider: {}", providerId);
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  public void reloadProviders() {
    registry.reload();
    log.info("Manual provider reload triggered");
  }

  // ===== Internal helpers =====

  private boolean isDatabaseBacked() {
    return providerRepository != null && globalSettingRepository != null && encryptionService != null;
  }

  private Map<String, ProviderConfig> getLegacyProvidersOrThrow() {
    Map<String, ProviderConfig> providers = properties.getProviders();
    if (providers == null) {
      throw new BusinessException(ErrorCode.PROVIDER_CONFIG_READ_FAILED,
          "Provider 配置未初始化");
    }
    return providers;
  }

  ProviderConfig getLegacyProviderConfigOrThrow(String id) {
    ProviderConfig config = getLegacyProvidersOrThrow().get(id);
    if (config == null) {
      throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND,
          "Provider '" + id + "' 不存在");
    }
    return config;
  }

  private void createProviderLegacy(CreateProviderRequest request) {
    Map<String, ProviderConfig> providers = getLegacyProvidersOrThrow();
    if (providers.containsKey(request.id())) {
      throw new BusinessException(ErrorCode.PROVIDER_ALREADY_EXISTS,
          "Provider '" + request.id() + "' 已存在");
    }

    ProviderConfig config = new ProviderConfig();
    config.setBaseUrl(request.baseUrl());
    config.setApiKey(request.apiKey());
    config.setModel(request.model());
    config.setEmbeddingModel(request.embeddingModel());
    config.setEmbeddingDimensions(request.embeddingDimensions());
    config.setSupportsEmbedding(request.supportsEmbedding());
    config.setTemperature(request.temperature());
    providers.put(request.id(), config);

    String envKey = toEnvKey(request.id());
    configFilePersistence.writeProviderToYaml(request.id(), config, envKey);
    configFilePersistence.writeEnvValue(envKey, request.apiKey());
    registry.reload();
  }

  private void updateProviderLegacy(String id, UpdateProviderRequest request) {
    ProviderConfig config = getLegacyProviderConfigOrThrow(id);
    String trimmedBaseUrl = trimOrNull(request.baseUrl());
    if (request.baseUrl() != null && trimmedBaseUrl == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "baseUrl 不能为空字符串");
    }
    String trimmedModel = trimOrNull(request.model());
    if (request.model() != null && trimmedModel == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "model 不能为空字符串");
    }
    String trimmedApiKey = trimOrNull(request.apiKey());
    if (request.apiKey() != null && trimmedApiKey == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "apiKey 不能为空字符串");
    }

    if (trimmedBaseUrl != null) config.setBaseUrl(trimmedBaseUrl);
    if (trimmedModel != null) config.setModel(trimmedModel);
    if (request.embeddingModel() != null) {
      config.setEmbeddingModel(trimOrNull(request.embeddingModel()));
    }
    if (request.embeddingDimensions() != null) {
      config.setEmbeddingDimensions(resolveEmbeddingDimensions(request.embeddingDimensions()));
    }
    if (request.supportsEmbedding() != null) {
      config.setSupportsEmbedding(request.supportsEmbedding());
    }
    if (request.temperature() != null) {
      config.setTemperature(request.temperature());
    }
    if (trimmedApiKey != null) {
      config.setApiKey(trimmedApiKey);
      configFilePersistence.updateEnvValue(toEnvKey(id), trimmedApiKey);
    }

    configFilePersistence.writeProviderToYaml(id, config, toEnvKey(id));
    registry.reload();
  }

  private void deleteProviderLegacy(String id) {
    if (id.equals(properties.getDefaultProvider())) {
      throw new BusinessException(ErrorCode.PROVIDER_DEFAULT_CANNOT_DELETE,
          "默认 Provider '" + id + "' 不可删除，请先切换默认 Provider");
    }
    getLegacyProviderConfigOrThrow(id);
    getLegacyProvidersOrThrow().remove(id);
    String envKey = toEnvKey(id);
    configFilePersistence.removeProviderFromYaml(id);
    configFilePersistence.removeFromEnv(envKey);
    registry.reload();
  }

  private void updateDefaultProviderLegacy(DefaultProviderDTO request) {
    String providerId = trimOrNull(request.defaultProvider());
    if (providerId == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "defaultProvider 不能为空");
    }
    getLegacyProviderConfigOrThrow(providerId);
    properties.setDefaultProvider(providerId);
    configFilePersistence.writeDefaultProviderToYaml(providerId);
    registry.reload();
  }

  private void updateDefaultEmbeddingProviderLegacy(DefaultProviderDTO request) {
    String providerId = trimOrNull(request.defaultEmbeddingProvider());
    if (providerId == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "defaultEmbeddingProvider 不能为空");
    }
    getLegacyProviderConfigOrThrow(providerId);
    properties.setDefaultEmbeddingProvider(providerId);
    configFilePersistence.writeDefaultEmbeddingProviderToYaml(providerId);
    registry.reload();
  }

  private ProviderRuntimeConfig toRuntimeConfig(ProviderConfig config) {
    return new ProviderRuntimeConfig(
        config.getBaseUrl(),
        config.getApiKey(),
        config.getModel(),
        config.getEmbeddingModel(),
        resolveEmbeddingDimensions(config.getEmbeddingDimensions()),
        Boolean.TRUE.equals(config.getSupportsEmbedding()) || trimOrNull(config.getEmbeddingModel()) != null,
        config.getTemperature()
    );
  }

  LlmProviderEntity getProviderEntityOrThrow(String id) {
    return providerRepository.findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.PROVIDER_NOT_FOUND,
            "Provider '" + id + "' 不存在"));
  }

  private LlmGlobalSettingEntity getGlobalSettingOrThrow() {
    return globalSettingRepository.findById(LlmGlobalSettingEntity.SINGLETON_ID)
        .orElseThrow(() -> new BusinessException(ErrorCode.PROVIDER_CONFIG_READ_FAILED,
            "默认 Provider 配置未初始化"));
  }

  private ProviderRuntimeConfig getProviderRuntimeConfigOrThrow(String id) {
    LlmProviderEntity provider = getProviderEntityOrThrow(id);
    return new ProviderRuntimeConfig(
        provider.getBaseUrl(),
        decryptApiKey(provider),
        provider.getModel(),
        provider.getEmbeddingModel(),
        resolveEmbeddingDimensions(provider.getEmbeddingDimensions()),
        provider.isSupportsEmbedding(),
        provider.getTemperature()
    );
  }

  private String decryptApiKey(LlmProviderEntity provider) {
    return encryptionService.decrypt(provider.getApiKeyNonce(), provider.getApiKeyCiphertext());
  }

  String maskApiKey(String apiKey) {
    if (apiKey == null || apiKey.length() <= 6) {
      return "***";
    }
    return apiKey.substring(0, 3) + "***" + apiKey.substring(apiKey.length() - 3);
  }

  private String abbreviate(String text) {
    if (text == null || text.isBlank()) {
      return "[no body]";
    }
    String normalized = text.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= 200) {
      return normalized;
    }
    return normalized.substring(0, 200) + "...";
  }

  private List<String> buildConnectivityTestUrls(String baseUrl) {
    String normalizedBaseUrl = ApiPathResolver.stripTrailingSlashes(baseUrl);
    LinkedHashSet<String> candidateUrls = new LinkedHashSet<>();

    candidateUrls.add(normalizedBaseUrl + "/chat/completions");
    if (!ApiPathResolver.baseUrlContainsVersion(normalizedBaseUrl)) {
      candidateUrls.add(normalizedBaseUrl + "/v1/chat/completions");
    }

    return List.copyOf(candidateUrls);
  }

  private Map<String, Object> buildConnectivityTestRequestBody(String model) {
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("model", model);
    requestBody.put("messages", List.of(Map.of(
        "role", "user",
        "content", "Reply with OK only."
    )));
    requestBody.put("max_tokens", 1);
    return requestBody;
  }

  String trimOrNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private String requireNonBlank(String value, String fieldName) {
    String normalized = trimOrNull(value);
    if (normalized == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 不能为空");
    }
    return normalized;
  }

  private void validateEmbeddingConfig(
      String providerId,
      boolean supportsEmbedding,
      String embeddingModel,
      Integer embeddingDimensions) {
    String normalizedModel = trimOrNull(embeddingModel);
    if (!supportsEmbedding) {
      return;
    }
    if (normalizedModel == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST,
          "支持 Embedding 的 Provider 必须填写 embeddingModel");
    }
    if (looksLikeChatModel(normalizedModel)) {
      String recommendation = RECOMMENDED_EMBEDDING_MODELS.get(providerId.toLowerCase());
      String suffix = recommendation != null
          ? "，推荐填写 " + recommendation
          : "，请填写该厂商真实的 Embedding 模型名";
      throw new BusinessException(ErrorCode.BAD_REQUEST,
          "Embedding Model 不能填写聊天模型 '" + normalizedModel + "'" + suffix);
    }
    if (embeddingDimensions == null || embeddingDimensions <= 0) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "向量维度必须为正整数");
    }
  }

  private Integer resolveEmbeddingDimensions(Integer configuredDimensions) {
    if (configuredDimensions != null && configuredDimensions > 0) {
      return configuredDimensions;
    }
    return properties.getEmbeddingDimensions();
  }

  private boolean looksLikeChatModel(String model) {
    String lower = model.toLowerCase();
    return lower.startsWith("glm-")
        || lower.startsWith("deepseek")
        || lower.startsWith("kimi")
        || lower.startsWith("moonshot")
        || lower.startsWith("qwen")
        || lower.startsWith("ernie");
  }

  private String toEnvKey(String providerId) {
    return "PROVIDER_" + providerId.toUpperCase().replace("-", "_") + "_API_KEY";
  }

  // ===== Provider test logic (called under read lock) =====

  private ProviderTestResult doTestProvider(ProviderRuntimeConfig config, String id) {
    try {
      SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
      requestFactory.setConnectTimeout(5000);
      requestFactory.setReadTimeout(10000);

      RestClient restClient = RestClient.builder()
          .defaultHeader("Authorization", "Bearer " + config.apiKey())
          .requestFactory(requestFactory)
          .build();

      Map<String, Object> requestBody = buildConnectivityTestRequestBody(config.model());

      List<String> candidateUrls = buildConnectivityTestUrls(config.baseUrl());
      String lastFailureMessage = "Unknown error";

      for (String targetUrl : candidateUrls) {
        try {
          restClient.post()
              .uri(URI.create(targetUrl))
              .body(requestBody)
              .retrieve()
              .toEntity(String.class);
          log.info("Provider connectivity test succeeded: providerId={}, baseUrl={}, targetUrl={}, model={}",
              id, config.baseUrl(), targetUrl, config.model());
          return ProviderTestResult.builder()
              .success(true)
              .message("连接成功")
              .model(config.model())
              .build();
        } catch (RestClientResponseException e) {
          String responseBody = abbreviate(e.getResponseBodyAsString());
          lastFailureMessage = String.format(
              "HTTP %s on %s, body=%s",
              e.getStatusCode().value(),
              targetUrl,
              responseBody
          );
          log.warn(
              "Provider connectivity test failed with response: providerId={}, baseUrl={}, targetUrl={}, model={}, status={}, body={}",
              id,
              config.baseUrl(),
              targetUrl,
              config.model(),
              e.getStatusCode().value(),
              responseBody,
              e
          );
        } catch (Exception e) {
          lastFailureMessage = String.format(
              "%s on %s: %s",
              e.getClass().getSimpleName(),
              targetUrl,
              e.getMessage()
          );
          log.warn(
              "Provider connectivity test failed: providerId={}, baseUrl={}, targetUrl={}, model={}, error={}",
              id,
              config.baseUrl(),
              targetUrl,
              config.model(),
              e.getMessage(),
              e
          );
        }
      }
      return ProviderTestResult.builder()
          .success(false)
          .message("连接失败: " + lastFailureMessage)
          .model(config.model())
          .build();
    } catch (Exception e) {
      log.warn("Provider connectivity test setup failed: providerId={}, baseUrl={}, model={}, error={}",
          id, config.baseUrl(), config.model(), e.getMessage(), e);
      return ProviderTestResult.builder()
          .success(false)
          .message("连接失败: " + e.getMessage())
          .model(config.model())
          .build();
    }
  }

  private record ProviderRuntimeConfig(
      String baseUrl,
      String apiKey,
      String model,
      String embeddingModel,
      Integer embeddingDimensions,
      boolean supportsEmbedding,
      Double temperature
  ) {
  }
}
