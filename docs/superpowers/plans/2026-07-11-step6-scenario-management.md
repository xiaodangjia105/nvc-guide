# Step 6：场景库管理实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现场景库的 CRUD 和 AI 生成功能，为 NVC 练习提供场景驱动模式支持。

**Architecture:** 三层架构（Controller → Service → Repository），使用现有 NvcScenarioEntity 和 NvcScenarioRepository，新增 Service 层业务逻辑、DTO 数据传输、Controller REST API。AI 生成通过 LlmProviderRegistry 调用大模型。

**Tech Stack:** Spring Boot 4.0, Java 21, Spring Data JPA, LLM (via LlmProviderRegistry), Jackson

## Global Constraints

- 包名：`nvc.guide.modules.nvcscenario`
- 使用 `@Slf4j` 日志注解
- 使用 `@RequiredArgsConstructor` 依赖注入
- 使用 `record` 作为 DTO
- 异常使用 `BusinessException(ErrorCode.XXX)`
- API 路径：`/api/nvc/scenarios`
- 返回类型：`Result<T>`

---

### Task 1: 创建 DTO 类

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcscenario/dto/ScenarioDTO.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcscenario/dto/ScenarioGenerateRequest.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcscenario/dto/ScenarioQueryRequest.java`

**Interfaces:**
- Produces: `ScenarioDTO`, `ScenarioGenerateRequest`, `ScenarioQueryRequest` records

- [ ] **Step 1: 创建 ScenarioDTO**

```java
package nvc.guide.modules.nvcscenario.dto;

import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcscenario.model.NvcScenarioType;

public record ScenarioDTO(
    Long id,
    String title,
    String description,
    NvcScenarioType scenarioType,
    NvcDifficulty difficulty,
    String focusElements,
    String context,
    String sampleDialogue,
    String tags,
    Boolean isSystem,
    Integer usageCount
) {}
```

- [ ] **Step 2: 创建 ScenarioGenerateRequest**

```java
package nvc.guide.modules.nvcscenario.dto;

import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcscenario.model.NvcScenarioType;

import java.util.List;

public record ScenarioGenerateRequest(
    NvcScenarioType scenarioType,
    NvcDifficulty difficulty,
    List<String> focusElements,
    String description
) {}
```

- [ ] **Step 3: 创建 ScenarioQueryRequest**

```java
package nvc.guide.modules.nvcscenario.dto;

import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcscenario.model.NvcScenarioType;

public record ScenarioQueryRequest(
    NvcScenarioType scenarioType,
    NvcDifficulty difficulty
) {}
```

- [ ] **Step 4: 验证编译**

Run: `cd app && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcscenario/dto/
git commit -m "feat(step6): add scenario DTOs"
```

---

### Task 2: 创建 NvcScenarioService 基础方法

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcscenario/service/NvcScenarioService.java`

**Interfaces:**
- Consumes: `NvcScenarioRepository`, `LlmProviderRegistry`, `StructuredOutputInvoker`, `ObjectMapper`
- Produces: `NvcScenarioService` with `queryScenarios()`, `getScenario()`, `incrementUsage()`, `toDTO()`

- [ ] **Step 1: 创建 NvcScenarioService 骨架**

```java
package nvc.guide.modules.nvcscenario.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.common.ai.StructuredOutputInvoker;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcscenario.dto.ScenarioDTO;
import nvc.guide.modules.nvcscenario.dto.ScenarioGenerateRequest;
import nvc.guide.modules.nvcscenario.dto.ScenarioQueryRequest;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import nvc.guide.modules.nvcscenario.model.NvcScenarioType;
import nvc.guide.modules.nvcscenario.repository.NvcScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcScenarioService {

    private final NvcScenarioRepository scenarioRepository;
    private final LlmProviderRegistry llmProviderRegistry;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final ObjectMapper objectMapper;

    /**
     * 查询场景列表（按类型和难度筛选）
     */
    public List<NvcScenarioEntity> queryScenarios(ScenarioQueryRequest query) {
        if (query.scenarioType() != null && query.difficulty() != null) {
            return scenarioRepository.findByScenarioTypeAndDifficulty(
                query.scenarioType(), query.difficulty());
        }
        if (query.scenarioType() != null) {
            return scenarioRepository.findByScenarioType(query.scenarioType());
        }
        if (query.difficulty() != null) {
            return scenarioRepository.findByDifficulty(query.difficulty());
        }
        return scenarioRepository.findByIsSystemTrueOrderByUsageCountDesc();
    }

    /**
     * 获取单个场景
     */
    public NvcScenarioEntity getScenario(Long id) {
        return scenarioRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NVC_SCENARIO_NOT_FOUND,
                "Scenario not found: " + id));
    }

    /**
     * 场景使用次数 +1
     */
    public void incrementUsage(Long scenarioId) {
        scenarioRepository.findById(scenarioId).ifPresent(scenario -> {
            scenario.setUsageCount(scenario.getUsageCount() + 1);
            scenarioRepository.save(scenario);
        });
    }

    /**
     * 转换为 DTO
     */
    public ScenarioDTO toDTO(NvcScenarioEntity entity) {
        return new ScenarioDTO(
            entity.getId(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getScenarioType(),
            entity.getDifficulty(),
            entity.getFocusElements(),
            entity.getContext(),
            entity.getSampleDialogue(),
            entity.getTags(),
            entity.getIsSystem(),
            entity.getUsageCount()
        );
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd app && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcscenario/service/NvcScenarioService.java
git commit -m "feat(step6): add NvcScenarioService with basic methods"
```

---

### Task 3: 为 NvcScenarioService 编写单元测试

**Files:**
- Create: `app/src/test/java/nvc/guide/modules/nvcscenario/service/NvcScenarioServiceTest.java`

**Interfaces:**
- Tests: `queryScenarios()`, `getScenario()`, `incrementUsage()`, `toDTO()`

- [ ] **Step 1: 创建测试类**

```java
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
```

- [ ] **Step 2: 运行测试**

Run: `cd app && ../gradlew test --tests "nvc.guide.modules.nvcscenario.service.NvcScenarioServiceTest"`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/nvc/guide/modules/nvcscenario/service/NvcScenarioServiceTest.java
git commit -m "test(step6): add NvcScenarioService unit tests"
```

---

### Task 4: 实现 AI 生成场景功能

**Files:**
- Modify: `app/src/main/java/nvc/guide/modules/nvcscenario/service/NvcScenarioService.java`

**Interfaces:**
- Consumes: `LlmProviderRegistry`, `StructuredOutputInvoker`, `ObjectMapper`, `nvc-scenario-generate-system.st`
- Produces: `generateScenario(ScenarioGenerateRequest)` method

- [ ] **Step 1: 添加 generateScenario 方法**

在 `NvcScenarioService.java` 的 `incrementUsage()` 方法之前添加：

```java
/**
 * AI 生成新场景
 */
public NvcScenarioEntity generateScenario(ScenarioGenerateRequest request) {
    String systemPrompt = loadSystemPrompt();
    String userPrompt = buildGeneratePrompt(request);

    ChatClient chatClient = llmProviderRegistry.getPlainChatClient(null);
    String result = structuredOutputInvoker.invokeStructuredOutput(
        userPrompt + "\n\n" + systemPrompt,
        chatClient,
        output -> output
    );

    try {
        var node = objectMapper.readTree(result);
        NvcScenarioEntity scenario = NvcScenarioEntity.builder()
            .title(node.has("title") ? node.get("title").asText() : "AI 生成场景")
            .description(node.has("description") ? node.get("description").asText() : result)
            .scenarioType(request.scenarioType() != null ? request.scenarioType() : NvcScenarioType.WORKPLACE)
            .difficulty(request.difficulty() != null ? request.difficulty() : NvcDifficulty.MEDIUM)
            .context(node.has("context") ? node.get("context").asText() : null)
            .focusElements(node.has("focus_elements") ? node.get("focus_elements").toString() : null)
            .tags(node.has("tags") ? node.get("tags").toString() : null)
            .isSystem(false)
            .usageCount(0)
            .build();

        NvcScenarioEntity saved = scenarioRepository.save(scenario);
        log.info("AI scenario generated: id={}, title={}", saved.getId(), saved.getTitle());
        return saved;

    } catch (Exception e) {
        log.error("Failed to parse AI generated scenario: {}", result, e);
        throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT,
            "场景生成失败，请重试");
    }
}

private String loadSystemPrompt() {
    try {
        ClassPathResource resource = new ClassPathResource("prompts/nvc-scenario-generate-system.st");
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
        return "你是NVC练习场景设计师，请根据用户需求生成NVC练习场景，以JSON格式返回。";
    }
}

private String buildGeneratePrompt(ScenarioGenerateRequest request) {
    StringBuilder sb = new StringBuilder("请生成一个NVC练习场景。\n\n");

    if (request.scenarioType() != null) {
        sb.append("场景类型：").append(request.scenarioType()).append("\n");
    }
    if (request.difficulty() != null) {
        sb.append("难度：").append(request.difficulty()).append("\n");
    }
    if (request.focusElements() != null && !request.focusElements().isEmpty()) {
        sb.append("重点练习要素：").append(String.join(", ", request.focusElements())).append("\n");
    }
    if (request.description() != null) {
        sb.append("用户描述：").append(request.description()).append("\n");
    }

    return sb.toString();
}
```

- [ ] **Step 2: 添加必要的 import**

在文件顶部添加：

```java
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
```

- [ ] **Step 3: 验证编译**

Run: `cd app && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcscenario/service/NvcScenarioService.java
git commit -m "feat(step6): add AI scenario generation"
```

---

### Task 5: 创建 NvcScenarioController

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcscenario/controller/NvcScenarioController.java`

**Interfaces:**
- Consumes: `NvcScenarioService`
- Produces: REST API `/api/nvc/scenarios`

- [ ] **Step 1: 创建 Controller**

```java
package nvc.guide.modules.nvcscenario.controller;

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
    @PostMapping("/generate")
    public Result<ScenarioDTO> generateScenario(@Valid @RequestBody ScenarioGenerateRequest request) {
        var scenario = scenarioService.generateScenario(request);
        return Result.success(scenarioService.toDTO(scenario));
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd app && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcscenario/controller/NvcScenarioController.java
git commit -m "feat(step6): add NvcScenarioController"
```

---

### Task 6: 为 NvcScenarioController 编写单元测试

**Files:**
- Create: `app/src/test/java/nvc/guide/modules/nvcscenario/controller/NvcScenarioControllerTest.java`

**Interfaces:**
- Tests: `queryScenarios()`, `getScenario()`, `generateScenario()`

- [ ] **Step 1: 创建测试类**

```java
package nvc.guide.modules.nvcscenario.controller;

import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcscenario.dto.ScenarioDTO;
import nvc.guide.modules.nvcscenario.dto.ScenarioGenerateRequest;
import nvc.guide.modules.nvcscenario.dto.ScenarioQueryRequest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcScenarioController 测试")
class NvcScenarioControllerTest {

    @Mock
    private NvcScenarioService scenarioService;
    @InjectMocks
    private NvcScenarioController controller;

    private NvcScenarioEntity buildScenario(Long id, String title) {
        return NvcScenarioEntity.builder()
            .id(id)
            .title(title)
            .description("测试描述")
            .scenarioType(NvcScenarioType.WORKPLACE)
            .difficulty(NvcDifficulty.MEDIUM)
            .isSystem(true)
            .usageCount(0)
            .build();
    }

    private ScenarioDTO buildDTO(Long id, String title) {
        return new ScenarioDTO(
            id, title, "测试描述",
            NvcScenarioType.WORKPLACE, NvcDifficulty.MEDIUM,
            null, null, null, null, true, 0);
    }

    @Test
    @DisplayName("GET /api/nvc/scenarios 返回场景列表")
    void queryScenarios_returnsList() {
        NvcScenarioEntity scenario = buildScenario(1L, "职场场景");
        ScenarioDTO dto = buildDTO(1L, "职场场景");

        when(scenarioService.queryScenarios(any(ScenarioQueryRequest.class)))
            .thenReturn(List.of(scenario));
        when(scenarioService.toDTO(scenario)).thenReturn(dto);

        Result<List<ScenarioDTO>> result = controller.queryScenarios(null, null);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("职场场景", result.getData().get(0).title());
    }

    @Test
    @DisplayName("GET /api/nvc/scenarios/{id} 返回单个场景")
    void getScenario_returnsSingle() {
        NvcScenarioEntity scenario = buildScenario(1L, "家庭场景");
        ScenarioDTO dto = buildDTO(1L, "家庭场景");

        when(scenarioService.getScenario(1L)).thenReturn(scenario);
        when(scenarioService.toDTO(scenario)).thenReturn(dto);

        Result<ScenarioDTO> result = controller.getScenario(1L);

        assertEquals(200, result.getCode());
        assertEquals("家庭场景", result.getData().title());
    }

    @Test
    @DisplayName("POST /api/nvc/scenarios/generate 生成新场景")
    void generateScenario_returnsGenerated() {
        NvcScenarioEntity generated = buildScenario(2L, "AI 生成场景");
        generated.setIsSystem(false);
        ScenarioDTO dto = buildDTO(2L, "AI 生成场景");

        when(scenarioService.generateScenario(any(ScenarioGenerateRequest.class)))
            .thenReturn(generated);
        when(scenarioService.toDTO(generated)).thenReturn(dto);

        ScenarioGenerateRequest request = new ScenarioGenerateRequest(
            NvcScenarioType.FAMILY, NvcDifficulty.HARD,
            List.of("feeling"), "和父母冲突");

        Result<ScenarioDTO> result = controller.generateScenario(request);

        assertEquals(200, result.getCode());
        assertEquals("AI 生成场景", result.getData().title());
    }
}
```

- [ ] **Step 2: 运行测试**

Run: `cd app && ../gradlew test --tests "nvc.guide.modules.nvcscenario.controller.NvcScenarioControllerTest"`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/nvc/guide/modules/nvcscenario/controller/NvcScenarioControllerTest.java
git commit -m "test(step6): add NvcScenarioController unit tests"
```

---

### Task 7: 运行全量测试并验证

**Files:**
- None (verification only)

- [ ] **Step 1: 运行所有测试**

Run: `cd app && ../gradlew test`
Expected: All tests PASS

- [ ] **Step 2: 验证 API 端点**

启动应用后测试：
```bash
# 查询场景列表
curl http://localhost:8080/api/nvc/scenarios

# 按类型筛选
curl "http://localhost:8080/api/nvc/scenarios?scenarioType=WORKPLACE"

# 获取单个场景
curl http://localhost:8080/api/nvc/scenarios/1

# AI 生成场景
curl -X POST http://localhost:8080/api/nvc/scenarios/generate \
  -H "Content-Type: application/json" \
  -d '{"scenarioType":"FAMILY","difficulty":"HARD","description":"和父母因为催婚产生冲突"}'
```

- [ ] **Step 3: Final Commit**

```bash
git add -A
git commit -m "feat(step6): complete scenario library management"
```

---

## 验证清单

```
□ NvcScenarioService 编译通过
  □ queryScenarios() 能按类型和难度筛选
  □ getScenario() 能获取单个场景
  □ generateScenario() 能调用 AI 生成新场景
  □ incrementUsage() 能更新使用次数
□ 单元测试全部通过
□ API 测试：
  □ GET /api/nvc/scenarios 返回场景列表
  □ GET /api/nvc/scenarios?scenarioType=WORKPLACE 按类型筛选
  □ GET /api/nvc/scenarios/1 返回单个场景
  □ POST /api/nvc/scenarios/generate 生成新场景
□ Git 提交："feat(step6): complete scenario library management"
```
