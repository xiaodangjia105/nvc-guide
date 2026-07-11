# Step 6：场景库管理 — 设计文档

> 日期：2026-07-11
>
> 状态：设计完成，待实现
>
> 方案选择：方案 A（精简版），预留 BC 扩展性

---

## 1. 目标

实现场景库的基础 CRUD 和 AI 生成功能，为 NVC 练习提供场景驱动模式支持。

**核心功能：**
- 场景查询（按类型/难度筛选）
- 场景详情获取
- AI 生成新场景
- 场景使用统计

---

## 2. 现状分析

### 已完成部分

| 组件 | 状态 | 说明 |
|------|------|------|
| `NvcScenarioEntity` | ✅ 已存在 | 结构完善，含 id、title、description、scenarioType、difficulty、focusElements、context、sampleDialogue、tags、isSystem、createdBy、usageCount、createdAt |
| `NvcScenarioRepository` | ✅ 已存在 | 有基本查询方法 |
| `NvcScenarioType` 枚举 | ✅ 已存在 | WORKPLACE、FAMILY、INTIMATE、SOCIAL、SELF |
| `NvcDifficulty` | ✅ 已存在 | 位于 nvcpractice.model 包 |
| Prompt 模板 | ✅ 已存在 | `nvc-scenario-generate-system.st` |

### 需要创建

| 文件 | 类型 | 说明 |
|------|------|------|
| `NvcScenarioService` | Service | 场景业务逻辑 |
| `ScenarioDTO` | DTO | 场景响应 |
| `ScenarioGenerateRequest` | DTO | AI 生成请求 |
| `ScenarioQueryRequest` | DTO | 查询条件 |
| `NvcScenarioController` | Controller | REST API |

---

## 3. API 设计

### 3.1 查询场景列表

```
GET /api/nvc/scenarios
```

**请求参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| scenarioType | String | 否 | 场景类型：WORKPLACE/FAMILY/INTIMATE/SOCIAL/SELF |
| difficulty | String | 否 | 难度：EASY/MEDIUM/HARD |

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "title": "职场被抢功劳",
      "description": "你辛苦完成的项目...",
      "scenarioType": "WORKPLACE",
      "difficulty": "MEDIUM",
      "focusElements": "[\"observation\",\"request\"]",
      "context": "详细背景...",
      "sampleDialogue": null,
      "tags": "[\"职场\",\"冲突\"]",
      "isSystem": true,
      "usageCount": 42
    }
  ]
}
```

### 3.2 获取单个场景

```
GET /api/nvc/scenarios/{id}
```

**响应：** 同上，单个对象。

### 3.3 AI 生成场景

```
POST /api/nvc/scenarios/generate
```

**请求体：**
```json
{
  "scenarioType": "FAMILY",
  "difficulty": "HARD",
  "focusElements": ["feeling", "need"],
  "description": "和父母因为催婚产生冲突"
}
```

**响应：** 生成的场景对象（同上）。

---

## 4. 数据模型

### 4.1 DTO 定义

**ScenarioDTO**
```java
public record ScenarioDTO(
    Long id,
    String title,
    String description,
    NvcScenarioType scenarioType,
    NvcDifficulty difficulty,
    String focusElements,    // JSON 字符串
    String context,
    String sampleDialogue,
    String tags,             // JSON 字符串
    Boolean isSystem,
    Integer usageCount
) {}
```

**ScenarioGenerateRequest**
```java
public record ScenarioGenerateRequest(
    NvcScenarioType scenarioType,     // 可选：指定类型
    NvcDifficulty difficulty,         // 可选：指定难度
    List<String> focusElements,       // 可选：重点练习要素
    String description                // 可选：用户描述/需求
) {}
```

**ScenarioQueryRequest**
```java
public record ScenarioQueryRequest(
    NvcScenarioType scenarioType,  // 可选：场景类型
    NvcDifficulty difficulty       // 可选：难度
) {}
```

---

## 5. Service 设计

### 5.1 NvcScenarioService

**核心方法：**

```java
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
     * AI 生成新场景
     */
    public NvcScenarioEntity generateScenario(ScenarioGenerateRequest request) {
        // 1. 加载 Prompt 模板
        // 2. 构建用户 Prompt
        // 3. 调用 LLM
        // 4. 解析 JSON 响应
        // 5. 构建并保存实体
        // 返回保存的场景
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

### 5.2 AI 生成逻辑

1. **Prompt 构建**：
   - 系统 Prompt：从 `nvc-scenario-generate-system.st` 加载
   - 用户 Prompt：根据 `ScenarioGenerateRequest` 动态构建

2. **LLM 调用**：
   - 使用 `structuredOutputInvoker.invokeStructuredOutput()`
   - 解析 JSON 响应

3. **实体构建**：
   - 从 JSON 提取字段
   - 设置默认值（类型、难度）
   - 标记为非系统场景（`isSystem = false`）

---

## 6. Controller 设计

```java
@RestController
@RequestMapping("/api/nvc/scenarios")
@RequiredArgsConstructor
public class NvcScenarioController {

    private final NvcScenarioService scenarioService;

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

    @GetMapping("/{id}")
    public Result<ScenarioDTO> getScenario(@PathVariable Long id) {
        return Result.success(scenarioService.toDTO(
            scenarioService.getScenario(id)));
    }

    @PostMapping("/generate")
    public Result<ScenarioDTO> generateScenario(
            @Valid @RequestBody ScenarioGenerateRequest request) {
        var scenario = scenarioService.generateScenario(request);
        return Result.success(scenarioService.toDTO(scenario));
    }
}
```

---

## 7. 集成点

### 7.1 对话流程集成

在 `NvcPracticeDialogueService.sendMessage()` 中：

```java
// 场景驱动模式首次对话时，增加场景使用次数
if (session.getScenarioId() != null) {
    scenarioService.incrementUsage(session.getScenarioId());
}
```

---

## 8. 扩展预留

| 未来功能 | 预留设计 | 实现方式 |
|----------|----------|----------|
| 方案 B 缓存 | Service 方法签名不变 | 内部加 `@Cacheable` |
| 方案 B 分页 | Repository 方法可加参数 | `Pageable pageable` |
| 方案 B 编辑删除 | Controller 可加端点 | `PUT`/`DELETE` |
| 方案 C 智能推荐 | 新增方法 | `recommendScenarios(Long userId)` |
| 方案 C 标签搜索 | Repository 可扩展 | `@Query` JSONB 查询 |

---

## 9. 验证清单

```
□ NvcScenarioService 编译通过
  □ queryScenarios() 能按类型和难度筛选
  □ getScenario() 能获取单个场景
  □ generateScenario() 能调用 AI 生成新场景
  □ incrementUsage() 能更新使用次数
□ API 测试：
  □ GET /api/nvc/scenarios 返回场景列表
  □ GET /api/nvc/scenarios?scenarioType=WORKPLACE 按类型筛选
  □ GET /api/nvc/scenarios/1 返回单个场景
  □ POST /api/nvc/scenarios/generate 生成新场景
□ Git 提交："Step 6: Add scenario library management"
```

---

## 10. 文件清单

```
app/src/main/java/nvc/guide/modules/nvcscenario/
├── service/
│   └── NvcScenarioService.java
├── dto/
│   ├── ScenarioDTO.java
│   ├── ScenarioGenerateRequest.java
│   └── ScenarioQueryRequest.java
└── controller/
    └── NvcScenarioController.java
```
