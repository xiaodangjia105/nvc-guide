package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcpractice.dto.CreatePromptVersionRequest;
import nvc.guide.modules.nvcpractice.dto.PromptVersionResponse;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcPromptVersionEntity;
import nvc.guide.modules.nvcpractice.repository.NvcPromptVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcPromptVersionService 测试")
class NvcPromptVersionServiceTest {

    @Mock
    private NvcPromptVersionRepository versionRepository;
    @Mock
    private RedisService redisService;

    private NvcPromptVersionService service;

    @BeforeEach
    void setUp() {
        service = new NvcPromptVersionService(versionRepository, redisService);
    }

    private NvcPromptVersionEntity buildVersion(int version, boolean isActive, int traffic) {
        return NvcPromptVersionEntity.builder()
            .id((long) version)
            .agentScene(NvcAgentScene.DIALOGUE_GUIDE)
            .version(version)
            .systemPrompt("Prompt v" + version)
            .isActive(isActive)
            .trafficPercentage(traffic)
            .totalCalls(0L)
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Nested
    @DisplayName("createVersion()")
    class CreateVersionTests {

        @Test
        @DisplayName("创建新版本，版本号自增")
        void createsNewVersionWithIncrement() {
            when(versionRepository.findMaxVersion(NvcAgentScene.DIALOGUE_GUIDE))
                .thenReturn(2);
            when(versionRepository.save(any())).thenAnswer(i -> {
                NvcPromptVersionEntity e = i.getArgument(0);
                e.setId(3L);
                return e;
            });

            CreatePromptVersionRequest request = new CreatePromptVersionRequest(
                "新 Prompt", "优化引导语气", null);

            PromptVersionResponse result = service.createVersion(
                NvcAgentScene.DIALOGUE_GUIDE, request);

            assertEquals(3, result.version());
            assertEquals("新 Prompt", result.systemPrompt());
            assertFalse(result.isActive());
        }

        @Test
        @DisplayName("首个版本号为 1")
        void firstVersionIsOne() {
            when(versionRepository.findMaxVersion(NvcAgentScene.DIALOGUE_GUIDE))
                .thenReturn(null);
            when(versionRepository.save(any())).thenAnswer(i -> {
                NvcPromptVersionEntity e = i.getArgument(0);
                e.setId(1L);
                return e;
            });

            CreatePromptVersionRequest request = new CreatePromptVersionRequest(
                "初始 Prompt", null, null);

            PromptVersionResponse result = service.createVersion(
                NvcAgentScene.DIALOGUE_GUIDE, request);

            assertEquals(1, result.version());
        }
    }

    @Nested
    @DisplayName("activateVersion()")
    class ActivateVersionTests {

        @Test
        @DisplayName("激活版本时将其他活跃版本设为非活跃")
        void deactivatesOtherVersions() {
            NvcPromptVersionEntity v1 = buildVersion(1, true, 100);
            NvcPromptVersionEntity v2 = buildVersion(2, false, 0);

            when(versionRepository.findByAgentSceneAndVersion(
                NvcAgentScene.DIALOGUE_GUIDE, 2))
                .thenReturn(Optional.of(v2));
            when(versionRepository.findByAgentSceneAndIsActiveTrue(
                NvcAgentScene.DIALOGUE_GUIDE))
                .thenReturn(List.of(v1));
            when(versionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            PromptVersionResponse result = service.activateVersion(
                NvcAgentScene.DIALOGUE_GUIDE, 2);

            assertTrue(result.isActive());
            assertEquals(100, result.trafficPercentage());
            assertFalse(v1.getIsActive());
            assertEquals(0, v1.getTrafficPercentage());
            verify(redisService).delete("nvc:agent-config:DIALOGUE_GUIDE");
        }

        @Test
        @DisplayName("激活不存在的版本时抛出异常")
        void throwsWhenVersionNotFound() {
            when(versionRepository.findByAgentSceneAndVersion(any(), any()))
                .thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                () -> service.activateVersion(NvcAgentScene.DIALOGUE_GUIDE, 99));
        }
    }

    @Nested
    @DisplayName("selectVersion()")
    class SelectVersionTests {

        @Test
        @DisplayName("无活跃版本时返回 null")
        void returnsNullWhenNoActiveVersions() {
            when(versionRepository.findByAgentSceneAndIsActiveTrue(
                NvcAgentScene.DIALOGUE_GUIDE))
                .thenReturn(List.of());

            assertNull(service.selectVersion(NvcAgentScene.DIALOGUE_GUIDE, 1L));
        }

        @Test
        @DisplayName("单个活跃版本时直接返回")
        void returnsSingleActiveVersion() {
            NvcPromptVersionEntity v1 = buildVersion(1, true, 100);
            when(versionRepository.findByAgentSceneAndIsActiveTrue(
                NvcAgentScene.DIALOGUE_GUIDE))
                .thenReturn(List.of(v1));

            NvcPromptVersionEntity result = service.selectVersion(
                NvcAgentScene.DIALOGUE_GUIDE, 1L);

            assertEquals(1, result.getVersion());
        }

        @Test
        @DisplayName("A/B 路由按 userId 分配")
        void abRoutesByUserId() {
            NvcPromptVersionEntity v1 = buildVersion(1, true, 90);
            NvcPromptVersionEntity v2 = buildVersion(2, true, 10);
            when(versionRepository.findByAgentSceneAndIsActiveTrue(
                NvcAgentScene.DIALOGUE_GUIDE))
                .thenReturn(List.of(v1, v2));

            // userId=5 → 5%100=5 < 90 → v1
            NvcPromptVersionEntity result1 = service.selectVersion(
                NvcAgentScene.DIALOGUE_GUIDE, 5L);
            assertEquals(1, result1.getVersion());

            // userId=95 → 95%100=95 >= 90 → v2
            NvcPromptVersionEntity result2 = service.selectVersion(
                NvcAgentScene.DIALOGUE_GUIDE, 95L);
            assertEquals(2, result2.getVersion());
        }
    }

    @Nested
    @DisplayName("setTrafficSplit()")
    class SetTrafficSplitTests {

        @Test
        @DisplayName("流量百分比之和不为 100 时抛出异常")
        void throwsWhenNot100() {
            assertThrows(BusinessException.class,
                () -> service.setTrafficSplit(
                    NvcAgentScene.DIALOGUE_GUIDE, 1, 60, 2, 30));
        }
    }
}
