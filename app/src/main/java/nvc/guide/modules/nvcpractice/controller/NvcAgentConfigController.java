package nvc.guide.modules.nvcpractice.controller;

import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcpractice.dto.AgentConfigDTO;
import nvc.guide.modules.nvcpractice.dto.AgentConfigUpdateRequest;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.service.NvcAgentConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/nvc/agents")
@RequiredArgsConstructor
public class NvcAgentConfigController {

  private final NvcAgentConfigService agentConfigService;

  /**
   * 获取所有 Agent 配置
   */
  @GetMapping("/configs")
  public Result<List<AgentConfigDTO>> getAllConfigs() {
    var configs = agentConfigService.getAllConfigs().stream()
        .map(agentConfigService::toDTO)
        .toList();
    return Result.success(configs);
  }

  /**
   * 获取单个 Agent 配置
   */
  @GetMapping("/configs/{scene}")
  public Result<AgentConfigDTO> getConfig(@PathVariable NvcAgentScene scene) {
    var config = agentConfigService.getConfig(scene);
    return Result.success(agentConfigService.toDTO(config));
  }

  /**
   * 更新 Agent 配置（热更新）
   * 更新后立即生效，无需重启
   */
  @PutMapping("/configs/{scene}")
  public Result<AgentConfigDTO> updateConfig(
      @PathVariable NvcAgentScene scene,
      @Valid @RequestBody AgentConfigUpdateRequest request) {
    var updated = agentConfigService.updateConfig(scene, request);
    return Result.success(agentConfigService.toDTO(updated));
  }
}
