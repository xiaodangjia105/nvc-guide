# Step 2 设计规格：Agent 配置体系与调度中心

> 日期：2026-07-09
> 状态：已批准
> 前置：Step 1 已完成（8 Entity + 11 枚举 + 8 Repository + 种子数据）

---

## 1. 目标

实现 NVC 练习系统的核心服务层：

- **NvcAgentConfigService** — Agent 配置读取 + Redis 缓存 + 热更新
- **NvcAgentOrchestrator** — Agent 调度中心（规则引擎 + PracticeContext 组装）
- **NvcAgentChatService** — Agent 对话执行（调用 ChatClient，per-call ChatOptions）
- **NvcAgentConfigController** — 配置管理 REST API

---

## 2. 文件清单

```
nvc/guide/modules/nvcpractice/
├── service/
│   ├── NvcAgentConfigService.java      ← 配置读取 + Redis 缓存 + 热更新
│   ├── NvcAgentOrchestrator.java       ← 调度中心（核心）+ PracticeContext 组装
│   └── NvcAgentChatService.java        ← 对话执行（调用 ChatClient + ChatOptions）
├── dto/
│   ├── AgentConfigDTO.java             ← record，配置响应
│   ├── AgentConfigUpdateRequest.java   ← record，更新请求（含校验注解）
│   ├── AgentDecision.java              ← record，调度决策结果
│   └── PracticeContext.java            ← @Data @Builder，练习上下文
└── controller/
    └── NvcAgentConfigController.java   ← 配置管理 REST API
```

---

## 3. 依赖关系

```
NvcAgentConfigController
  └── NvcAgentConfigService
        ├── NvcAgentConfigRepository
        └── RedisService

NvcAgentOrchestrator（Step 3 的 NvcPracticeSessionService 会调用它）
  ├── NvcAgentConfigService
  ├── NvcAgentChatService
  ├── NvcPracticeSessionRepository
  ├── NvcPracticeMessageRepository
  ├── NvcEvaluationRepository
  └── NvcScenarioRepository（可选，场景描述注入）

NvcAgentChatService
  └── LlmProviderRegistry
```

---

## 4. NvcAgentConfigService

### 核心方法

```java
// 获取配置（cache-aside）
NvcAgentConfigEntity getConfig(NvcAgentScene scene)
  → RedisService.getOrLoad("nvc:agent-config:" + scene.name(), 5min, () -> DB查询)
  → 未找到 → throw BusinessException(NVC_AGENT_CONFIG_NOT_FOUND)

// 获取所有已启用配置
List<NvcAgentConfigEntity> getAllEnabledConfigs()

// 获取所有配置（含禁用）
List<NvcAgentConfigEntity> getAllConfigs()

// 热更新
NvcAgentConfigEntity updateConfig(NvcAgentScene scene, AgentConfigUpdateRequest request)
  → DB 查询 → 非 null 字段覆盖 → save → delete Redis 缓存

// DTO 转换
AgentConfigDTO toDTO(NvcAgentConfigEntity entity)
```

### 关键适配点

| 规格文档原方案 | 实际适配 |
|--------------|---------|
| `redisService.get/set` 手动序列化 | `RedisService.getOrLoad(key, ttl, loader)` cache-aside 模式 |
| `ObjectMapper` 手动 JSON 序列化 | `getOrLoad` 内部处理 |
| 包名 `interview.guide.*` | `nvc.guide.*` |

---

## 5. NvcAgentOrchestrator

### 5.1 PracticeContext 组装

```java
PracticeContext buildPracticeContext(Long sessionId, Long userId)
```

组装逻辑：
1. `session` = sessionRepository.findById(sessionId)
2. `recentMessages` = messageRepository 查询，取最近 20 条，翻转为正序
3. `lastEvaluation` = evaluationRepository 查最新一条
4. `roundCount` = messageRepository.countBySessionId(sessionId) / 2
5. `scenarioDescription` = scenarioRepository.findById(session.scenarioId).map(描述)
6. `userProfileSummary` = null（Step 5 档案模块接入）
7. `ragContext` = null（后续知识库模块接入）

### 5.2 调度决策

```java
AgentDecision decideNextAgent(PracticeContext context)
```

#### 通用规则

```
if session.phase == CREATED → SCENARIO_GENERATOR（"练习刚开始，需要生成场景"）
```

#### 结构化四步模式（STRUCTURED_FOUR_STEP）

```
if 无评估结果 → stepToCoach(currentStep)
if stepScore >= 70:
  if 已完成全部步骤 → DIALOGUE_GUIDE（action=COMPLETED_ALL_STEPS）
  else → stepToCoach(nextStep)（action=STEP_ADVANCE）
if stepScore < 70 → stepToCoach(currentStep)（action=STEP_RETRY）
```

#### 自由对话/场景驱动模式

```
if 无评估结果 → DIALOGUE_GUIDE
if overallScore < 50 → DIALOGUE_GUIDE（action=LOW_SCORE_GUIDE）
if overallScore >= 60 && roundCount > 3 && roundCount%3==0 → DIFFICULT_PARTNER（action=DIFFICULT_UPGRADE）
if 有薄弱要素 && overallScore < 70 → stepToCoach(weakest)（action=WEAK_ELEMENT_FOCUS）
else → DIALOGUE_GUIDE
```

#### stepToCoach 映射

```
OBSERVE → STEP_OBSERVE_COACH
FEELING → STEP_FEELING_COACH
NEED    → STEP_NEED_COACH
REQUEST → STEP_REQUEST_COACH
```

### 5.3 执行与反思

```java
// 执行 Agent 对话
String executeAgent(NvcAgentScene scene, PracticeContext context, String userMessage)
  → configService.getConfig(scene)
  → 如果 Agent 禁用，fallback 到 DIALOGUE_GUIDE
  → agentChatService.chat(config, context, userMessage)

// 练习后反思
String reflect(PracticeContext context)
  → 用 NVC_EXPRESSION_EVALUATOR 的 config
  → agentChatService.chatPlain(config, reflectPrompt, "")
  → 返回 JSON：weak_elements, suggested_difficulty, suggested_scenario_type, strategy_note
```

---

## 6. NvcAgentChatService

### 核心方法

```java
// 对话（非流式）
String chat(NvcAgentConfigEntity config, PracticeContext context, String userMessage)

// 对话（流式）
Flux<String> chatStream(NvcAgentConfigEntity config, PracticeContext context, String userMessage)

// 结构化输出（无工具、无记忆）
String chatPlain(NvcAgentConfigEntity config, String systemPrompt, String userPrompt)
```

### 模型参数传递

使用 Spring AI 2.0 的 per-call ChatOptions：

```java
private ChatOptions buildChatOptions(NvcAgentConfigEntity config) {
    return ChatOptions.builder()
        .temperature(config.getTemperature())
        .maxTokens(config.getMaxTokens())
        .topP(config.getTopP())
        .build();
}
```

调用时：
```java
chatClient.prompt()
    .options(buildChatOptions(config))
    .messages(messages)
    .call()
    .content();
```

### ChatClient 选择

| 方法 | ChatClient 来源 | 用途 |
|------|----------------|------|
| `chat()` / `chatStream()` | `getChatClientOrDefault(config.modelProvider)` | 对话类 Agent（引导、教练、搭档） |
| `chatPlain()` | `getPlainChatClient(config.modelProvider)` | 结构化输出（评估、反思） |

### 消息组装（buildMessages）

```
1. SystemMessage:
   - config.systemPrompt
   - + "\n\n[用户档案]\n" + userProfileSummary（如有）
   - + "\n\n[练习场景]\n" + scenarioDescription（如有）
   - + "\n\n[参考资料]\n" + ragContext（暂为 null）
2. 对话历史：recentMessages → UserMessage / AssistantMessage
3. 当前用户消息：UserMessage
```

---

## 7. DTO 设计

### AgentConfigDTO

```java
record AgentConfigDTO(
    NvcAgentScene agentScene,
    String displayName,
    String description,
    String systemPrompt,
    String modelProvider,
    String modelName,
    Double temperature,
    Integer maxTokens,
    Double topP,
    Boolean isEnabled
)
```

### AgentConfigUpdateRequest

```java
record AgentConfigUpdateRequest(
    String systemPrompt,
    String modelProvider,
    String modelName,
    @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
    @Min(100) @Max(8000) Integer maxTokens,
    @DecimalMin("0.0") @DecimalMax("1.0") Double topP,
    Boolean isEnabled
)
```

### AgentDecision

```java
record AgentDecision(
    NvcAgentScene scene,       // 选择的 Agent 角色
    String reason,             // 决策原因（日志/调试用）
    String action              // 动作标记（STEP_ADVANCE / LOW_SCORE_GUIDE / DIFFICULT_UPGRADE 等）
)
```

### PracticeContext

```java
@Data @Builder
public class PracticeContext {
    NvcPracticeSessionEntity session;
    List<NvcPracticeMessageEntity> recentMessages;
    NvcEvaluationEntity lastEvaluation;
    int roundCount;
    String userProfileSummary;    // null（Step 5）
    String scenarioDescription;   // 场景驱动模式
    String ragContext;            // null（知识库模块）
    NvcScenarioEntity scenario;
}
```

---

## 8. NvcAgentConfigController

### API 端点

```
GET  /api/nvc/agents/configs          → Result<List<AgentConfigDTO>>
GET  /api/nvc/agents/configs/{scene}  → Result<AgentConfigDTO>
PUT  /api/nvc/agents/configs/{scene}  → Result<AgentConfigDTO>（热更新）
```

- `@Valid @RequestBody` 校验更新请求
- `@RateLimit` 限流（可选）
- 返回 `Result<T>` 统一响应格式

---

## 9. ErrorCode

Step 1 已定义 `NVC_AGENT_CONFIG_NOT_FOUND(3006)`，Step 2 直接使用，无需新增。

---

## 10. 设计决策记录

### Q1: Spec 代码 vs 实际代码库？

**决策**：以实际代码库为准。
- 包名 `nvc.guide.*`（非 `interview.guide.*`）
- 用 `RedisService.getOrLoad()`（非手动 get/set 序列化）
- 用 `ChatOptions` 传递模型参数（非固定在 Provider 级别）

### Q2: 谁组装 PracticeContext？

**决策**：NvcAgentOrchestrator 自行组装。
- 从 Session/Message/Evaluation/Scenario Repository 拉取数据
- 外部调用者只需传 sessionId 和 userId
- 避免引入额外的 Builder 类

### Q3: 模型参数如何传递？

**决策**：Per-call ChatOptions。
- 每次调用 LLM 时根据 Agent 配置构建 ChatOptions
- 通过 `.prompt().options(chatOptions)` 传入
- 热更新天然生效，与现有 LlmProviderRegistry 架构一致

### Q4: RAG 集成？

**决策**：Step 2 仅预留字段，暂不接入。
- `PracticeContext.ragContext` 保持 null
- 后续知识库模块接入时再实现

### Q5: Step 2 范围？

**决策**：严格按规格文档范围。
- 3 Service + 4 DTO + 1 Controller
- Session 管理、消息持久化、评估服务留到 Step 3

### Q6: Orchestrator 用规则引擎还是 LLM？

**决策**：规则引擎（代码实现的 if/switch）。
- 当前调度逻辑清晰、可枚举
- 更可控、更快、无额外 LLM 调用开销
- Reflect 阶段用 LLM（需要自然语言理解能力）
- 未来可在 decideNextAgent 中加入 LLM 调用做更复杂的 Plan

---

## 11. 验证清单

```
□ 编译通过 + 项目启动成功
□ GET /api/nvc/agents/configs 返回 11 个 Agent 配置
□ GET /api/nvc/agents/configs/SCENARIO_GENERATOR 返回单个配置
□ PUT 更新配置后，GET 返回新值（热更新验证）
□ Redis 缓存命中验证（第二次 GET 不查 DB）
□ Orchestrator.decideNextAgent() 各分支覆盖
□ ChatService.chat() 能调用 LLM 返回结果
□ Git 提交："Step 2: Add Agent config service and orchestrator"
```
