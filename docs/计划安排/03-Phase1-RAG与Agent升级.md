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

## 二、Agent 工具调用框架（Day 5-6）— 已完成

> **实现说明：** Phase 1.2 已完成，使用了自定义 NvcTool 接口 + NvcToolRegistry 的方案。
> **后续新工具推荐使用 Spring AI 原生 `@Tool` 注解**，两种方式可共存。

### 2.1 已实现架构（Phase 1.2）

```
NvcTool (自定义接口)
    ↓ @Component 自动注册
NvcToolRegistry (Map<name, NvcTool>)
    ↓ toFunctionCallbacks()
List<ToolCallback> (Spring AI 标准)
    ↓ ChatClient.prompt().toolCallbacks()
LLM Function Calling
```

**保留价值：**
- `NvcToolSceneMapping` — 场景-工具映射，Spring AI 不提供
- `NvcChatRequest` + `NvcToolCallConfig` — 解耦编排层

### 2.2 后续新工具推荐方式：Spring AI @Tool 注解

```java
@Component
@RequiredArgsConstructor
public class NvcTools {
    private final NvcRagService ragService;
    private final NvcProfileService profileService;

    @Tool(description = "搜索 NVC 知识库，检索相关理论、话术模板、情绪词汇等信息")
    public String ragSearch(@ToolParam("查询内容") String query, ToolContext ctx) {
        Long userId = (Long) ctx.getContext().get("nvc.userId");
        List<RagResult> results = ragService.retrieve(query, ...);
        return results.stream().map(RagResult::text).collect(Collectors.joining("\n"));
    }

    @Tool(description = "查询用户 NVC 档案信息")
    public String profileQuery(ToolContext ctx) {
        Long userId = (Long) ctx.getContext().get("nvc.userId");
        return profileService.getUserProfilePrompt(userId);
    }
}
```

**@Tool 注解优势：**
- 零样板代码，无需自定义接口
- JSON Schema 自动生成
- `ToolContext` 原生支持上下文传递
- `ToolCallbacks.from()` 自动扫描注册

### 2.3 核心工具（10 个）

| 工具名 | 功能 | 调用的 Service | 实现方式 |
|--------|------|---------------|---------|
| rag_search | RAG 知识检索 | NvcRagService | NvcTool |
| wiki_search | 个人 Wiki 检索 | NvcWikiService | NvcTool（骨架） |
| wiki_write | 个人 Wiki 写入 | NvcWikiService | NvcTool（骨架） |
| profile_query | 用户档案查询 | NvcProfileService | NvcTool |
| profile_update | 用户档案更新 | NvcProfileService | NvcTool |
| evaluate_nvc | NVC 表达评估 | NvcEvaluationService | NvcTool |
| scenario_search | 场景搜索 | NvcScenarioService | NvcTool |
| scenario_generate | 场景生成 | NvcScenarioService | NvcTool |
| dashboard_query | 仪表盘数据 | NvcDashboardService | NvcTool |
| practice_start | 启动练习 | NvcPracticeSessionService | NvcTool |

---

## 三、Skill 标准化（Day 7-8）— 设计修正

> **原方案：** 自定义 `NvcSkill<T>` 接口 + `SkillInput` + `SkillContext`
> **修正后：** 直接使用 `@Service` + Spring AI `@Tool` 注解，无需自定义 Skill 接口

### 3.1 设计变更说明

**原方案问题：**
- `NvcSkill<T>` 接口与 Spring AI 的 `@Tool` 注解功能重叠
- `SkillInput` / `SkillContext` 与 Spring AI 的 `ToolContext` 功能重叠
- 增加了不必要的抽象层

**修正方案：**
- **Skill = 普通 `@Service`**，封装业务逻辑，不需要框架接口
- **Tool = `@Tool` 注解方法**，暴露给 LLM 调用
- **组合关系：** `@Tool` 方法内部调用 `@Service` 方法

### 3.2 核心 Skill（3 个）— 修正后

```java
// Skill 就是普通的 @Service — 不需要 NvcSkill 接口
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcEvaluationService {
    // 已有实现，封装评估 Prompt + StructuredOutputInvoker
    public NvcEvaluationEntity evaluateRealtime(...) { ... }
    public NvcEvaluationEntity evaluateFinal(...) { ... }
}

@Service
@RequiredArgsConstructor
public class NvcScenarioService {
    // 已有实现，封装场景生成逻辑
    public NvcScenarioEntity generateScenario(...) { ... }
}
```

### 3.3 Skill + Tool 组合模式 — 修正后

```java
// Tool 直接调用 Service（Skill），无需中间接口
@Component
@RequiredArgsConstructor
public class NvcTools {
    private final NvcEvaluationService evaluationService;
    private final NvcScenarioService scenarioService;

    @Tool(description = "评估用户的 NVC 表达质量")
    public String evaluateNvc(@ToolParam("用户表达") String expression, ToolContext ctx) {
        Long sessionId = (Long) ctx.getContext().get("nvc.sessionId");
        Long userId = (Long) ctx.getContext().get("nvc.userId");
        var result = evaluationService.evaluateRealtime(sessionId, userId, expression, ...);
        return formatResult(result);
    }

    @Tool(description = "生成 NVC 练习场景")
    public String scenarioGenerate(@ToolParam("情境") String situation, ToolContext ctx) {
        var scenario = scenarioService.generateScenario(...);
        return scenario.toString();
    }
}
```

**好处：**
- Skill（Service）既可被 `@Tool` 方法调用，也可在其他 Service 中直接调用
- 无需自定义接口，减少样板代码
- 与 Spring AI 生态完全兼容

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

### 3.5 新建文件（修正后）

```
# 不再需要 NvcSkill 接口和 SkillInput/SkillContext
# Skill 就是现有的 @Service 类

modules/nvcpractice/service/
├── NvcRagService.java          # 已有
└── NvcScenarioRecommendService.java  # 新增
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
