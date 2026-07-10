# Step 2：Agent 配置体系与调度中心 — 完成记录

> 完成日期：2026-07-09
>
> 操作人：Claude Code + 用户协作 + Subagent-Driven Development（6 个 Task 串行执行）
>
> 耗时：约 45 分钟

---

## 一、做了什么

### 1.1 设计分析阶段（Brainstorming）

在实现前，先对 Step2 文档进行了全面分析，发现并确认了以下关键差异：

| 差异项 | 规格文档 | 实际代码库 | 决策 |
|--------|---------|-----------|------|
| 包名 | `interview.guide.modules.*` | `nvc.guide.modules.*` | 以实际代码库为准 |
| Redis 缓存 | `redisService.get/set` 手动序列化 | `RedisService.getOrLoad()` cache-aside | 用 getOrLoad |
| ObjectMapper | 手动 JSON 序列化 | getOrLoad 内部处理 | 不需要 |
| 模型参数传递 | 未明确 | 两种方案可选 | Per-call ChatOptions |
| RAG 集成 | 需要 | 无现成服务 | 预留字段，暂不接入 |
| NvcScenarioEntity 包路径 | `nvcpractice.model.*` | `nvcscenario.model` | 修正 |

### 1.2 用户确认的设计决策

| 决策项 | 结论 | 原因 |
|--------|------|------|
| Spec vs Code 不一致 | 以实际代码库为准 | 包名、Redis 用法、ChatClient 调用方式适配实际架构 |
| PracticeContext 谁来组装 | Orchestrator 自行组装 | 外部调用者只需传 sessionId 和 userId，避免引入额外 Builder |
| 模型参数传递 | Per-call ChatOptions | 与现有 LlmProviderRegistry 架构一致，热更新天然生效 |
| RAG 集成 | 预留字段，暂不接入 | Step 2 专注 Agent 配置和调度逻辑 |
| Step 2 范围 | 严格按规格文档 | 3 Service + 4 DTO + 1 Controller，Session 管理留到 Step 3 |
| Orchestrator 实现方式 | 规则引擎（if/switch） | 逻辑清晰可枚举，更快更可控，Reflect 阶段用 LLM |

### 1.3 Subagent-Driven 执行

采用 Subagent-Driven Development 模式，每个 Task 派发独立 implementer + reviewer：

| Task | 内容 | Agent 模型 | 测试数 | Review 结果 |
|------|------|-----------|--------|------------|
| Task 1 | DTO 类（4 个文件） | haiku | 0（纯数据类） | ✅ 一次通过 |
| Task 2 | NvcAgentConfigService | sonnet | 6 | ⚠️ 2 个 Important（通配符导入 + @Transactional），修复后通过 |
| Task 3 | NvcAgentChatService | sonnet | 3 | ⚠️ 1 个 Important（通配符导入），修复后通过 |
| Task 4 | NvcAgentOrchestrator | sonnet | 16 | ⚠️ 3 个 Important（NPE guard + 弱要素测试 + reflect 测试），修复后通过 |
| Task 5 | NvcAgentConfigController | sonnet | 3 | ✅ 一次通过 |
| Task 6 | 编译 + 启动验证 | 直接执行 | 27（全量） | ✅ 通过 |

---

## 二、创建的文件清单

### 2.1 DTO 类（4 个）

| 文件 | 类型 | 说明 |
|------|------|------|
| `dto/AgentConfigDTO.java` | record | Agent 配置响应 DTO，10 个字段 |
| `dto/AgentConfigUpdateRequest.java` | record | 配置更新请求，7 个字段含校验注解 |
| `dto/AgentDecision.java` | record | 调度决策结果：scene + reason + action |
| `dto/PracticeContext.java` | @Data @Builder | 练习上下文，8 个字段（含预留的 userProfileSummary、ragContext） |

### 2.2 Service 类（3 个）

| 文件 | 行数 | 核心方法 | 说明 |
|------|------|---------|------|
| `service/NvcAgentConfigService.java` | 118 | `getConfig()`, `updateConfig()`, `toDTO()` | 配置读取 + Redis cache-aside + 热更新 |
| `service/NvcAgentChatService.java` | 120 | `chat()`, `chatStream()`, `chatPlain()` | LLM 对话执行，per-call ChatOptions |
| `service/NvcAgentOrchestrator.java` | 270 | `buildPracticeContext()`, `decideNextAgent()`, `executeAgent()`, `reflect()` | 调度中心，规则引擎 |

### 2.3 Controller 类（1 个）

| 文件 | 行数 | API 端点 |
|------|------|---------|
| `controller/NvcAgentConfigController.java` | 57 | `GET /api/nvc/agents/configs`, `GET /api/nvc/agents/configs/{scene}`, `PUT /api/nvc/agents/configs/{scene}` |

### 2.4 测试文件（4 个）

| 文件 | 测试数 | 覆盖范围 |
|------|--------|---------|
| `NvcAgentConfigServiceTest.java` | 6 | 缓存命中、缓存未命中、不存在异常、更新+清缓存、更新不存在、DTO 转换 |
| `NvcAgentChatServiceTest.java` | 3 | ChatClient 选择逻辑（getChatClientOrDefault vs getPlainChatClient）、chatStream |
| `NvcAgentOrchestratorTest.java` | 16 | buildContext（3）、decideNextAgent 结构化四步（5）、自由对话（4）、CREATED（1）、executeAgent（2）、reflect（1） |
| `NvcAgentConfigControllerTest.java` | 3 | getAllConfigs、getConfig、updateConfig |

---

## 三、核心设计详解

### 3.1 NvcAgentConfigService — 配置缓存与热更新

**缓存策略：** Redis cache-aside 模式

```
getConfig(scene):
  1. RedisService.getOrLoad("nvc:agent-config:" + scene.name(), 5min, () -> DB查询)
  2. 缓存命中 → 直接返回
  3. 缓存未命中 → 查 DB → 写入 Redis → 返回
  4. DB 也不存在 → throw BusinessException(NVC_AGENT_CONFIG_NOT_FOUND)

updateConfig(scene, request):
  1. DB 查询 → 2. 非 null 字段覆盖 → 3. save → 4. delete Redis 缓存
  5. 下次 getConfig() 自动从 DB 重新加载（热更新生效）
```

**关键适配：** 使用 `RedisService.getOrLoad()` 而非手动 `get/set` 序列化，一行搞定 cache-aside。

### 3.2 NvcAgentOrchestrator — 调度中心

**调度决策逻辑（规则引擎）：**

```
decideNextAgent(context):
  ┌─ session.phase == CREATED → SCENARIO_GENERATOR
  │
  ├─ mode == STRUCTURED_FOUR_STEP:
  │   ├─ 无评估 → stepToCoach(currentStep)
  │   ├─ stepScore >= 70:
  │   │   ├─ 全部完成 → DIALOGUE_GUIDE (COMPLETED_ALL_STEPS)
  │   │   └─ 未完成 → stepToCoach(nextStep) (STEP_ADVANCE)
  │   └─ stepScore < 70 → stepToCoach(currentStep) (STEP_RETRY)
  │
  └─ mode in [SCENARIO, FREE_DIALOG]:
      ├─ 无评估 → DIALOGUE_GUIDE
      ├─ overallScore < 50 → DIALOGUE_GUIDE (LOW_SCORE_GUIDE)
      ├─ score >= 60 && rounds > 3 && rounds%3==0 → DIFFICULT_PARTNER (DIFFICULT_UPGRADE)
      ├─ 有薄弱要素 && score < 70 → stepToCoach(weakest) (WEAK_ELEMENT_FOCUS)
      └─ else → DIALOGUE_GUIDE
```

**PracticeContext 组装：**

```
buildPracticeContext(sessionId, userId):
  1. session = sessionRepository.findById(sessionId)
  2. recentMessages = messageRepository 查询最近 20 条
  3. lastEvaluation = evaluationRepository 查最新一条
  4. roundCount = 用户消息计数
  5. scenarioDescription = scenarioRepository 查询场景描述
  6. userProfileSummary = null（Step 5 接入）
  7. ragContext = null（知识库模块接入）
```

### 3.3 NvcAgentChatService — 对话执行

**模型参数传递：** Per-call ChatOptions

```java
// 每次调用 LLM 时根据 Agent 配置构建 ChatOptions
OpenAiChatOptions.builder()
    .temperature(config.getTemperature())
    .maxTokens(config.getMaxTokens())
    .topP(config.getTopP())
    .build();

// 通过 .prompt().options(chatOptions) 传入
chatClient.prompt()
    .options(buildChatOptions(config))
    .messages(messages)
    .call()
    .content();
```

**三种调用模式：**

| 方法 | ChatClient 来源 | 用途 |
|------|----------------|------|
| `chat()` | `getChatClientOrDefault(config.modelProvider)` | 对话类 Agent |
| `chatStream()` | 同上 | 流式输出 |
| `chatPlain()` | `getPlainChatClient(config.modelProvider)` | 结构化输出（评估、反思） |

**消息组装：**

```
SystemMessage = config.systemPrompt
  + [用户档案摘要]（如有，暂为空）
  + [练习场景描述]（如有）
  + [RAG 知识]（暂为空）
+ 对话历史（UserMessage / AssistantMessage）
+ 当前用户消息（UserMessage）
```

### 3.4 NvcAgentConfigController — REST API

```
GET  /api/nvc/agents/configs          → Result<List<AgentConfigDTO>>
GET  /api/nvc/agents/configs/{scene}  → Result<AgentConfigDTO>
PUT  /api/nvc/agents/configs/{scene}  → Result<AgentConfigDTO>（@Valid @RequestBody 热更新）
```

---

## 四、目录结构

```
app/src/main/java/nvc/guide/modules/nvcpractice/
├── dto/
│   ├── AgentConfigDTO.java              # record，配置响应
│   ├── AgentConfigUpdateRequest.java    # record，更新请求（含校验注解）
│   ├── AgentDecision.java               # record，调度决策结果
│   └── PracticeContext.java             # @Data @Builder，练习上下文
│
├── service/
│   ├── NvcAgentConfigService.java       # 配置读取 + Redis 缓存 + 热更新
│   ├── NvcAgentChatService.java         # LLM 对话执行 + per-call ChatOptions
│   └── NvcAgentOrchestrator.java        # 调度中心（规则引擎 + Context 组装）
│
├── controller/
│   └── NvcAgentConfigController.java    # 配置管理 REST API
│
├── model/                               # Step 1 已创建
└── repository/                          # Step 1 已创建

app/src/test/java/nvc/guide/modules/nvcpractice/
├── controller/
│   └── NvcAgentConfigControllerTest.java    # 3 个测试
└── service/
    ├── NvcAgentConfigServiceTest.java       # 6 个测试
    ├── NvcAgentChatServiceTest.java         # 3 个测试
    └── NvcAgentOrchestratorTest.java        # 16 个测试
```

---

## 五、与原 Step2 文档的差异

| 项目 | 原文档 | 实际实现 | 原因 |
|------|--------|---------|------|
| 包名 | `interview.guide.modules.*` | `nvc.guide.modules.*` | Step 0 已重命名 |
| Redis 缓存 | `redisService.get/set` 手动序列化 | `RedisService.getOrLoad()` | 实际代码库已有 cache-aside API |
| ObjectMapper | 手动 JSON 序列化 | 不需要 | getOrLoad 内部处理 |
| NvcScenarioEntity 导入 | `nvcpractice.model.*` | `nvcscenario.model.NvcScenarioEntity` | Entity 实际在 nvcscenario 包 |
| 模型参数传递 | 未明确 | Per-call `OpenAiChatOptions` | 设计决策：与 LlmProviderRegistry 架构一致 |
| @Transactional | 未提及 | `updateConfig()` 添加 | 项目规范：写操作需事务保护 |
| reflect() NPE 防护 | 无 | 添加 null guard | 防止无评估数据时崩溃 |
| 测试方式 | `@WebMvcTest` + `MockMvc` | `@ExtendWith(MockitoExtension.class)` | Spring Boot 4.0.1 模块变更 |

---

## 六、Review 过程中修复的问题

### Task 2 修复

| 问题 | 严重性 | 修复方式 |
|------|--------|---------|
| 测试文件通配符导入 | 🟡 Important | 替换为具体导入：`assertEquals`, `assertTrue`, `assertThrows`, `any`, `anyString`, `never`, `verify`, `when` |
| `updateConfig()` 缺少 @Transactional | 🟡 Important | 添加 `@Transactional` 注解 |

### Task 3 修复

| 问题 | 严重性 | 修复方式 |
|------|--------|---------|
| 测试文件通配符导入 | 🟡 Important | 替换为 15 个具体导入 |

### Task 4 修复

| 问题 | 严重性 | 修复方式 |
|------|--------|---------|
| `reflect()` NPE 风险 | 🟡 Important | 添加 null guard，无评估时返回默认 JSON |
| 弱要素聚焦路径无测试 | 🟡 Important | 添加 `lowScore_withWeakElement_focusesWeakElement` 测试 |
| reflect 测试过于浅层 | 🟡 Important | 改为验证 evaluator config 获取和 chatPlain 调用参数 |

### Task 5 修复

无修复。

---

## 七、验证结果

| 验证项 | 结果 | 说明 |
|--------|------|------|
| 编译 | ✅ BUILD SUCCESSFUL | `.\gradlew.bat :app:compileJava` 通过 |
| 全量测试 | ✅ 27/27 通过 | 4 个测试文件，覆盖所有核心逻辑 |
| 项目启动 | ✅ 正常启动 | 无异常日志 |
| 包名一致性 | ✅ `nvc.guide.modules.*` | 全部文件统一 |
| 通配符导入 | ✅ 无 | 所有文件使用具体导入 |
| 100 字符列限制 | ✅ 符合 | 测试文件也遵循 |

### 测试覆盖详情

**NvcAgentConfigServiceTest（6 个）：**
- `cacheHit_returnsCached` — 缓存命中时直接返回
- `cacheMiss_loadsFromDb` — 缓存未命中时从 DB 加载
- `notFound_throwsException` — DB 不存在时抛异常
- `updatesFieldsAndClearsCache` — 更新字段并清除缓存
- `updateConfig.notFound_throwsException` — 更新不存在配置时抛异常
- `convertsEntityToDTO` — Entity 到 DTO 转换

**NvcAgentChatServiceTest（3 个）：**
- `chat_usesCorrectChatClient` — chat 使用 getChatClientOrDefault
- `chatPlain_usesPlainChatClient` — chatPlain 使用 getPlainChatClient
- `chatStream_doesNotThrow` — chatStream 不抛异常

**NvcAgentOrchestratorTest（16 个）：**
- `buildContext_*`（3 个）— 基本组装、场景注入、空消息处理
- `decideNextAgent.created_returnsScenarioGenerator` — CREATED 阶段
- `decideStructuredStep.*`（5 个）— 无评估、评分推进、评分保持、REQUEST 完成、步骤映射
- `decideFreeDialog.*`（4 个）— 无评估、低分引导、困难搭档、弱要素聚焦
- `executeAgent_*`（2 个）— 正常执行、禁用 fallback
- `reflect_*`（1 个）— 使用评估官配置

**NvcAgentConfigControllerTest（3 个）：**
- `getAllConfigs` — 列表查询
- `getConfig` — 单个查询
- `updateConfig` — 更新配置

---

## 八、Git 提交记录

```
8bd5941 feat(step2): add NvcAgentConfigController with REST API
d65c05e fix(step2): guard reflect NPE, add weak-element and reflect tests
067d23c fix(step2): enforce 100-char column limit in test
d6dd1b3 fix(step2): replace wildcard imports with explicit imports
0aae0be feat(step2): add NvcAgentOrchestrator with rule-based scheduling
d746a98 fix(step2): expand wildcard imports in ChatServiceTest
da1c0a5 feat(step2): add NvcAgentChatService with per-call ChatOptions
5293ba4 fix(step2): expand wildcard imports, add @Transactional to updateConfig
5c7f696 feat(step2): add NvcAgentConfigService with Redis cache-aside
df65704 feat(step2): add DTO classes for Agent config and orchestration
```

---

## 九、设计文档

- 设计规格：`docs/superpowers/specs/2026-07-09-step2-agent-config-orchestrator-design.md`
- 实现计划：`docs/superpowers/plans/2026-07-09-step2-agent-config-orchestrator.md`
- 进度记录：`docs/进度记录/Step2-Agent配置体系与调度中心-完成记录.md`
- 原始文档：`docs/steps/Step2-Agent配置体系与调度中心.md`

---

## 十、后续步骤

按照实施计划，下一步是 **Step 3：NVC 文字练习核心流程**，需要：

1. 创建 `NvcPracticeSessionService`（会话生命周期管理）
2. 创建 `NvcPracticeDialogueService`（对话流程编排）
3. 创建 `NvcEvaluationService`（实时评估 + 最终评估）
4. 创建 Redis Stream 异步评估监听器
5. 创建练习相关的 Controller 和 DTO

以及后续 Step 的依赖：
- Step 4：NVC 评估引擎（Prompt 模板 + 结构化输出）
- Step 5：用户档案系统（能力画像 + 向量化个性化）
- Step 6：场景库管理
- Step 7：RAG 知识库
- Step 8：NVC 语音练习
