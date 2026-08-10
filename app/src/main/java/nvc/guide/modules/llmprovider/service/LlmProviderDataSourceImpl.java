package nvc.guide.modules.llmprovider.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.ai.LlmProviderDataSource;
import nvc.guide.common.ai.ProviderSnapshot;
import nvc.guide.modules.llmprovider.model.LlmGlobalSettingEntity;
import nvc.guide.modules.llmprovider.model.LlmProviderEntity;
import nvc.guide.modules.llmprovider.repository.LlmGlobalSettingRepository;
import nvc.guide.modules.llmprovider.repository.LlmProviderRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Implementation of {@link LlmProviderDataSource} that reads from the database
 * via the llmprovider module's repositories and encryption service.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LlmProviderDataSourceImpl implements LlmProviderDataSource {

    private final LlmProviderRepository providerRepository;
    private final LlmGlobalSettingRepository globalSettingRepository;
    private final ApiKeyEncryptionService encryptionService;

    @Override
    public Optional<ProviderSnapshot> loadProvider(String providerId) {
        return providerRepository.findById(providerId)
            .filter(LlmProviderEntity::isEnabled)
            .map(entity -> new ProviderSnapshot(
                entity.getId(),
                entity.getBaseUrl(),
                encryptionService.decrypt(entity.getApiKeyNonce(), entity.getApiKeyCiphertext()),
                entity.getModel(),
                entity.getEmbeddingModel(),
                entity.getEmbeddingDimensions(),
                entity.isSupportsEmbedding(),
                entity.getTemperature()
            ));
    }

    @Override
    public String resolveDefaultChatProviderId() {
        return globalSettingRepository.findById(LlmGlobalSettingEntity.SINGLETON_ID)
            .map(LlmGlobalSettingEntity::getDefaultChatProviderId)
            .filter(id -> id != null && !id.isBlank())
            .orElse(null);
    }

    @Override
    public String resolveDefaultEmbeddingProviderId() {
        return globalSettingRepository.findById(LlmGlobalSettingEntity.SINGLETON_ID)
            .map(LlmGlobalSettingEntity::getDefaultEmbeddingProviderId)
            .filter(id -> id != null && !id.isBlank())
            .orElse(null);
    }
}
