package nvc.guide.modules.llmprovider.service;

import nvc.guide.modules.llmprovider.dto.AsrConfigDTO;
import nvc.guide.modules.llmprovider.dto.AsrConfigRequest;
import nvc.guide.modules.llmprovider.dto.CreateProviderRequest;
import nvc.guide.modules.llmprovider.dto.DefaultProviderDTO;
import nvc.guide.modules.llmprovider.dto.ProviderDTO;
import nvc.guide.modules.llmprovider.dto.ProviderTestResult;
import nvc.guide.modules.llmprovider.dto.TtsConfigDTO;
import nvc.guide.modules.llmprovider.dto.TtsConfigRequest;
import nvc.guide.modules.llmprovider.dto.UpdateProviderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * LLM Provider 配置服务（Facade）。
 * <p>
 * 委托给三个聚焦的子服务：
 * <ul>
 *   <li>{@link LlmProviderCrudService} — Provider CRUD 操作</li>
 *   <li>{@link ConfigFilePersistenceService} — YAML / .env 文件持久化</li>
 *   <li>{@link AsrTtsConfigService} — ASR / TTS 语音配置管理</li>
 * </ul>
 * 保持原有 public API 不变，Controller 无需修改。
 */
@Service
@Slf4j
public class LlmProviderConfigService {

  private final LlmProviderCrudService crudService;
  private final AsrTtsConfigService asrTtsService;

  public LlmProviderConfigService(
      LlmProviderCrudService crudService,
      AsrTtsConfigService asrTtsService) {
    this.crudService = crudService;
    this.asrTtsService = asrTtsService;
  }

  // ===== Provider CRUD =====

  public List<ProviderDTO> listProviders() {
    return crudService.listProviders();
  }

  public ProviderDTO getProvider(String id) {
    return crudService.getProvider(id);
  }

  public DefaultProviderDTO getDefaultProvider() {
    return crudService.getDefaultProvider();
  }

  public ProviderTestResult testProvider(String id) {
    return crudService.testProvider(id);
  }

  public void createProvider(CreateProviderRequest request) {
    crudService.createProvider(request);
  }

  public void updateProvider(String id, UpdateProviderRequest request) {
    crudService.updateProvider(id, request);
  }

  public void deleteProvider(String id) {
    crudService.deleteProvider(id);
  }

  public void updateDefaultProvider(DefaultProviderDTO request) {
    crudService.updateDefaultProvider(request);
  }

  public void updateDefaultEmbeddingProvider(DefaultProviderDTO request) {
    crudService.updateDefaultEmbeddingProvider(request);
  }

  public void reloadProviders() {
    crudService.reloadProviders();
  }

  // ===== ASR / TTS =====

  public AsrConfigDTO getAsrConfig() {
    return asrTtsService.getAsrConfig();
  }

  public TtsConfigDTO getTtsConfig() {
    return asrTtsService.getTtsConfig();
  }

  public ProviderTestResult testAsrConfig() {
    return asrTtsService.testAsrConfig();
  }

  public void updateAsrConfig(AsrConfigRequest request) {
    asrTtsService.updateAsrConfig(request);
  }

  public void updateTtsConfig(TtsConfigRequest request) {
    asrTtsService.updateTtsConfig(request);
  }
}
