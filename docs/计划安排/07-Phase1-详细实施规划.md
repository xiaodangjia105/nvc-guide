# Phase 1 详细实施规划 — RAG + Agent 工具调用 + Skill 标准化

> 创建时间：2026-07-17
> 前置条件：Phase 0 核心 Bug 已修复（BUG-1/2/4/6）
> 总预估：6-7 天

---

## 〇、基建现状评估

### 已具备 ✅

| 基建 | 位置 | 说明 |
|------|------|------|
| Spring AI 2.0.0-M4 | `libs.versions.toml` | OpenAI 兼容 API，支持 Function Calling |
| spring-ai-agent-utils 0.7.0 | `AgentUtilsConfiguration` | SkillsTool 已接入 |
| `ToolCallingManager` | `LlmProviderRegistry` | 已注入，支持工具调用生命周期管理 |
| `ToolCallAdvisor` | `LlmProviderRegistry.buildDefaultAdvisors()` | 已配置在 ChatClient Advisor 链 |
| `ToolCallback` 机制 | `AgentUtilsConfiguration` | `interviewSkillsToolCallback` 有完整示例 |
| `StructuredOutputInvoker` | `common/ai/` | BeanOutputConverter + 重试 + 修复 |
| `NvcAgentChatService` | `nvcpractice/service/` | 对话服务，可扩展注入工具 |
| `NvcAgentOrchestrator` | `nvcpractice/service/` | 调度中心，ModeRouter 策略模式 |
| `NvcEvaluationService` | `nvcpractice/service/` | 5 维度评估，结构化输出 |
| `KnowledgeBaseQueryService` | `knowledgebase/service/` | 向量检索 + 查询重写 + 流式输出 |
| `KnowledgeBaseVectorService` | `knowledgebase/service/` | pgvector COSINE 相似度检索 |
| `NvcProfileService` | `nvcprofile/service/` | 档案 CRUD + 能力分数管理 |
| `NvcScenarioService` | `nvcscenario/service/` | 场景 CRUD + AI 生成 |
| `NvcDashboardService` | `nvcprofile/service/` | 仪表盘统计 |
| `PracticeContext` | `nvcpractice/dto/` | 已有 `userProfileSummary`、`ragContext` 字段（未填充） |

### 缺失 ❌

| 缺失项 | 影响 | 阻塞的工具 |
|--------|------|-----------|
| `NvcRagService` | 无法做 NVC 专用 RAG 检索 | `rag_search` |
| `KnowledgeBaseType` 枚举 | 无法按类型区分知识库 | `rag_search`（多库分类） |
| `NvcWikiService` | Wiki 模块不存在 | `wiki_search`、`wiki_write` |
| `NvcWikiEntity` | Wiki 数据模型不存在 | `wiki_search`、`wiki_write` |
| `NvcEvaluationSkill` | 评估未标准化为 Skill | `evaluate_nvc` |
| `NvcScenarioGenerateSkill` | 场景生成未标准化 | `scenario_generate` |
| `profileSummary` 未填充 | PracticeContext 缺用户档案 | Agent 个性化上下文 |
| `ragContext` 未填充 | PracticeContext 缺 RAG 知识 | Agent RAG 增强 |
| NVC 知识文档 | 无 NVC 理论/话术/情绪词汇 | RAG 知识库内容 |

---

## 一、Phase 0.5：基建补全（1 天）

> 目标：填充 PracticeContext 的两个空字段，让现有 Agent 对话获得个性化 + RAG 能力
> 这一步不做，后续所有工具的上下文注入都不完整

### 1.1 填充 profileSummary

**问题**：`NvcAgentOrchestrator.buildPracticeContext()` 未加载用户档案

**修改文件**：`NvcAgentOrchestrator.java`

**方案**：
```java
// 注入 NvcProfileService
private final NvcProfileService profileService;

// buildPracticeContext() 中增加
String userProfileSummary = null;
NvcUserProfileEntity profile = profileService.getOrCreateProfile(userId);
if (profile != null) {
    userProfileSummary = formatProfile(profile);
}

return PracticeContext.builder()
    // ... 现有字段
    .userProfileSummary(userProfileSummary)  // 新增
    .build();
```

**formatProfile 方法**：
```java
private String formatProfile(NvcUserProfileEntity profile) {
    StringBuilder sb = new StringBuilder();
    sb.append("NVC等级: ").append(profile.getNvcLevel());
    sb.append("\n沟通背景: ").append(profile.getCommunicationBackground());
    sb.append("\n性格特征: ").append(profile.getPersonalityTraits());
    sb.append("\n沟通风格: ").append(profile.getCommunicationStyle());
    sb.append("\n情绪触发器: ").append(profile.getEmotionTriggers());
    return sb.toString();
}
```

### 1.2 填充 ragContext

**问题**：`buildPracticeContext()` 未做 RAG 检索

**前置**：需要先完成 1.3（NvcRagService），此处先预留接口

**方案**：
```java
// 注入 NvcRagService（1.3 节创建）
private final NvcRagService ragService;

// buildPracticeContext() 中增加
String ragContext = null;
if (scenarioDescription != null) {
    List<RagResult> results = ragService.retrieve(
        scenarioDescription, userId,
        List.of(KnowledgeBaseType.NVC_THEORY, KnowledgeBaseType.SPEECH_TEMPLATE),
        3
    );
    if (!results.isEmpty()) {
        ragContext = results.stream()
            .map(RagResult::text)
            .collect(Collectors.joining("\n\n"));
    }
}
```

### 1.3 Controller 业务逻辑移 Service

**问题**：`NvcPracticeController.completeSession()` 包含评估编排逻辑

**修改文件**：
- `NvcPracticeSessionService.java` — 新增 `completeAndEvaluate` 方法
- `NvcPracticeController.java` — 简化为路由委托

**方案**：
```java
// NvcPracticeSessionService.java
public NvcPracticeSessionEntity completeAndEvaluate(Long sessionId, Long userId) {
    NvcPracticeSessionEntity session = completeSession(sessionId);
    if (session.getCurrentPhase() == NvcSessionPhase.EVALUATED) {
        return session; // 已评估，直接返回
    }
    List<NvcPracticeMessageEntity> messages =
        messageRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);
    if (!messages.isEmpty()) {
        try {
            evaluationService.evaluateFinal(sessionId, userId, messages);
            session = updatePhase(sessionId, NvcSessionPhase.EVALUATED);
        } catch (Exception e) {
            log.error("Final evaluation failed: sessionId={}", sessionId, e);
        }
    }
    return session;
}
```

### 1.4 验收标准

```
□ profileSummary 正确注入到 Agent 系统提示词
□ Agent 对话中能看到用户档案信息
□ completeSession 业务逻辑在 Service 层
□ Controller 仅做路由委托
```

---

## 二、Phase 1.1：RAG 知识库集成（2 天）

> 目标：让 Agent 对话能引用 NVC 知识，实现个性化检索
> 简历关键词：pgvector、混合检索、个性化权重、多知识库管理

### Day 1：NvcRagService + 多知识库分类

#### 2.1 KnowledgeBaseType 枚举

**新建文件**：`knowledgebase/model/KnowledgeBaseType.java`

```java
public enum KnowledgeBaseType {
    NVC_THEORY,       // NVC 理论知识
    SPEECH_TEMPLATE,  // 话术模板
    EMOTION_VOCAB,    // 情绪词汇
    USER_CASE,        // 用户案例
    PERSONAL_WIKI     // 个人 Wiki（按用户隔离）
}
```

**修改文件**：`KnowledgeBaseEntity.java` — 添加 `type` 字段

```java
@Enumerated(EnumType.STRING)
private KnowledgeBaseType type;
```

**修改文件**：`KnowledgeBaseListService.java` — 按类型查询

```java
public List<KnowledgeBaseEntity> listByType(KnowledgeBaseType type) {
    return knowledgeBaseRepository.findByType(type);
}
```

#### 2.2 NvcRagService

**新建文件**：`nvcpractice/service/NvcRagService.java`

**核心职责**：
- 封装 NVC 专用的 RAG 检索逻辑
- 支持多知识库类型过滤
- 支持个性化权重（基于用户薄弱要素）
- 复用 `KnowledgeBaseVectorService` 的向量检索能力

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcRagService {

    private final KnowledgeBaseVectorService vectorService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 统一 RAG 检索
     * @param query 检索查询
     * @param userId 用户ID（用于 Wiki 隔离）
     * @param knowledgeTypes 检索的知识库类型
     * @param topK 返回结果数量
     */
    public List<RagResult> retrieve(String query, Long userId,
                                     List<KnowledgeBaseType> knowledgeTypes,
                                     int topK) {
        // 1. 按类型查出所有匹配的知识库 ID
        List<Long> kbIds = knowledgeBaseRepository
            .findByTypeIn(knowledgeTypes).stream()
            .map(KnowledgeBaseEntity::getId)
            .toList();

        if (kbIds.isEmpty()) {
            return List.of();
        }

        // 2. 向量检索
        List<Document> docs = vectorService.similaritySearch(
            query, kbIds, topK, 0.3);

        // 3. 转换为 RagResult
        return docs.stream()
            .map(doc -> new RagResult(
                doc.getText(),
                doc.getMetadata(),
                (Double) doc.getMetadata().getOrDefault("distance", 0.0)
            ))
            .toList();
    }

    /**
     * 个性化检索（基于用户薄弱要素加权）
     */
    public List<RagResult> retrievePersonalized(String query, Long userId,
                                                  NvcPracticeStep weakElement,
                                                  int topK) {
        // 1. 基础检索
        List<RagResult> baseResults = retrieve(query, userId,
            List.of(KnowledgeBaseType.NVC_THEORY, KnowledgeBaseType.SPEECH_TEMPLATE),
            topK * 2);

        // 2. 按薄弱要素加权排序
        String weakKeyword = weakElement.name().toLowerCase();
        return baseResults.stream()
            .sorted((a, b) -> {
                double scoreA = a.score() * (containsKeyword(a, weakKeyword) ? 1.5 : 1.0);
                double scoreB = b.score() * (containsKeyword(b, weakKeyword) ? 1.5 : 1.0);
                return Double.compare(scoreB, scoreA);
            })
            .limit(topK)
            .toList();
    }

    private boolean containsKeyword(RagResult result, String keyword) {
        return result.text().toLowerCase().contains(keyword);
    }
}
```

**新建文件**：`nvcpractice/dto/RagResult.java`

```java
public record RagResult(
    String text,
    Map<String, Object> metadata,
    double score
) {}
```

#### 2.3 KnowledgeBaseRepository 扩展

**修改文件**：`KnowledgeBaseRepository.java`

```java
List<KnowledgeBaseEntity> findByType(KnowledgeBaseType type);
List<KnowledgeBaseEntity> findByTypeIn(List<KnowledgeBaseType> types);
```

### Day 2：NVC 知识文档 + Agent 对话集成

#### 2.4 NVC 知识文档

**新建目录**：`resources/knowledge/`

| 文件 | 内容 | 预估字数 |
|------|------|---------|
| `NVC理论基础.md` | 四要素详解、NVC 与传统沟通区别、核心原则 | 3000-5000 字 |
| `NVC话术模板.md` | 职场/家庭/亲密关系/社交场景话术，每类 10+ 条 | 5000-8000 字 |
| `情绪与需求词汇库.md` | 感受词汇表（正面/负面各 50+）、需求词汇表（5 类） | 3000-5000 字 |
| `NVC案例库.md` | 成功/失败案例对比，每类 3-5 组 | 3000-5000 字 |

**文档格式**：
```markdown
# NVC 理论基础

## 一、四要素详解

### 1. 观察（Observation）
观察是指客观描述事实，不掺杂评价和判断。
...

### 2. 感受（Feeling）
感受是指我们内心的情感体验。
...
```

#### 2.5 Agent 对话集成 RAG

**修改文件**：`NvcAgentOrchestrator.buildPracticeContext()`

在 1.2 节的基础上，填充 `ragContext` 字段。

#### 2.6 知识库种子数据脚本

**新建文件**：`resources/db/migration/seed-knowledge-base.sql`

将 4 个知识文档注册到 `knowledge_bases` 表，并触发向量化。

#### 2.7 前端：知识库类型筛选

**修改文件**：`KnowledgeBaseManagePage.tsx`

增加类型下拉筛选器。

### 验收标准

```
□ NvcRagService 可用
  □ retrieve() 按类型检索正常
  □ retrievePersonalized() 个性化加权生效
  □ 返回结果包含文本、元数据、分数

□ 多知识库分类
  □ KnowledgeBaseType 枚举定义完成
  □ KnowledgeBaseEntity 新增 type 字段
  □ 按类型查询 API 正常

□ NVC 知识文档
  □ 4 份知识文档已创建
  □ 已注册到知识库并完成向量化

□ Agent 对话集成
  □ profileSummary 已注入（Phase 0.5）
  □ ragContext 已注入
  □ Agent 回复能引用 RAG 知识
```

---

## 三、Phase 1.2：Agent 工具调用框架（2 天）

> 目标：实现 NvcTool 接口 + NvcToolRegistry + 10 个核心工具
> 简历关键词：Spring AI Function Calling、动态工具注册、Agent 工具调用框架

### Day 1：框架 + 5 个工具

#### 3.1 NvcTool 接口

**新建文件**：`nvcpractice/tool/NvcTool.java`

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

    /** 输入参数的 JSON Schema */
    JsonSchema inputSchema();

    /** 执行工具 */
    NvcToolResult execute(JsonNode input, ToolContext context);
}
```

#### 3.2 NvcToolResult

**新建文件**：`nvcpractice/tool/NvcToolResult.java`

```java
public record NvcToolResult(
    boolean success,
    String data,
    String errorMessage
) {
    public static NvcToolResult success(String data) {
        return new NvcToolResult(true, data, null);
    }

    public static NvcToolResult failure(String errorMessage) {
        return new NvcToolResult(false, null, errorMessage);
    }
}
```

#### 3.3 ToolContext

**新建文件**：`nvcpractice/tool/ToolContext.java`

```java
@Data
@Builder
public class ToolContext {
    private Long userId;
    private Long sessionId;
    private NvcPracticeSessionEntity session;
    private PracticeContext practiceContext;
}
```

#### 3.4 NvcToolRegistry

**新建文件**：`nvcpractice/tool/NvcToolRegistry.java`

```java
@Service
@Slf4j
public class NvcToolRegistry {

    private final Map<String, NvcTool> tools;

    /**
     * Spring 自动注入所有 NvcTool 实现
     */
    public NvcToolRegistry(List<NvcTool> toolList) {
        this.tools = toolList.stream()
            .collect(Collectors.toMap(NvcTool::name, t -> t));
        log.info("[NvcToolRegistry] Registered {} tools: {}",
            tools.size(), tools.keySet());
    }

    public NvcTool getTool(String name) {
        return tools.get(name);
    }

    public List<NvcTool> getAllTools() {
        return List.copyOf(tools.values());
    }

    /**
     * 转换为 Spring AI FunctionCallback 列表
     * 用于 ChatClient.prompt().functionCallbacks()
     */
    public List<FunctionCallback> toFunctionCallbacks() {
        return tools.values().stream()
            .map(this::toFunctionCallback)
            .toList();
    }

    /**
     * 按名称列表获取子集（不同 Agent 可用不同工具集）
     */
    public List<FunctionCallback> toFunctionCallbacks(List<String> toolNames) {
        return toolNames.stream()
            .map(tools::get)
            .filter(Objects::nonNull)
            .map(this::toFunctionCallback)
            .toList();
    }

    private FunctionCallback toFunctionCallback(NvcTool tool) {
        return FunctionCallback.builder()
            .function(tool.name(), (input) -> {
                try {
                    JsonNode jsonNode = new ObjectMapper().readTree(input);
                    ToolContext context = ToolContext.builder().build(); // 上下文由调用方注入
                    NvcToolResult result = tool.execute(jsonNode, context);
                    return result.success() ? result.data() : "Error: " + result.errorMessage();
                } catch (Exception e) {
                    log.error("Tool execution failed: tool={}", tool.name(), e);
                    return "Error: " + e.getMessage();
                }
            })
            .description(tool.description())
            .inputTypeSchema(tool.inputSchema())
            .build();
    }
}
```

#### 3.5 NvcAgentChatService 升级

**修改文件**：`NvcAgentChatService.java`

在 `chat()` 和 `chatStream()` 方法中注入工具回调：

```java
private final NvcToolRegistry toolRegistry;

public String chat(NvcAgentConfigEntity config, PracticeContext context,
                   String userMessage, Map<String, String> promptVariables) {
    ChatClient client = getChatClient(config);
    List<Message> messages = buildMessages(config, context, userMessage, promptVariables);

    return client.prompt()
        .options(buildChatOptions(config))
        .functionCallbacks(toolRegistry.toFunctionCallbacks()) // 注入工具
        .messages(messages)
        .call()
        .content();
}
```

#### 3.6 核心工具实现（第一批 5 个）

| # | 工具名 | 依赖服务 | 实现复杂度 |
|---|--------|---------|-----------|
| 1 | `rag_search` | NvcRagService | 🟢 低 — 调用已有 Service |
| 2 | `profile_query` | NvcProfileService | 🟢 低 — 调用已有 Service |
| 3 | `dashboard_query` | NvcDashboardService | 🟢 低 — 调用已有 Service |
| 4 | `scenario_search` | NvcScenarioService | 🟢 低 — 调用已有 Service |
| 5 | `evaluate_nvc` | NvcEvaluationService | 🟡 中 — 需包装为 Skill |

**rag_search 工具示例**：

```java
@Component
@RequiredArgsConstructor
public class RagSearchTool implements NvcTool {

    private final NvcRagService ragService;

    @Override
    public String name() { return "rag_search"; }

    @Override
    public String description() {
        return "搜索 NVC 知识库，检索相关理论、话术模板、情绪词汇等信息";
    }

    @Override
    public JsonSchema inputSchema() {
        return JsonSchemaGenerator.fromClass(RagSearchInput.class);
    }

    @Override
    public NvcToolResult execute(JsonNode input, ToolContext context) {
        String query = input.get("query").asText();
        int topK = input.has("topK") ? input.get("topK").asInt(5) : 5;

        List<RagResult> results = ragService.retrieve(
            query, context.getUserId(),
            List.of(KnowledgeBaseType.NVC_THEORY,
                    KnowledgeBaseType.SPEECH_TEMPLATE,
                    KnowledgeBaseType.EMOTION_VOCAB),
            topK
        );

        String formatted = results.stream()
            .map(r -> "- " + r.text())
            .collect(Collectors.joining("\n"));

        return NvcToolResult.success(formatted);
    }

    record RagSearchInput(String query, Integer topK) {}
}
```

### Day 2：剩余 5 个工具 + 工具选择策略

#### 3.7 核心工具实现（第二批 5 个）

| # | 工具名 | 依赖服务 | 实现复杂度 |
|---|--------|---------|-----------|
| 6 | `scenario_generate` | NvcScenarioService + AI | 🟡 中 — 需结构化输出 |
| 7 | `wiki_search` | NvcWikiService | 🔴 高 — Wiki 模块未创建 |
| 8 | `wiki_write` | NvcWikiService | 🔴 高 — Wiki 模块未创建 |
| 9 | `profile_update` | NvcProfileService | 🟢 低 — 调用已有 Service |
| 10 | `practice_start` | NvcPracticeSessionService | 🟢 低 — 调用已有 Service |

**注意**：`wiki_search` 和 `wiki_write` 依赖 Wiki 模块（Phase 2），此处先实现接口骨架，返回 "功能暂未开放"。

#### 3.8 工具选择策略

不同 Agent 场景使用不同的工具子集：

```java
// NvcToolRegistry 中增加预定义工具集
public static final Map<NvcAgentScene, List<String>> SCENE_TOOLS = Map.of(
    NvcAgentScene.NVC_EXPRESSION_EVALUATOR,
        List.of("evaluate_nvc", "rag_search"),
    NvcAgentScene.SCENARIO_GENERATOR,
        List.of("scenario_search", "scenario_generate", "profile_query"),
    NvcAgentScene.DIALOGUE_GUIDE,
        List.of("rag_search", "profile_query"),
    NvcAgentScene.EMPATHY_COACH,
        List.of("rag_search", "profile_query"),
    NvcAgentScene.NVC_KNOWLEDGE_ADVISOR,
        List.of("rag_search", "wiki_search")
);
```

#### 3.9 AgentDecision 扩展

**修改文件**：`AgentDecision.java` — 增加工具列表字段

```java
public record AgentDecision(
    NvcAgentScene scene,
    String reason,
    String reflection,
    Map<String, String> promptVariables,
    List<String> availableTools  // 新增：该 Agent 可用的工具列表
) {
    // 保留旧构造器兼容
    public AgentDecision(NvcAgentScene scene, String reason,
                         String reflection, Map<String, String> promptVariables) {
        this(scene, reason, reflection, promptVariables, null);
    }
}
```

#### 3.10 新建文件清单

```
app/src/main/java/nvc/guide/modules/nvcpractice/
├── tool/
│   ├── NvcTool.java                    # 工具接口
│   ├── NvcToolResult.java              # 工具执行结果
│   ├── NvcToolRegistry.java            # 工具注册中心
│   ├── ToolContext.java                # 工具执行上下文
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
├── dto/
│   └── RagResult.java                  # RAG 检索结果
└── service/
    └── NvcRagService.java              # NVC RAG 检索服务

app/src/main/java/nvc/guide/modules/knowledgebase/
└── model/
    └── KnowledgeBaseType.java          # 知识库类型枚举
```

### 验收标准

```
□ 框架层
  □ NvcTool 接口定义完成
  □ NvcToolRegistry 自动注册所有 @Component 工具
  □ toFunctionCallbacks() 正确转换为 Spring AI FunctionCallback
  □ NvcAgentChatService 能注入工具回调

□ 工具实现
  □ 10 个工具全部实现（wiki_search/wiki_write 为骨架）
  □ 每个工具有独立的 inputSchema
  □ 工具执行结果正确返回

□ Agent 集成
  □ Agent 对话中 LLM 能识别并调用工具
  □ 工具结果正确注入对话上下文
  □ 不同 Agent 场景使用不同工具子集
```

---

## 四、Phase 1.3：Skill 标准化（1 天）

> 目标：将评估、场景生成、对话引导封装为标准化 Skill
> 简历关键词：可扩展 Skill 体系、泛型接口、Skill+Tool 组合模式

### 4.1 NvcSkill 接口

**新建文件**：`nvcpractice/skill/NvcSkill.java`

```java
/**
 * NVC Skill 标准化接口
 * Skill = 业务逻辑封装（面向代码复用）
 * Tool = Agent 可调用的外部能力（面向 LLM Function Calling）
 * 关系：一个 Skill 可以被包装为 Tool，也可以直接在 Service 中调用
 *
 * @param <T> Skill 输出类型
 */
public interface NvcSkill<T> {

    /** Skill 名称 */
    String name();

    /** Skill 描述 */
    String description();

    /** 执行 Skill */
    T execute(SkillInput input, SkillContext context);
}
```

### 4.2 SkillInput / SkillContext

**新建文件**：`nvcpractice/skill/SkillInput.java`

```java
@Data
@Builder
public class SkillInput {
    private String userMessage;
    private String scenarioDescription;
    private Map<String, Object> extraData;
}
```

**新建文件**：`nvcpractice/skill/SkillContext.java`

```java
@Data
@Builder
public class SkillContext {
    private Long userId;
    private Long sessionId;
    private PracticeContext practiceContext;
    private NvcAgentConfigEntity agentConfig;
}
```

### 4.3 三个核心 Skill

#### NvcEvaluationSkill

**新建文件**：`nvcpractice/skill/NvcEvaluationSkill.java`

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class NvcEvaluationSkill implements NvcSkill<NvcEvaluationResult> {

    private final StructuredOutputInvoker structuredOutputInvoker;
    private final LlmProviderRegistry llmProviderRegistry;

    @Override
    public String name() { return "nvc_evaluation"; }

    @Override
    public String description() { return "评估用户的 NVC 表达质量"; }

    @Override
    public NvcEvaluationResult execute(SkillInput input, SkillContext context) {
        // 封装现有 NvcEvaluationService 的评估逻辑
        ChatClient client = llmProviderRegistry.getPlainChatClient(
            context.getAgentConfig().getModelProvider());

        String prompt = buildEvaluationPrompt(input);
        return structuredOutputInvoker.invokeStructuredOutput(
            prompt, client, NvcEvaluationResult.class);
    }
}
```

#### NvcScenarioGenerateSkill

**新建文件**：`nvcpractice/skill/NvcScenarioGenerateSkill.java`

```java
@Component
@RequiredArgsConstructor
public class NvcScenarioGenerateSkill implements NvcSkill<NvcScenarioEntity> {

    private final StructuredOutputInvoker structuredOutputInvoker;
    private final LlmProviderRegistry llmProviderRegistry;

    @Override
    public String name() { return "scenario_generate"; }

    @Override
    public String description() { return "生成 NVC 练习场景"; }

    @Override
    public NvcScenarioEntity execute(SkillInput input, SkillContext context) {
        ChatClient client = llmProviderRegistry.getPlainChatClient(
            context.getAgentConfig().getModelProvider());

        String prompt = buildScenarioPrompt(input);
        return structuredOutputInvoker.invokeStructuredOutput(
            prompt, client, NvcScenarioEntity.class);
    }
}
```

#### NvcDialogueGuideSkill

**新建文件**：`nvcpractice/skill/NvcDialogueGuideSkill.java`

```java
@Component
@RequiredArgsConstructor
public class NvcDialogueGuideSkill implements NvcSkill<String> {

    private final NvcAgentChatService agentChatService;

    @Override
    public String name() { return "dialogue_guide"; }

    @Override
    public String description() { return "NVC 对话引导"; }

    @Override
    public String execute(SkillInput input, SkillContext context) {
        NvcAgentConfigEntity config = context.getAgentConfig();
        return agentChatService.chat(
            config, context.getPracticeContext(),
            input.getUserMessage(), Map.of());
    }
}
```

### 4.4 Skill + Tool 组合

**EvaluateNvcTool 改造**（使用 NvcEvaluationSkill）：

```java
@Component
@RequiredArgsConstructor
public class EvaluateNvcTool implements NvcTool {

    private final NvcEvaluationSkill evaluationSkill;

    @Override
    public String name() { return "evaluate_nvc"; }

    @Override
    public NvcToolResult execute(JsonNode input, ToolContext context) {
        SkillInput skillInput = SkillInput.builder()
            .userMessage(input.get("expression").asText())
            .build();
        SkillContext skillContext = SkillContext.builder()
            .userId(context.getUserId())
            .sessionId(context.getSessionId())
            .practiceContext(context.getPracticeContext())
            .agentConfig(context.getPracticeContext().getSession()
                .getCurrentAgentConfig()) // 需扩展
            .build();

        NvcEvaluationResult result = evaluationSkill.execute(skillInput, skillContext);
        return NvcToolResult.success(formatResult(result));
    }
}
```

### 4.5 场景推荐服务

**新建文件**：`nvcpractice/service/NvcScenarioRecommendService.java`

```java
@Service
@RequiredArgsConstructor
public class NvcScenarioRecommendService {

    private final NvcProfileService profileService;
    private final NvcScenarioRepository scenarioRepository;

    public List<NvcScenarioEntity> recommend(Long userId, int limit) {
        // 1. 获取用户能力雷达
        AbilityRadarDTO radar = profileService.getAbilityRadar(userId);

        // 2. 找出最弱维度
        String weakest = findWeakestDimension(radar);

        // 3. 搜索该维度相关的场景
        List<NvcScenarioEntity> candidates = scenarioRepository
            .findByFocusElementsContaining(weakest);

        // 4. 排除最近练习过的场景（简单实现：取前 N 个）
        return candidates.stream().limit(limit).toList();
    }

    private String findWeakestDimension(AbilityRadarDTO radar) {
        Map<String, Integer> scores = Map.of(
            "OBSERVATION", radar.observationScore(),
            "FEELING", radar.feelingScore(),
            "NEED", radar.needScore(),
            "REQUEST", radar.requestScore()
        );
        return scores.entrySet().stream()
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("OBSERVATION");
    }
}
```

### 4.6 新建文件清单

```
app/src/main/java/nvc/guide/modules/nvcpractice/
├── skill/
│   ├── NvcSkill.java                    # Skill 接口
│   ├── SkillInput.java                  # Skill 输入
│   ├── SkillContext.java                # Skill 上下文
│   ├── NvcEvaluationSkill.java          # 评估 Skill
│   ├── NvcScenarioGenerateSkill.java    # 场景生成 Skill
│   └── NvcDialogueGuideSkill.java       # 对话引导 Skill
└── service/
    └── NvcScenarioRecommendService.java # 场景推荐服务
```

### 验收标准

```
□ Skill 框架
  □ NvcSkill<T> 接口定义完成
  □ SkillInput / SkillContext 定义完成
  □ 3 个 Skill 全部实现

□ Skill + Tool 组合
  □ EvaluateNvcTool 内部调用 NvcEvaluationSkill
  □ ScenarioGenerateTool 内部调用 NvcScenarioGenerateSkill
  □ Skill 可在 Service 中直接调用，也可通过 Tool 间接调用

□ 场景推荐
  □ 基于用户薄弱维度推荐场景
  □ 推荐结果与用户能力相关
```

---

## 五、完整文件清单汇总

### 后端新增文件

```
app/src/main/java/nvc/guide/modules/

# RAG 服务
nvcpractice/service/NvcRagService.java
nvcpractice/dto/RagResult.java
knowledgebase/model/KnowledgeBaseType.java

# 工具框架
nvcpractice/tool/NvcTool.java
nvcpractice/tool/NvcToolResult.java
nvcpractice/tool/NvcToolRegistry.java
nvcpractice/tool/ToolContext.java
nvcpractice/tool/RagSearchTool.java
nvcpractice/tool/ProfileQueryTool.java
nvcpractice/tool/ProfileUpdateTool.java
nvcpractice/tool/DashboardQueryTool.java
nvcpractice/tool/ScenarioSearchTool.java
nvcpractice/tool/ScenarioGenerateTool.java
nvcpractice/tool/EvaluateNvcTool.java
nvcpractice/tool/WikiSearchTool.java
nvcpractice/tool/WikiWriteTool.java
nvcpractice/tool/PracticeStartTool.java

# Skill 框架
nvcpractice/skill/NvcSkill.java
nvcpractice/skill/SkillInput.java
nvcpractice/skill/SkillContext.java
nvcpractice/skill/NvcEvaluationSkill.java
nvcpractice/skill/NvcScenarioGenerateSkill.java
nvcpractice/skill/NvcDialogueGuideSkill.java

# 场景推荐
nvcpractice/service/NvcScenarioRecommendService.java
```

### 后端修改文件

```
# Phase 0.5
nvcpractice/service/NvcAgentOrchestrator.java  — 填充 profileSummary + ragContext
nvcpractice/service/NvcPracticeSessionService.java  — completeAndEvaluate 方法
nvcpractice/controller/NvcPracticeController.java  — 简化 completeSession

# Phase 1.1
knowledgebase/model/KnowledgeBaseEntity.java  — 新增 type 字段
knowledgebase/repository/KnowledgeBaseRepository.java  — 按类型查询
knowledgebase/service/KnowledgeBaseListService.java  — 按类型查询

# Phase 1.2
nvcpractice/service/NvcAgentChatService.java  — 注入工具回调
nvcpractice/dto/AgentDecision.java  — 新增 availableTools 字段

# Phase 1.3
nvcpractice/tool/EvaluateNvcTool.java  — 改用 NvcEvaluationSkill
nvcpractice/tool/ScenarioGenerateTool.java  — 改用 NvcScenarioGenerateSkill
```

### 资源文件

```
# NVC 知识文档
resources/knowledge/NVC理论基础.md
resources/knowledge/NVC话术模板.md
resources/knowledge/情绪与需求词汇库.md
resources/knowledge/NVC案例库.md

# 种子数据
resources/db/migration/seed-knowledge-base.sql
```

---

## 六、技术决策记录

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 工具接口 | 自定义 NvcTool 接口 | 先实现，后续可适配 MCP 协议 |
| RAG 检索 | 复用 KnowledgeBaseVectorService | 避免重复实现向量检索 |
| 工具注册 | Spring List\<NvcTool\> 自动注入 | 零配置，加 @Component 即注册 |
| FunctionCallback 转换 | 手动构建 | Spring AI 2.0 的 ToolCallback.builder() 灵活度高 |
| Skill 封装 | Java 泛型接口 + @Component | 类型安全，Spring 自动发现 |
| 场景推荐 | 基于规则（非 ML） | 数据量不足，规则更可控 |
| Wiki 工具 | 先实现骨架 | 依赖 Wiki 模块（Phase 2），不阻塞框架 |
| 个性化权重 | 关键词匹配 × 1.5 | 简单有效，后续可升级为语义权重 |

---

## 七、风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| LLM 不调用工具 | 工具框架白做 | 在系统提示词中明确指示工具使用场景 |
| 工具调用延迟高 | 对话卡顿 | 设置超时（10s），失败后降级为普通对话 |
| FunctionCallback Schema 不兼容 | 工具注册失败 | 先用简单 JSON Schema，逐步完善 |
| RAG 检索结果噪音大 | Agent 回复质量下降 | 设置 minScore 阈值过滤低质量结果 |
| NVC 知识文档质量差 | RAG 效果不佳 | 手工编写 + AI 辅助润色 |

---

## 八、实施顺序建议

```
Day 1:  Phase 0.5 — 基建补全
        ├── profileSummary 填充
        ├── Controller 业务逻辑移 Service
        └── 验证 Agent 对话能看到用户档案

Day 2:  Phase 1.1 — RAG 集成（上）
        ├── KnowledgeBaseType 枚举 + Entity 修改
        ├── NvcRagService 实现
        └── KnowledgeBaseRepository 扩展

Day 3:  Phase 1.1 — RAG 集成（下）
        ├── 4 份 NVC 知识文档编写
        ├── 种子数据脚本
        ├── ragContext 填充到 PracticeContext
        └── 验证 Agent 对话能引用 RAG 知识

Day 4:  Phase 1.2 — 工具框架（上）
        ├── NvcTool / NvcToolResult / ToolContext 接口
        ├── NvcToolRegistry 实现
        ├── NvcAgentChatService 升级（注入工具）
        └── 5 个简单工具：rag_search, profile_query,
            dashboard_query, scenario_search, practice_start

Day 5:  Phase 1.2 — 工具框架（下）
        ├── 3 个工具：evaluate_nvc, scenario_generate, profile_update
        ├── 2 个骨架工具：wiki_search, wiki_write
        ├── AgentDecision 扩展（availableTools）
        └── 工具选择策略

Day 6:  Phase 1.3 — Skill 标准化
        ├── NvcSkill 接口 + SkillInput/SkillContext
        ├── 3 个 Skill 实现
        ├── Skill + Tool 组合改造
        └── NvcScenarioRecommendService

Day 7:  联调 + 测试
        ├── 端到端测试：Agent 对话 + 工具调用
        ├── RAG 检索质量验证
        └── 修复问题
```
