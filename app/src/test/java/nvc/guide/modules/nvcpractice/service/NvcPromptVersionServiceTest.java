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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcPromptVersionService 测试")
class NvcPromptVersionServiceTest {

    @Mock
    private NvcPromptVersionRepository versionRepository;

    @Mock
    private RedisService redisService;

    @InjectMocks
    private NvcPromptVersionService service;

    private static final NvcAgentScene SCENE = NvcAgentScene.DIALOGUE_GUIDE;

    // ===== Helper =====

    private NvcPromptVersionEntity buildEntity(Long id, int version, boolean isActive, int trafficPct) {
        NvcPromptVersionEntity entity = NvcPromptVersionEntity.builder()
                .id(id)
                .agentScene(SCENE)
                .version(version)
                .systemPrompt("System prompt for v" + version)
                .isActive(isActive)
                .trafficPercentage(trafficPct)
                .totalCalls(0L)
                .createdAt(LocalDateTime.now())
                .build();
        return entity;
    }

    // ==================== selectVersion ====================

    @Nested
    @DisplayName("selectVersion — A/B 路由")
    class SelectVersion {

        @Test
        @DisplayName("无活跃版本时返回 null")
        void noActiveVersionsReturnsNull() {
            when(versionRepository.findByAgentSceneAndIsActiveTrue(SCENE))
                    .thenReturn(Collections.emptyList());

            NvcPromptVersionEntity result = service.selectVersion(SCENE, 1L);

            assertNull(result);
        }

        @Test
        @DisplayName("只有一个活跃版本时直接返回该版本")
        void singleActiveVersionReturned() {
            NvcPromptVersionEntity v1 = buildEntity(1L, 1, true, 100);
            when(versionRepository.findByAgentSceneAndIsActiveTrue(SCENE))
                    .thenReturn(List.of(v1));

            NvcPromptVersionEntity result = service.selectVersion(SCENE, 42L);

            assertEquals(v1, result);
        }

        @Test
        @DisplayName("两个版本 90/10 分流 — 大部分 userId 应落在 v1")
        void abRouting9010MostInV1() {
            NvcPromptVersionEntity v1 = buildEntity(1L, 1, true, 90);
            NvcPromptVersionEntity v2 = buildEntity(2L, 2, true, 10);
            when(versionRepository.findByAgentSceneAndIsActiveTrue(SCENE))
                    .thenReturn(List.of(v1, v2));

            int v1Count = 0;
            int v2Count = 0;
            // 测试 100 个不同的 userId
            for (long userId = 0; userId < 100; userId++) {
                NvcPromptVersionEntity result = service.selectVersion(SCENE, userId);
                if (result.getId().equals(1L)) {
                    v1Count++;
                } else {
                    v2Count++;
                }
            }

            // 90/10 分流：v1 应拿到约 90%（bucket = userId % 100, bucket < 90 → v1）
            assertEquals(90, v1Count, "90/10 分流下 v1 应收到 90 个 userId");
            assertEquals(10, v2Count, "90/10 分流下 v2 应收到 10 个 userId");
        }

        @Test
        @DisplayName("两个版本 50/50 分流 — 均匀分配")
        void abRouting5050EvenSplit() {
            NvcPromptVersionEntity v1 = buildEntity(1L, 1, true, 50);
            NvcPromptVersionEntity v2 = buildEntity(2L, 2, true, 50);
            when(versionRepository.findByAgentSceneAndIsActiveTrue(SCENE))
                    .thenReturn(List.of(v1, v2));

            int v1Count = 0;
            for (long userId = 0; userId < 100; userId++) {
                NvcPromptVersionEntity result = service.selectVersion(SCENE, userId);
                if (result.getId().equals(1L)) {
                    v1Count++;
                }
            }

            assertEquals(50, v1Count, "50/50 分流下每个版本应收到 50 个 userId");
        }

        @Test
        @DisplayName("userId 为 null 时返回第一个活跃版本")
        void nullUserIdReturnsFirstVersion() {
            NvcPromptVersionEntity v1 = buildEntity(1L, 1, true, 60);
            NvcPromptVersionEntity v2 = buildEntity(2L, 2, true, 40);
            when(versionRepository.findByAgentSceneAndIsActiveTrue(SCENE))
                    .thenReturn(List.of(v1, v2));

            NvcPromptVersionEntity result = service.selectVersion(SCENE, null);

            assertEquals(v1, result, "userId 为 null 时应 fallback 到第一个版本");
        }

        @Test
        @DisplayName("三个版本 33/33/34 分流 — bucket 分配正确")
        void threeWaySplit() {
            NvcPromptVersionEntity v1 = buildEntity(1L, 1, true, 33);
            NvcPromptVersionEntity v2 = buildEntity(2L, 2, true, 33);
            NvcPromptVersionEntity v3 = buildEntity(3L, 3, true, 34);
            when(versionRepository.findByAgentSceneAndIsActiveTrue(SCENE))
                    .thenReturn(List.of(v1, v2, v3));

            int[] counts = new int[3];
            for (long userId = 0; userId < 100; userId++) {
                NvcPromptVersionEntity result = service.selectVersion(SCENE, userId);
                counts[result.getVersion() - 1]++;
            }

            assertEquals(33, counts[0], "v1 应收到 33 个 userId");
            assertEquals(33, counts[1], "v2 应收到 33 个 userId");
            assertEquals(34, counts[2], "v3 应收到 34 个 userId");
        }

        @Test
        @DisplayName("大量 userId — 分流比例近似正确（90/10）")
        void abRoutingLargeUserIds() {
            NvcPromptVersionEntity v1 = buildEntity(1L, 1, true, 90);
            NvcPromptVersionEntity v2 = buildEntity(2L, 2, true, 10);
            when(versionRepository.findByAgentSceneAndIsActiveTrue(SCENE))
                    .thenReturn(List.of(v1, v2));

            int v1Count = 0;
            int total = 10000;
            for (long userId = 0; userId < total; userId++) {
                NvcPromptVersionEntity result = service.selectVersion(SCENE, userId);
                if (result.getId().equals(1L)) {
                    v1Count++;
                }
            }

            // userId & Long.MAX_VALUE % 100 是确定性映射，10000 个 userId 正好覆盖 0..99 循环 100 次
            assertEquals(9000, v1Count, "10000 个 userId 中 90% 应落在 v1");
        }
    }

    // ==================== setTrafficSplit ====================

    @Nested
    @DisplayName("setTrafficSplit — 流量分配")
    class SetTrafficSplit {

        @Test
        @DisplayName("正常设置 80/20 流量分配")
        void validTrafficSplit8020() {
            NvcPromptVersionEntity v1 = buildEntity(1L, 1, true, 100);
            NvcPromptVersionEntity v2 = buildEntity(2L, 2, false, 0);
            when(versionRepository.findByAgentSceneAndVersion(SCENE, 1)).thenReturn(Optional.of(v1));
            when(versionRepository.findByAgentSceneAndVersion(SCENE, 2)).thenReturn(Optional.of(v2));

            service.setTrafficSplit(SCENE, 1, 80, 2, 20);

            assertTrue(v1.getIsActive());
            assertEquals(80, v1.getTrafficPercentage());
            assertTrue(v2.getIsActive());
            assertEquals(20, v2.getTrafficPercentage());
            verify(versionRepository).save(v1);
            verify(versionRepository).save(v2);
            verify(redisService).delete("nvc:agent-config:" + SCENE.name());
        }

        @Test
        @DisplayName("百分比之和不等于 100 — 抛出 BusinessException")
        void percentagesNotSumTo100Throws() {
            assertThrows(BusinessException.class,
                    () -> service.setTrafficSplit(SCENE, 1, 60, 2, 30));
        }

        @Test
        @DisplayName("百分比之和超过 100 — 抛出 BusinessException")
        void percentagesOver100Throws() {
            assertThrows(BusinessException.class,
                    () -> service.setTrafficSplit(SCENE, 1, 70, 2, 40));
        }

        @Test
        @DisplayName("pct1 为 null — 抛出 BusinessException")
        void nullPct1Throws() {
            assertThrows(BusinessException.class,
                    () -> service.setTrafficSplit(SCENE, 1, null, 2, 50));
        }

        @Test
        @DisplayName("pct2 为 null — 抛出 BusinessException")
        void nullPct2Throws() {
            assertThrows(BusinessException.class,
                    () -> service.setTrafficSplit(SCENE, 1, 50, 2, null));
        }

        @Test
        @DisplayName("版本不存在 — 抛出 BusinessException")
        void versionNotFoundThrows() {
            when(versionRepository.findByAgentSceneAndVersion(SCENE, 99))
                    .thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                    () -> service.setTrafficSplit(SCENE, 99, 50, 2, 50));
        }

        @Test
        @DisplayName("第二个版本不存在 — 抛出 BusinessException")
        void secondVersionNotFoundThrows() {
            NvcPromptVersionEntity v1 = buildEntity(1L, 1, true, 100);
            when(versionRepository.findByAgentSceneAndVersion(SCENE, 1)).thenReturn(Optional.of(v1));
            when(versionRepository.findByAgentSceneAndVersion(SCENE, 99))
                    .thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                    () -> service.setTrafficSplit(SCENE, 1, 50, 99, 50));
        }
    }

    // ==================== activateVersion ====================

    @Nested
    @DisplayName("activateVersion — 版本激活")
    class ActivateVersion {

        @Test
        @DisplayName("激活版本 — 设为活跃并清除同场景其他活跃版本")
        void activateVersionDeactivatesOthers() {
            NvcPromptVersionEntity target = buildEntity(1L, 2, false, 0);
            NvcPromptVersionEntity other = buildEntity(2L, 1, true, 100);

            when(versionRepository.findByAgentSceneAndVersion(SCENE, 2))
                    .thenReturn(Optional.of(target));
            when(versionRepository.findByAgentSceneAndIsActiveTrue(SCENE))
                    .thenReturn(List.of(other));
            when(versionRepository.save(any(NvcPromptVersionEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PromptVersionResponse response = service.activateVersion(SCENE, 2);

            assertNotNull(response);
            assertTrue(target.getIsActive());
            assertEquals(100, target.getTrafficPercentage());
            assertNotNull(target.getActivatedAt());
            // other 被取消激活
            assertFalse(other.getIsActive());
            assertEquals(0, other.getTrafficPercentage());
            verify(redisService).delete("nvc:agent-config:" + SCENE.name());
        }

        @Test
        @DisplayName("激活不存在的版本 — 抛出 BusinessException")
        void activateNonExistentVersionThrows() {
            when(versionRepository.findByAgentSceneAndVersion(SCENE, 99))
                    .thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                    () -> service.activateVersion(SCENE, 99));
        }
    }

    // ==================== createVersion ====================

    @Nested
    @DisplayName("createVersion — 版本创建")
    class CreateVersion {

        @Test
        @DisplayName("首次创建版本 — 版本号为 1")
        void createFirstVersion() {
            when(versionRepository.findMaxVersion(SCENE)).thenReturn(null);
            when(versionRepository.save(any(NvcPromptVersionEntity.class)))
                    .thenAnswer(inv -> {
                        NvcPromptVersionEntity entity = inv.getArgument(0);
                        entity.setId(1L);
                        return entity;
                    });

            CreatePromptVersionRequest request = new CreatePromptVersionRequest(
                    "New system prompt", "Initial version", null);

            PromptVersionResponse response = service.createVersion(SCENE, request);

            assertNotNull(response);
            assertEquals(1, response.version());
            assertEquals(SCENE, response.agentScene());
            assertEquals("New system prompt", response.systemPrompt());
            assertFalse(response.isActive());
        }

        @Test
        @DisplayName("已有版本时 — 版本号递增")
        void createNextVersionIncrements() {
            when(versionRepository.findMaxVersion(SCENE)).thenReturn(3);
            when(versionRepository.save(any(NvcPromptVersionEntity.class)))
                    .thenAnswer(inv -> {
                        NvcPromptVersionEntity entity = inv.getArgument(0);
                        entity.setId(4L);
                        return entity;
                    });

            CreatePromptVersionRequest request = new CreatePromptVersionRequest(
                    "Updated prompt", "Bug fix", 10);

            PromptVersionResponse response = service.createVersion(SCENE, request);

            assertEquals(4, response.version());
            assertEquals(10, response.trafficPercentage());
        }

        @Test
        @DisplayName("trafficPercentage 为 null 时默认为 0")
        void nullTrafficPercentageDefaultsToZero() {
            when(versionRepository.findMaxVersion(SCENE)).thenReturn(0);
            when(versionRepository.save(any(NvcPromptVersionEntity.class)))
                    .thenAnswer(inv -> {
                        NvcPromptVersionEntity entity = inv.getArgument(0);
                        entity.setId(1L);
                        return entity;
                    });

            CreatePromptVersionRequest request = new CreatePromptVersionRequest(
                    "Prompt", "note", null);

            PromptVersionResponse response = service.createVersion(SCENE, request);

            assertEquals(0, response.trafficPercentage());
        }
    }

    // ==================== getVersions ====================

    @Nested
    @DisplayName("getVersions — 版本列表")
    class GetVersions {

        @Test
        @DisplayName("返回场景的所有版本（按版本号降序）")
        void returnsAllVersions() {
            NvcPromptVersionEntity v1 = buildEntity(1L, 1, true, 100);
            NvcPromptVersionEntity v2 = buildEntity(2L, 2, false, 0);
            when(versionRepository.findByAgentSceneOrderByVersionDesc(SCENE))
                    .thenReturn(List.of(v2, v1));

            List<PromptVersionResponse> results = service.getVersions(SCENE);

            assertEquals(2, results.size());
            assertEquals(2, results.get(0).version());
            assertEquals(1, results.get(1).version());
        }

        @Test
        @DisplayName("无版本时返回空列表")
        void noVersionsReturnsEmptyList() {
            when(versionRepository.findByAgentSceneOrderByVersionDesc(SCENE))
                    .thenReturn(Collections.emptyList());

            List<PromptVersionResponse> results = service.getVersions(SCENE);

            assertTrue(results.isEmpty());
        }
    }

    // ==================== incrementCallCount ====================

    @Nested
    @DisplayName("incrementCallCount — 调用计数")
    class IncrementCallCount {

        @Test
        @DisplayName("委托 repository 执行原子自增")
        void delegatesToRepository() {
            service.incrementCallCount(42L);

            verify(versionRepository).incrementCallCount(42L);
        }
    }
}
