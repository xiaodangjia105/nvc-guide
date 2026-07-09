# Step 6：场景库管理

> 目标：实现场景库的 CRUD、AI 生成场景、场景推荐
>
> 预计耗时：1-2 天
>
> 前置条件：Step 1 已完成（场景表和初始数据就绪）
>
> 这一步较简单，主要是 CRUD + AI 场景生成

---

## 6.1 本步要创建的文件清单

```
app/src/main/java/interview/guide/modules/nvcscenario/
├── service/
│   └── NvcScenarioService.java            ← 场景 CRUD + AI 生成 + 推荐
├── dto/
│   ├── ScenarioDTO.java                   ← 场景响应 DTO
│   ├── ScenarioGenerateRequest.java       ← AI 生成场景请求
│   └── ScenarioQueryRequest.java          ← 场景查询条件
└── controller/
    └── NvcScenarioController.java         ← 场景 API

app/src/main/resources/prompts/
└── nvc-scenario-generate-system.st        ← 已在 Step 3 创建，复用
```

---

## 6.2 NvcScenarioService

路径：`app/src/main/java/interview/guide/modules/nvcscenario/service/NvcScenarioService.java`

```java
package interview.guide.modules.nvcscenario.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.nvcscenario.dto.ScenarioDTO;
import interview.guide.modules.nvcscenario.dto.ScenarioGenerateRequest;
import interview.guide.modules.nvcscenario.dto.ScenarioQueryRequest;
import interview.guide.modules.nvcscenario.model.NvcDifficulty;
import interview.guide.modules.nvcscenario.model.NvcScenarioEntity;
import interview.guide.modules.nvcscenario.model.NvcScenarioType;
import interview.guide.modules.nvcscenario.repository.NvcScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

        // 解析 AI 返回的 JSON，创建场景
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
}
```

---

## 6.3 DTO 类

### ScenarioDTO

```java
package interview.guide.modules.nvcscenario.dto;

import interview.guide.modules.nvcscenario.model.NvcDifficulty;
import interview.guide.modules.nvcscenario.model.NvcScenarioType;

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

### ScenarioGenerateRequest

```java
package interview.guide.modules.nvcscenario.dto;

import interview.guide.modules.nvcscenario.model.NvcDifficulty;
import interview.guide.modules.nvcscenario.model.NvcScenarioType;

import java.util.List;

public record ScenarioGenerateRequest(
    NvcScenarioType scenarioType,
    NvcDifficulty difficulty,
    List<String> focusElements,
    String description  // 用户对场景的描述/需求
) {}
```

### ScenarioQueryRequest

```java
package interview.guide.modules.nvcscenario.dto;

import interview.guide.modules.nvcscenario.model.NvcDifficulty;
import interview.guide.modules.nvcscenario.model.NvcScenarioType;

public record ScenarioQueryRequest(
    NvcScenarioType scenarioType,
    NvcDifficulty difficulty
) {}
```

---

## 6.4 NvcScenarioController

路径：`app/src/main/java/interview/guide/modules/nvcscenario/controller/NvcScenarioController.java`

```java
package interview.guide.modules.nvcscenario.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.nvcscenario.dto.ScenarioDTO;
import interview.guide.modules.nvcscenario.dto.ScenarioGenerateRequest;
import interview.guide.modules.nvcscenario.dto.ScenarioQueryRequest;
import interview.guide.modules.nvcscenario.model.NvcDifficulty;
import interview.guide.modules.nvcscenario.model.NvcScenarioType;
import interview.guide.modules.nvcscenario.service.NvcScenarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

---

## 6.5 集成到对话流程

在 `NvcPracticeDialogueService.sendMessage()` 中，场景驱动模式首次对话时自动使用场景：

```java
// 在构建 PracticeContext 时，如果会话有 scenarioId，自动加载场景并增加使用次数
if (session.getScenarioId() != null) {
    scenarioService.incrementUsage(session.getScenarioId());
}
```

---

## 6.6 验证清单

```
□ NvcScenarioService 编译通过
  □ queryScenarios() 能按类型和难度筛选
  □ getScenario() 能获取单个场景
  □ generateScenario() 能调用 AI 生成新场景
□ API 测试：
  □ GET /api/nvc/scenarios 返回场景列表
  □ GET /api/nvc/scenarios?scenarioType=WORKPLACE 按类型筛选
  □ GET /api/nvc/scenarios/1 返回单个场景
  □ POST /api/nvc/scenarios/generate 生成新场景
□ Git 提交："Step 6: Add scenario library management"
```
