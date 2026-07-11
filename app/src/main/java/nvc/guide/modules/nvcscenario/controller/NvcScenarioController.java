package nvc.guide.modules.nvcscenario.controller;

import nvc.guide.common.annotation.RateLimit;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcscenario.dto.ScenarioDTO;
import nvc.guide.modules.nvcscenario.dto.ScenarioGenerateRequest;
import nvc.guide.modules.nvcscenario.dto.ScenarioQueryRequest;
import nvc.guide.modules.nvcscenario.model.NvcScenarioType;
import nvc.guide.modules.nvcscenario.service.NvcScenarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/nvc/scenarios")
@RequiredArgsConstructor
public class NvcScenarioController {

    private final NvcScenarioService scenarioService;

    /**
     * 查询场景列表
     */
    @GetMapping
    public Result<List<ScenarioDTO>> queryScenarios(
            @RequestParam(required = false) NvcScenarioType scenarioType,
            @RequestParam(required = false) NvcDifficulty difficulty) {
        var scenarios = scenarioService.queryScenarios(
                new ScenarioQueryRequest(scenarioType, difficulty))
            .stream()
            .map(scenarioService::toDTO)
            .toList();
        return Result.success(scenarios);
    }

    /**
     * 获取单个场景详情
     */
    @GetMapping("/{id}")
    public Result<ScenarioDTO> getScenario(@PathVariable Long id) {
        return Result.success(scenarioService.toDTO(scenarioService.getScenario(id)));
    }

    /**
     * AI 生成新场景
     */
    @RateLimit(count = 10)
    @PostMapping("/generate")
    public Result<ScenarioDTO> generateScenario(@Valid @RequestBody ScenarioGenerateRequest request) {
        var scenario = scenarioService.generateScenario(request);
        return Result.success(scenarioService.toDTO(scenario));
    }
}
