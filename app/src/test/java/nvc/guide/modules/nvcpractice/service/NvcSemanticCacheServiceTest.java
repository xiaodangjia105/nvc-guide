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
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcSemanticCacheService 测试")
class NvcSemanticCacheServiceTest {

    @Mock
    private NvcSemanticCacheRepository cacheRepository;
    @Mock
    private EmbeddingModel embeddingModel;

    private NvcSemanticCacheService service;

    @BeforeEach
    void setUp() {
        service = new NvcSemanticCacheService(cacheRepository, embeddingModel);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.95);
        ReflectionTestUtils.setField(service, "maxResults", 3);
        ReflectionTestUtils.setField(service, "ttlDays", 30);
    }

    @Nested
    @DisplayName("lookup()")
    class LookupTests {

        @Test
        @DisplayName("不可缓存场景直接返回 null")
        void returnsNullForNonCacheableScene() {
            String result = service.lookup("问题", NvcAgentScene.DIFFICULT_PARTNER);

            assertNull(result);
            verifyNoInteractions(embeddingModel);
        }

        @Test
        @DisplayName("缓存禁用时返回 null")
        void returnsNullWhenDisabled() {
            ReflectionTestUtils.setField(service, "enabled", false);

            String result = service.lookup("问题", NvcAgentScene.DIALOGUE_GUIDE);

            assertNull(result);
            verifyNoInteractions(embeddingModel);
        }
    }

    @Nested
    @DisplayName("cache()")
    class CacheTests {

        @Test
        @DisplayName("不可缓存场景不存入")
        void doesNotCacheForNonCacheableScene() {
            service.cache("问题", "回答", NvcAgentScene.DIFFICULT_PARTNER);

            verifyNoInteractions(cacheRepository);
        }
    }

    @Nested
    @DisplayName("getStats()")
    class GetStatsTests {

        @Test
        @DisplayName("正确返回统计")
        void returnsCorrectStats() {
            when(cacheRepository.count()).thenReturn(42L);
            when(cacheRepository.getTotalHitCount()).thenReturn(128L);

            NvcSemanticCacheService.CacheStats stats = service.getStats();

            assertEquals(42, stats.totalEntries());
            assertEquals(128, stats.totalHits());
        }
    }
}
