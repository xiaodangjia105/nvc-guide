# Phase 1.2 Agent 工具调用框架 — 深度解析

> 分析日期：2026-07-19
> 分支：`worktree-phase12-tool-framework`
> 概述：为 NVC 练习模块的 Agent 引入 Function Calling 能力，使 Agent 能在对话中自主调用外部工具

---

## 一、功能概述

Phase 1.2 的核心目标是：**让 NVC 练习中的各种 Agent（教练、评估师、场景生成官等）能够在对话过程中自主调用外部工具**，而不是仅仅依赖预注入到 System Prompt 中的静态信息。

### 1.1 Before vs After

```
Phase 1.1（工具调用前）：
┌─────────────────────────────────────────────────────┐
│ Agent 对话                                           │
│                                                      │
│ System Prompt                                        │
│ ├── 基础 Prompt                                      │
│ ├── [用户档案]       ← 启动时注入，对话中不会更新     │
│ ├── [练习场景]       ← 启动时注入                     │
│ ├── [参考资料]       ← RAG 检索结果，每次对话前注入    │
│ └── [当前配置]                                       │
│                                                      │
│ Agent 只能使用 Prompt 中已有的信息                    │
│ 无法在对话过程中主动获取新信息                        │
└─────────────────────────────────────────────────────┘

Phase 1.2（工具调用后）：
┌─────────────────────────────────────────────────────┐
│ Agent 对话                                           │
│                                                      │
│ System Prompt（同上）                                │
│                                                      │
│ + 可用工具列表：                                      │
│ ├── rag_search       → 搜索 NVC 知识库               │
│ ├── profile_query    → 查询用户档案                   │
│ ├── evaluate_nvc     → 评估 NVC 表达                  │
│ ├── scenario_search  → 搜索练习场景                   │
│ ├── scenario_generate→ AI 生成场景                    │
│ ├── practice_start   → 启动练习会话                   │
│ ├── dashboard_query  → 查询统计数据                   │
│ ├── profile_update   → 更新用户档案                   │
│ ├── wiki_search      → 搜索个人 Wiki（骨架）          │
│ └── wiki_write       → 写入个人 Wiki（骨架）          │
│                                                      │
│ Agent 可以在对话中自主决定：                          │
│ "我需要查一下用户档案" → 调用 profile_query           │
│ "让我检索相关知识" → 调用 rag_search                  │
│ "我来评估一下你的表达" → 调用 evaluate_nvc            │
└─────────────────────────────────────────────────────┘
```

### 1.2 新增/修改文件清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `tool/NvcTool.java` | **新增** | 工具接口定义 |
| `tool/NvcToolResult.java` | **新增** | 工具执行结果 |
| `tool/NvcToolContext.java` | **新增** | 工具执行上下文 |
| `tool/NvcToolRegistry.java` | **新增** | 工具注册中心（Spring AI 适配） |
| `tool/NvcToolSceneMapping.java` | **新增** | 场景-工具映射表 |
| `tool/RagSearchTool.java` | **新增** | RAG 知识检索工具 |
| `tool/ProfileQueryTool.java` | **新增** | 用户档案查询工具 |
| `tool/ProfileUpdateTool.java` | **新增** | 用户档案更新工具 |
| `tool/EvaluateNvcTool.java` | **新增** | NVC 表达评估工具 |
| `tool/ScenarioSearchTool.java` | **新增** | 场景搜索工具 |
| `tool/ScenarioGenerateTool.java` | **新增** | 场景生成工具 |
| `tool/PracticeStartTool.java` | **新增** | 练习启动工具 |
| `tool/DashboardQueryTool.java` | **新增** | 统计查询工具 |
| `tool/WikiSearchTool.java` | **新增** | Wiki 搜索（骨架） |
| `tool/WikiWriteTool.java` | **新增** | Wiki 写入（骨架） |
| `dto/NvcChatRequest.java` | **新增** | 统一对话请求 DTO |
| `dto/NvcToolCallConfig.java` | **新增** | 工具调用配置 DTO |
| `dto/AgentDecision.java` | **修改** | 新增 `availableTools` 字段 |
| `service/NvcAgentChatService.java` | **修改** | 支持工具注入和 ToolContext |
| `service/NvcAgentOrchestrator.java` | **修改** | 传递工具配置到 ChatService |
| `router/FreeDialogRouter.java` | **修改** | 注入场景工具列表 |
| `router/ScenarioRouter.java` | **修改** | 注入场景工具列表 |
| `router/StructuredRouter.java` | **修改** | 注入场景工具列表 |
| `tool/NvcToolRegistryTest.java` | **新增** | 单元测试 |

---

## 二、架构设计

### 2.1 整体架构图

```
┌──────────────────────────────────────────────────────────────────┐
│                        ModeRouter 层                              │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────────┐ │
│  │FreeDialog    │ │Scenario      │ │Structured                │ │
│  │Router        │ │Router        │ │Router                    │ │
│  │              │ │              │ │                          │ │
│  │ tools=[      │ │ tools=[      │ │ tools=[                  │ │
│  │  rag_search, │ │  scenario_   │ │  rag_search,             │ │
│  │  profile_    │ │  search,     │ │  profile_query           │ │
│  │  query       │ │  scenario_   │ │ ]                        │ │
│  │ ]            │ │  generate,   │ │                          │ │
│  │              │ │  profile_    │ │                          │ │
│  │              │ │  query       │ │                          │ │
│  │              │ │ ]            │ │                          │ │
│  └──────┬───────┘ └──────┬───────┘ └────────────┬─────────────┘ │
│         │                │                       │               │
│         └────────────────┼───────────────────────┘               │
│                          ▼                                       │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │               AgentDecision                               │   │
│  │  scene: DIALOGUE_GUIDE                                    │   │
│  │  reason: "自由对话模式"                                    │   │
│  │  promptVariables: {mode: "free_dialog"}                   │   │
│  │  availableTools: ["rag_search", "profile_query"]  ← 新增  │   │
│  └──────────────────────────┬───────────────────────────────┘   │
└─────────────────────────────┼────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│                   NvcAgentOrchestrator                            │
│                                                                  │
│  executeAgent(decision, context, userMessage):                   │
│    │                                                             │
│    ├── 1. 获取 Agent 配置                                        │
│    │                                                             │
│    ├── 2. 构建 NvcToolCallConfig ← 从 decision.availableTools   │
│    │      NvcToolCallConfig.builder()                            │
│    │          .toolNames(decision.availableTools())              │
│    │          .build()                                           │
│    │                                                             │
│    ├── 3. 构建 NvcChatRequest ← 统一请求对象                    │
│    │      NvcChatRequest.builder()                               │
│    │          .agentConfig(config)                               │
│    │          .practiceContext(context)                          │
│    │          .userMessage(userMessage)                          │
│    │          .promptVariables(decision.promptVariables())       │
│    │          .toolConfig(toolConfig)                            │
│    │          .build()                                           │
│    │                                                             │
│    └── 4. 调用 ChatService                                      │
│           agentChatService.chat(request)                         │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│                   NvcAgentChatService                             │
│                                                                  │
│  chat(NvcChatRequest request):                                   │
│    │                                                             │
│    ├── 1. 获取 ChatClient                                        │
│    ├── 2. 构建消息列表（System + History + User）                │
│    ├── 3. 构建 prompt spec                                       │
│    │                                                             │
│    ├── 4. 如果 toolConfig.isEnabled()：                          │
│    │      ├── spec.toolContext(buildToolContextMap(request))      │
│    │      │   └── 注入 nvc.userId, nvc.sessionId,               │
│    │      │       nvc.practiceContext 到 ToolContext              │
│    │      └── spec.toolCallbacks(                                │
│    │          toolRegistry.toFunctionCallbacks(toolNames))       │
│    │          └── 按名称过滤，只注入该场景可用的工具              │
│    │                                                             │
│    └── 5. spec.call().content()                                  │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│                    NvcToolRegistry                                │
│                                                                  │
│  Spring 自动注入所有 @Component NvcTool 实现                     │
│                                                                  │
│  toFunctionCallbacks(toolNames):                                 │
│    ├── 按名称过滤工具                                            │
│    ├── 每个 NvcTool → FunctionToolCallback                       │
│    │   ├── .description(tool.description())                      │
│    │   ├── .inputSchema(tool.inputSchema())                      │
│    │   └── .inputType(String.class)                              │
│    │       执行逻辑：                                            │
│    │       ├── 从 ToolContext 提取 NvcToolContext                 │
│    │       ├── 调用 tool.execute(input, nvcContext)              │
│    │       └── 返回 result.data() 或 "Error: ..."               │
│    └── 返回 List<ToolCallback>                                   │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│                    具体工具实现                                    │
│                                                                  │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────────┐ │
│  │RagSearchTool │ │ProfileQuery  │ │EvaluateNvcTool           │ │
│  │              │ │Tool          │ │                          │ │
│  │ NvcRagService│ │NvcProfile    │ │NvcEvaluationService      │ │
│  │   .retrieve()│ │Service       │ │  .evaluateRealtime()     │ │
│  └──────────────┘ └──────────────┘ └──────────────────────────┘ │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────────┐ │
│  │ScenarioSearch│ │ScenarioGen   │ │PracticeStartTool         │ │
│  │Tool          │ │erateTool     │ │                          │ │
│  │NvcScenario   │ │NvcScenario   │ │NvcPracticeSessionService │ │
│  │Service       │ │Service       │ │  .createSession()        │ │
│  └──────────────┘ └──────────────┘ └──────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 分层设计

```
┌─────────────────────────────────────────────────┐
│  Router 层（决策层）                              │
│  职责：决定当前 Agent 可用哪些工具               │
│  代表：FreeDialogRouter, ScenarioRouter          │
├─────────────────────────────────────────────────┤
│  Orchestrator 层（编排层）                        │
│  职责：将工具配置传递给 ChatService              │
│  代表：NvcAgentOrchestrator                      │
├─────────────────────────────────────────────────┤
│  ChatService 层（执行层）                         │
│  职责：将工具注册到 Spring AI ChatClient         │
│  代表：NvcAgentChatService                       │
├─────────────────────────────────────────────────┤
│  Registry 层（适配层）                            │
│  职责：NvcTool → Spring AI FunctionToolCallback  │
│  代表：NvcToolRegistry                           │
├─────────────────────────────────────────────────┤
│  Tool 层（能力层）                                │
│  职责：具体的业务逻辑                             │
│  代表：RagSearchTool, ProfileQueryTool 等        │
└─────────────────────────────────────────────────┘
```

---

## 三、核心接口与 DTO

### 3.1 NvcTool — 工具接口

```java
public interface NvcTool {

    /** 工具名称，用于 Function Calling */
    String name();

    /** 工具描述，告诉 LLM 这个工具能做什么 */
    String description();

    /** 输入参数的 JSON Schema（字符串格式） */
    String inputSchema();

    /** 执行工具 */
    NvcToolResult execute(String input, NvcToolContext context);
}
```

**设计要点**：
- 实现此接口并加上 `@Component` 即可自动注册
- `inputSchema()` 返回 JSON Schema 字符串，Spring AI 会将其传递给 LLM
- `execute()` 接收 JSON 字符串输入和 NVC 上下文

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
    private Long userId;                // 当前用户 ID
    private Long sessionId;             // 当前会话 ID
    private PracticeContext practiceContext;  // 完整的练习上下文
}
```

**安全设计**：userId 和 sessionId 来自服务端会话，**不由 LLM 提供**，防止 IDOR（越权访问）。

### 3.4 NvcChatRequest — 统一对话请求

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
}
```

**设计目的**：将之前 `chat(config, context, userMessage, promptVariables)` 的 4 个参数封装为一个对象，新增 `toolConfig` 字段而不破坏原有签名。

### 3.5 NvcToolCallConfig — 工具调用配置

```java
@Data
@Builder
public class NvcToolCallConfig {
    private final List<String> toolNames;           // 可用工具名称列表
    @Builder.Default
    private final Map<String, Object> extraContext = Map.of();  // 额外上下文

    public static NvcToolCallConfig disabled() {
        return new NvcToolCallConfig(List.of(), Map.of());
    }

    public boolean isEnabled() {
        return toolNames != null && !toolNames.isEmpty();
    }
}
```

### 3.6 AgentDecision — 新增 availableTools 字段

```java
public record AgentDecision(
    NvcAgentScene scene,
    String reason,
    String action,
    Map<String, String> promptVariables,
    List<String> availableTools    // ← 新增：该 Agent 可用的工具名称列表
) {
    // 便捷构造器（无工具），兼容现有调用
    public AgentDecision(NvcAgentScene scene, String reason, String action) {
        this(scene, reason, action, Map.of(), List.of());
    }

    // 便捷构造器（无工具），兼容现有调用
    public AgentDecision(NvcAgentScene scene, String reason, String action,
                         Map<String, String> promptVariables) {
        this(scene, reason, action, promptVariables, List.of());
    }
}
```

---

## 四、NvcToolRegistry — 工具注册中心

### 4.1 自动注册机制

```java
@Service
@Slf4j
public class NvcToolRegistry {

    private final Map<String, NvcTool> tools;

    // Spring 自动注入所有 NvcTool 实现
    public NvcToolRegistry(List<NvcTool> toolList) {
        this.tools = toolList.stream()
            .collect(Collectors.toMap(NvcTool::name, t -> t));
        log.info("[NvcToolRegistry] Registered {} tools: {}",
            tools.size(), tools.keySet());
    }
}
```

**工作原理**：Spring 的 `List<NvcTool>` 注入会自动收集所有标记了 `@Component` 的 `NvcTool` 实现类。只需实现接口 + 加 `@Component`，无需手动注册。

### 4.2 NvcTool → Spring AI FunctionToolCallback 适配

```java
private ToolCallback toFunctionCallback(NvcTool tool) {
    return FunctionToolCallback.builder(tool.name(),
            (String input, ToolContext aiContext) -> {
                try {
                    // 1. 从 Spring AI ToolContext 提取 NVC 上下文
                    NvcToolContext nvcContext = extractNvcContext(aiContext);

                    // 2. 执行工具
                    NvcToolResult result = tool.execute(input, nvcContext);

                    // 3. 返回结果给 LLM
                    return result.success()
                        ? result.data()
                        : "Error: " + result.errorMessage();
                } catch (Exception e) {
                    return "Error: 工具执行异常: " + e.getMessage();
                }
            })
        .description(tool.description())
        .inputSchema(tool.inputSchema())
        .inputType(String.class)
        .build();
}
```

### 4.3 NvcToolContext 提取

```java
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

### 4.4 按名称过滤

```java
// 全量转换 — 主 Agent 使用
public List<ToolCallback> toFunctionCallbacks() {
    return tools.values().stream().map(this::toFunctionCallback).toList();
}

// 按名称子集 — 练习 Agent 按场景过滤
public List<ToolCallback> toFunctionCallbacks(List<String> toolNames) {
    if (toolNames == null || toolNames.isEmpty()) {
        return List.of();
    }
    return toolNames.stream()
        .map(tools::get)
        .filter(Objects::nonNull)    // 忽略不存在的工具名
        .map(this::toFunctionCallback)
        .toList();
}
```

---

## 五、NvcToolSceneMapping — 场景-工具映射

### 5.1 映射表

```java
@Component
public class NvcToolSceneMapping {

    private static final Map<NvcAgentScene, List<String>> SCENE_TOOLS = Map.ofEntries(
        // 练习 Agent 场景
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

        // 步骤教练
        Map.entry(NvcAgentScene.STEP_OBSERVE_COACH, List.of("rag_search")),
        Map.entry(NvcAgentScene.STEP_FEELING_COACH, List.of("rag_search")),
        Map.entry(NvcAgentScene.STEP_NEED_COACH, List.of("rag_search")),
        Map.entry(NvcAgentScene.STEP_REQUEST_COACH, List.of("rag_search")),

        // 困难搭档 — 不给工具，纯角色扮演
        Map.entry(NvcAgentScene.DIFFICULT_PARTNER, List.of()),

        // 档案分析 — 只读
        Map.entry(NvcAgentScene.PROFILE_ANALYZER,
            List.of("profile_query", "dashboard_query"))
    );
}
```

### 5.2 设计哲学

```
┌─────────────────────────────────────────────────────────────┐
│                    场景-工具映射设计原则                      │
│                                                             │
│ 1. 最小权限原则                                             │
│    每个 Agent 只获得完成其职责所需的最小工具集               │
│    ├── DIALOGUE_GUIDE → rag_search + profile_query          │
│    ├── SCENARIO_GENERATOR → scenario_search + generate      │
│    ├── DIFFICULT_PARTNER → 无工具（纯角色扮演）             │
│    └── STEP_*_COACH → rag_search（只需要知识检索）          │
│                                                             │
│ 2. 职责分离                                                 │
│    ├── 评估师 → evaluate_nvc + rag_search                   │
│    ├── 知识顾问 → rag_search + wiki_search                  │
│    └── 档案分析 → profile_query + dashboard_query（只读）   │
│                                                             │
│ 3. 安全边界                                                 │
│    ├── profile_update 只给主 Agent（Phase 2）               │
│    ├── practice_start 只给主 Agent（Phase 2）               │
│    └── 困难搭档完全不给工具（保持角色纯粹性）               │
└─────────────────────────────────────────────────────────────┘
```

---

## 六、Router 改造 — 工具注入

### 6.1 改造前

```java
// FreeDialogRouter（改造前）
public AgentDecision route(PracticeContext context) {
    return new AgentDecision(
        NvcAgentScene.DIALOGUE_GUIDE,
        "自由对话模式，使用教练引导",
        null,
        Map.of("mode", "free_dialog")
    );
    // 没有工具列表
}
```

### 6.2 改造后

```java
// FreeDialogRouter（改造后）
@Component
@RequiredArgsConstructor
public class FreeDialogRouter implements ModeRouter {

    private final NvcToolSceneMapping toolSceneMapping;  // ← 注入

    @Override
    public AgentDecision route(PracticeContext context) {
        return new AgentDecision(
            NvcAgentScene.DIALOGUE_GUIDE,
            "自由对话模式",
            null,
            Map.of("mode", "free_dialog"),
            toolSceneMapping.getDefaultTools(NvcAgentScene.DIALOGUE_GUIDE)
            // ← 传入工具列表：["rag_search", "profile_query"]
        );
    }
}
```

### 6.3 三个 Router 的工具分配

```
FreeDialogRouter（自由对话）：
└── DIALOGUE_GUIDE → ["rag_search", "profile_query"]

ScenarioRouter（场景驱动）：
├── CREATED 阶段 → SCENARIO_GENERATOR → ["scenario_search", "scenario_generate", "profile_query"]
└── 后续阶段 → DIFFICULT_PARTNER → []（无工具）

StructuredRouter（结构化四步）：
└── DIALOGUE_GUIDE → ["rag_search", "profile_query"]
```

---

## 七、NvcAgentChatService 改造 — 工具注入

### 7.1 改造前

```java
// NvcAgentChatService（改造前）
public String chat(
    NvcAgentConfigEntity config, PracticeContext context,
    String userMessage, Map<String, String> promptVariables) {
    ChatClient client = getChatClient(config);
    List<Message> messages = buildMessages(config, context, userMessage, promptVariables);

    return client.prompt()
        .options(buildChatOptions(config))
        .messages(messages)
        .call()
        .content();
    // 没有工具注入
}
```

### 7.2 改造后

```java
// NvcAgentChatService（改造后）
public String chat(NvcChatRequest request) {
    ChatClient client = getChatClient(request.getAgentConfig());
    List<Message> messages = buildMessages(
        request.getAgentConfig(), request.getPracticeContext(),
        request.getUserMessage(), request.getPromptVariables());

    var spec = client.prompt()
        .options(buildChatOptions(request.getAgentConfig()))
        .messages(messages);

    // 如果有工具配置，注入工具
    if (request.getToolConfig().isEnabled()) {
        spec = spec.toolContext(buildToolContextMap(request));      // 注入上下文
        spec = spec.toolCallbacks(
            toolRegistry.toFunctionCallbacks(
                request.getToolConfig().getToolNames()));           // 注入工具
    }

    return spec.call().content();
}
```

### 7.3 ToolContext 注入

```java
private Map<String, Object> buildToolContextMap(NvcChatRequest request) {
    Map<String, Object> map = new HashMap<>();
    PracticeContext ctx = request.getPracticeContext();
    if (ctx != null && ctx.getSession() != null) {
        map.put("nvc.userId", ctx.getSession().getUserId());
        map.put("nvc.sessionId", ctx.getSession().getId());
        map.put("nvc.practiceContext", ctx);
    }
    map.putAll(request.getToolConfig().getExtraContext());
    return map;
}
```

**安全设计**：userId 和 sessionId 从服务端 `PracticeContext.getSession()` 获取，**不从客户端传入**，防止 LLM 被诱导传入其他用户的 ID。

---

## 八、NvcAgentOrchestrator 改造 — 传递工具配置

### 8.1 改造前

```java
// NvcAgentOrchestrator（改造前）
public String executeAgent(
    AgentDecision decision, PracticeContext context, String userMessage) {
    NvcAgentConfigEntity config = agentConfigService.getConfig(decision.scene());
    return agentChatService.chat(config, context, userMessage, decision.promptVariables());
    // 直接调用旧方法
}
```

### 8.2 改造后

```java
// NvcAgentOrchestrator（改造后）
public String executeAgent(
    AgentDecision decision, PracticeContext context, String userMessage) {
    NvcAgentConfigEntity config = agentConfigService.getConfig(decision.scene());

    // 1. 从 decision 中提取工具配置
    NvcToolCallConfig toolConfig = NvcToolCallConfig.builder()
        .toolNames(decision.availableTools())
        .build();

    // 2. 构建统一对话请求
    return agentChatService.chat(NvcChatRequest.builder()
        .agentConfig(config)
        .practiceContext(context)
        .userMessage(userMessage)
        .promptVariables(decision.promptVariables())
        .toolConfig(toolConfig)
        .build());
}
```

---

## 九、10 个工具实现详解

### 9.1 RagSearchTool — RAG 知识检索

```
工具名：rag_search
描述：搜索 NVC 知识库，检索相关理论、话术模板、情绪词汇等信息
输入：{ query: string, topK?: number }
输出：格式化的知识列表

执行逻辑：
├── 调用 NvcRagService.retrieve(query, types, topK)
├── 检索 NVC_THEORY + SPEECH_TEMPLATE + EMOTION_VOCAB 三种类型
└── 格式化为 "- 知识内容" 列表

使用场景：对话教练、步骤教练、共情教练、知识顾问
```

### 9.2 ProfileQueryTool — 用户档案查询

```
工具名：profile_query
描述：查询用户 NVC 档案，包括等级、沟通背景、性格特征、能力雷达等
输入：{}（无参数，userId 从上下文获取）
输出：用户档案的 Prompt 文本

执行逻辑：
├── 从 NvcToolContext 获取 userId（安全，不由 LLM 提供）
└── 调用 NvcProfileService.getUserProfilePrompt(userId)

使用场景：对话教练、场景生成官、共情教练、档案分析
安全：IDOR 防护 — userId 来自服务端会话
```

### 9.3 ProfileUpdateTool — 用户档案更新

```
工具名：profile_update
描述：更新用户 NVC 档案字段
输入：{ field: string, value: string }
支持字段：communicationBackground, personalityTraits, communicationStyle, emotionTriggers

执行逻辑：
├── 从 NvcToolContext 获取 userId
├── 根据 field 构建 UserProfileUpdateRequest
└── 调用 NvcProfileService.updateProfile(userId, request)

使用场景：主 Agent（Phase 2），当前未分配给任何练习 Agent
```

### 9.4 EvaluateNvcTool — NVC 表达评估

```
工具名：evaluate_nvc
描述：评估用户的 NVC 表达质量，返回四维度评分
输入：{ expression: string, scenario?: string }
输出："观察: 85/100, 感受: 70/100, 需求: 90/100, 请求: 60/100\n反馈: ..."

执行逻辑：
├── 从 NvcToolContext 获取 sessionId 和 userId
└── 调用 NvcEvaluationService.evaluateRealtime(sessionId, userId, expression, scenario, null)

使用场景：NVC 表达评估师
安全：sessionId 和 userId 来自服务端
```

### 9.5 ScenarioSearchTool — 场景搜索

```
工具名：scenario_search
描述：搜索 NVC 练习场景，可按类型和难度筛选
输入：{ scenarioType?: string, difficulty?: string }
输出：匹配的场景列表 JSON

执行逻辑：
├── 解析 scenarioType 和 difficulty（可选）
└── 调用 NvcScenarioService.queryScenarios(query)

使用场景：场景生成官
```

### 9.6 ScenarioGenerateTool — 场景生成

```
工具名：scenario_generate
描述：AI 生成一个新的 NVC 练习场景
输入：{ scenarioType?: string, difficulty?: string, focusElements?: string[], description?: string }
输出：生成的场景 JSON

执行逻辑：
├── 构建 ScenarioGenerateRequest
└── 调用 NvcScenarioService.generateScenario(request)

使用场景：场景生成官
```

### 9.7 PracticeStartTool — 练习启动

```
工具名：practice_start
描述：启动一个新的 NVC 练习会话
输入：{ scenarioId?: Long, mode: string, difficulty?: string }
输出："练习会话已创建，sessionId=123"

执行逻辑：
├── 从 NvcToolContext 获取 userId
├── 解析 mode（SCENARIO/FREE_DIALOG/STRUCTURED_FOUR_STEP）
└── 调用 NvcPracticeSessionService.createSession(userId, request)

使用场景：主 Agent（Phase 2），当前未分配给任何练习 Agent
```

### 9.8 DashboardQueryTool — 统计查询

```
工具名：dashboard_query
描述：查询用户练习统计数据
输入：{ period?: string }
输出：统计数据 JSON

执行逻辑：
├── 从 NvcToolContext 获取 userId
└── 调用 NvcDashboardService.getUserStats(userId)

使用场景：档案分析（只读）
```

### 9.9 WikiSearchTool / WikiWriteTool — Wiki 工具（骨架）

```
工具名：wiki_search / wiki_write
描述：搜索/写入个人 Wiki 知识条目
输入：wiki_search: { query, topK } / wiki_write: { title, content, category }
输出："Wiki 功能暂未开放，敬请期待"

状态：骨架实现，返回固定错误消息
用途：预留接口，后续 Phase 实现
```

---

## 十、完整调用链路

### 10.1 一次完整的工具调用流程

```
用户发送消息："帮我查一下 NVC 中观察和评价的区别"
  │
  ▼
① NvcPracticeDialogueService.sendMessageStream()
   ├── 保存用户消息到 DB
   └── 调用 orchestrator.buildPracticeContext()
  │
  ▼
② NvcAgentOrchestrator.buildPracticeContext()
   └── 加载历史、场景、用户档案、RAG 知识
  │
  ▼
③ NvcAgentOrchestrator.decideNextAgent(context)
   └── FreeDialogRouter.route(context)
       └── AgentDecision(
           scene: DIALOGUE_GUIDE,
           availableTools: ["rag_search", "profile_query"]
       )
  │
  ▼
④ NvcAgentOrchestrator.executeAgentStream(decision, context, userMessage)
   ├── NvcToolCallConfig(toolNames: ["rag_search", "profile_query"])
   └── NvcChatRequest(
       agentConfig: ...,
       practiceContext: ...,
       userMessage: "帮我查一下...",
       toolConfig: toolConfig
   )
  │
  ▼
⑤ NvcAgentChatService.chatStream(request)
   ├── buildMessages() → System + History + User
   ├── toolContext: {nvc.userId: 1, nvc.sessionId: 5, nvc.practiceContext: ...}
   ├── toolCallbacks: [RagSearchTool→FunctionToolCallback, ProfileQueryTool→FunctionToolCallback]
   └── client.prompt().toolContext(...).toolCallbacks(...).stream().content()
  │
  ▼
⑥ Spring AI 内部处理
   ├── 将 toolCallbacks 的 description + inputSchema 注入到 LLM 请求
   ├── LLM 分析用户问题，决定调用 rag_search
   ├── LLM 生成 tool_call: {name: "rag_search", input: '{"query":"观察和评价的区别"}'}
   ├── Spring AI 执行 FunctionToolCallback
   │   ├── extractNvcContext(aiContext) → NvcToolContext(userId=1, sessionId=5)
   │   └── ragSearchTool.execute('{"query":"观察和评价的区别"}', nvcContext)
   │       └── NvcRagService.retrieve("观察和评价的区别", [NVC_THEORY, ...], 5)
   │           └── 返回知识列表
   ├── Spring AI 将工具结果作为 ToolResponseMessage 注入对话
   └── LLM 基于工具结果生成最终回答
  │
  ▼
⑦ 流式输出给前端
   └── Flux<String> tokenStream
```

### 10.2 时序图

```
用户     前端     Controller  DialogueService  Orchestrator  ChatService  ToolRegistry  LLM    Tool
 │        │          │            │                │             │            │          │       │
 │─发送──→│──POST───→│──调用──────→│──构建上下文───→│             │            │          │       │
 │        │          │            │                │             │            │          │       │
 │        │          │            │                │─决定Agent──→│            │          │       │
 │        │          │            │                │             │            │          │       │
 │        │          │            │                │─构建请求───→│            │          │       │
 │        │          │            │                │             │            │          │       │
 │        │          │            │                │             │─获取工具──→│          │       │
 │        │          │            │                │             │←─ToolCBs───│          │       │
 │        │          │            │                │             │            │          │       │
 │        │          │            │                │             │─prompt()──→│          │       │
 │        │          │            │                │             │+toolCtx    │          │       │
 │        │          │            │                │             │+toolCBs    │          │       │
 │        │          │            │                │             │            │          │       │
 │        │          │            │                │             │            │          │─分析──→│
 │        │          │            │                │             │            │          │       │
 │        │          │            │                │             │            │          │─调用──→│
 │        │          │            │                │             │            │          │       │
 │        │          │            │                │             │            │          │←结果───│
 │        │          │            │                │             │            │          │       │
 │        │          │            │                │             │←──流式─────│          │       │
 │        │          │            │                │←──流式──────│            │          │       │
 │        │          │            │←──流式─────────│             │            │          │       │
 │        │          │←──SSE──────│                │             │            │          │       │
 │←SSE────│          │            │                │             │            │          │       │
```

---

## 十一、安全设计

### 11.1 IDOR 防护（ProfileQueryTool）

```java
// ProfileQueryTool.execute()
public NvcToolResult execute(String input, NvcToolContext context) {
    Long userId = context.getUserId();    // ← 从服务端上下文获取
    if (userId == null) {
        return NvcToolResult.failure("缺少用户ID");
    }
    // userId 不由 LLM 提供，防止越权访问其他用户的档案
}
```

**修复记录**：commit `c193915` 专门修复了 IDOR 问题，确保 userId 从安全的 `NvcToolContext` 获取，而非从 LLM 的输入参数中获取。

### 11.2 工具权限隔离

```
┌─────────────────────────────────────────────────────────────┐
│                    工具权限矩阵                              │
│                                                             │
│ Agent 场景               │ 可用工具                         │
│ ─────────────────────────┼──────────────────────────────── │
│ DIALOGUE_GUIDE           │ rag_search, profile_query       │
│ SCENARIO_GENERATOR       │ scenario_search, scenario_      │
│                          │ generate, profile_query          │
│ DIFFICULT_PARTNER        │ （无工具）                       │
│ NVC_EXPRESSION_EVALUATOR │ evaluate_nvc, rag_search        │
│ EMPATHY_COACH            │ rag_search, profile_query       │
│ NVC_KNOWLEDGE_ADVISOR    │ rag_search, wiki_search         │
│ STEP_*_COACH             │ rag_search                      │
│ PROFILE_ANALYZER         │ profile_query, dashboard_query  │
│ ─────────────────────────┼──────────────────────────────── │
│ 主 Agent（Phase 2）      │ 全部 10 个工具                  │
└─────────────────────────────────────────────────────────────┘
```

### 11.3 异常处理

```java
// NvcToolRegistry.toFunctionCallback() 中的异常处理
(String input, ToolContext aiContext) -> {
    try {
        NvcToolContext nvcContext = extractNvcContext(aiContext);
        NvcToolResult result = tool.execute(input, nvcContext);
        return result.success()
            ? result.data()
            : "Error: " + result.errorMessage();
    } catch (Exception e) {
        log.error("[NvcToolRegistry] Tool execution failed: tool={}", tool.name(), e);
        return "Error: 工具执行异常: " + e.getMessage();
        // 不抛出异常，返回错误字符串给 LLM，让 LLM 决定如何处理
    }
}
```

---

## 十二、向后兼容设计

### 12.1 旧方法保留

```java
// NvcAgentChatService 中保留了旧方法签名
// ═══ 原有方法 — 保持不变，向后兼容 ═══

public String chat(
    NvcAgentConfigEntity config, PracticeContext context,
    String userMessage, Map<String, String> promptVariables) {
    // 内部委托给新方法，toolConfig 默认 disabled
    return chat(NvcChatRequest.builder()
        .agentConfig(config)
        .practiceContext(context)
        .userMessage(userMessage)
        .promptVariables(promptVariables)
        .build());
}
```

### 12.2 AgentDecision 便捷构造器

```java
// 无工具的便捷构造器，兼容现有调用
public AgentDecision(NvcAgentScene scene, String reason, String action) {
    this(scene, reason, action, Map.of(), List.of());
}
```

---

## 十三、测试覆盖

### 13.1 NvcToolRegistryTest

```java
@DisplayName("NvcToolRegistry 测试")
class NvcToolRegistryTest {

    @Test
    @DisplayName("自动注册所有 NvcTool 实现")
    void registerAllTools() {
        NvcToolRegistry registry = new NvcToolRegistry(List.of(t1, t2));
        assertEquals(2, registry.getAllTools().size());
    }

    @Test
    @DisplayName("toFunctionCallbacks() 返回全量工具")
    void toFunctionCallbacks_all() { ... }

    @Test
    @DisplayName("toFunctionCallbacks(List) 按名称过滤")
    void toFunctionCallbacks_filtered() { ... }

    @Test
    @DisplayName("toFunctionCallbacks(List) 忽略不存在的工具名")
    void toFunctionCallbacks_ignoreUnknown() { ... }

    @Test
    @DisplayName("toFunctionCallbacks 空列表返回空")
    void toFunctionCallbacks_empty() { ... }
}
```

---

## 十四、总结

### 14.1 核心设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 工具注册方式 | Spring 自动注入 `List<NvcTool>` | 零配置，加 `@Component` 即可 |
| 工具适配方式 | `FunctionToolCallback` 适配器 | 与 Spring AI 原生 Function Calling 集成 |
| 工具分配方式 | `NvcToolSceneMapping` 静态映射 | 简单可控，每个 Agent 只拿需要的工具 |
| 上下文传递 | `ToolContext Map` + `NvcToolContext` | 安全（IDOR 防护），解耦 |
| 请求封装 | `NvcChatRequest` 统一 DTO | 扩展性好，新增字段不改签名 |
| 旧代码兼容 | 保留旧方法 + 委托模式 | 渐进式迁移，不破坏现有功能 |

### 14.2 扩展路径

```
Phase 1.2（当前）：框架 + 10 个工具骨架
├── NvcTool 接口 + NvcToolRegistry
├── 10 个工具实现（8 个完整 + 2 个 Wiki 骨架）
├── 场景-工具映射
└── Router + ChatService + Orchestrator 改造

Phase 2（未来）：
├── 主 Agent（全量工具）
├── Wiki 工具实现（PERSONAL_WIKI 类型知识库）
├── 工具调用链（多工具串联）
├── 工具调用统计和监控
└── 动态工具权限（基于用户等级）
```
