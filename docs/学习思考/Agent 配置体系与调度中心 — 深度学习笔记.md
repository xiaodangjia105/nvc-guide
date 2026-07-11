# Step 2：Agent 配置体系与调度中心 — 深度学习笔记

> 本文档是对 `docs/steps/Step2-Agent配置体系与调度中心.md` 的深度解读，从业务场景出发，逐层深入到架构设计、技术选型、代码实现与使用方式。

---

## 目录

1. [业务场景：为什么需要这个东西？](#一业务场景为什么需要这个东西)
2. [架构设计：怎么拆分职责？](#二架构设计怎么拆分职责)
3. [核心设计：Plan-Execute-Reflect 思维链](#三核心设计plan-execute-reflect-思维链)
4. [技术选型解析](#四技术选型解析)
5. [代码实现详解](#五代码实现详解)
6. [怎么用这个东西？](#六怎么用这个东西)
7. [总结：Step 2 的核心价值](#七总结step-2-的核心价值)

---

## 一、业务场景：为什么需要这个东西？

### 1.1 NVC 是什么

NVC（Nonviolent Communication，非暴力沟通）是一套由 Marshall Rosenberg 提出的沟通方法论，核心是**四个要素**：

| 要素 | 关键问题 | 正例 | 反例 |
|------|---------|------|------|
| **观察** | 发生了什么事实？ | "你这周有三天10点后回家" | "你总是不着家"（评论） |
| **感受** | 你内心什么情绪？ | "我感到担心" | "我觉得你不关心我"（想法伪装成感受） |
| **需求** | 什么深层需求没被满足？ | "我需要安全感" | "你每天7点陪我"（策略不是需求） |
| **请求** | 你希望对方具体做什么？ | "你愿意每周六陪我散步一小时吗？" | "你不要再加班了"（命令） |

NVC 的难点不在于"知道"四要素，而在于**练**——在真实冲突情境中能下意识地用观察→感受→需求→请求的方式表达，而不是滑回评判→指责→命令的老习惯。

### 1.2 痛点：光看书学不会

一个人想学 NVC，看一百遍书，到了真实吵架场景仍然会用"你总是""你怎么这么自私"来表达。缺少的是**安全的练习环境**——一个能在压力下反复训练、且给出即时反馈的对话场景。

### 1.3 由此产生的需求

这个项目要做的就是一个 **AI 驱动的 NVC 练习助手**，核心需求有三个：

1. **多角色对话模拟**：不能只有一个 AI 角色。你需要一个能模拟"场景中对方"的 AI（对话引导官），也需要一个故意刁难你的 AI（困难搭档），还需要一个会评估你表达质量的 AI（评估官）。
2. **动态调度**：练习过程中，AI 不能一成不变。如果用户表现差，应该切换到更温和的引导角色；如果表现好，应该提升难度。这需要一个"大脑"来做调度决策。
3. **可调参热更新**：运营人员（或开发者自己）需要在不重启服务的情况下调整 Agent 的提示词、模型温度等参数，快速迭代效果。

**Step 2 就是解决这三个需求的核心一步**。

---

## 二、架构设计：怎么拆分职责？

Step 2 产出了三个核心类，它们各司其职，形成一个 **决策 → 执行 → 配置管理** 的三角结构：

```
                    ┌─────────────────────────┐
                    │  NvcAgentConfigService   │  ← 配置管理层
                    │  (DB + Redis 缓存)        │     "每个 Agent 的提示词是什么？"
                    └────────────┬────────────┘
                                 │  读取配置
                                 ▼
┌──────────────────────────────────────────────────────┐
│               NvcAgentOrchestrator                    │  ← 调度决策层
│                                                       │     "下一步该用哪个 Agent？"
│   PLAN (decideNextAgent)                              │
│     ↓                                                 │
│   EXECUTE (executeAgent) → NvcAgentChatService.chat() │
│     ↓                                                 │
│   REFLECT (reflect)                                   │
└──────────────────────────┬───────────────────────────┘
                           │  调用执行
                           ▼
                    ┌─────────────────────┐
                    │ NvcAgentChatService  │  ← 执行层
                    │ (组装消息→调用 LLM)   │     "拿到配置后，实际跟 LLM 对话"
                    └─────────────────────┘
```

### 2.1 为什么拆成三层而不是一个类？

这是经典的**关注点分离（Separation of Concerns）**：

- **Orchestrator 只管决策**：它看当前练习状态（第几轮、评分多少、哪个步骤），决定"下一步该用哪个 Agent 角色"。它不关心怎么调 LLM。
- **ChatService 只管执行**：它拿到 Agent 配置 + 对话历史 + 用户消息，组装成 Spring AI 的 `List<Message>`，调用 `ChatClient`，返回 AI 回复。它不关心该不该切 Agent。
- **ConfigService 只管配置**：它负责 Agent 配置的读取、缓存、更新。它不关心谁来用配置。

好处是：**Orchestrator 可以单独写单元测试**（不需要 mock LLM 调用），只需要验证它的决策逻辑是否正确。

### 2.2 文件清单

Step 2 创建的文件如下：

```
app/src/main/java/interview/guide/modules/nvcpractice/
├── service/
│   ├── NvcAgentConfigService.java         ← Agent 配置读取 + Redis 缓存 + 热更新
│   ├── NvcAgentOrchestrator.java          ← Agent 调度中心（核心类）
│   └── NvcAgentChatService.java           ← Agent 对话执行（调用 ChatClient）
├── dto/
│   ├── AgentConfigDTO.java                ← Agent 配置响应 DTO
│   ├── AgentConfigUpdateRequest.java      ← Agent 配置更新请求
│   ├── AgentDecision.java                 ← Agent 调度决策结果
│   └── PracticeContext.java               ← 练习上下文（传递给 Agent 的信息）
└── controller/
    └── NvcAgentConfigController.java      ← Agent 配置管理 API
```

---

## 三、核心设计：Plan-Execute-Reflect 思维链

这是整个系统的"灵魂"设计，来源于 AI Agent 领域的经典范式。

### 3.1 什么是 Plan-Execute-Reflect？

| 阶段 | 含义 | 在本项目中 |
|------|------|-----------|
| **PLAN** | 分析当前状态，规划下一步做什么 | `decideNextAgent()` —— 根据练习模式、评分、轮次，决定下一个 Agent 角色 |
| **EXECUTE** | 执行规划的动作 | `executeAgent()` —— 用选定的 Agent 配置调用 LLM 生成回复 |
| **REFLECT** | 反思执行结果，调整策略 | `reflect()` —— 练习结束后，分析整体表现，给出下次练习建议 |

### 3.2 为什么要这样设计？

如果只用一个 AI Agent，每次用户说完话就直接丢给 LLM 回复，你无法控制练习的节奏和难度梯度。比如：

- 用户连续三轮说了"你总是""你每次都"这种评判性语言 → 应该切换到**观察步骤教练**专门练观察
- 用户四要素都表达得不错 → 应该切换到**困难搭档**提升难度
- 用户练习完所有四步 → 应该进入综合练习模式

这些决策用**规则代码**实现（而不是再调一次 LLM 做规划），好处是：
1. **快**：规则判断毫秒级，LLM 调用要几秒
2. **可控**：规则明确可测，LLM 规划有随机性
3. **省 Token**：不花额外的 API 费用

Reflect 阶段才用 LLM，因为"反思整体表现并给出建议"需要自然语言理解能力。

### 3.3 Agent 调度全景流程

```
┌─────────────────────────────────────────────────────────────────┐
│                    NvcAgentOrchestrator                          │
│                    （Agent 调度中心）                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  用户选择练习模式                                                │
│       │                                                         │
│       ▼                                                         │
│  [SCENARIO_GENERATOR] ──→ 根据用户档案 + 场景库生成练习场景       │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────────────────────────────────────┐       │
│  │              练习循环（多轮对话）                      │       │
│  │                                                     │       │
│  │  [DIALOGUE_GUIDE] 或 [DIFFICULT_PARTNER]            │       │
│  │       │                                             │       │
│  │       ▼  用户回复后                                  │       │
│  │  [NVC_EXPRESSION_EVALUATOR] ──→ 实时评估             │       │
│  │       │                                             │       │
│  │       ├── 评分 < 60 → 切换 [DIALOGUE_GUIDE] 引导重试 │       │
│  │       ├── 要素缺失 → 切换对应 [STEP_*_COACH] 针对练习 │       │
│  │       ├── 评分 ≥ 60 且四要素完整 → 推进              │       │
│  │       └── 难度升级 → 切换 [DIFFICULT_PARTNER]        │       │
│  │                                                     │       │
│  │  （循环直到用户结束或达到目标）                        │       │
│  └─────────────────────────────────────────────────────┘       │
│       │                                                         │
│       ▼                                                         │
│  [NVC_EXPRESSION_EVALUATOR] ──→ 最终综合评估                    │
│       │                                                         │
│       ▼                                                         │
│  [EMPATHY_COACH] ──→ 共情能力分析（可选）                       │
│       │                                                         │
│       ▼                                                         │
│  生成练习报告 + 更新用户档案                                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 四、技术选型解析

### 4.1 Redis 缓存 Agent 配置

**需求**：每次对话都要读取 Agent 配置（系统提示词、温度等），高频查 DB 是浪费。

**方案**：Redis 缓存 + 主动失效（Cache-Aside 模式）

```
读取流程：Redis GET → 命中返回 / 未命中 → 查 DB → 写 Redis（TTL 5 分钟）
更新流程：写 DB → 删 Redis Key → 下次读取自动回源
```

为什么用 `RedisService`（基于 Redisson）而不是 `@Cacheable` 注解？

看 `RedisService` 的实现（`infrastructure/redis/RedisService.java`）：

```java
public <T> T get(String key) {
    RBucket<T> bucket = redissonClient.getBucket(key);
    return bucket.get();
}
```

它底层用的是 **Redisson 的 `RBucket`**，相比 Spring Cache 注解有以下优势：
- **精确控制**：可以在更新时主动 `delete(key)`，不需要 `@CacheEvict` 的魔法
- **JSON 序列化**：用 `ObjectMapper.writeValueAsString` 把 Entity 序列化成 JSON 存进 Redis，反序列化时用 `readValue` 还原
- **TTL 精确**：可以指定 `TimeUnit.MINUTES`，Redisson 底层用 `SETEX` 原子操作

### 4.2 Spring AI 的 ChatClient 体系

看 `LlmProviderRegistry`（`common/ai/LlmProviderRegistry.java`）：

```java
public ChatClient getChatClientOrDefault(String providerId) {
    if (providerId != null && !providerId.isBlank()) {
        return getChatClient(providerId);
    }
    return getDefaultChatClient();
}
```

这是项目的**多 Provider 路由机制**。每个 Agent 配置可以指定自己用哪个 Provider（比如评估官用更精确的模型、对话引导官用更便宜的模型）。`LlmProviderRegistry` 内部用 `ConcurrentHashMap` 缓存了 ChatClient 实例：

- `clientCache.computeIfAbsent(id + ":plain", ...)` —— 不带工具调用的 ChatClient
- `clientCache.computeIfAbsent(id, ...)` —— 带 SkillsTool 的标准 ChatClient

这样每个 Agent 调用时不用重复创建 ChatClient，避免重复初始化开销。

LlmProviderRegistry 提供的几种 ChatClient 类型：

| 方法 | 用途 | 是否带工具调用 | 场景 |
|------|------|---------------|------|
| `getChatClientOrDefault(providerId)` | 标准 ChatClient | 是（SkillsTool） | 正常对话 |
| `getPlainChatClient(providerId)` | 纯 ChatClient | 否 | 结构化输出（评估、反思） |
| `getVoiceChatClient(providerId)` | 语音专用 | 是（流式） | 语音面试 |

### 4.3 Spring AI 的 Message 体系

`NvcAgentChatService.buildMessages()` 方法展示了 Spring AI 对话的核心 API：

```java
messages.add(new SystemMessage(systemPrompt));    // 系统提示词
messages.add(new UserMessage(msg.getContent()));  // 用户历史消息
messages.add(new AssistantMessage(msg.getContent())); // AI 历史回复
messages.add(new UserMessage(userMessage));       // 当前用户消息
```

Spring AI 的 `Message` 接口有三个子类：
- `SystemMessage`：设定 AI 的角色和行为约束（就是 Agent 的系统提示词）
- `UserMessage`：用户说的话
- `AssistantMessage`：AI 之前说的

把历史对话和当前消息都塞进 List，一起发给 LLM，这就是**无状态对话**的实现方式——每次请求都把完整上下文发给 LLM。相比有状态的 `ChatMemory`（Spring AI 也提供），手动管理更灵活，可以精确控制滑动窗口大小、注入哪些上下文。

### 4.4 StructuredOutputInvoker — 结构化输出重试器

在 Orchestrator 的 `reflect()` 和评估引擎中，需要 LLM 返回 JSON 格式的结构化数据。`StructuredOutputInvoker`（`common/ai/StructuredOutputInvoker.java`）提供了带重试的结构化输出调用：

```java
public <T> T invoke(
    ChatClient chatClient,
    String systemPromptWithFormat,
    String userPrompt,
    BeanOutputConverter<T> outputConverter,
    ErrorCode errorCode,
    String errorPrefix,
    String logContext,
    Logger log
) { ... }
```

工作原理：
1. 第一次调用：发送系统提示词 + 用户提示词
2. 如果返回的 JSON 解析失败：构造"修复提示词"（包含上次错误信息），重试
3. 超过最大重试次数（默认 2 次）：抛出 `BusinessException`

配置项（`StructuredOutputProperties`）：

```java
private int structuredMaxAttempts = 2;              // 最大重试次数
private boolean structuredIncludeLastError = true;  // 重试时是否包含上次错误
private boolean structuredRetryUseRepairPrompt = true; // 使用修复提示词
private boolean structuredRetryAppendStrictJsonInstruction = true; // 追加严格 JSON 指令
```

---

## 五、代码实现详解

### 5.1 NvcAgentConfigService — 配置热更新

路径：`app/src/main/java/interview/guide/modules/nvcpractice/service/NvcAgentConfigService.java`

核心流程：

**① 读取配置（带缓存）**：

```java
public NvcAgentConfigEntity getConfig(NvcAgentScene scene) {
    String cacheKey = CACHE_KEY_PREFIX + scene.name();  // "nvc:agent:config:DIALOGUE_GUIDE"

    // 1. 先查 Redis
    String cached = redisService.get(cacheKey);
    if (cached != null) {
        return objectMapper.readValue(cached, NvcAgentConfigEntity.class);
        // 反序列化失败 → 删缓存，继续回源
    }

    // 2. Redis 没有 → 查 DB
    NvcAgentConfigEntity config = agentConfigRepository.findByAgentScene(scene)
        .orElseThrow(...);

    // 3. 写回 Redis（TTL 5 分钟）
    redisService.set(cacheKey, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

    return config;
}
```

**关键细节**：缓存用的是 JSON 字符串而不是 Redisson 的对象存储。原因是 Entity 里有些字段（如 `NvcAgentScene` 枚举）直接用 RBucket 序列化可能有兼容性问题，用 `ObjectMapper` 转 JSON 更通用、更可调试（可以直接在 Redis CLI 里看到明文）。

**② 更新配置（热更新）**：

```java
public NvcAgentConfigEntity updateConfig(NvcAgentScene scene, AgentConfigUpdateRequest request) {
    // 1. 从 DB 查出来
    NvcAgentConfigEntity config = agentConfigRepository.findByAgentScene(scene)...;

    // 2. 只更新请求中非 null 的字段（部分更新语义）
    if (request.systemPrompt() != null) config.setSystemPrompt(...);
    if (request.temperature() != null) config.setTemperature(...);
    ...

    // 3. 保存到 DB
    NvcAgentConfigEntity saved = agentConfigRepository.save(config);

    // 4. 主动删缓存（不是更新缓存！）
    redisService.delete(cacheKey);

    return saved;
}
```

**为什么是删除缓存而不是更新缓存？** 这是 Cache-Aside 模式的标准做法——删缓存比写缓存更安全：如果在保存 DB 和更新缓存之间有并发读取，写缓存可能导致缓存和 DB 不一致。删缓存后，下次读取自然回源拿最新数据。

### 5.2 NvcAgentOrchestrator — 调度中心

路径：`app/src/main/java/interview/guide/modules/nvcpractice/service/NvcAgentOrchestrator.java`

这是最核心的类。`decideNextAgent()` 是入口，它根据**练习模式**分流到两条决策路径：

#### 路径一：结构化四步模式（`decideStructuredStep`）

```
用户在练"观察"步骤 → 评估得分 75 → ≥70 → 推进到"感受"步骤
用户在练"请求"步骤 → 评估得分 45 → <70 → 继续练"请求"
四步全过 → 进入综合练习（DIALOGUE_GUIDE）
```

关键逻辑：

```java
if (stepScore >= 70) {
    NvcPracticeStep nextStep = nextStep(currentStep);  // OBSERVE→FEELING→NEED→REQUEST→COMPLETED
    if (nextStep == NvcPracticeStep.COMPLETED) {
        return new AgentDecision(DIALOGUE_GUIDE, "四步全部通过，进入综合练习", "COMPLETED_ALL_STEPS");
    }
    return new AgentDecision(stepToCoach(nextStep), currentStep + " 通过，推进到 " + nextStep, "STEP_ADVANCE");
}
// 评分 < 70，继续当前步骤
return new AgentDecision(stepToCoach(currentStep), ..., "STEP_RETRY");
```

`stepToCoach()` 是一个 switch 表达式，把 NVC 四步映射到对应的 Agent：

```java
private NvcAgentScene stepToCoach(NvcPracticeStep step) {
    return switch (step) {
        case OBSERVE -> NvcAgentScene.STEP_OBSERVE_COACH;
        case FEELING -> NvcAgentScene.STEP_FEELING_COACH;
        case NEED -> NvcAgentScene.STEP_NEED_COACH;
        case REQUEST -> NvcAgentScene.STEP_REQUEST_COACH;
        default -> NvcAgentScene.DIALOGUE_GUIDE;
    };
}
```

`nextStep()` 定义了四步的推进顺序：

```java
private NvcPracticeStep nextStep(NvcPracticeStep current) {
    return switch (current) {
        case OBSERVE -> NvcPracticeStep.FEELING;
        case FEELING -> NvcPracticeStep.NEED;
        case NEED -> NvcPracticeStep.REQUEST;
        case REQUEST -> NvcPracticeStep.COMPLETED;
        default -> NvcPracticeStep.COMPLETED;
    };
}
```

#### 路径二：自由对话/场景驱动模式（`decideFreeDialog`）

这个更复杂，有四级决策：

```
没有评估 → 用 DIALOGUE_GUIDE（对话引导官）
↓ 有评估了
评分 < 50 → 切换 DIALOGUE_GUIDE（降低难度，温和引导）
↓ 评分 ≥ 50
评分 ≥ 60 且轮次 > 3 且每3轮 → 切换 DIFFICULT_PARTNER（困难搭档，提升难度）
↓ 不满足上面
有薄弱要素且评分 < 70 → 切换对应 STEP_*_COACH（针对性练习）
↓ 默认
继续 DIALOGUE_GUIDE
```

`findWeakestElement()` 方法遍历四维评分，找最低的那个维度，让对应的步骤教练来针对性辅导：

```java
private NvcPracticeStep findWeakestElement(PracticeContext context) {
    NvcEvaluationEntity eval = context.getLastEvaluation();
    if (eval == null) return null;

    int minScore = Integer.MAX_VALUE;
    NvcPracticeStep weakest = null;

    if (eval.getObservationScore() != null && eval.getObservationScore() < minScore) {
        minScore = eval.getObservationScore();
        weakest = NvcPracticeStep.OBSERVE;
    }
    // ... 感受、需求、请求同理

    return weakest;
}
```

这就是"自适应学习"的体现——AI 不是按固定流程走，而是根据用户弱项动态调整。

#### EXECUTE 阶段

```java
public String executeAgent(NvcAgentScene scene, PracticeContext context, String userMessage) {
    NvcAgentConfigEntity config = agentConfigService.getConfig(scene);

    // 降级：如果选的 Agent 被禁用了，回退到 DIALOGUE_GUIDE
    if (!config.getIsEnabled()) {
        log.warn("Agent is disabled: {}, falling back to DIALOGUE_GUIDE", scene);
        config = agentConfigService.getConfig(NvcAgentScene.DIALOGUE_GUIDE);
    }

    return agentChatService.chat(config, context, userMessage);
}
```

注意这里的**降级逻辑**：如果某个 Agent 被运营人员手动禁用了（`is_enabled = false`），不会报错，而是自动回退到默认的对话引导官。这保证了系统的健壮性。

#### REFLECT 阶段

```java
public String reflect(PracticeContext context) {
    String reflectPrompt = """
        基于以下练习记录，请分析用户的表现并给出下次练习的策略建议：

        练习模式：%s
        练习轮次：%d
        最终评分：观察%d 感受%d 需求%d 请求%d 综合%d

        请输出JSON格式：
        {
          "weak_elements": ["薄弱要素列表"],
          "suggested_difficulty": "EASY/MEDIUM/HARD",
          "suggested_scenario_type": "建议的场景类型",
          "strategy_note": "策略建议说明"
        }
        """.formatted(
            context.getSession().getPracticeMode(),
            context.getRoundCount(),
            context.getLastEvaluation().getObservationScore(),
            context.getLastEvaluation().getFeelingScore(),
            context.getLastEvaluation().getNeedScore(),
            context.getLastEvaluation().getRequestScore(),
            context.getLastEvaluation().getOverallScore()
        );

    return agentChatService.chatPlain(evaluatorConfig, reflectPrompt);
}
```

这里用 `chatPlain()` 而不是 `chat()`——因为反思阶段不需要注入对话历史和用户消息，只需要一个结构化的 prompt。`chatPlain` 调用的是 `LlmProviderRegistry.getPlainChatClient()`（不带 SkillsTool 的 ChatClient），更适合结构化输出场景。

### 5.3 NvcAgentChatService — 对话执行

路径：`app/src/main/java/interview/guide/modules/nvcpractice/service/NvcAgentChatService.java`

**核心方法 `buildMessages()`** — 上下文注入：

这个方法把多种信息拼装成 LLM 能理解的消息序列：

```
1. 系统提示词（Agent 配置里的 systemPrompt）
2. 追加用户档案摘要（如果有）→ "[用户档案]\n内向，容易焦虑..."
3. 追加场景描述（如果有）→ "[练习场景]\n同事抢功..."
4. 追加 RAG 知识（如果有）→ "[参考资料]\nNVC 理论中..."
5. 对话历史（最近 N 条消息，USER → UserMessage，ASSISTANT → AssistantMessage）
6. 当前用户消息
```

这样做的好处是**每个 Agent 都能获得个性化上下文**。比如困难搭档知道用户是"内向、容易焦虑"的，就可以针对性地施压；观察步骤教练知道用户在练职场场景，可以举职场相关的例子。

**流式输出**：

```java
public Flux<String> chatStream(NvcAgentConfigEntity config, PracticeContext context, String userMessage) {
    ChatClient chatClient = getChatClient(config);
    List<Message> messages = buildMessages(config, context, userMessage);

    return chatClient.prompt()
        .messages(messages)
        .stream()
        .content();
}
```

返回 `Flux<String>`（Reactor 响应式流），前端通过 SSE（Server-Sent Events）接收，实现打字机效果。

### 5.4 DTO 设计

| DTO | 类型 | 职责 |
|-----|------|------|
| `AgentDecision` | `record` | Orchestrator 的决策结果（哪个 Agent + 原因 + 动作标记） |
| `PracticeContext` | `@Data @Builder` | 练习上下文容器（会话、历史、评估、档案等） |
| `AgentConfigDTO` | `record` | 返回给前端的配置视图（不暴露 Entity 内部结构） |
| `AgentConfigUpdateRequest` | `record` | 前端更新配置的请求体（带 JSR-303 校验） |

**为什么 `AgentDecision` 和 `AgentConfigDTO` 用 `record`，而 `PracticeContext` 用 `@Data @Builder`？**

- `record` 是 Java 16+ 的不可变数据载体，天然适合 DTO——一次构造不可变，自动生成 `equals`/`hashCode`/`toString`。
- `PracticeContext` 需要用 Builder 模式构建（字段多、有些可选），所以用 Lombok 的 `@Builder`。如果用 record 也能 Builder（Java 21 的 record 支持 builder），但选择 `@Data @Builder` 更灵活（可以后续修改字段值）。

**AgentConfigUpdateRequest 的校验注解**：

```java
public record AgentConfigUpdateRequest(
    String systemPrompt,
    String modelProvider,
    String modelName,

    @DecimalMin("0.0") @DecimalMax("2.0")
    Double temperature,

    @Min(100) @Max(8000)
    Integer maxTokens,

    @DecimalMin("0.0") @DecimalMax("1.0")
    Double topP,

    Boolean isEnabled
) {}
```

使用 JSR-303（Bean Validation）注解，配合 Controller 层的 `@Valid`，在请求进入 Service 之前就拦截非法参数。比如 `temperature` 只能是 0.0~2.0，传 3.0 会直接返回 400。

### 5.5 Controller — NvcAgentConfigController

路径：`app/src/main/java/interview/guide/modules/nvcpractice/controller/NvcAgentConfigController.java`

```java
@GetMapping("/configs")
public Result<List<AgentConfigDTO>> getAllConfigs() { ... }

@PutMapping("/configs/{scene}")
public Result<AgentConfigDTO> updateConfig(@PathVariable NvcAgentScene scene, ...) { ... }
```

注意 `@PathVariable NvcAgentScene scene`——Spring 会自动把 URL 路径参数转换为枚举。如果传了不存在的场景名（如 `/configs/INVALID`），Spring 会直接返回 400。这是 Spring MVC 的 `Converter` 机制在起作用。

RESTful 风格：
- `GET /api/nvc/agents/configs` — 获取所有 Agent 配置
- `GET /api/nvc/agents/configs/{scene}` — 获取单个 Agent 配置
- `PUT /api/nvc/agents/configs/{scene}` — 更新 Agent 配置（热更新）

---

## 六、怎么用这个东西？

### 6.1 运营/开发场景：热更新 Agent 提示词

假设你在测试"困难搭档"的效果，发现它太极端了，想把温度从 0.9 降到 0.7：

```bash
curl -X PUT http://localhost:8080/api/nvc/agents/configs/DIFFICULT_PARTNER \
-H "Content-Type: application/json" \
-d '{"temperature": 0.7, "systemPrompt": "你是NVC练习中的困难搭档...（修改后的提示词）"}'
```

更新后立即生效——下一次对话调用 `getConfig(DIFFICULT_PARTNER)` 时，Redis 缓存已被删除，会从 DB 读取新配置。**不需要重启服务**。

### 6.2 前端管理页面

前端有一个 `NvcAgentConfigPage`，运营人员可以在上面：
- 查看所有 Agent 的当前配置（提示词、温度、模型、是否启用）
- 修改某个 Agent 的提示词（比如调优对话引导官的引导方式）
- 启用/禁用某个 Agent（比如暂时关掉困难搭档，只做温和练习）
- 切换 Agent 使用的 LLM Provider（比如评估官用更精确的模型，对话官用更便宜的）

### 6.3 练习流程中的调用

在后续 Step 3（NVC 文字练习核心流程）中，`NvcPracticeService` 会这样使用：

```java
// 1. 用户发了一条消息
// 2. 构建 PracticeContext（加载会话、历史消息、最近评估、用户档案、RAG 知识）
PracticeContext context = PracticeContext.builder()
    .session(session)
    .recentMessages(recentMessages)
    .lastEvaluation(lastEval)
    .roundCount(roundCount)
    .userProfileSummary(profileSummary)
    .scenarioDescription(scenarioDesc)
    .ragContext(ragContext)
    .build();

// 3. PLAN：让 Orchestrator 决定下一步用哪个 Agent
AgentDecision decision = orchestrator.decideNextAgent(context);

// 4. EXECUTE：用选定的 Agent 执行对话
String aiReply = orchestrator.executeAgent(decision.scene(), context, userMessage);

// 5. 保存 AI 回复到 DB
// 6. 如果需要评估，调用评估引擎
// 7. 练习结束时：REFLECT
String reflection = orchestrator.reflect(context);
```

### 6.4 Agent 角色一览

系统共定义了 10 个 Agent 角色（`NvcAgentScene` 枚举）：

| 分类 | Agent 场景 | 显示名称 | 职责 |
|------|-----------|---------|------|
| 练习引导 | `SCENARIO_GENERATOR` | 场景生成官 | 根据用户档案和偏好生成 NVC 练习场景 |
| 练习引导 | `DIALOGUE_GUIDE` | 对话引导官 | 模拟真实对话对象，引导用户练习 NVC |
| 练习引导 | `DIFFICULT_PARTNER` | 困难搭档 | 故意用评判/指责语气，训练用户在压力下保持 NVC |
| 结构化练习 | `STEP_OBSERVE_COACH` | 观察步骤教练 | 引导用户区分观察和评论 |
| 结构化练习 | `STEP_FEELING_COACH` | 感受步骤教练 | 引导用户识别和表达感受 |
| 结构化练习 | `STEP_NEED_COACH` | 需求步骤教练 | 引导用户发现深层需求 |
| 结构化练习 | `STEP_REQUEST_COACH` | 请求步骤教练 | 引导用户提出具体可执行的请求 |
| 评估反馈 | `NVC_EXPRESSION_EVALUATOR` | NVC 表达评估官 | 评估用户表达中的四要素质量 |
| 评估反馈 | `EMPATHY_COACH` | 共情教练 | 分析用户的倾听和共情能力 |
| 知识辅助 | `NVC_KNOWLEDGE_ADVISOR` | NVC 知识顾问 | 回答 NVC 理论问题（结合 RAG） |

---

## 七、总结：Step 2 的核心价值

| 能力 | 没有 Step 2 时 | 有 Step 2 后 |
|------|----------------|-------------|
| **多 Agent 切换** | 只有一个 AI 角色，对话平淡 | 根据状态自动切换 10 种 Agent 角色 |
| **自适应难度** | 固定流程，不管用户表现 | 低分自动引导、高分自动加难 |
| **Agent 调参** | 改提示词要改代码、重启服务 | API 热更新，秒级生效 |
| **个性化** | 每个 Agent 不知道用户是谁 | 注入用户档案、RAG 知识 |
| **可测试** | 决策和执行混在一起 | Orchestrator 可独立单测（不依赖 LLM） |

### 关键设计决策记录

#### 为什么用 Redis 缓存 Agent 配置？

- Agent 配置在每次对话时都需要读取（系统提示词、温度等）
- 频繁查 DB 会有性能开销
- Redis 缓存 + 主动失效 = 热更新 + 高性能

#### 为什么 Orchestrator 不直接依赖具体 Agent 实现？

- Orchestrator 只做**决策**（选择哪个 Agent），不做**执行**（调用 LLM）
- 执行由 NvcAgentChatService 负责
- 这样 Orchestrator 的逻辑可以单独测试，不依赖 LLM 调用

#### 为什么 Plan-Execute-Reflect 不用 LLM 做 Plan？

- 当前版本的 Plan 逻辑用代码实现（规则判断），更可控、更快
- 如果未来需要更复杂的策略，可以在 decideNextAgent 中加入 LLM 调用做 Plan
- Reflect 阶段用了 LLM（chatPlain），因为反思需要自然语言理解能力

---

> Step 2 不是"一个功能"，而是整个 NVC 练习系统的**中枢神经系统**，让练习从"跟一个 AI 聊天"升级为"有策略、有节奏、有反馈的自适应训练"。