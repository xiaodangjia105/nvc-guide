# Phase 1.2：Agent 工具调用框架 — 设计文档

> 创建时间：2026-07-19
> 状态：已批准
> 预估：2 天

---

## 一、设计目标

实现 NvcTool 接口 + NvcToolRegistry + 10 个核心工具，为练习 Agent 和主 Agent 提供统一的工具调用能力。

**核心原则**：
- 工具模块独立，不侵入编排层
- 编排层变更（共享记忆、路由调整）不影响工具模块
- 向后兼容，原有 API 不变
- 可扩展，未来加新工具只需实现接口 + @Component

---

## 二、架构总览

```
编排层 (Orchestrator)              工具层 (Tool)
    │                                  │
    │  构建 NvcChatRequest              │  NvcTool (接口)
    │  (含可选的 NvcToolCallConfig)      │  NvcToolRegistry (注册中心)
    │                                  │  NvcToolSceneMapping (映射表)
    │                                  │
    └──────────────┬───────────────────┘
                   │
                   v
           NvcAgentChatService
           (纯执行层，不关心工具选择逻辑)
                   │
                   v
              ChatClient
              (Spring AI ToolContext + FunctionCallback)
                   │
                   v
              LLM Function Calling
```

**两种工具使用模式**：

| 模式 | 触发者 | 适用场景 | 实现方式 |
|------|--------|---------|---------|
| LLM 驱动 | LLM 自主决定 | 对话中的按需调用 | NvcTool → FunctionCallback |
| 编排层强制 | Orchestrator 代码 | 流程中的固定步骤 | 直接调用 Service/Skill |

---

## 三、核心接口

### 3.1 NvcTool — 工具接口

```java
/**
 * NVC 工具接口 — Agent 可调用的外部能力
 * 实现此接口并加上 @Component 注解即可自动注册到 NvcToolRegistry
 */
public interface NvcTool {
    /** 工具名称，用于 Function Calling */
    String name();

    /** 工具描述，告诉 LLM 这个工具能做什么 */
    String description();

    /** 输入参数的 JSON Schema（字符串格式，与 Spring AI ToolDefinition 一致） */
    String inputSchema();

    /** 执行工具 */
    NvcToolResult execute(String input, NvcToolContext context);
}
```

**设计决策**：
- `inputSchema()` 返回 `String` 而非 `JsonSchema`，与 Spring AI 的 `ToolDefinition.inputSchema()` 一致
- `execute` 的 `input` 为 `String`（JSON），与 Spring AI 的 `FunctionCallback.call(String)` 一致
- 使用 `NvcToolContext` 而非 `ToolContext`，避免与 Spring AI 的 `ToolContext` 重名

### 3.2 NvcToolResult — 工具执行结果

```java
public record NvcToolResult(boolean success, String data, String errorMessage) {
    public static NvcToolResult success(String data) {
        return new NvcToolResult(true, data, null);
    }
    public static NvcToolResult failure(String errorMessage) {
        return new NvcToolResult(false, null, errorMessage);
    }
}
```

### 3.3 NvcToolContext — 工具执行上下文

```java
@Data
@Builder
public class NvcToolContext {
    private Long userId;
    private Long sessionId;
    private PracticeContext practiceContext;
}
```

---

## 四、NvcToolRegistry — 工具注册中心

### 4.1 自动注册

```java
@Service
@Slf4j
public class NvcToolRegistry {

    private final Map<String, NvcTool> tools;

    /** Spring 自动注入所有 NvcTool 实现 */
    public NvcToolRegistry(List<NvcTool> toolList) {
        this.tools = toolList.stream()
            .collect(Collectors.toMap(NvcTool::name, t -> t));
        log.info("[NvcToolRegistry] Registered {} tools: {}", tools.size(), tools.keySet());
    }

    public NvcTool getTool(String name) { return tools.get(name); }
    public List<NvcTool> getAllTools() { return List.copyOf(tools.values()); }
}
```

### 4.2 Spring AI FunctionCallback 转换

```java
/** 全量转换 — 主 Agent 使用 */
public List<ToolCallback> toFunctionCallbacks() {
    return tools.values().stream().map(this::toFunctionCallback).toList();
}

/** 按名称子集 — 练习 Agent 按场景过滤 */
public List<ToolCallback> toFunctionCallbacks(List<String> toolNames) {
    return toolNames.stream()
        .map(tools::get)
        .filter(Objects::nonNull)
        .map(this::toFunctionCallback)
        .toList();
}

/** NvcTool → Spring AI FunctionToolCallback 适配 */
private ToolCallback toFunctionCallback(NvcTool tool) {
    return FunctionToolCallback.builder(tool.name(), (String input, ToolContext aiContext) -> {
            NvcToolContext nvcContext = extractNvcContext(aiContext);
            NvcToolResult result = tool.execute(input, nvcContext);
            return result.success() ? result.data() : "Error: " + result.errorMessage();
        })
        .description(tool.description())
        .inputSchema(tool.inputSchema())
        .inputType(String.class)
        .build();
}

/** 从 Spring AI ToolContext Map 中提取 NvcToolContext */
private NvcToolContext extractNvcContext(ToolContext aiContext) {
    if (aiContext == null || aiContext.getContext().isEmpty()) {
        return NvcToolContext.builder().build();
    }
    Map<String, Object> map = aiContext.getContext();
    return NvcToolContext.builder()
        .userId((Long) map.get("nvc.userId"))
        .sessionId((Long) map.get("nvc.sessionId"))
        .practiceContext((PracticeContext) map.get("nvc.practiceContext"))
        .build();
}
```

**关键点**：Spring AI 2.0 的 `FunctionToolCallback.builder(name, BiFunction<String, ToolContext, String>)` 接受一个双参数函数，第二个参数是 Spring AI 的 `ToolContext`（Map 包装），我们从中提取 NVC 上下文。

---

## 五、NvcChatRequest + NvcToolCallConfig — 解耦设计

### 5.1 NvcChatRequest — 统一对话请求对象

```java
@Data
@Builder
public class NvcChatRequest {
    private final NvcAgentConfigEntity agentConfig;
    private final PracticeContext practiceContext;
    private final String userMessage;
    private final Map<String, String> promptVariables;

    /** 工具配置（可选）— 不传则无工具调用 */
    @Builder.Default
    private final NvcToolCallConfig toolConfig = NvcToolCallConfig.disabled();

    // ═══ 未来扩展点 ═══
    // private SharedMemoryConfig sharedMemory;  // 共享记忆
    // private StreamConfig streamConfig;        // 流式配置
    // private ToolExecutionListener listener;   // 工具执行回调
}
```

### 5.2 NvcToolCallConfig — 工具调用配置

```java
@Data
@Builder
public class NvcToolCallConfig {
    /** 可用工具名称列表 */
    private final List<String> toolNames;

    /** 额外上下文（传递给 Spring AI ToolContext） */
    @Builder.Default
    private final Map<String, Object> extraContext = Map.of();

    public static NvcToolCallConfig disabled() {
        return new NvcToolCallConfig(List.of(), Map.of());
    }

    public boolean isEnabled() {
        return toolNames != null && !toolNames.isEmpty();
    }
}
```

### 5.3 NvcAgentChatService 升级

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcAgentChatService {

    private final LlmProviderRegistry llmProviderRegistry;
    private final NvcToolRegistry toolRegistry;  // 新增

    // ═══ 原有方法 — 保持不变，向后兼容 ═══
    public String chat(NvcAgentConfigEntity config, PracticeContext context,
                       String userMessage, Map<String, String> promptVariables) {
        return chat(NvcChatRequest.builder()
            .agentConfig(config).practiceContext(context)
            .userMessage(userMessage).promptVariables(promptVariables).build());
    }

    public Flux<String> chatStream(NvcAgentConfigEntity config, PracticeContext context,
                                   String userMessage, Map<String, String> promptVariables) {
        return chatStream(NvcChatRequest.builder()
            .agentConfig(config).practiceContext(context)
            .userMessage(userMessage).promptVariables(promptVariables).build());
    }

    // ═══ 新方法 — 统一入口 ═══
    public String chat(NvcChatRequest request) {
        ChatClient client = getChatClient(request.getAgentConfig());
        // 复用原有 buildMessages，从 request 中提取参数
        List<Message> messages = buildMessages(
            request.getAgentConfig(), request.getPracticeContext(),
            request.getUserMessage(), request.getPromptVariables());

        var spec = client.prompt()
            .options(buildChatOptions(request.getAgentConfig()))
            .messages(messages);

        if (request.getToolConfig().isEnabled()) {
            spec = spec.toolContext(buildToolContextMap(request));
            spec = spec.functionCallbacks(
                toolRegistry.toFunctionCallbacks(request.getToolConfig().getToolNames()));
        }

        return spec.call().content();
    }

    public Flux<String> chatStream(NvcChatRequest request) {
        ChatClient client = getChatClient(request.getAgentConfig());
        // 复用原有 buildMessages，从 request 中提取参数
        List<Message> messages = buildMessages(
            request.getAgentConfig(), request.getPracticeContext(),
            request.getUserMessage(), request.getPromptVariables());

        var spec = client.prompt()
            .options(buildChatOptions(request.getAgentConfig()))
            .messages(messages);

        if (request.getToolConfig().isEnabled()) {
            spec = spec.toolContext(buildToolContextMap(request));
            spec = spec.functionCallbacks(
                toolRegistry.toFunctionCallbacks(request.getToolConfig().getToolNames()));
        }

        return spec.stream().content();
    }

    // ═══ 上下文构建 ═══
    private Map<String, Object> buildToolContextMap(NvcChatRequest request) {
        Map<String, Object> map = new HashMap<>();
        PracticeContext ctx = request.getPracticeContext();
        if (ctx != null) {
            map.put("nvc.userId", ctx.getUserId());
            map.put("nvc.sessionId", ctx.getSessionId());
            map.put("nvc.practiceContext", ctx);
        }
        map.putAll(request.getToolConfig().getExtraContext());
        return map;
    }

    // ═══ chatPlain 保持不变 — 不注入工具 ═══
    // chatPlain 用于评估、反思等需要结构化输出的场景，不注入工具
    // 原有签名和逻辑不变
}
```

---

## 六、工具选择策略

### 6.1 NvcToolSceneMapping — 场景-工具映射表

```java
@Component
public class NvcToolSceneMapping {

    private static final Map<NvcAgentScene, List<String>> SCENE_TOOLS = Map.ofEntries(
        Map.entry(NvcAgentScene.NVC_EXPRESSION_EVALUATOR,
            List.of("evaluate_nvc", "rag_search")),
        Map.entry(NvcAgentScene.DIALOGUE_GUIDE,
            List.of("rag_search", "profile_query")),
        Map.entry(NvcAgentScene.SCENARIO_GENERATOR,
            List.of("scenario_search", "scenario_generate", "profile_query")),
        Map.entry(NvcAgentScene.EMPATHY_COACH,
            List.of("rag_search", "profile_query")),
        Map.entry(NvcAgentScene.NVC_KNOWLEDGE_ADVISOR,
            List.of("rag_search", "wiki_search")),
        Map.entry(NvcAgentScene.STEP_OBSERVE_COACH, List.of("rag_search")),
        Map.entry(NvcAgentScene.STEP_FEELING_COACH, List.of("rag_search")),
        Map.entry(NvcAgentScene.STEP_NEED_COACH, List.of("rag_search")),
        Map.entry(NvcAgentScene.STEP_REQUEST_COACH, List.of("rag_search")),
        Map.entry(NvcAgentScene.DIFFICULT_PARTNER, List.of()),
        Map.entry(NvcAgentScene.PROFILE_ANALYZER,
            List.of("profile_query", "dashboard_query"))
    );

    public List<String> getDefaultTools(NvcAgentScene scene) {
        return SCENE_TOOLS.getOrDefault(scene, List.of());
    }

    /** 主 Agent — 全量工具（Phase 2 使用） */
    public List<String> getAllTools() {
        return List.of("rag_search", "wiki_search", "wiki_write",
            "profile_query", "profile_update", "evaluate_nvc",
            "scenario_search", "scenario_generate", "dashboard_query",
            "practice_start");
    }
}
```

### 6.2 AgentDecision 扩展

```java
public record AgentDecision(
    NvcAgentScene scene,
    String reason,
    String reflection,
    Map<String, String> promptVariables,
    List<String> availableTools  // 新增
) {
    public AgentDecision(NvcAgentScene scene, String reason,
                         String reflection, Map<String, String> promptVariables) {
        this(scene, reason, reflection, promptVariables, List.of());
    }
}
```

### 6.3 ModeRouter 使用示例

```java
@Component
@RequiredArgsConstructor
public class ScenarioRouter implements ModeRouter {

    private final NvcToolSceneMapping toolSceneMapping;

    @Override
    public AgentDecision route(PracticeContext context) {
        NvcAgentScene scene = determineScene(context);
        List<String> tools = toolSceneMapping.getDefaultTools(scene);

        return new AgentDecision(scene, "场景路由决策", null,
            Map.of("mode", "scenario"), tools);
    }
}
```

---

## 七、10 个工具实现

### 7.1 工具列表

| # | 工具名 | 依赖服务 | 复杂度 | 阶段 |
|---|--------|---------|--------|------|
| 1 | rag_search | NvcRagService | 🟢 低 | Phase 1.2 |
| 2 | profile_query | NvcProfileService | 🟢 低 | Phase 1.2 |
| 3 | profile_update | NvcProfileService | 🟢 低 | Phase 1.2 |
| 4 | dashboard_query | NvcDashboardService | 🟢 低 | Phase 1.2 |
| 5 | scenario_search | NvcScenarioService | 🟢 低 | Phase 1.2 |
| 6 | scenario_generate | NvcScenarioService + AI | 🟡 中 | Phase 1.2 |
| 7 | evaluate_nvc | NvcEvaluationService | 🟡 中 | Phase 1.2 |
| 8 | wiki_search | NvcWikiService | 🔴 高 | Phase 1.2（骨架） |
| 9 | wiki_write | NvcWikiService | 🔴 高 | Phase 1.2（骨架） |
| 10 | practice_start | NvcPracticeSessionService | 🟢 低 | Phase 1.2 |

### 7.2 输入记录定义

```java
// 每个工具的输入 Record（放在各自工具类内部或独立 dto 包）
record RagSearchInput(String query, Integer topK) {}
record ProfileQueryInput(Long userId) {}
record ProfileUpdateInput(String field, String value) {}
record DashboardQueryInput(String period) {}  // "week"/"month"/"all"
record ScenarioSearchInput(String keyword, String difficulty, Integer limit) {}
record ScenarioGenerateInput(String situation, String relationship, String focusElement) {}
record EvaluateNvcInput(String expression, String scenario) {}
record WikiSearchInput(String query, Integer topK) {}
record WikiWriteInput(String title, String content, String category) {}
record PracticeStartInput(Long scenarioId, String mode) {}
```

### 7.3 JSON Schema 生成

使用 Spring AI 的 `JsonSchemaGenerator.generateForType()` 自动从 Record 生成：

```java
@Override
public String inputSchema() {
    return JsonSchemaGenerator.generateForType(RagSearchInput.class);
}
```

### 7.4 工具实现模板

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class RagSearchTool implements NvcTool {

    private final NvcRagService ragService;

    @Override
    public String name() { return "rag_search"; }

    @Override
    public String description() {
        return "搜索 NVC 知识库，检索相关理论、话术模板、情绪词汇等信息。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(RagSearchInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            RagSearchInput params = JsonParser.fromJson(input, RagSearchInput.class);
            int topK = params.topK() != null ? params.topK() : 5;

            List<RagResult> results = ragService.retrieve(
                params.query(), context.getUserId(),
                List.of(KnowledgeBaseType.NVC_THEORY,
                        KnowledgeBaseType.SPEECH_TEMPLATE,
                        KnowledgeBaseType.EMOTION_VOCAB),
                topK
            );

            String formatted = results.stream()
                .map(r -> "- " + r.text())
                .collect(Collectors.joining("\n"));

            return NvcToolResult.success(formatted.isEmpty() ? "未找到相关知识" : formatted);
        } catch (Exception e) {
            log.error("[RagSearchTool] Execution failed", e);
            return NvcToolResult.failure("知识检索失败: " + e.getMessage());
        }
    }

    record RagSearchInput(String query, Integer topK) {}
}
```

### 7.5 骨架工具（wiki_search / wiki_write）

```java
@Component
@Slf4j
public class WikiSearchTool implements NvcTool {

    @Override
    public String name() { return "wiki_search"; }

    @Override
    public String description() { return "搜索个人 Wiki 知识库"; }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(WikiSearchInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        return NvcToolResult.failure("Wiki 功能暂未开放，敬请期待");
    }

    record WikiSearchInput(String query, Integer topK) {}
}
```

---

## 八、错误处理

### 8.1 工具执行异常

```java
// NvcToolRegistry 中的统一错误处理
private ToolCallback toFunctionCallback(NvcTool tool) {
    return FunctionToolCallback.builder(tool.name(), (String input, ToolContext aiContext) -> {
        try {
            NvcToolContext nvcContext = extractNvcContext(aiContext);
            NvcToolResult result = tool.execute(input, nvcContext);
            return result.success() ? result.data() : "Error: " + result.errorMessage();
        } catch (Exception e) {
            log.error("[NvcToolRegistry] Tool execution failed: tool={}", tool.name(), e);
            return "Error: 工具执行异常: " + e.getMessage();
        }
    })
    .description(tool.description())
    .inputSchema(tool.inputSchema())
    .inputType(String.class)
    .build();
}
```

### 8.2 超时策略

- Spring AI 的 `ToolCallingManager` 有内置超时机制
- 单个工具执行超时默认 10 秒
- 超时后返回错误信息给 LLM，LLM 可以决定是否重试或降级

---

## 九、新建文件清单

```
app/src/main/java/nvc/guide/modules/nvcpractice/
├── tool/
│   ├── NvcTool.java                    # 工具接口
│   ├── NvcToolResult.java              # 工具执行结果
│   ├── NvcToolContext.java             # 工具执行上下文
│   ├── NvcToolRegistry.java            # 工具注册中心 + Spring AI 适配
│   ├── NvcToolSceneMapping.java        # 场景-工具映射表
│   ├── RagSearchTool.java              # RAG 知识检索
│   ├── ProfileQueryTool.java           # 用户档案查询
│   ├── ProfileUpdateTool.java          # 用户档案更新
│   ├── DashboardQueryTool.java         # 仪表盘数据查询
│   ├── ScenarioSearchTool.java         # 场景搜索
│   ├── ScenarioGenerateTool.java       # 场景生成
│   ├── EvaluateNvcTool.java            # NVC 表达评估
│   ├── WikiSearchTool.java             # Wiki 搜索（骨架）
│   ├── WikiWriteTool.java              # Wiki 写入（骨架）
│   └── PracticeStartTool.java          # 启动练习
└── dto/
    ├── NvcChatRequest.java             # 统一对话请求对象
    └── NvcToolCallConfig.java          # 工具调用配置

修改文件：
├── NvcAgentChatService.java            # 注入工具 + NvcChatRequest
├── AgentDecision.java                  # 新增 availableTools 字段
└── ModeRouter 实现类                   # 工具选择逻辑
```

---

## 十、验收标准

```
□ 框架层
  □ NvcTool 接口定义完成
  □ NvcToolResult / NvcToolContext 定义完成
  □ NvcToolRegistry 自动注册所有 @Component 工具
  □ toFunctionCallbacks() 正确转换为 Spring AI FunctionCallback
  □ NvcChatRequest / NvcToolCallConfig 定义完成
  □ NvcAgentChatService 能注入工具回调
  □ 原有 chat() 方法向后兼容

□ 工具实现
  □ 10 个工具全部实现（wiki_search/wiki_write 为骨架）
  □ 每个工具有独立的 inputSchema（自动生成）
  □ 工具执行结果正确返回

□ Agent 集成
  □ Agent 对话中 LLM 能识别并调用工具
  □ 工具结果正确注入对话上下文
  □ 不同 Agent 场景使用不同工具子集
  □ NvcToolSceneMapping 映射表配置完成

□ 错误处理
  □ 工具执行异常被捕获并返回错误信息
  □ 超时机制生效
```

---

## 十一、技术决策记录

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 工具接口 | 自定义 NvcTool 接口 | 与 Phase 1.3 NvcSkill 衔接，支持 ToolContext |
| inputSchema 类型 | String | 与 Spring AI ToolDefinition.inputSchema() 一致 |
| 上下文传递 | Spring AI ToolContext Map | Spring AI 2.0 原生支持，无 ThreadLocal |
| 请求对象 | NvcChatRequest + NvcToolCallConfig | 解耦编排层，可扩展 |
| 映射表位置 | 代码中（NvcToolSceneMapping） | 类型安全、简单、性能好 |
| JSON Schema 生成 | JsonSchemaGenerator.generateForType() | 自动从 Record 生成，零配置 |
| 骨架工具 | 返回 "功能暂未开放" | 不阻塞框架，Phase 2 补全 |
