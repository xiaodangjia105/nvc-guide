# Phase 1：RAG + Agent 升级（7 天）

> 前置条件：Phase 0 完成
>
> 简历亮点：RAG 知识检索系统、Agent 工具调用框架、可扩展 Skill 体系

---

## 一、RAG 知识库集成（Day 3-4）

### 1.1 当前状态

KnowledgeBase 模块**已存在**，支持文档上传/分块/向量化/RAG 查询。但：
- 只有一个通用知识库，无分类
- 未与 NVC 练习对话集成
- 无个性化检索

### 1.2 多知识库管理

**KnowledgeBaseEntity 新增 type 字段：**

```java
public enum KnowledgeBaseType {
    NVC_THEORY,      // NVC 理论知识
    SPEECH_TEMPLATE, // 话术模板
    EMOTION_VOCAB,   // 情绪词汇
    USER_CASE,       // 用户案例
    PERSONAL_WIKI    // 个人 Wiki（按用户隔离）
}
```

**修改文件：**
- `knowledgebase/model/KnowledgeBaseEntity.java` — 添加 type 字段
- `knowledgebase/model/KnowledgeBaseType.java` — 新建枚举
- `knowledgebase/service/KnowledgeBaseListService.java` — 按类型查询
- `knowledgebase/controller/KnowledgeBaseController.java` — 按类型过滤

### 1.3 NvcRagService（新建）

```java
@Service
public class NvcRagService {

    /**
     * 统一 RAG 检索
     * @param query 检索查询
     * @param userId 用户ID（用于个性化和 Wiki 隔离）
     * @param knowledgeTypes 检索的知识库类型
     * @param topK 返回结果数量
     */
    List<RagResult> retrieve(String query, Long userId,
                             List<KnowledgeBaseType> knowledgeTypes,
                             int topK);

    /**
     * 个性化检索（基于用户薄弱要素加权）
     */
    List<RagResult> retrievePersonalized(String query, Long userId,
                                          NvcPracticeStep weakElement);
}
```

**检索策略：**
1. 向量检索：pgvector COSINE 相似度（已有基础设施）
2. 关键词检索：PostgreSQL full-text search
3. 混合排序：向量分数 × 0.7 + 关键词分数 × 0.3
4. 个性化加权：用户薄弱要素相关内容 × 1.5

**RAG 在本项目的价值：**
- LLM 懂 NVC 理论，但不懂 **100+ 条话术模板** 和 **用户的个人知识**
- RAG 的价值在于**个性化**和**专业化**，而非补充通用知识

### 1.4 Agent 对话集成 RAG

```java
// NvcAgentChatService.buildMessages() 中增加
if (context.getScenarioDescription() != null) {
    List<RagResult> ragResults = ragService.retrieve(
        context.getScenarioDescription(),
        context.getSession().getUserId(),
        List.of(KnowledgeBaseType.NVC_THEORY, KnowledgeBaseType.SPEECH_TEMPLATE),
        3
    );
    systemPrompt += "\n\n[参考资料]\n" + formatRagResults(ragResults);
}
```

**同时修复：** `buildPracticeContext` 中填充 `ragContext` 字段。

### 1.5 NVC 知识文档准备

在 `resources/knowledge/` 下准备 4 个知识库文档：
- `NVC理论基础.md` — 四要素详解、NVC 与传统沟通区别
- `NVC话术模板.md` — 职场/家庭/亲密关系/社交场景话术
- `情绪与需求词汇库.md` — 感受词汇表、需求词汇表
- `NVC案例库.md` — 成功/失败案例对比

### 1.6 前端

- KnowledgeBaseManagePage 增加类型筛选

---

## 二、Agent 工具调用框架（Day 5-6）

### 2.1 NvcTool 接口

```java
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

### 2.2 NvcToolRegistry

```java
@Service
public class NvcToolRegistry {
    private final Map<String, NvcTool> tools;

    // Spring 自动注入所有 NvcTool 实现
    public NvcToolRegistry(List<NvcTool> toolList) {
        this.tools = toolList.stream()
            .collect(Collectors.toMap(NvcTool::name, t -> t));
    }

    public NvcTool getTool(String name) { return tools.get(name); }
    public List<NvcTool> getAllTools() { return List.copyOf(tools.values()); }

    /** 转换为 Spring AI Function Callback */
    public List<FunctionCallback> toFunctionCallbacks() {
        return tools.values().stream()
            .map(this::toFunctionCallback)
            .toList();
    }
}
```

### 2.3 核心工具（10 个）

| 工具名 | 功能 | 调用的 Service |
|--------|------|---------------|
| rag_search | RAG 知识检索 | NvcRagService |
| wiki_search | 个人 Wiki 检索 | NvcWikiService |
| wiki_write | 个人 Wiki 写入 | NvcWikiService |
| profile_query | 用户档案查询 | NvcProfileService |
| profile_update | 用户档案更新 | NvcProfileService |
| evaluate_nvc | NVC 表达评估 | NvcEvaluationSkill |
| scenario_search | 场景搜索 | NvcScenarioService |
| scenario_generate | 场景生成 | NvcScenarioGenerateSkill |
| dashboard_query | 仪表盘数据 | NvcDashboardService |
| practice_start | 启动练习 | NvcPracticeSessionService |

### 2.4 Spring AI Function Calling 集成

```java
// NvcAgentChatService 升级
public String chat(NvcAgentConfigEntity config, PracticeContext context,
                   String userMessage, Map<String, String> promptVariables) {
    ChatClient client = getChatClient(config);
    List<Message> messages = buildMessages(config, context, userMessage, promptVariables);

    return client.prompt()
        .options(buildChatOptions(config))
        .functions(toolRegistry.toFunctionCallbacks()) // 注入工具
        .messages(messages)
        .call()
        .content();
}
```

### 2.5 新建文件

```
modules/nvcpractice/tool/
├── NvcTool.java
├── NvcToolRegistry.java
├── NvcToolResult.java
├── ToolContext.java
├── RagSearchTool.java
├── WikiSearchTool.java
├── WikiWriteTool.java
├── ProfileQueryTool.java
├── ProfileUpdateTool.java
├── EvaluateNvcTool.java
├── ScenarioSearchTool.java
├── ScenarioGenerateTool.java
├── DashboardQueryTool.java
└── PracticeStartTool.java
```

---

## 三、Skill 标准化（Day 7-8）

### 3.1 NvcSkill 接口

```java
public interface NvcSkill<T> {
    /** Skill 名称 */
    String name();

    /** Skill 描述 */
    String description();

    /** 执行 Skill */
    T execute(SkillInput input, SkillContext context);
}
```

### 3.2 核心 Skill（3 个）

```java
@Component
public class NvcEvaluationSkill implements NvcSkill<NvcEvaluationResult> {
    // 封装评估 Prompt + StructuredOutputInvoker + 后处理
}

@Component
public class NvcScenarioGenerateSkill implements NvcSkill<NvcScenarioEntity> {
    // 封装场景生成 Prompt + JSON 解析
}

@Component
public class NvcDialogueGuideSkill implements NvcSkill<String> {
    // 封装对话引导 Prompt + 上下文注入
}
```

### 3.3 Skill + Tool 组合模式

```java
// EvaluateNvcTool 内部调用 NvcEvaluationSkill
@Component
public class EvaluateNvcTool implements NvcTool {
    private final NvcEvaluationSkill evaluationSkill;

    @Override
    public NvcToolResult execute(JsonNode input, ToolContext context) {
        NvcEvaluationResult result = evaluationSkill.execute(...);
        return NvcToolResult.success(result);
    }
}
```

**好处：** Skill 既可被主 Agent 通过 Tool 调用，也可在 Service 中直接调用。

### 3.4 场景推荐服务

```java
@Service
public class NvcScenarioRecommendService {

    public List<NvcScenarioEntity> recommend(Long userId, int limit) {
        // 1. 获取用户能力雷达
        AbilityRadarDTO radar = profileService.getAbilityRadar(userId);

        // 2. 找出最弱维度
        String weakest = findWeakestDimension(radar);

        // 3. 搜索该维度相关的场景
        List<NvcScenarioEntity> candidates = scenarioRepository
            .findByFocusElementsContaining(weakest);

        // 4. 排除最近练习过的场景
        // 5. 返回 Top-K
    }
}
```

### 3.5 新建文件

```
modules/nvcpractice/skill/
├── NvcSkill.java
├── SkillInput.java
├── SkillContext.java
├── NvcEvaluationSkill.java
├── NvcScenarioGenerateSkill.java
└── NvcDialogueGuideSkill.java

modules/nvcpractice/service/
├── NvcRagService.java
└── NvcScenarioRecommendService.java
```

---

## 四、验收标准

```
□ RAG 集成
  □ Agent 回复能引用 RAG 知识
  □ 多知识库管理正常（按类型查询）
  □ 个性化权重生效（薄弱要素优先）

□ Agent 工具调用
  □ 10 个工具全部实现
  □ Agent 能通过 Function Calling 调用工具
  □ 工具调用结果正确注入上下文

□ Skill 标准化
  □ 3 个 Skill 全部实现
  □ Skill 能被 Tool 包装调用
  □ 场景推荐基于用户档案

□ 场景推荐
  □ 推荐场景与用户薄弱环节相关
  □ 不重复推荐最近练习过的场景
```

---

## 五、技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 工具接口 | 自定义 NvcTool 接口 | 先实现，后续适配 MCP |
| RAG 检索 | 混合检索（向量 + 关键词） | 提高召回率 |
| 个性化权重 | 基于用户档案动态调整 | 实现简单，效果明显 |
| 知识库隔离 | 逻辑隔离（type 字段） | 比物理隔离更灵活 |
| Skill 封装 | Java 接口 + Prompt 模板 | 与现有架构一致 |
| 推荐算法 | 基于规则（非 ML） | 数据量不足，规则更可控 |
