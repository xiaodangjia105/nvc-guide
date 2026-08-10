package nvc.guide.common.ai;

/**
 * LLM Provider configuration snapshot.
 * This is a common-layer record that abstracts away the database entity details.
 */
public record ProviderSnapshot(
    String id,
    String baseUrl,
    String apiKey,
    String model,
    String embeddingModel,
    Integer embeddingDimensions,
    boolean supportsEmbedding,
    Double temperature
) {
}
