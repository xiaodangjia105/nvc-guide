package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcpractice.dto.AgentConfigDTO;
import nvc.guide.modules.nvcpractice.dto.AgentConfigUpdateRequest;
import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.repository.NvcAgentConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcAgentConfigService 测试")
class NvcAgentConfigServiceTest {

  @Mock
  private NvcAgentConfigRepository agentConfigRepository;
  @Mock
  private RedisService redisService;

  private NvcAgentConfigService service;

  @BeforeEach
  void setUp() {
    service = new NvcAgentConfigService(agentConfigRepository, redisService);
  }

  private NvcAgentConfigEntity buildConfig(NvcAgentScene scene) {
    return NvcAgentConfigEntity.builder()
        .id(1L)
        .agentScene(scene)
        .displayName("测试 Agent")
        .description("测试描述")
        .systemPrompt("你是测试 Agent")
        .modelProvider("mimo")
        .modelName("test-model")
        .temperature(0.7)
        .maxTokens(2000)
        .topP(0.9)
        .isEnabled(true)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  @Nested
  @DisplayName("getConfig()")
  class GetConfigTests {

    @Test
    @DisplayName("缓存命中时直接返回缓存数据")
    void cacheHit_returnsCached() {
      NvcAgentConfigEntity cached = buildConfig(NvcAgentScene.DIALOGUE_GUIDE);
      when(redisService.getOrLoad(anyString(), any(Duration.class), any()))
          .thenReturn(cached);

      NvcAgentConfigEntity result = service.getConfig(NvcAgentScene.DIALOGUE_GUIDE);

      assertEquals(NvcAgentScene.DIALOGUE_GUIDE, result.getAgentScene());
      verify(agentConfigRepository, never()).findByAgentScene(any());
    }

    @Test
    @DisplayName("缓存未命中时从 DB 加载")
    void cacheMiss_loadsFromDb() {
      NvcAgentConfigEntity dbConfig = buildConfig(NvcAgentScene.SCENARIO_GENERATOR);
      when(redisService.getOrLoad(anyString(), any(Duration.class), any()))
          .thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<String, NvcAgentConfigEntity> loader = invocation.getArgument(2);
            return loader.apply("test-key");
          });
      when(agentConfigRepository.findByAgentScene(NvcAgentScene.SCENARIO_GENERATOR))
          .thenReturn(Optional.of(dbConfig));

      NvcAgentConfigEntity result = service.getConfig(NvcAgentScene.SCENARIO_GENERATOR);

      assertEquals(NvcAgentScene.SCENARIO_GENERATOR, result.getAgentScene());
      verify(agentConfigRepository).findByAgentScene(NvcAgentScene.SCENARIO_GENERATOR);
    }

    @Test
    @DisplayName("DB 也不存在时抛出 BusinessException")
    void notFound_throwsException() {
      when(redisService.getOrLoad(anyString(), any(Duration.class), any()))
          .thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<String, NvcAgentConfigEntity> loader = invocation.getArgument(2);
            return loader.apply("test-key");
          });
      when(agentConfigRepository.findByAgentScene(NvcAgentScene.EMPATHY_COACH))
          .thenReturn(Optional.empty());

      assertThrows(BusinessException.class,
          () -> service.getConfig(NvcAgentScene.EMPATHY_COACH));
    }
  }

  @Nested
  @DisplayName("updateConfig()")
  class UpdateConfigTests {

    @Test
    @DisplayName("更新非 null 字段并清除缓存")
    void updatesFieldsAndClearsCache() {
      NvcAgentConfigEntity existing = buildConfig(NvcAgentScene.DIALOGUE_GUIDE);
      when(agentConfigRepository.findByAgentScene(NvcAgentScene.DIALOGUE_GUIDE))
          .thenReturn(Optional.of(existing));
      when(agentConfigRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      AgentConfigUpdateRequest request = new AgentConfigUpdateRequest(
          "新提示词", null, null, 0.9, null, null, null);

      NvcAgentConfigEntity result = service.updateConfig(
          NvcAgentScene.DIALOGUE_GUIDE, request);

      assertEquals("新提示词", result.getSystemPrompt());
      assertEquals(0.9, result.getTemperature());
      // 未传的字段保持原值
      assertEquals("mimo", result.getModelProvider());
      verify(redisService).delete("nvc:agent-config:DIALOGUE_GUIDE");
    }

    @Test
    @DisplayName("更新不存在的配置时抛出异常")
    void notFound_throwsException() {
      when(agentConfigRepository.findByAgentScene(any()))
          .thenReturn(Optional.empty());

      AgentConfigUpdateRequest request = new AgentConfigUpdateRequest(
          null, null, null, null, null, null, null);

      assertThrows(BusinessException.class,
          () -> service.updateConfig(NvcAgentScene.EMPATHY_COACH, request));
    }
  }

  @Nested
  @DisplayName("toDTO()")
  class ToDTOTests {

    @Test
    @DisplayName("正确转换 Entity 到 DTO")
    void convertsEntityToDTO() {
      NvcAgentConfigEntity entity = buildConfig(NvcAgentScene.STEP_OBSERVE_COACH);

      AgentConfigDTO dto = service.toDTO(entity);

      assertEquals(NvcAgentScene.STEP_OBSERVE_COACH, dto.agentScene());
      assertEquals("测试 Agent", dto.displayName());
      assertEquals(0.7, dto.temperature());
      assertTrue(dto.isEnabled());
    }
  }
}
