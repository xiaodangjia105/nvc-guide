package nvc.guide.modules.nvcpractice.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcscenario.dto.ScenarioGenerateRequest;
import nvc.guide.modules.nvcscenario.model.NvcScenarioType;
import nvc.guide.modules.nvcscenario.service.NvcScenarioService;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScenarioGenerateTool implements NvcTool {

    private final NvcScenarioService scenarioService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() { return "scenario_generate"; }

    @Override
    public String description() {
        return "AI 生成一个新的 NVC 练习场景。需要指定情境、关系和关注的 NVC 要素。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(ScenarioGenerateInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            ScenarioGenerateInput params = JsonParser.fromJson(input, ScenarioGenerateInput.class);

            NvcScenarioType scenarioType = params.scenarioType() != null
                ? NvcScenarioType.valueOf(params.scenarioType()) : null;
            NvcDifficulty difficulty = params.difficulty() != null
                ? NvcDifficulty.valueOf(params.difficulty()) : null;

            ScenarioGenerateRequest request = new ScenarioGenerateRequest(
                scenarioType, difficulty, params.focusElements(), params.description());

            var scenario = scenarioService.generateScenario(request);
            String json = objectMapper.writeValueAsString(scenario);
            return NvcToolResult.success(json);
        } catch (IllegalArgumentException e) {
            log.warn("[ScenarioGenerateTool] Invalid enum value: {}", e.getMessage());
            return NvcToolResult.failure("参数值无效: " + e.getMessage());
        } catch (Exception e) {
            log.error("[ScenarioGenerateTool] Execution failed", e);
            return NvcToolResult.failure("场景生成失败: " + e.getMessage());
        }
    }

    record ScenarioGenerateInput(String scenarioType, String difficulty,
                                  List<String> focusElements, String description) {}
}
