package nvc.guide.modules.nvcpractice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcSemanticCacheEntity;
import nvc.guide.modules.nvcpractice.repository.NvcSemanticCacheRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 语义缓存服务
 * 基于向量相似度的 LLM 响应缓存，减少重复调用
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcSemanticCacheService {

    private final NvcSemanticCacheRepository cacheRepository;
    private final EmbeddingModel embeddingModel;

    @Value("${nvc.semantic-cache.enabled:true}")
    private boolean enabled;

    @Value("${nvc.semantic-cache.threshold:0.95}")
    private double similarityThreshold;

    @Value("${nvc.semantic-cache.max-results:3}")
    private int maxResults;

    @Value("${nvc.semantic-cache.ttl-days:30}")
    private int ttlDays;

    /**
     * 可缓存的 Agent 场景（仅知识问答类）
     */
    private static final List<NvcAgentScene> CACHEABLE_SCENES = List.of(
        NvcAgentScene.DIALOGUE_GUIDE,
        NvcAgentScene.NVC_KNOWLEDGE_ADVISOR,
        NvcAgentScene.STEP_OBSERVE_COACH,
        NvcAgentScene.STEP_FEELING_COACH,
        NvcAgentScene.STEP_NEED_COACH,
        NvcAgentScene.STEP_REQUEST_COACH,
        NvcAgentScene.EMPATHY_COACH
    );

    /**
     * 查询语义缓存
     * @return 缓存命中时返回响应文本，未命中返回 null
     */
    @Transactional
    public String lookup(String query, NvcAgentScene scene) {
        if (!enabled || !isCacheable(scene)) {
            return null;
        }

        try {
            String embeddingStr = embedQuery(query);
            if (embeddingStr == null) {
                return null;
            }

            List<NvcSemanticCacheEntity> results = cacheRepository
                .findBySimilarity(embeddingStr, similarityThreshold, maxResults);

            if (results.isEmpty()) {
                log.debug("Semantic cache miss: query={}", query.substring(0, Math.min(50, query.length())));
                return null;
            }

            // 命中缓存：更新命中计数
            NvcSemanticCacheEntity best = results.get(0);
            best.setHitCount(best.getHitCount() + 1);
            best.setLastHitAt(LocalDateTime.now());
            cacheRepository.save(best);

            log.info("Semantic cache hit: similarity={:.3f}, hitCount={}, query={}",
                1.0, best.getHitCount(), query.substring(0, Math.min(50, query.length())));
            return best.getResponse();

        } catch (Exception e) {
            log.warn("Semantic cache lookup failed, falling through to LLM: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将 LLM 响应存入缓存
     */
    @Transactional
    public void cache(String query, String response, NvcAgentScene scene) {
        if (!enabled || !isCacheable(scene)) {
            return;
        }

        try {
            String embeddingStr = embedQuery(query);
            if (embeddingStr == null) {
                return;
            }

            NvcSemanticCacheEntity entity = NvcSemanticCacheEntity.builder()
                .queryText(query)
                .queryEmbedding(embeddingStr)
                .response(response)
                .agentScene(scene)
                .hitCount(0)
                .expiresAt(LocalDateTime.now().plusDays(ttlDays))
                .build();

            cacheRepository.save(entity);
            log.debug("Cached LLM response: scene={}, queryLength={}", scene, query.length());

        } catch (Exception e) {
            log.warn("Failed to cache LLM response: {}", e.getMessage());
        }
    }

    /**
     * 定时清理过期缓存（每天凌晨 3 点）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpired() {
        int deleted = cacheRepository.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired semantic cache entries", deleted);
        }
    }

    /**
     * 获取缓存统计
     */
    @Transactional(readOnly = true)
    public CacheStats getStats() {
        long totalEntries = cacheRepository.count();
        Long totalHits = cacheRepository.getTotalHitCount();
        return new CacheStats(totalEntries, totalHits != null ? totalHits : 0);
    }

    /**
     * 清空所有缓存
     */
    @Transactional
    public void clearAll() {
        cacheRepository.deleteAll();
        log.info("Semantic cache cleared");
    }

    private boolean isCacheable(NvcAgentScene scene) {
        return CACHEABLE_SCENES.contains(scene);
    }

    private String embedQuery(String text) {
        try {
            EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
            EmbeddingResponse response = embeddingModel.call(request);
            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                return null;
            }
            float[] embedding = response.getResults().get(0).getOutput();
            return floatArrayToVector(embedding);
        } catch (Exception e) {
            log.warn("Embedding generation failed: {}", e.getMessage());
            return null;
        }
    }

    private String floatArrayToVector(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    public record CacheStats(long totalEntries, long totalHits) {}
}
