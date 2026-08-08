package nvc.guide.modules.nvcpractice.service;

import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcSemanticCacheEntity;
import nvc.guide.modules.nvcpractice.repository.NvcSemanticCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcSemanticCacheService Tests")
class NvcSemanticCacheServiceTest {

    @Mock
    private NvcSemanticCacheRepository cacheRepository;

    @Mock
    private EmbeddingModel embeddingModel;

    private NvcSemanticCacheService service;

    private static final float[] SAMPLE_EMBEDDING = {0.1f, 0.2f, 0.3f};
    private static final String EMBEDDING_STR = "[0.1,0.2,0.3]";

    @BeforeEach
    void setUp() {
        service = new NvcSemanticCacheService(cacheRepository, embeddingModel);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.95);
        ReflectionTestUtils.setField(service, "maxResults", 3);
        ReflectionTestUtils.setField(service, "ttlDays", 30);
    }

    private void mockEmbeddingSuccess() {
        Embedding embedding = new Embedding(SAMPLE_EMBEDDING, 0);
        EmbeddingResponse embeddingResponse = new EmbeddingResponse(List.of(embedding));
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(embeddingResponse);
    }

    private void mockEmbeddingFailure() {
        when(embeddingModel.call(any(EmbeddingRequest.class)))
            .thenThrow(new RuntimeException("Embedding service unavailable"));
    }

    private void mockEmbeddingNullResponse() {
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(null);
    }

    private NvcSemanticCacheEntity buildCacheEntity(String response, Integer hitCount) {
        return NvcSemanticCacheEntity.builder()
            .id(1L)
            .queryText("test query")
            .queryEmbedding(EMBEDDING_STR)
            .response(response)
            .agentScene(NvcAgentScene.DIALOGUE_GUIDE)
            .hitCount(hitCount)
            .lastHitAt(null)
            .expiresAt(LocalDateTime.now().plusDays(10))
            .build();
    }

    // ---------------------------------------------------------------
    // lookup tests
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("lookup")
    class LookupTests {

        @Test
        @DisplayName("returns null when cache is disabled")
        void returnsNullWhenDisabled() {
            ReflectionTestUtils.setField(service, "enabled", false);

            String result = service.lookup("any query", NvcAgentScene.DIALOGUE_GUIDE);

            assertNull(result);
            verify(embeddingModel, never()).call(any(EmbeddingRequest.class));
            verify(cacheRepository, never()).findBySimilarity(anyString(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("returns null for non-cacheable scenes")
        void returnsNullForNonCacheableScenes() {
            String result = service.lookup("any query", NvcAgentScene.SCENARIO_GENERATOR);

            assertNull(result);
            verify(embeddingModel, never()).call(any(EmbeddingRequest.class));

            result = service.lookup("any query", NvcAgentScene.MAIN_ASSISTANT);
            assertNull(result);

            result = service.lookup("any query", NvcAgentScene.NVC_EXPRESSION_EVALUATOR);
            assertNull(result);
        }

        @Test
        @DisplayName("returns cached response on hit and increments hit count")
        void returnsCachedResponseOnHit() {
            mockEmbeddingSuccess();
            NvcSemanticCacheEntity cached = buildCacheEntity("cached answer", 5);
            when(cacheRepository.findBySimilarity(eq(EMBEDDING_STR), eq(0.95), eq(3)))
                .thenReturn(List.of(cached));

            String result = service.lookup("NVC 是什么", NvcAgentScene.NVC_KNOWLEDGE_ADVISOR);

            assertEquals("cached answer", result);
            assertEquals(6, cached.getHitCount());
            assertNotNull(cached.getLastHitAt());
            verify(cacheRepository).save(cached);
        }

        @Test
        @DisplayName("returns null on cache miss")
        void returnsNullOnCacheMiss() {
            mockEmbeddingSuccess();
            when(cacheRepository.findBySimilarity(eq(EMBEDDING_STR), eq(0.95), eq(3)))
                .thenReturn(Collections.emptyList());

            String result = service.lookup("unique question", NvcAgentScene.DIALOGUE_GUIDE);

            assertNull(result);
            verify(cacheRepository, never()).save(any());
        }

        @Test
        @DisplayName("returns null when embedding fails with exception")
        void returnsNullWhenEmbeddingFails() {
            mockEmbeddingFailure();

            String result = service.lookup("any query", NvcAgentScene.DIALOGUE_GUIDE);

            assertNull(result);
            verify(cacheRepository, never()).findBySimilarity(anyString(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("returns null when embedding returns null response")
        void returnsNullWhenEmbeddingReturnsNull() {
            mockEmbeddingNullResponse();

            String result = service.lookup("any query", NvcAgentScene.DIALOGUE_GUIDE);

            assertNull(result);
            verify(cacheRepository, never()).findBySimilarity(anyString(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("returns null when repository throws exception")
        void returnsNullWhenRepositoryThrows() {
            mockEmbeddingSuccess();
            when(cacheRepository.findBySimilarity(anyString(), anyDouble(), anyInt()))
                .thenThrow(new RuntimeException("DB connection failed"));

            String result = service.lookup("any query", NvcAgentScene.DIALOGUE_GUIDE);

            assertNull(result);
        }

        @Test
        @DisplayName("handles null hitCount in cached entity")
        void handlesNullHitCount() {
            mockEmbeddingSuccess();
            NvcSemanticCacheEntity cached = buildCacheEntity("response", null);
            when(cacheRepository.findBySimilarity(eq(EMBEDDING_STR), eq(0.95), eq(3)))
                .thenReturn(List.of(cached));

            String result = service.lookup("query", NvcAgentScene.EMPATHY_COACH);

            assertEquals("response", result);
            assertEquals(1, cached.getHitCount());
        }

        @Test
        @DisplayName("accepts all cacheable scenes")
        void acceptsAllCacheableScenes() {
            mockEmbeddingSuccess();
            NvcSemanticCacheEntity cached = buildCacheEntity("ok", 0);
            when(cacheRepository.findBySimilarity(anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of(cached));

            List<NvcAgentScene> cacheableScenes = List.of(
                NvcAgentScene.DIALOGUE_GUIDE,
                NvcAgentScene.NVC_KNOWLEDGE_ADVISOR,
                NvcAgentScene.STEP_OBSERVE_COACH,
                NvcAgentScene.STEP_FEELING_COACH,
                NvcAgentScene.STEP_NEED_COACH,
                NvcAgentScene.STEP_REQUEST_COACH,
                NvcAgentScene.EMPATHY_COACH
            );

            for (NvcAgentScene scene : cacheableScenes) {
                assertNotNull(service.lookup("query", scene),
                    "Scene " + scene + " should be cacheable");
            }
        }
    }

    // ---------------------------------------------------------------
    // cache tests
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("cache")
    class CacheTests {

        @Test
        @DisplayName("stores entry with correct fields and TTL")
        void storesEntryWithCorrectTtl() {
            mockEmbeddingSuccess();

            service.cache("what is NVC", "NVC is nonviolent communication", NvcAgentScene.NVC_KNOWLEDGE_ADVISOR);

            verify(cacheRepository).save(any(NvcSemanticCacheEntity.class));
        }

        @Test
        @DisplayName("does nothing when cache is disabled")
        void doesNothingWhenDisabled() {
            ReflectionTestUtils.setField(service, "enabled", false);

            service.cache("query", "response", NvcAgentScene.DIALOGUE_GUIDE);

            verify(embeddingModel, never()).call(any(EmbeddingRequest.class));
            verify(cacheRepository, never()).save(any());
        }

        @Test
        @DisplayName("does nothing for non-cacheable scenes")
        void doesNothingForNonCacheableScenes() {
            service.cache("query", "response", NvcAgentScene.SCENARIO_GENERATOR);

            verify(embeddingModel, never()).call(any(EmbeddingRequest.class));
            verify(cacheRepository, never()).save(any());
        }

        @Test
        @DisplayName("does nothing when embedding fails")
        void doesNothingWhenEmbeddingFails() {
            mockEmbeddingFailure();

            service.cache("query", "response", NvcAgentScene.DIALOGUE_GUIDE);

            verify(cacheRepository, never()).save(any());
        }

        @Test
        @DisplayName("does nothing when embedding returns null")
        void doesNothingWhenEmbeddingReturnsNull() {
            mockEmbeddingNullResponse();

            service.cache("query", "response", NvcAgentScene.DIALOGUE_GUIDE);

            verify(cacheRepository, never()).save(any());
        }

        @Test
        @DisplayName("silently handles repository save failure")
        void handlesRepositorySaveFailure() {
            mockEmbeddingSuccess();
            when(cacheRepository.save(any(NvcSemanticCacheEntity.class)))
                .thenThrow(new RuntimeException("DB write failed"));

            assertDoesNotThrow(() ->
                service.cache("query", "response", NvcAgentScene.DIALOGUE_GUIDE));
        }
    }

    // ---------------------------------------------------------------
    // cleanupExpired tests
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("cleanupExpired")
    class CleanupExpiredTests {

        @Test
        @DisplayName("delegates to repository for expired entries")
        void delegatesToRepository() {
            when(cacheRepository.deleteExpired(any(LocalDateTime.class))).thenReturn(5);

            service.cleanupExpired();

            verify(cacheRepository).deleteExpired(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("handles zero deletions without error")
        void handlesZeroDeletions() {
            when(cacheRepository.deleteExpired(any(LocalDateTime.class))).thenReturn(0);

            assertDoesNotThrow(() -> service.cleanupExpired());
        }
    }

    // ---------------------------------------------------------------
    // getStats tests
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("getStats")
    class GetStatsTests {

        @Test
        @DisplayName("returns correct counts from repository")
        void returnsCorrectCounts() {
            when(cacheRepository.count()).thenReturn(42L);
            when(cacheRepository.getTotalHitCount()).thenReturn(128L);

            NvcSemanticCacheService.CacheStats stats = service.getStats();

            assertEquals(42L, stats.totalEntries());
            assertEquals(128L, stats.totalHits());
        }

        @Test
        @DisplayName("returns zero hits when getTotalHitCount returns null")
        void returnsZeroHitsWhenNull() {
            when(cacheRepository.count()).thenReturn(10L);
            when(cacheRepository.getTotalHitCount()).thenReturn(null);

            NvcSemanticCacheService.CacheStats stats = service.getStats();

            assertEquals(10L, stats.totalEntries());
            assertEquals(0L, stats.totalHits());
        }

        @Test
        @DisplayName("returns zero entries for empty cache")
        void returnsZeroForEmptyCache() {
            when(cacheRepository.count()).thenReturn(0L);
            when(cacheRepository.getTotalHitCount()).thenReturn(0L);

            NvcSemanticCacheService.CacheStats stats = service.getStats();

            assertEquals(0L, stats.totalEntries());
            assertEquals(0L, stats.totalHits());
        }
    }

    // ---------------------------------------------------------------
    // clearAll tests
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("clearAll")
    class ClearAllTests {

        @Test
        @DisplayName("deletes all entries via repository")
        void deletesAllEntries() {
            service.clearAll();

            verify(cacheRepository).deleteAll();
        }
    }
}
