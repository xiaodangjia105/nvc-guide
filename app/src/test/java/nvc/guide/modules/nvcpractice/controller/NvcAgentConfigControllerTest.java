package nvc.guide.modules.nvcpractice.controller;

import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcpractice.dto.AgentConfigDTO;
import nvc.guide.modules.nvcpractice.dto.AgentConfigUpdateRequest;
import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.service.NvcAgentConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcAgentConfigController 测试")
class NvcAgentConfigControllerTest {

  @Mock private NvcAgentConfigService agentConfigService;
  @InjectMocks private NvcAgentConfigController controller;

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

  @Test
  @DisplayName("GET /api/nvc/agents/configs 返回所有配置")
  void getAllConfigs_returnsList() {
    NvcAgentConfigEntity config = buildConfig(NvcAgentScene.DIALOGUE_GUIDE);
    AgentConfigDTO dto = new AgentConfigDTO(
        NvcAgentScene.DIALOGUE_GUIDE, "测试 Agent", "测试描述",
        "你是测试 Agent", "mimo", "test-model", 0.7, 2000, 0.9, true,
        70, 10, 30);

    when(agentConfigService.getAllConfigs()).thenReturn(List.of(config));
    when(agentConfigService.toDTO(config)).thenReturn(dto);

    Result<List<AgentConfigDTO>> result = controller.getAllConfigs();

    assertEquals(200, result.getCode());
    assertEquals(1, result.getData().size());
    assertEquals(NvcAgentScene.DIALOGUE_GUIDE, result.getData().get(0).agentScene());
  }

  @Test
  @DisplayName("GET /api/nvc/agents/configs/{scene} 返回单个配置")
  void getConfig_returnsSingle() {
    NvcAgentConfigEntity config = buildConfig(NvcAgentScene.SCENARIO_GENERATOR);
    AgentConfigDTO dto = new AgentConfigDTO(
        NvcAgentScene.SCENARIO_GENERATOR, "测试 Agent", "测试描述",
        "你是测试 Agent", "mimo", "test-model", 0.7, 2000, 0.9, true,
        70, 10, 30);

    when(agentConfigService.getConfig(NvcAgentScene.SCENARIO_GENERATOR)).thenReturn(config);
    when(agentConfigService.toDTO(config)).thenReturn(dto);

    Result<AgentConfigDTO> result = controller.getConfig(NvcAgentScene.SCENARIO_GENERATOR);

    assertEquals(200, result.getCode());
    assertEquals(NvcAgentScene.SCENARIO_GENERATOR, result.getData().agentScene());
  }

  @Test
  @DisplayName("PUT /api/nvc/agents/configs/{scene} 更新配置")
  void updateConfig_updatesAndReturns() {
    NvcAgentConfigEntity updated = buildConfig(NvcAgentScene.DIALOGUE_GUIDE);
    updated.setTemperature(0.9);
    AgentConfigDTO dto = new AgentConfigDTO(
        NvcAgentScene.DIALOGUE_GUIDE, "测试 Agent", "测试描述",
        "你是测试 Agent", "mimo", "test-model", 0.9, 2000, 0.9, true,
        70, 10, 30);

    when(agentConfigService.updateConfig(any(), any())).thenReturn(updated);
    when(agentConfigService.toDTO(updated)).thenReturn(dto);

    AgentConfigUpdateRequest request = new AgentConfigUpdateRequest(
        null, null, null, 0.9, null, null, null, null, null, null);

    Result<AgentConfigDTO> result = controller.updateConfig(
        NvcAgentScene.DIALOGUE_GUIDE, request);

    assertEquals(200, result.getCode());
    assertEquals(0.9, result.getData().temperature());
  }
}
