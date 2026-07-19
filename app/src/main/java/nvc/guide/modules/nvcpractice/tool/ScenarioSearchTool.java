package nvc.guide.modules.nvcpractice.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcscenario.dto.ScenarioQueryRequest;
import nvc.guide.modules.nvcscenario.model.NvcScenarioType;
import nvc.guide.modules.nvcscenario.service.NvcScenarioService;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScenarioSearchTool implements NvcTool {

    private final NvcScenarioService scenarioService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() { return "scenario_search"; }

    @Override
    public String description() {
        return "搜索 NVC 练习场景，可按场景类型和难度筛选。返回匹配的场景列表。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(ScenarioSearchInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            ScenarioSearchInput params = JsonParser.fromJson(input, ScenarioSearchInput.class);

            NvcScenarioType scenarioType = params.scenarioType() != null
                ? NvcScenarioType.valueOf(params.scenarioType()) : null;
            NvcDifficulty difficulty = params.difficulty() != null
                ? NvcDifficulty.valueOf(params.difficulty()) : null;

            ScenarioQueryRequest query = new ScenarioQueryRequest(scenarioType, difficulty);
            var scenarios = scenarioService.queryScenarios(query);
            String json = objectMapper.writeValueAsString(scenarios);
            return NvcToolResult.success(json);
        } catch (IllegalArgumentException e) {
            log.warn("[ScenarioSearchTool] Invalid enum value: {}", e.getMessage());
            return NvcToolResult.failure("参数值无效: " + e.getMessage());
        } catch (Exception e) {
            log.error("[ScenarioSearchTool] Execution failed", e);
            return NvcToolResult.failure("场景搜索失败: " + e.getMessage());
        }
    }

    record ScenarioSearchInput(String scenarioType, String difficulty) {}
}
