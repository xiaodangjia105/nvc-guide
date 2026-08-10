package nvc.guide.common.ai;

import java.util.Optional;

/**
 * Interface for resolving LLM provider configuration from the data source.
 * Defined in common layer to break the reverse dependency on modules/llmprovider.
 */
public interface LlmProviderDataSource {

    /**
     * Load provider configuration by ID.
     * Returns empty if the provider is not found or not enabled.
     */
    Optional<ProviderSnapshot> loadProvider(String providerId);

    /**
     * Resolve the default chat provider ID from global settings.
     * Returns null if no global setting is configured.
     */
    String resolveDefaultChatProviderId();

    /**
     * Resolve the default embedding provider ID from global settings.
     * Returns null if no global setting is configured.
     */
    String resolveDefaultEmbeddingProviderId();
}
