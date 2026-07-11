package nvc.guide.modules.nvcscenario.controller;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcscenario.dto.ScenarioDTO;
import nvc.guide.modules.nvcscenario.dto.ScenarioGenerateRequest;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import nvc.guide.modules.nvcscenario.model.NvcScenarioType;
import nvc.guide.modules.nvcscenario.service.NvcScenarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcScenarioController 测试")
class NvcScenarioControllerTest {

    @Mock
    private NvcScenarioService scenarioService;
    @InjectMocks
    private NvcScenarioController controller;

    private NvcScenarioEntity buildScenario(Long id, NvcScenarioType type, NvcDifficulty diff) {
        return NvcScenarioEntity.builder()
            .id(id)
            .title("测试场景 " + id)
            .description("场景描述 " + id)
            .scenarioType(type)
            .difficulty(diff)
            .focusElements("[\"观察\"]")
            .context("背景信息")
            .tags("[\"职场\"]")
            .isSystem(true)
            .usageCount(0)
            .build();
    }

    private ScenarioDTO buildDTO(Long id, NvcScenarioType type, NvcDifficulty diff) {
        return new ScenarioDTO(
            id, "测试场景 " + id, "场景描述 " + id,
            type, diff, "[\"观察\"]", "背景信息", null, "[\"职场\"]", true, 0);
    }

    @Test
    @DisplayName("GET /api/nvc/scenarios 返回所有场景")
    void queryScenarios_returnsList() {
        NvcScenarioEntity s1 = buildScenario(1L, NvcScenarioType.WORKPLACE, NvcDifficulty.MEDIUM);
        NvcScenarioEntity s2 = buildScenario(2L, NvcScenarioType.FAMILY, NvcDifficulty.EASY);
        ScenarioDTO dto1 = buildDTO(1L, NvcScenarioType.WORKPLACE, NvcDifficulty.MEDIUM);
        ScenarioDTO dto2 = buildDTO(2L, NvcScenarioType.FAMILY, NvcDifficulty.EASY);

        when(scenarioService.queryScenarios(any())).thenReturn(List.of(s1, s2));
        when(scenarioService.toDTO(s1)).thenReturn(dto1);
        when(scenarioService.toDTO(s2)).thenReturn(dto2);

        Result<List<ScenarioDTO>> result = controller.queryScenarios(null, null);

        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
        assertEquals(NvcScenarioType.WORKPLACE, result.getData().get(0).scenarioType());
        assertEquals(NvcScenarioType.FAMILY, result.getData().get(1).scenarioType());
    }

    @Test
    @DisplayName("GET /api/nvc/scenarios/{id} 返回单个场景")
    void getScenario_returnsSingle() {
        NvcScenarioEntity scenario = buildScenario(1L, NvcScenarioType.WORKPLACE, NvcDifficulty.MEDIUM);
        ScenarioDTO dto = buildDTO(1L, NvcScenarioType.WORKPLACE, NvcDifficulty.MEDIUM);

        when(scenarioService.getScenario(1L)).thenReturn(scenario);
        when(scenarioService.toDTO(scenario)).thenReturn(dto);

        Result<ScenarioDTO> result = controller.getScenario(1L);

        assertEquals(200, result.getCode());
        assertEquals("测试场景 1", result.getData().title());
    }

    @Test
    @DisplayName("GET /api/nvc/scenarios/{id} 场景不存在时抛出异常")
    void getScenario_notFound_throwsException() {
        when(scenarioService.getScenario(999L))
            .thenThrow(new BusinessException(ErrorCode.NVC_SCENARIO_NOT_FOUND, "Scenario not found: 999"));

        assertThrows(BusinessException.class, () -> controller.getScenario(999L));
    }

    @Test
    @DisplayName("POST /api/nvc/scenarios/generate 生成新场景")
    void generateScenario_createsAndReturns() {
        NvcScenarioEntity scenario = buildScenario(10L, NvcScenarioType.WORKPLACE, NvcDifficulty.HARD);
        ScenarioDTO dto = buildDTO(10L, NvcScenarioType.WORKPLACE, NvcDifficulty.HARD);
        ScenarioGenerateRequest request = new ScenarioGenerateRequest(
            NvcScenarioType.WORKPLACE, NvcDifficulty.HARD, List.of("观察", "感受"), "职场冲突");

        when(scenarioService.generateScenario(request)).thenReturn(scenario);
        when(scenarioService.toDTO(scenario)).thenReturn(dto);

        Result<ScenarioDTO> result = controller.generateScenario(request);

        assertEquals(200, result.getCode());
        assertEquals(10L, result.getData().id());
        assertEquals(NvcScenarioType.WORKPLACE, result.getData().scenarioType());
    }

    @Test
    @DisplayName("GET /api/nvc/scenarios 按类型筛选")
    void queryScenarios_filterByType() {
        NvcScenarioEntity s1 = buildScenario(1L, NvcScenarioType.WORKPLACE, NvcDifficulty.MEDIUM);
        ScenarioDTO dto1 = buildDTO(1L, NvcScenarioType.WORKPLACE, NvcDifficulty.MEDIUM);

        when(scenarioService.queryScenarios(any())).thenReturn(List.of(s1));
        when(scenarioService.toDTO(s1)).thenReturn(dto1);

        Result<List<ScenarioDTO>> result =
            controller.queryScenarios(NvcScenarioType.WORKPLACE, null);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }
}
