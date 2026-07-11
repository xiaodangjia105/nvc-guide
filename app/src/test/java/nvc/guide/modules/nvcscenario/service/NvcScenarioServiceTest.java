package nvc.guide.modules.nvcscenario.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.common.ai.StructuredOutputInvoker;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcscenario.dto.ScenarioDTO;
import nvc.guide.modules.nvcscenario.dto.ScenarioQueryRequest;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import nvc.guide.modules.nvcscenario.model.NvcScenarioType;
import nvc.guide.modules.nvcscenario.repository.NvcScenarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcScenarioService 测试")
class NvcScenarioServiceTest {

    @Mock
    private NvcScenarioRepository scenarioRepository;
    @Mock
    private LlmProviderRegistry llmProviderRegistry;
    @Mock
    private StructuredOutputInvoker structuredOutputInvoker;
    @Mock
    private ObjectMapper objectMapper;

    private NvcScenarioService service;

    @BeforeEach
    void setUp() {
        service = new NvcScenarioService(
            scenarioRepository, llmProviderRegistry,
            structuredOutputInvoker, objectMapper);
    }

    private NvcScenarioEntity buildScenario(Long id, String title,
            NvcScenarioType type, NvcDifficulty difficulty) {
        return NvcScenarioEntity.builder()
            .id(id)
            .title(title)
            .description("测试描述")
            .scenarioType(type)
            .difficulty(difficulty)
            .focusElements("[\"observation\"]")
            .context("测试背景")
            .isSystem(true)
            .usageCount(0)
            .build();
    }

    @Nested
    @DisplayName("queryScenarios()")
    class QueryScenariosTests {

        @Test
        @DisplayName("按类型和难度查询")
        void queryByTypeAndDifficulty() {
            ScenarioQueryRequest query = new ScenarioQueryRequest(
                NvcScenarioType.WORKPLACE, NvcDifficulty.MEDIUM);
            NvcScenarioEntity scenario = buildScenario(
                1L, "职场场景", NvcScenarioType.WORKPLACE, NvcDifficulty.MEDIUM);

            when(scenarioRepository.findByScenarioTypeAndDifficulty(
                NvcScenarioType.WORKPLACE, NvcDifficulty.MEDIUM))
                .thenReturn(List.of(scenario));

            List<NvcScenarioEntity> result = service.queryScenarios(query);

            assertEquals(1, result.size());
            assertEquals("职场场景", result.get(0).getTitle());
        }

        @Test
        @DisplayName("仅按类型查询")
        void queryByTypeOnly() {
            ScenarioQueryRequest query = new ScenarioQueryRequest(
                NvcScenarioType.FAMILY, null);
            NvcScenarioEntity scenario = buildScenario(
                1L, "家庭场景", NvcScenarioType.FAMILY, NvcDifficulty.EASY);

            when(scenarioRepository.findByScenarioType(NvcScenarioType.FAMILY))
                .thenReturn(List.of(scenario));

            List<NvcScenarioEntity> result = service.queryScenarios(query);

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("仅按难度查询")
        void queryByDifficultyOnly() {
            ScenarioQueryRequest query = new ScenarioQueryRequest(
                null, NvcDifficulty.HARD);
            NvcScenarioEntity scenario = buildScenario(
                1L, "困难场景", NvcScenarioType.INTIMATE, NvcDifficulty.HARD);

            when(scenarioRepository.findByDifficulty(NvcDifficulty.HARD))
                .thenReturn(List.of(scenario));

            List<NvcScenarioEntity> result = service.queryScenarios(query);

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("无条件查询返回系统场景")
        void queryNoConditions_returnsSystemScenarios() {
            ScenarioQueryRequest query = new ScenarioQueryRequest(null, null);
            NvcScenarioEntity scenario = buildScenario(
                1L, "系统场景", NvcScenarioType.WORKPLACE, NvcDifficulty.MEDIUM);

            when(scenarioRepository.findByIsSystemTrueOrderByUsageCountDesc())
                .thenReturn(List.of(scenario));

            List<NvcScenarioEntity> result = service.queryScenarios(query);

            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("getScenario()")
    class GetScenarioTests {

        @Test
        @DisplayName("场景存在时返回场景")
        void found_returnsScenario() {
            NvcScenarioEntity scenario = buildScenario(
                1L, "测试场景", NvcScenarioType.WORKPLACE, NvcDifficulty.MEDIUM);

            when(scenarioRepository.findById(1L))
                .thenReturn(Optional.of(scenario));

            NvcScenarioEntity result = service.getScenario(1L);

            assertEquals("测试场景", result.getTitle());
        }

        @Test
        @DisplayName("场景不存在时抛出异常")
        void notFound_throwsException() {
            when(scenarioRepository.findById(999L))
                .thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                () -> service.getScenario(999L));
        }
    }

    @Nested
    @DisplayName("incrementUsage()")
    class IncrementUsageTests {

        @Test
        @DisplayName("场景存在时增加使用次数")
        void found_incrementsUsage() {
            NvcScenarioEntity scenario = buildScenario(
                1L, "测试场景", NvcScenarioType.WORKPLACE, NvcDifficulty.MEDIUM);
            scenario.setUsageCount(5);

            when(scenarioRepository.findById(1L))
                .thenReturn(Optional.of(scenario));
            when(scenarioRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

            service.incrementUsage(1L);

            assertEquals(6, scenario.getUsageCount());
            verify(scenarioRepository).save(scenario);
        }

        @Test
        @DisplayName("场景不存在时不抛出异常")
        void notFound_doesNothing() {
            when(scenarioRepository.findById(999L))
                .thenReturn(Optional.empty());

            assertDoesNotThrow(() -> service.incrementUsage(999L));
            verify(scenarioRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("toDTO()")
    class ToDTOTests {

        @Test
        @DisplayName("正确转换 Entity 到 DTO")
        void convertsEntityToDTO() {
            NvcScenarioEntity entity = buildScenario(
                1L, "测试场景", NvcScenarioType.WORKPLACE, NvcDifficulty.MEDIUM);

            ScenarioDTO dto = service.toDTO(entity);

            assertEquals(1L, dto.id());
            assertEquals("测试场景", dto.title());
            assertEquals(NvcScenarioType.WORKPLACE, dto.scenarioType());
            assertEquals(NvcDifficulty.MEDIUM, dto.difficulty());
            assertTrue(dto.isSystem());
            assertEquals(0, dto.usageCount());
        }
    }
}
