# Phase 2 面试亮点补充规划 — Trace 可观测性 + 量化指标 + Fallback 降级

> **⚠️ 此文档已过时，仅供参考。** 三大亮点（P0-3 Fallback 降级 + P0-1 指标采集 + P0-2 Trace 可观测）已全部完成并合并 master，共 74 文件 +5844 行。

> 创建时间：2026-08-04
> 目标：补全简历三大短板（量化指标 / Trace 可观测性 / LLM Fallback 降级），让项目在 Agent 方向面试中有完整的技术深度闭环
> 总预估：10-14 天
> 执行顺序：P0-3 → P0-1 → P0-2（由快到慢，每步产出可独立写入简历）

---

## 〇、现状评估

### 已具备 ✅

| 能力 | 位置 | 说明 |
|------|------|------|
| Agent Loop 执行引擎 | `nvcassistant/AgentLoop` | 自建主循环，7 层 Hook 链 |
| 异步 Stream 基建 | `common/async/AbstractStreamProducer/Consumer` | 4 组已在用（评估/语音/Wiki/知识库向量化） |
| Redis Stream | `RedisService` | 已有 Stream 读写能力 |
| LLM Provider 管理 | `llmprovider/` | 多供应商管理 + API Key 加密 |
| 评估引擎 | `nvcpractice/NvcEvaluationService` | 四要素结构化评估 |
| Trace 埋点雏形 | `nvcassistant/LoggingToolHook` | 已有日志级 Trace，但未结构化落库 |
| 统一评估服务 | `common/evaluation/UnifiedEvaluationService` | 已有评估框架 |

### 缺失 ❌

| 缺失项 | 影响 | 本计划覆盖 |
|--------|------|-----------|
| LLM Fallback 降级 | LLM 异常时业务中断 | P0-3 |
| 量化指标采集 | 简历无数字 | P0-1 |
| Trace 结构化落库 | 无法回溯 Agent 调用链路 | P0-2 |
| Trace 可视化页面 | 无法直观查看链路 | P0-2 |
| 离线评估体系 | 无法量化系统质量 | P0-2 |
| Golden Dataset | 评估无基准 | P0-2 |
| 语音模块遗留完善 | 功能未闭环 | 穿插在 P0-2 |
| 推荐服务未集成 | 首页无推荐 | 穿插在 P0-1 |

---

## 一、P0-3：LLM Fallback 降级（1-2 天）

> **分支**：`feat/fallback-degradation`
> **目标**：全链路 LLM 异常时，后端规则与模板接管，保障业务流程不中断

### 1.1 降级策略设计

#### 分层降级架构

```
LLM 调用入口（统一拦截层）
  ├── 正常路径：LLM 返回结果
  └── 异常路径（超时/限流/格式错误）
       ├── 对话降级：NVC 引导话术模板
       ├── 评估降级：关键词匹配粗略评分 + 标记"降级评估"
       └── 场景生成降级：种子场景库随机分配
```

#### 异常类型定义

```java
public enum LlmFailureType {
    TIMEOUT,          // 调用超时
    RATE_LIMITED,     // 限流
    INVALID_RESPONSE, // 返回格式异常
    PROVIDER_ERROR,   // 供应商错误（5xx）
    UNKNOWN           // 未知异常
}
```

### 1.2 实现清单

#### 1.2.1 统一降级拦截器

**新建文件**：`nvcassistant/fallback/LlmFallbackHandler.java`

```java
/**
 * 统一 LLM 降级处理器
 * 所有 LLM 调用入口通过此 Handler 包装，异常时自动降级
 */
public class LlmFallbackHandler {

    /**
     * 执行 LLM 调用，失败时自动降级
     * @param llmCall 正常 LLM 调用逻辑
     * @param fallback 降级逻辑
     * @param context 调用上下文（用于日志和 Trace）
     */
    public <T> T executeWithFallback(
        Supplier<T> llmCall,
        Supplier<T> fallback,
        LlmCallContext context
    );
}
```

**核心逻辑**：
- 捕获 `LlmCallException` 及子类（超时/限流/格式/供应商错误）
- **重试策略**：首次失败后等待 1s 重试，二次失败等待 3s 重试，三次失败才降级
- 记录失败事件到 Trace（为 P0-2 预留接口）
- 调用对应场景的降级逻辑
- 降级结果标记 `degraded: true`，前端可展示"降级模式"提示

**完整执行流程**：
```
executeWithFallback(llmCall, fallback, context)
  │
  ├── 第 1 次调用 llmCall
  │   ├── 成功 → 返回结果
  │   └── 失败 → 记录失败类型，等待 1s
  │
  ├── 第 2 次调用 llmCall（重试）
  │   ├── 成功 → 返回结果
  │   └── 失败 → 记录失败类型，等待 3s
  │
  ├── 第 3 次调用 llmCall（重试）
  │   ├── 成功 → 返回结果
  │   └── 失败 → 进入降级
  │
  └── 降级路径
      ├── 记录降级事件（失败类型 + 重试次数 + 总耗时）
      ├── 调用 fallback.get()
      ├── 标记结果 degraded=true
      └── 返回降级结果
```

**异常分类与重试策略**：
```java
public enum LlmFailureType {
    TIMEOUT(3000, true),          // 超时：等 3s 重试
    RATE_LIMITED(5000, true),     // 限流：等 5s 重试
    INVALID_RESPONSE(0, true),    // 格式错误：立即重试
    PROVIDER_ERROR(2000, true),   // 供应商错误：等 2s 重试
    UNKNOWN(1000, false);         // 未知：等 1s 重试，但只重试 1 次

    private final long retryDelayMs;
    private final boolean retryable;
}
```

#### 1.2.2 对话降级模板

**新建文件**：`nvcassistant/fallback/DialogFallbackTemplates.java`

```java
/**
 * NVC 对话降级模板库
 * LLM 异常时，返回预设的 NVC 引导话术（不是"服务不可用"的废话）
 */
public class DialogFallbackTemplates {

    // 按练习步骤分类的引导话术
    private static final Map<NvcPracticeStep, List<String>> STEP_TEMPLATES;

    // 按场景分类的引导话术
    private static final Map<String, List<String>> SCENARIO_TEMPLATES;

    /**
     * 根据当前练习状态选择合适的降级话术
     * 逻辑：匹配当前步骤 + 随机选择，避免重复
     */
    public String selectTemplate(PracticeContext context);
}
```

**降级话术完整内容**（按步骤 × 轮次，随机选择避免重复）：

**观察步骤降级模板（6 条）**：
1. "让我们先停下来，客观描述一下刚才发生了什么？注意区分事实和评价哦。"
2. "能告诉我具体发生了什么吗？尽量只描述你看到和听到的，不加判断。"
3. "试着用摄像机回放的方式描述——如果有人录下来，画面里是什么？"
4. "你说的'他总是...'，能换成'在这次具体的事件中，他做了什么'吗？"
5. "我们先聚焦事实：什么时候、在哪里、发生了什么？"
6. "观察是 NVC 的第一步。能用'我看到/听到...'开头重新描述一下吗？"

**感受步骤降级模板（6 条）**：
1. "当你经历这些的时候，内心的感受是什么？试着用'我感到...'来表达。"
2. "这个 situation 让你产生了什么情绪？开心、失落、焦虑、还是其他？"
3. "注意区分感受和想法哦。'我感到被忽视'是想法，'我感到孤独'才是感受。"
4. "你能找到一个词来描述此刻的内心状态吗？比如委屈、不安、释然..."
5. "身体有没有给你信号？胸口发紧、肩膀僵硬——这些往往是感受的线索。"
6. "如果用一个颜色来形容你现在的感受，会是什么？背后的情绪是什么？"

**需求步骤降级模板（6 条）**：
1. "这个感受背后，你有什么需要没有被满足呢？"
2. "NVC 认为所有感受都指向某个需要。你的需要是被尊重、被理解、还是其他？"
3. "试着用'我需要...'开头，说出你内心最渴望的东西。"
4. "如果对方完全理解了你的感受，你最希望他做什么改变？那个'希望'就是你的需要。"
5. "需要是普世的——安全、尊重、连接、自主。你的需要属于哪一类？"
6. "有时候愤怒背后是未被满足的需要。你的愤怒在告诉你什么？"

**请求步骤降级模板（6 条）**：
1. "基于你的需要，你能提出一个具体、可执行的请求吗？"
2. "好的请求是具体的、正向的、可操作的。'不要这样做'不如'请你那样做'。"
3. "如果对方只能说'好'或'不行'，你的请求足够清晰让他做出回应吗？"
4. "试着用'你愿意...吗？'的句式，把你的需要转化为一个具体请求。"
5. "请求不是命令。你愿意接受对方说'不'吗？如果愿意，这就是一个真正的请求。"
6. "你希望对方具体做什么？比如'今晚我们能花 30 分钟聊聊吗？'"

**自由对话降级模板（4 条）**：
1. "我注意到你提到了一些重要的事情。能再多说一些吗？我想更好地理解你。"
2. "谢谢你愿意分享。在 NVC 中，我们试着用观察-感受-需求-请求的框架来表达。你想从哪一步开始？"
3. "听起来这对你很重要。你能试着描述一下具体发生了什么，以及你当时的感受吗？"
4. "我在这里倾听你。如果你愿意，我们可以一起用 NVC 的方式来梳理这件事。"

**场景模式降级模板（3 条）**：
1. "这是一个很好的练习场景。让我们从观察开始——在这个场景中，你看到了什么具体事实？"
2. "进入这个场景，你的第一反应是什么？试着区分事实和评价。"
3. "这个场景触发了你的什么感受？背后有什么需要？"

#### 1.2.3 评估降级

**新建文件**：`nvcpractice/fallback/EvaluationFallbackService.java`

```java
/**
 * 评估降级服务
 * LLM 评估异常时，用关键词匹配给出粗略评分
 */
public class EvaluationFallbackService {

    /**
     * 关键词匹配评估
     * 基于 NVC 四要素的关键词表，计算粗略分数
     */
    public NvcEvaluationResult evaluateByKeyWords(String userMessage, NvcPracticeStep step);

    /**
     * 标记为降级评估
     * 降级评估结果写入 DB 时标记 degraded=true
     * 后续服务恢复后可重新评估
     */
    public void markAsDegraded(NvcEvaluationEntity entity);
}
```

**关键词匹配评分算法**：

```java
/**
 * 四要素关键词评分器
 * 每个要素基础分 5 分，根据正向/负向关键词加减分
 * 最终分数 1-10，保留 1 位小数
 */
public class KeywordScorer {

    // ===== 观察维度 =====
    private static final List<String> OBSERVATION_POSITIVE = List.of(
        "看到", "听到", "注意到", "发现", "观察到", "记录到",
        "在...时候", "当...的时候", "第一次", "第二次", "那天",
        "具体来说", "实际上", "数据显示"
    );
    private static final List<String> OBSERVATION_NEGATIVE = List.of(
        "总是", "从来", "每次", "永远", "根本", "简直",
        "应该", "必须", "一定", "当然",
        "自私", "懒", "不负责任", "不尊重", "不关心", // 人身标签
        "太...了", "那么...", "这么..."  // 绝对化
    );
    // 评分规则：
    // 基础 5 分
    // 每个正向关键词 +0.5（上限 +3）
    // 每个负向关键词 -0.8（下限 -4）
    // 包含具体数字/时间 +1（如"三次"、"昨天"、"10点"）

    // ===== 感受维度 =====
    private static final List<String> FEELING_POSITIVE = List.of(
        "感到", "觉得", "感觉", "内心",
        // 正向情绪
        "开心", "高兴", "感激", "温暖", "安心", "兴奋", "满足", "欣慰", "感动", "放心", "愉快",
        // 负向情绪（也是正确的感受表达）
        "失落", "焦虑", "委屈", "疲惫", "孤独", "沮丧", "不安", "愤怒", "失望", "困惑",
        "紧张", "害怕", "担心", "难过", "伤心", "痛苦", "无奈", "无力"
    );
    private static final List<String> FEELING_NEGATIVE = List.of(
        "我觉得", "我认为", "我想",  // 想法而非感受
        "被忽视", "被抛弃", "被控制", "被误解",  // "被X"是想法
        "不公平", "不合理", "不应该",  // 判断而非感受
        "他让我", "她让我", "你让我"  // 把感受归因于他人
    );
    // 评分规则：
    // 基础 5 分
    // 使用"我感到/我感到..." +1
    // 每个正向情绪词 +0.5（上限 +3）
    // 每个负向模式 -0.8（下限 -4）

    // ===== 需求维度 =====
    private static final List<String> NEED_POSITIVE = List.of(
        "需要", "希望", "想要", "渴望", "期待", "重视",
        "被尊重", "被理解", "被认可", "被关心", "被接纳",
        "安全感", "归属感", "自主", "自由", "成长", "连接",
        "诚实", "信任", "公平", "平等", "和谐"
    );
    private static final List<String> NEED_NEGATIVE = List.of(
        "你必须", "你应该", "你得",  // 把需求强加于人
        "不要", "别再", "停止",  // 负向表达而非需求
        "因为你", "都怪你", "都是你"  // 归因而非需求
    );
    // 评分规则：
    // 基础 5 分
    // 明确表达需要 +1.5（如"我需要被尊重"）
    // 每个需求词汇 +0.5（上限 +3）
    // 负向模式 -0.8（下限 -4）

    // ===== 请求维度 =====
    private static final List<String> REQUEST_POSITIVE = List.of(
        "能不能", "可以", "请你", "你愿意", "是否可以",
        "我希望你", "我请求", "请", "麻烦",
        "具体来说", "比如", "比如说",  // 具体化
        "今天", "明天", "以后", "每次"  // 时间具体化
    );
    private static final List<String> REQUEST_NEGATIVE = List.of(
        "不要", "别", "停止", "不许",  // 负向请求
        "永远", "一直", "每次都要",  // 不可执行
        "你应该知道", "你心里清楚"  // 模糊请求
    );
    // 评分规则：
    // 基础 5 分
    // 使用正向请求句式 +1.5
    // 包含具体行动 +1（如"今晚花30分钟聊聊"）
    // 负向模式 -0.8（下限 -4）
    // 只有请求没有前面三要素 -2（请求不完整）

    /**
     * 对单条消息进行四要素评分
     */
    public NvcEvaluationResult score(String userMessage, NvcPracticeStep currentStep) {
        double observation = scoreDimension(userMessage, OBSERVATION_POSITIVE, OBSERVATION_NEGATIVE, 5.0);
        double feeling = scoreDimension(userMessage, FEELING_POSITIVE, FEELING_NEGATIVE, 5.0);
        double need = scoreDimension(userMessage, NEED_POSITIVE, NEED_NEGATIVE, 5.0);
        double request = scoreDimension(userMessage, REQUEST_POSITIVE, REQUEST_NEGATIVE, 5.0);

        // 根据当前步骤调整权重（当前步骤评分更严格）
        // ...

        return new NvcEvaluationResult(
            clamp(observation), clamp(feeling), clamp(need), clamp(request),
            "【降级评估】此评分为关键词匹配生成，仅供参考",
            "degraded"
        );
    }
}
```

**降级评估标记**：
- 评估结果的 `evaluationType` 字段标记为 `DEGRADED`
- 结果中附加提示："此评分为关键词匹配生成，仅供参考，服务恢复后可重新评估"
- DB 中 `NvcEvaluationEntity.degraded = true`
- 后续可通过 API `/api/nvc/evaluations/{id}/re-evaluate` 触发 LLM 重新评估

#### 1.2.4 场景生成降级

**修改文件**：`nvcpractice/tools/ScenarioGenerateTool.java`

在现有 `execute()` 方法中增加降级逻辑：
```java
try {
    // 正常 LLM 生成场景
    return generateByLlm(request);
} catch (LlmCallException e) {
    // 降级：从种子场景库随机分配
    return selectRandomFromSeedScenarios(request.getDifficulty(), request.getFocusElements());
}
```

### 1.3 集成点

| 调用方 | 集成方式 | 改动文件 |
|--------|---------|---------|
| AgentLoop（对话） | `LlmFallbackHandler` 包装 LLM 调用 | `AgentLoop.java` |
| NvcEvaluationService（评估） | 注入 `EvaluationFallbackService` | `NvcEvaluationService.java` |
| ScenarioGenerateTool（场景） | 内部 catch 降级 | `ScenarioGenerateTool.java` |
| NvcCommunicationAnalysisService（分析） | `LlmFallbackHandler` 包装 | `NvcCommunicationAnalysisService.java` |

### 1.4 前端适配

**修改文件**：`frontend/src/utils/sse.ts` + `NvcChatPanel.tsx`

- SSE metadata 事件增加 `degraded: true` 字段
- 前端展示"降级模式"提示条（黄色横幅："AI 服务暂时降级，当前为引导模式"）

### 1.5 测试

**新建测试文件**：`app/src/test/java/nvc/guide/modules/nvcassistant/fallback/`

| 测试类 | 测试项 | 断言 |
|--------|--------|------|
| `LlmFallbackHandlerTest` | 超时降级 | Mock LLM 抛出 TimeoutException，验证返回降级结果且 degraded=true |
| `LlmFallbackHandlerTest` | 限流降级 | Mock LLM 抛出 RateLimitException，验证等待重试后降级 |
| `LlmFallbackHandlerTest` | 重试成功 | Mock LLM 第一次失败第二次成功，验证返回正常结果 |
| `LlmFallbackHandlerTest` | 重试耗尽 | Mock LLM 三次都失败，验证触发降级 |
| `DialogFallbackTemplatesTest` | 步骤匹配 | 验证 OBSERVE 步骤返回观察类模板 |
| `DialogFallbackTemplatesTest` | 不重复 | 连续调用 10 次，验证返回的模板不完全相同 |
| `EvaluationFallbackServiceTest` | 评判词检测 | 输入含"总是/从来"，验证观察分 < 5 |
| `EvaluationFallbackServiceTest` | 情绪词检测 | 输入含"感到失落"，验证感受分 >= 6 |
| `EvaluationFallbackServiceTest` | 降级标记 | 验证 DB 中 degraded=true |
| `ScenarioGenerateToolTest` | 种子降级 | Mock LLM 异常，验证返回种子场景库中的场景 |

### 1.6 文档更新

- `services/nvc-backend/AGENTS.md` — 增加降级策略说明
- `docs/decisions/` — 新增决策记录：为什么选择规则降级而非模板降级

### 1.7 预期产出（简历素材）

```
设计 LLM 全链路 Fallback 降级体系，对话场景采用 NVC 引导话术模板接管，
评估场景采用关键词匹配粗略评分并标记降级，场景生成降级为种子库随机分配；
保障全链路任一 LLM 异常时业务流程正常不中断，对话始终有回复。
```

---

## 二、P0-1：量化指标采集（2-3 天）

> **分支**：`feat/metrics-collection`
> **目标**：采集 4 项核心指标 + 评估一致性，产出简历可用的数字
> **前置**：P0-3 已合并（降级标记数据可用于指标统计）

### 2.1 指标体系设计

| 指标 | 计算方式 | 数据来源 |
|------|---------|---------|
| **Token 消耗** | 每次 LLM 调用的 input_tokens + output_tokens | LLM 返回的 usage 字段 |
| **端到端延迟** | 用户发送消息 → SSE done 事件的时间差 | 请求时间戳 - 完成时间戳 |
| **上下文压缩效果** | 压缩前后 Token 数对比 | ContextManager 日志 |
| **评估一致性** | LLM 评分 vs Golden Dataset 标注的一致率 | 离线评估脚本 |

### 2.2 实现清单

#### 2.2.1 指标采集存储

**新建文件**：`nvcassistant/metrics/MetricsCollector.java`

```java
/**
 * Agent 指标采集器
 * 在 AgentLoop 各阶段采集指标，异步写入 Redis + 批量落库
 */
@Component
public class MetricsCollector {

    // 采集项
    public void recordLlmCall(String traceId, LlmCallMetrics metrics);
    // metrics 包含：inputTokens, outputTokens, latencyMs, model, success/degraded

    public void recordE2ELatency(String sessionId, long latencyMs);

    public void recordCompression(String sessionId, int beforeTokens, int afterTokens);

    public void recordToolCall(String toolName, boolean success, long latencyMs);
}
```

**新建 Entity**：`nvcassistant/metrics/AgentMetricsEntity.java`

```java
@Entity
@Table(name = "agent_metrics")
public class AgentMetricsEntity {
    @Id @GeneratedValue
    private Long id;
    private String sessionId;
    private String traceId;       // 关联 Trace（P0-2）
    private String metricType;    // TOKEN / LATENCY / COMPRESSION / TOOL_CALL
    private Map<String, Object> payload;  // JSONB 存储具体数据
    private LocalDateTime createdAt;
}
```

**异步落库**：复用 `AbstractStreamProducer/Consumer` 基建

```
MetricsCollector → MetricsStreamProducer → Redis Stream → MetricsStreamConsumer → PostgreSQL
```

**建表 SQL**：
```sql
-- 指标采集表
CREATE TABLE agent_metrics (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64),           -- 关联 Trace（P0-2 阶段填充）
    metric_type VARCHAR(32) NOT NULL,  -- TOKEN / LATENCY / COMPRESSION / TOOL_CALL
    payload JSONB NOT NULL,         -- 具体指标数据
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_metrics_session ON agent_metrics(session_id);
CREATE INDEX idx_metrics_type ON agent_metrics(metric_type);
CREATE INDEX idx_metrics_created ON agent_metrics(created_at);

-- 按月分区（可选，数据量大时启用）
-- CREATE TABLE agent_metrics_y2026m08 PARTITION OF agent_metrics
--   FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
```

**Payload 结构示例**：
```json
// TOKEN 类型
{"inputTokens": 1200, "outputTokens": 180, "model": "qwen-plus", "degraded": false}

// LATENCY 类型
{"latencyMs": 1800, "phase": "e2e", "sessionId": "sess-xxx"}

// COMPRESSION 类型
{"beforeTokens": 3200, "afterTokens": 1800, "reductionPercent": 43.75, "summary": "..."}

// TOOL_CALL 类型
{"toolName": "rag_search", "success": true, "latencyMs": 450, "resultCount": 3}
```

#### 2.2.2 指标统计服务

**新建文件**：`nvcassistant/metrics/MetricsStatsService.java`

```java
/**
 * 指标统计服务
 * 从 DB 聚合计算各项指标，支持按时间范围/用户/场景筛选
 */
@Service
public class MetricsStatsService {

    // Token 统计
    public TokenStats getTokenStats(LocalDateTime from, LocalDateTime to);
    // 返回：totalTokens, avgTokensPerSession, avgInputTokens, avgOutputTokens

    // 延迟统计
    public LatencyStats getLatencyStats(LocalDateTime from, LocalDateTime to);
    // 返回：p50, p90, p99, avgLatencyMs

    // 压缩效果
    public CompressionStats getCompressionStats(LocalDateTime from, LocalDateTime to);
    // 返回：compressionTriggerRate, avgTokenReduction, avgReductionPercent

    // 工具调用统计
    public ToolCallStats getToolCallStats(LocalDateTime from, LocalDateTime to);
    // 返回：perToolSuccessRate, avgToolLatency, failureCategories
}
```

#### 2.2.3 指标查询 API

**新建文件**：`nvcassistant/controller/MetricsController.java`

```java
@RestController
@RequestMapping("/api/nvc/metrics")
public class MetricsController {

    @GetMapping("/token")
    public Result<TokenStats> getTokenStats(
        @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime from,
        @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime to
    );

    @GetMapping("/latency")
    public Result<LatencyStats> getLatencyStats(...);

    @GetMapping("/compression")
    public Result<CompressionStats> getCompressionStats(...);

    @GetMapping("/tools")
    public Result<ToolCallStats> getToolCallStats(...);

    @GetMapping("/overview")
    public Result<MetricsOverview> getOverview(...);  // 综合概览
}
```

#### 2.2.4 评估一致性验证

**新建文件**：`nvcpractice/evaluation/EvaluationConsistencyVerifier.java`

```java
/**
 * 评估一致性验证器
 * 对比 LLM 评分与 Golden Dataset 标注，计算一致率
 */
@Service
public class EvaluationConsistencyVerifier {

    /**
     * 运行一致性验证
     * @return ConsistencyReport 包含各维度一致率、偏差分布、低分样本列表
     */
    public ConsistencyReport verify(List<GoldenSample> dataset);

    /**
     * 低分样本回流
     * 偏差 > 阈值的样本标记为需要人工审核
     */
    public List<GoldenSample> identifyOutliers(ConsistencyReport report, double threshold);
}
```

#### 2.2.5 Golden Dataset 构建

**新建目录**：`app/src/test/resources/evaluation/`

**构建流程**：
1. 用 LLM 生成 100 条 NVC 场景问答对（覆盖 4 个步骤 × 3 个难度 × 多种场景）
2. 每条包含：场景描述、用户输入、四要素评分（1-10）、评分理由
3. 人工审核修正（重点审核边界分数和特殊场景）
4. 存储为 JSON 格式，纳入版本控制

**Golden Sample 结构**：
```json
{
  "id": "GS-001",
  "scenario": "同事在会议上打断了你的发言",
  "step": "OBSERVE",
  "difficulty": "INTERMEDIATE",
  "userInput": "你刚才在会上打断了我好几次，你总是这样不尊重人",
  "expectedScores": {
    "observation": 3,
    "feeling": 4,
    "need": 5,
    "request": 2
  },
  "reasoning": "包含评判词'总是'和'不尊重人'，观察维度低；感受表达模糊；需求未明确；无具体请求",
  "category": "workplace_conflict",
  "tags": ["评判性语言", "观察与评论混淆", "需求不明确"]
}
```

**Golden Dataset 生成 Prompt**：

```
你是一个 NVC（非暴力沟通）领域的专家培训师。请为我生成 NVC 练习评估的测试数据集。

要求：
1. 生成 100 条测试样本，覆盖以下维度：
   - 4 个 NVC 步骤：观察(OBSERVE)、感受(FEELING)、需求(NEED)、请求(REQUEST)
   - 3 个难度：BEGINNER、INTERMEDIATE、ADVANCED
   - 6 种场景类别：workplace_conflict(职场冲突)、family_communication(家庭沟通)、
     romantic_relationship(亲密关系)、friend_dispute(朋友矛盾)、
     self_reflection(自我觉察)、social_scenario(社交场景)

2. 每条样本包含：
   - scenario: 场景描述（1-2句话）
   - step: 当前练习步骤
   - difficulty: 难度
   - userInput: 用户的 NVC 表达尝试（50-150字）
   - expectedScores: 四要素评分（1-10分，整数）
   - reasoning: 评分理由（说明为什么给这个分数）
   - category: 场景类别

3. 评分标准：
   - 观察(1-10)：是否客观描述事实 vs 混入评判/评论
   - 感受(1-10)：是否表达真实情绪 vs 混入想法/判断
   - 需求(1-10)：是否明确表达普世需要 vs 归因于他人
   - 请求(1-10)：是否具体可执行 vs 模糊或命令式

4. 样本分布要求：
   - 每个步骤 25 条
   - 每个难度至少 30 条
   - 每个场景类别至少 15 条
   - 包含至少 10 条"边界案例"（评分在 4-6 之间的模糊表达）
   - 包含至少 10 条"典型错误"（明确违反 NVC 原则的表达）

5. 输出格式：JSON 数组，每个元素包含上述字段

请开始生成。
```

**Golden Dataset 审核清单**：
- [ ] 每条样本的评分是否合理（与 reasoning 一致）
- [ ] 边界案例的评分是否有争议（多条对比一致性）
- [ ] 场景覆盖是否全面（6 类场景 × 4 步骤 × 3 难度）
- [ ] 用户输入是否自然（不像机器生成的模板句）
- [ ] 评分分布是否合理（不应全部集中在 7-8 分）
```

### 2.3 集成点

| 改动点 | 文件 | 说明 |
|--------|------|------|
| AgentLoop 埋点 | `AgentLoop.java` | 在 LLM 调用前后调用 MetricsCollector |
| ContextManager 埋点 | `ContextManager.java` | 压缩时记录前后 Token 数 |
| ToolExecutor 埋点 | `ToolExecutor.java` | 工具调用成功/失败/耗时 |
| NvcAgentOrchestrator | `NvcAgentOrchestrator.java` | 集成推荐服务（遗留项） |

### 2.4 遗留项收尾

#### 推荐服务集成（0.5 天）

**修改文件**：`NvcAgentOrchestrator.java`

```java
// 在 reflect() 方法中调用推荐
private void reflect(PracticeContext context) {
    // ... 现有逻辑
    // 新增：推荐下一步场景
    List<NvcScenario> recommendations = scenarioRecommendService.recommend(
        context.getUserProfile(),
        context.getEvaluations()
    );
    context.setRecommendedScenarios(recommendations);
}
```

**新增 API**：`NvcPracticeController.java`

```java
@GetMapping("/recommendations")
public Result<List<ScenarioResponse>> getRecommendations(@RequestParam String userId);
```

### 2.5 测试

| 测试项 | 方式 |
|--------|------|
| 指标采集 | 单元测试验证 MetricsCollector 正确写入 |
| 异步落库 | 集成测试验证 Redis Stream → PostgreSQL 链路 |
| 统计查询 | 单元测试验证聚合计算逻辑 |
| 评估一致性 | Golden Dataset 上运行验证，输出一致率报告 |
| 推荐集成 | 单元测试验证推荐结果非空且排序合理 |

### 2.6 文档更新

- `services/nvc-backend/AGENTS.md` — 增加指标采集说明
- `docs/decisions/` — 新增决策记录：指标采集方案选型

### 2.7 预期产出（简历素材）

**量化数据**（运行 Golden Dataset 验证后填入）：
```
- Token 消耗：平均单次对话 XXX tokens，上下文压缩后降低 XX%
- 端到端延迟：P50 XXms，P90 XXms
- 评估一致性：LLM 评分 vs Golden Dataset 一致率 XX%
- 工具调用成功率：XX%（14 个工具）
```

**简历描述**：
```
设计量化指标采集体系，覆盖 Token 消耗、端到端延迟、上下文压缩效果、
工具调用成功率 4 项核心指标；基于 100 条 Golden Dataset 验证评估一致性，
LLM 评分与标注一致率达 XX%；指标数据通过 Redis Stream 异步采集，
批量落库 PostgreSQL，支撑系统效果量化评估与持续优化。
```

---

## 三、P0-2：Trace 可观测性 + 离线评估（5-7 天）

> **分支**：`feat/trace-observability`
> **目标**：构建全链路 Trace 可观测体系 + 离线评估闭环
> **前置**：P0-1 已合并（指标采集基建可复用）

### 3.1 Trace 数据模型设计

#### 3.1.1 核心概念

```
Trace（一次完整对话）
  └── Span（一个 Agent 调用步骤）
       ├── type: INTENT_ROUTING / LLM_CALL / TOOL_CALL / COMPRESSION / EVALUATION
       ├── input: 输入数据
       ├── output: 输出数据
       ├── duration: 耗时
       ├── status: SUCCESS / DEGRADED / FAILED
       ├── tokenUsage: {input, output}
       └── metadata: 扩展字段
```

#### 3.1.2 Entity 设计

**新建文件**：`nvcassistant/trace/AgentTraceEntity.java`

```java
@Entity
@Table(name = "agent_trace", indexes = {
    @Index(name = "idx_trace_session", columnList = "sessionId"),
    @Index(name = "idx_trace_created", columnList = "createdAt")
})
public class AgentTraceEntity {
    @Id
    private String traceId;          // UUID
    private String sessionId;
    private String userId;
    private String mode;             // FREE_DIALOG / SCENARIO / STRUCTURED
    private String triggerType;      // USER_MESSAGE / TOOL_CALL / AUTO
    private int totalSpans;
    private long totalDurationMs;
    private int totalInputTokens;
    private int totalOutputTokens;
    private String finalStatus;      // SUCCESS / DEGRADED / FAILED
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "trace", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    private List<AgentSpanEntity> spans;
}
```

**新建文件**：`nvcassistant/trace/AgentSpanEntity.java`

```java
@Entity
@Table(name = "agent_span", indexes = {
    @Index(name = "idx_span_trace", columnList = "traceId"),
    @Index(name = "idx_span_type", columnList = "spanType")
})
public class AgentSpanEntity {
    @Id
    private String spanId;           // UUID
    private String traceId;
    private int sequence;            // 在 Trace 中的顺序
    private String spanType;         // INTENT_ROUTING / LLM_CALL / TOOL_CALL / COMPRESSION / EVALUATION
    private String componentName;    // 具体组件名（IntentRouter / AgentLoop / ToolExecutor 等）

    @Column(columnDefinition = "TEXT")
    private String inputPayload;     // 输入 JSON

    @Column(columnDefinition = "TEXT")
    private String outputPayload;    // 输出 JSON

    private long durationMs;
    private String status;           // SUCCESS / DEGRADED / FAILED
    private Integer inputTokens;
    private Integer outputTokens;
    private String failureReason;    // 失败原因

    @Column(columnDefinition = "JSONB")
    private String metadata;         // 扩展字段 JSON

    private LocalDateTime createdAt;
}
```

**建表 SQL**：
```sql
-- Trace 主表
CREATE TABLE agent_trace (
    trace_id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    mode VARCHAR(32) NOT NULL,          -- FREE_DIALOG / SCENARIO / STRUCTURED
    trigger_type VARCHAR(32) NOT NULL,  -- USER_MESSAGE / TOOL_CALL / AUTO
    total_spans INT NOT NULL DEFAULT 0,
    total_duration_ms BIGINT NOT NULL DEFAULT 0,
    total_input_tokens INT NOT NULL DEFAULT 0,
    total_output_tokens INT NOT NULL DEFAULT 0,
    final_status VARCHAR(16) NOT NULL,  -- SUCCESS / DEGRADED / FAILED
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trace_session ON agent_trace(session_id);
CREATE INDEX idx_trace_user ON agent_trace(user_id);
CREATE INDEX idx_trace_created ON agent_trace(created_at);
CREATE INDEX idx_trace_status ON agent_trace(final_status);

-- Span 子表
CREATE TABLE agent_span (
    span_id VARCHAR(64) PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL REFERENCES agent_trace(trace_id),
    sequence INT NOT NULL,
    span_type VARCHAR(32) NOT NULL,      -- INTENT_ROUTING / LLM_CALL / TOOL_CALL / COMPRESSION / EVALUATION / FALLBACK / METRICS
    component_name VARCHAR(64) NOT NULL, -- IntentRouter / AgentLoop / ToolExecutor / ContextManager / NvcEvaluationService / LlmFallbackHandler / MetricsCollector
    input_payload TEXT,
    output_payload TEXT,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,         -- SUCCESS / DEGRADED / FAILED
    input_tokens INT,
    output_tokens INT,
    failure_reason TEXT,
    metadata JSONB,                      -- 扩展字段
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_span_trace ON agent_span(trace_id);
CREATE INDEX idx_span_type ON agent_span(span_type);
CREATE INDEX idx_span_status ON agent_span(status);
CREATE INDEX idx_span_created ON agent_span(created_at);
```

**配置项**（`application.yml`）：
```yaml
nvc:
  trace:
    enabled: true                    # 是否开启 Trace
    max-spans-per-trace: 50          # 单个 Trace 最大 Span 数（防止异常循环）
    payload-max-length: 4096         # input/output payload 最大字符数（超过截断）
    stream-key: "nvc:trace:stream"   # Redis Stream key
    consumer-group: "trace-persist"  # 消费者组名
    batch-size: 10                   # 批量落库大小
    batch-timeout-ms: 5000           # 批量落库超时
  metrics:
    enabled: true                    # 是否开启指标采集
    stream-key: "nvc:metrics:stream"
    consumer-group: "metrics-persist"
```

### 3.2 实现清单

#### 3.2.1 Trace 采集层

**新建文件**：`nvcassistant/trace/TraceManager.java`

```java
/**
 * Trace 管理器
 * 负责 Trace/Span 的创建、关联、完成、异步落库
 */
@Component
public class TraceManager {

    // ThreadLocal 持有当前 Trace 上下文
    private static final ThreadLocal<TraceContext> CURRENT_TRACE = new ThreadLocal<>();

    /** 开启新 Trace */
    public AgentTraceEntity startTrace(String sessionId, String userId, String mode);

    /** 创建子 Span */
    public AgentSpanEntity startSpan(String spanType, String componentName);

    /** 完成 Span */
    public void endSpan(AgentSpanEntity span, String status, String output);

    /** 完成 Trace，异步落库 */
    public void endTrace(AgentTraceEntity trace);

    /** 获取当前 Trace（供 Hook/Service 使用） */
    public TraceContext current();
}
```

**新建文件**：`nvcassistant/trace/TraceStreamProducer.java` + `TraceStreamConsumer.java`

复用 `AbstractStreamProducer/Consumer` 基建，异步落库：
```
TraceManager.endTrace() → TraceStreamProducer → Redis Stream → TraceStreamConsumer → PostgreSQL
```

#### 3.2.2 各组件埋点

| 组件 | 埋点位置 | Span 类型 | 采集数据 |
|------|---------|----------|---------|
| `IntentRouter` | `route()` 方法 | INTENT_ROUTING | 输入文本、识别意图、置信度 |
| `AgentLoop` | LLM 调用前后 | LLM_CALL | prompt、response、tokens、延迟 |
| `ToolExecutor` | 工具执行前后 | TOOL_CALL | 工具名、输入、输出、延迟、成功/失败 |
| `ContextManager` | 压缩触发时 | COMPRESSION | 压缩前后 Token 数、摘要内容 |
| `NvcEvaluationService` | 评估执行时 | EVALUATION | 输入消息、评分结果、延迟 |
| `LlmFallbackHandler` | 降级触发时 | FALLBACK | 异常类型、降级策略、降级结果 |
| `MetricsCollector` | 指标写入时 | METRICS | 指标类型、数据 |

**埋点示例（AgentLoop.java）**：
```java
AgentSpanEntity llmSpan = traceManager.startSpan("LLM_CALL", "AgentLoop");
llmSpan.setInputPayload(sanitize(prompt));
try {
    ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
    llmSpan.setOutputPayload(sanitize(response.getResult().getOutput().getText()));
    llmSpan.setInputTokens(response.getMetadata().getUsage().getPromptTokens());
    llmSpan.setOutputTokens(response.getMetadata().getUsage().getCompletionTokens());
    traceManager.endSpan(llmSpan, "SUCCESS", null);
    return response;
} catch (Exception e) {
    traceManager.endSpan(llmSpan, "FAILED", e.getMessage());
    throw e;
}
```

#### 3.2.3 Trace 查询 API

**新建文件**：`nvcassistant/controller/TraceController.java`

```java
@RestController
@RequestMapping("/api/nvc/traces")
public class TraceController {

    /** 按 sessionId 查询 Trace 列表 */
    @GetMapping
    public Result<List<AgentTraceEntity>> listBySession(
        @RequestParam String sessionId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    );

    /** 查询单个 Trace 详情（含 Spans） */
    @GetMapping("/{traceId}")
    public Result<AgentTraceEntity> getDetail(@PathVariable String traceId);

    /** 按时间范围查询 Trace（支持筛选） */
    @GetMapping("/search")
    public Result<List<AgentTraceEntity>> search(
        @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime from,
        @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime to,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String mode
    );

    /** Trace 统计概览 */
    @GetMapping("/stats")
    public Result<TraceStats> getStats(
        @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime from,
        @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime to
    );
}
```

#### 3.2.4 前端 Trace 链路时间线页

**新建文件**：`frontend/src/pages/TraceDetailPage.tsx`

**页面结构**：
```
┌─────────────────────────────────────────────────────┐
│ Trace #xxx | 2026-08-04 14:30 | 场景模式 | 3.2s    │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌─────────┐                                        │
│  │ Intent  │──→ FREE_DIALOG (置信度 0.95)           │
│  │ Router  │    120ms                               │
│  └─────────┘                                        │
│      │                                              │
│      ▼                                              │
│  ┌─────────┐  ┌──────────────────────────────────┐  │
│  │  LLM    │──│ 你好！我注意到你提到了...         │  │
│  │  Call   │  │ input: 1200 tokens                │  │
│  │         │  │ output: 180 tokens                │  │
│  └─────────┘  │ 1.8s                              │  │
│      │        └──────────────────────────────────┘  │
│      ▼                                              │
│  ┌─────────┐                                        │
│  │  Tool   │──→ rag_search("NVC观察技巧")           │
│  │  Call   │    结果: 3 条文档, 450ms               │  │
│  └─────────┘                                        │
│      │                                              │
│      ▼                                              │
│  ┌─────────┐  ┌──────────────────────────────────┐  │
│  │  LLM    │──│ 观察是指客观描述你看到...          │  │
│  │  Call   │  │ input: 2400 tokens                │  │
│  │         │  │ output: 320 tokens                │  │
│  └─────────┘  │ 1.2s                              │  │
│               └──────────────────────────────────┘  │
│                                                     │
│  ┌─────────┐                                        │
│  │Eval     │──→ observation:7 feeling:8 need:6 req:5│
│  │ Trigger │    800ms                              │  │
│  └─────────┘                                        │
│                                                     │
│  总计: 3.2s | 3800 tokens | 5 spans | SUCCESS      │
└─────────────────────────────────────────────────────┘
```

**技术实现**：
- 甘特图/时间线用 CSS Grid + framer-motion 实现
- 每个 Span 是一个可点击的卡片，展开显示 input/output 详情
- 时间轴按比例缩放，Span 之间有连线表示依赖关系
- 底部汇总栏显示总耗时、总 Token、Span 数量、状态

**新建文件清单**：

| 文件 | 用途 |
|------|------|
| `frontend/src/api/trace.ts` | Trace API 封装 |
| `frontend/src/pages/TraceListPage.tsx` | Trace 列表页（按 session 查看） |
| `frontend/src/pages/TraceDetailPage.tsx` | Trace 详情页（链路时间线） |
| `frontend/src/components/nvc/TraceTimeline.tsx` | 时间线主组件 |
| `frontend/src/components/nvc/TraceSpanCard.tsx` | 单个 Span 卡片 |
| `frontend/src/components/nvc/TraceSummaryBar.tsx` | 底部汇总栏 |
| `frontend/src/components/nvc/TraceFilterBar.tsx` | 筛选栏（按状态/类型/时间） |
| `frontend/src/types/trace.ts` | TypeScript 类型定义 |

**trace.ts API 封装**：
```typescript
import { request } from './request';

export interface AgentTrace {
  traceId: string;
  sessionId: string;
  userId: string;
  mode: string;
  triggerType: string;
  totalSpans: number;
  totalDurationMs: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  finalStatus: 'SUCCESS' | 'DEGRADED' | 'FAILED';
  createdAt: string;
  spans: AgentSpan[];
}

export interface AgentSpan {
  spanId: string;
  sequence: number;
  spanType: string;
  componentName: string;
  inputPayload: string;
  outputPayload: string;
  durationMs: number;
  status: string;
  inputTokens?: number;
  outputTokens?: number;
  failureReason?: string;
  metadata?: Record<string, unknown>;
}

export interface TraceStats {
  totalTraces: number;
  avgDurationMs: number;
  avgTokensPerTrace: number;
  successRate: number;
  topFailureReasons: { reason: string; count: number }[];
}

export const traceApi = {
  listBySession: (sessionId: string, page = 0, size = 20) =>
    request.get<AgentTrace[]>(`/api/nvc/traces`, { params: { sessionId, page, size } }),

  getDetail: (traceId: string) =>
    request.get<AgentTrace>(`/api/nvc/traces/${traceId}`),

  search: (params: { from: string; to: string; status?: string; mode?: string }) =>
    request.get<AgentTrace[]>(`/api/nvc/traces/search`, { params }),

  getStats: (from: string, to: string) =>
    request.get<TraceStats>(`/api/nvc/traces/stats`, { params: { from, to } }),
};
```

**前端路由新增**：
```typescript
// constants/routes.ts 新增
{
  path: '/nvc/traces',
  element: <TraceListPage />,
},
{
  path: '/nvc/traces/:traceId',
  element: <TraceDetailPage />,
}
```

**TraceListPage 页面结构**：
```
┌─────────────────────────────────────────────────────┐
│ 🔍 Trace 查询                                       │
│                                                     │
│ Session ID: [sess-xxx    ]  状态: [全部 ▼]          │
│ 时间范围: [2026-08-01] ~ [2026-08-04]  [查询]       │
├─────────────────────────────────────────────────────┤
│ Trace ID     | 时间      | 模式   | Spans | 耗时  | 状态   │
│ tr-abc123    | 14:30:22  | 场景   | 5     | 3.2s  | ✅     │
│ tr-def456    | 14:28:15  | 自由   | 4     | 2.8s  | ⚠️降级 │
│ tr-ghi789    | 14:25:01  | 结构化 | 6     | 4.1s  | ✅     │
│ ...                                                │
│                                                     │
│ 统计：共 42 条 | 成功率 95.2% | 平均耗时 2.9s       │
└─────────────────────────────────────────────────────┘
```

#### 3.2.5 离线评估体系

**新建文件**：`nvcassistant/evaluation/OfflineEvaluationService.java`

```java
/**
 * 离线评估服务
 * 基于 Trace 数据批量评估系统质量
 */
@Service
public class OfflineEvaluationService {

    /**
     * 运行离线评估
     * @param from 评估起始时间
     * @param to 评估结束时间
     * @return EvaluationReport 包含 5 个维度的评分
     */
    public EvaluationReport evaluate(LocalDateTime from, LocalDateTime to);
}
```

**5 个评估维度详细算法**：

| 维度 | 评估方式 | 具体算法 | 指标 |
|------|---------|---------|------|
| **意图路由准确性** | 从 Trace 中提取 IntentRouter 的识别结果，对比用户实际行为 | 1. 提取所有 INTENT_ROUTING Span<br>2. 对比识别意图 vs 实际触发的工具/对话<br>3. 计算准确率 = 正确路由数 / 总路由数 | 准确率、误分类分布、各意图的精确率/召回率 |
| **工具调用稳定性** | 从 Trace 中统计 TOOL_CALL Span | 1. 按工具名分组统计<br>2. 成功率 = SUCCESS / (SUCCESS + FAILED)<br>3. 失败原因聚类 | 每工具成功率、失败原因 Top5、平均耗时 |
| **性能** | 从 Trace 中聚合时间戳 | 1. 端到端延迟 = 最后一个 Span 结束时间 - Trace 开始时间<br>2. LLM 延迟 = 所有 LLM_CALL Span 耗时之和<br>3. 计算 P50/P90/P99 | P50/P90/P99、延迟分布直方图 |
| **成本** | 从 Trace 中聚合 Token | 1. 每 Trace 的 Token = 所有 Span 的 inputTokens + outputTokens 之和<br>2. 按模式/场景分组统计 | 平均 Token/会话、Token 分布、成本趋势 |
| **对话质量** | LLM-as-Judge 评分 | 1. 对 Golden Dataset 每条样本生成系统回复<br>2. 用 Judge LLM 从 4 个维度评分（1-5）<br>3. 聚合计算均分和分布 | 相关性/忠实度/完整性/教学效果均分 |

**对话质量评估（LLM-as-Judge）**：

```java
/**
 * LLM-as-Judge 对话质量评估
 * 三步流程：
 * 1. 用当前系统对 Golden Dataset 中的用户输入生成 Agent 回复
 * 2. 用另一个 LLM（Judge）对 Agent 回复评分
 * 3. 聚合评分，计算对话质量指标
 */
public ConversationQualityReport evaluateConversationQuality(List<GoldenSample> dataset);
```

**Step 1：系统回复生成**：
```java
// 对每条 Golden Sample，模拟一次 Agent 对话
for (GoldenSample sample : dataset) {
    // 构建最小上下文
    PracticeContext context = buildMinimalContext(sample.getScenario(), sample.getStep());
    // 调用 Agent 生成回复
    String agentReply = agentChatService.chat(sample.getUserInput(), context);
    sample.setActualReply(agentReply);
}
```

**Step 2：LLM-as-Judge 评分 Prompt**：
```
你是一个 NVC（非暴力沟通）领域的专家评审。请对以下 Agent 的回复质量进行评分。

## 背景
- 场景：{scenario}
- 当前练习步骤：{step}
- 用户输入：{userInput}
- Agent 回复：{agentReply}

## 评分维度（每项 1-5 分）

1. **相关性（Relevance）**：Agent 的回复是否针对用户的表达内容？
   - 5分：完全针对用户表达，精准回应
   - 3分：大体相关，但有偏离
   - 1分：完全无关或答非所问

2. **忠实度（Faithfulness）**：Agent 的回复是否基于 NVC 理论，不包含错误信息？
   - 5分：完全符合 NVC 理论，无错误
   - 3分：基本正确，但有模糊或不精确之处
   - 1分：包含明显的 NVC 理论错误

3. **完整性（Completeness）**：Agent 的回复是否充分引导用户？
   - 5分：充分引导，有具体建议和示例
   - 3分：有一定引导但不够具体
   - 1分：只是泛泛而谈，无实际引导

4. **教学效果（Teaching Effectiveness）**：Agent 的回复是否帮助用户理解 NVC 原则？
   - 5分：用户能从回复中学到 NVC 知识
   - 3分：有一定教学但不够深入
   - 1分：无教学价值

## 输出格式（JSON）
{
  "relevance": {score}, "relevance_reason": "...",
  "faithfulness": {score}, "faithfulness_reason": "...",
  "completeness": {score}, "completeness_reason": "...",
  "teaching": {score}, "teaching_reason": "...",
  "overall": {average_score},
  "suggestion": "改进建议"
}
```

**Step 3：聚合评分**：
```java
public record ConversationQualityReport(
    double avgRelevance,        // 相关性均分
    double avgFaithfulness,     // 忠实度均分
    double avgCompleteness,     // 完整性均分
    double avgTeaching,         // 教学效果均分
    double overallScore,        // 综合均分
    List<SampleScore> scores,   // 每条样本的详细评分
    List<LowScoreSample> lowScores,  // 低分样本（overall < 3.0）
    String summary              // 综合评语
);
```

**评估报告结构**：
```java
public record EvaluationReport(
    String reportId,
    LocalDateTime evaluatedAt,
    int totalTraces,
    IntentRoutingAccuracy intentRouting,    // 意图路由准确率
    ToolCallStability toolCallStability,    // 工具调用稳定性
    PerformanceMetrics performance,          // 性能指标
    CostMetrics cost,                        // 成本指标
    ConversationQuality quality,             // 对话质量
    List<OutlierSample> outliers,            // 异常样本（低分/高偏差）
    String summary                           // 综合评语
);
```

#### 3.2.6 低分样本回流

**新建文件**：`nvcassistant/evaluation/OutlierAnalyzer.java`

```java
/**
 * 异常样本分析器
 * 从评估报告中提取低分/高偏差样本，标记为待优化
 */
@Service
public class OutlierAnalyzer {

    /**
     * 提取异常样本
     * 规则：
     * - 对话质量评分 < 3.0（满分 5.0）
     * - 意图路由误分类
     * - 工具调用连续失败 > 2 次
     * - 端到端延迟 > P95
     */
    public List<OutlierSample> analyze(EvaluationReport report);

    /**
     * 生成优化建议
     * 基于异常类型，给出具体优化方向
     */
    public List<OptimizationSuggestion> suggest(List<OutlierSample> outliers);
}
```

### 3.3 遗留项收尾

#### 语音模块完善（1-2 天）

**5 个待完善项的具体实现**：

**① PracticeContext 构建**
- 修改文件：`NvcVoiceService.java`
- 当前问题：语音模块的 PracticeContext 缺少用户档案和 RAG 知识
- 实现：
```java
// 注入依赖
private final NvcProfileService profileService;
private final NvcRagService ragService;

// 在 createSession() 中构建完整 context
PracticeContext context = PracticeContext.builder()
    .sessionId(session.getSessionId())
    .userId(userId)
    .mode(NvcPracticeMode.VOICE)
    .userProfileSummary(formatProfile(profileService.getOrCreateProfile(userId)))
    .ragContext(ragService.retrieveRelevantKnowledge("", userId))  // 初始为空，后续从 ASR 结果检索
    .scenarioDescription(scenario.getDescription())
    .build();
```

**② AgentConfig 集成**
- 修改文件：`NvcVoiceLlmService.java`
- 当前问题：语音 LLM 调用使用硬编码 prompt，未从 DB 读取 Agent 配置
- 实现：
```java
// 注入 NvcAgentConfigService
private final NvcAgentConfigService agentConfigService;

// 在 chat() 方法中
NvcAgentConfigEntity config = agentConfigService.getConfig(NvcAgentScene.VOICE_PRACTICE);
String systemPrompt = config.getSystemPrompt();
// 将 systemPrompt 注入 ChatClient
```

**③ 场景描述注入**
- 修改文件：`NvcVoiceService.java`
- 当前问题：语音会话创建时未加载场景描述
- 实现：
```java
// 在 createSession() 中
if (scenarioId != null) {
    NvcScenarioEntity scenario = scenarioService.getById(scenarioId);
    session.setScenarioDescription(scenario.getDescription());
    session.setScenarioTitle(scenario.getTitle());
}
// 在 VoicePipelineCoordinator 的 LLM 调用前，将场景描述注入 prompt
```

**④ 前端适配**
- 修改文件：`NvcVoicePage.tsx`
- 当前问题：语音页面可能未正确展示场景信息和评估结果
- 检查项：
  - [ ] 创建语音会话时是否可以选择场景
  - [ ] 语音对话中是否展示当前步骤指示
  - [ ] 评估结果是否正确展示
  - [ ] 语音会话结束后是否跳转到报告页

**⑤ Trace 埋点**
- 修改文件：`NvcVoiceWebSocketHandler.java`
- 在语音链路的关键节点接入 Trace：
  - ASR 识别完成 → 记录识别结果和耗时
  - LLM 调用 → 记录 prompt/response/tokens
  - TTS 合成 → 记录合成结果和耗时
  - 评估触发 → 记录评估结果

### 3.4 测试

| 测试项 | 方式 |
|--------|------|
| Trace 采集 | 单元测试验证 Span 创建/关联/完成 |
| 异步落库 | 集成测试验证 Redis Stream → PostgreSQL |
| Trace 查询 | 集成测试验证按 session/时间范围查询 |
| 离线评估 | 在 Golden Dataset 上运行完整评估流程 |
| 前端时间线 | 手动测试 + 截图验证 UI 效果 |
| 语音完善 | 手动测试语音练习全流程 |

### 3.5 文档更新

- `services/nvc-backend/AGENTS.md` — 增加 Trace 体系说明
- `docs/decisions/` — 新增决策记录：Trace 数据模型设计、异步落库方案
- `docs/计划安排/` — 更新计划进度

### 3.6 预期产出（简历素材）

**数据产出**（运行离线评估后填入）：
```
- Trace 覆盖：7 个关键节点埋点，支撑完整链路回放
- 意图路由准确率：XX%
- 工具调用成功率：XX%（14 个工具）
- 对话质量评分：XX/5.0（LLM-as-Judge）
- 端到端延迟：P50 XXms / P90 XXms
- Token 消耗：平均 XX tokens/session
```

**简历描述**：
```
构建全链路 Trace 可观测体系，在 Agent 调用链路 7 个关键节点（意图路由、
LLM 推理、工具调用、上下文压缩、评估触发、降级处理、指标采集）埋点，
记录输入/输出/耗时/Token，通过 Redis Stream 异步落库 PostgreSQL，
支撑链路逐步回放；设计前端链路时间线可视化页面，直观展示 Agent 调用时序关系。

基于 Trace 数据构建离线评估体系，覆盖意图路由准确率、工具调用稳定性、
端到端性能、Token 消耗、对话质量 5 个维度，其中对话质量采用 LLM-as-Judge
在 100 条 Golden Dataset 上自动评分（相关性/忠实度/完整性）；
低分样本自动标记回流，形成 Trace→评估→优化的完整闭环。
```

---

## 四、执行时间线

### 总览

```
Phase A: feat/fallback-degradation (1-2 天)
  Day 1: LlmFallbackHandler + 对话降级模板 + 评估降级 + 场景降级
  Day 2: 集成测试 + 前端降级提示 + 文档更新 + 合并 master

Phase B: feat/metrics-collection (2-3 天)
  Day 3: MetricsCollector + AgentMetricsEntity + 异步落库
  Day 4: MetricsStatsService + MetricsController + 推荐集成
  Day 5: Golden Dataset 构建 + 评估一致性验证 + 文档更新 + 合并 master

Phase C: feat/trace-observability (5-7 天)
  Day 6-7: TraceManager + AgentTraceEntity/SpanEntity + 各组件埋点
  Day 8-9: Trace 查询 API + 前端 TraceTimeline 页面
  Day 10-11: 离线评估服务 + 5 维度评估 + 低分样本回流
  Day 12: 语音模块遗留完善
  Day 13-14: 集成测试 + 文档更新 + 合并 master
```

### 分支合并顺序

```
master
  │
  ├── feat/fallback-degradation ──→ 合并 (Day 2)
  │
  ├── feat/metrics-collection ────→ 合并 (Day 5)
  │
  └── feat/trace-observability ───→ 合并 (Day 14)
```

每个分支独立可合并，互不阻塞。如果时间紧张，Phase A 和 B 可以先合并，Phase C 后续补充。

---

## 五、最终简历描述（完整版）

```
#### NVC 非暴力沟通练习助手 — 多 Agent 协同 AI 练习平台

`Spring Boot 4.0` `Java 21` `Spring AI 2.0` `React 18` `PostgreSQL` `pgvector` `Redis` `SSE` `WebSocket`

项目描述：
独立设计开发的 NVC 练习平台，通过多 Agent 协同调度实现三种练习模式，
覆盖文字+语音双模态，配套实时评估、用户画像与 RAG 知识增强。

核心职责：

1. 多 Agent 协同调度架构：
   设计编排中枢 + 11 个专职 Agent 的分层调度架构，Agent 配置支持
   DB + Redis 缓存热更新，运行时修改即时生效。

2. 自建 Agent Loop 与 Hook 链：
   自建 Agent 执行引擎，通过 7 层 Hook 链实现工具调用全生命周期管控；
   设计上下文压缩策略，>20 轮对话自动触发 LLM 摘要压缩。

3. LLM 全链路 Fallback 降级：
   设计分层降级体系，对话场景采用 NVC 引导话术模板接管，评估场景
   采用关键词匹配粗略评分并标记降级，场景生成降级为种子库随机分配，
   保障全链路任一 LLM 异常时业务流程正常不中断。

4. 实时流式评估引擎：
   异步触发 LLM 四要素结构化评分，采用 Redis Stream 解耦评估与
   对话主链路，评估结果驱动 Agent 调度决策，练习结束后生成综合报告。

5. 全链路 Trace 可观测体系：
   在 Agent 调用链路 7 个关键节点埋点，记录输入/输出/耗时/Token，
   异步落库支撑链路逐步回放；设计前端链路时间线可视化页面。

6. 量化指标与离线评估闭环：
   采集 Token 消耗、端到端延迟、上下文压缩效果等 4 项核心指标；
   基于 100 条 Golden Dataset 构建 5 维度离线评估体系（意图路由准确率、
   工具调用稳定性、性能、成本、对话质量），低分样本自动回流优化。

7. RAG 知识库 + 个性化检索：
   构建 NVC 领域向量知识库，根据用户能力画像薄弱维度动态调整检索权重，
   实现"千人千面"的知识增强对话。

技术栈：Spring Boot 4.0 / Java 21 / Spring AI 2.0 / PostgreSQL / pgvector /
Redis / React 18 / TypeScript / TailwindCSS 4 / Docker
```

---

## 六、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| Trace 数据量增长快 | DB 存储压力 | 按月分区表 + 定期归档 |
| LLM-as-Judge 评分不稳定 | 评估结果不可复现 | 固定 seed + 多次评分取均值 |
| 前端时间线页开发耗时 | 可能超出 2-3 天 | 先做简化版（纯列表），后续迭代为甘特图 |
| 语音模块遗留项比预期复杂 | 拖延 Phase C 进度 | 语音完善移到 Phase C 最后，不阻塞 Trace 主线 |

---

## 七、完整文件清单

### P0-3 新增/修改文件（feat/fallback-degradation）

| 类型 | 文件路径 | 说明 |
|------|---------|------|
| **新建** | `nvcassistant/fallback/LlmFailureType.java` | 异常类型枚举 |
| **新建** | `nvcassistant/fallback/LlmCallContext.java` | 调用上下文（session/trace/组件信息） |
| **新建** | `nvcassistant/fallback/LlmFallbackHandler.java` | 统一降级处理器（重试 + 降级） |
| **新建** | `nvcassistant/fallback/DialogFallbackTemplates.java` | 对话降级模板库（27 条模板） |
| **新建** | `nvcpractice/fallback/EvaluationFallbackService.java` | 评估降级服务（关键词评分） |
| **新建** | `nvcpractice/fallback/KeywordScorer.java` | 关键词评分器（四维度评分算法） |
| **修改** | `nvcassistant/AgentLoop.java` | LLM 调用接入 LlmFallbackHandler |
| **修改** | `nvcpractice/NvcEvaluationService.java` | 评估调用接入降级 |
| **修改** | `nvcpractice/tools/ScenarioGenerateTool.java` | 场景生成 catch 降级 |
| **修改** | `nvcprofile/NvcCommunicationAnalysisService.java` | 分析调用接入降级 |
| **修改** | `nvcpractice/model/NvcEvaluationEntity.java` | 新增 degraded 字段 |
| **修改** | `frontend/src/utils/sse.ts` | SSE metadata 解析 degraded 字段 |
| **修改** | `frontend/src/components/nvc/NvcChatPanel.tsx` | 降级模式提示条 |
| **新建** | `app/src/test/java/.../fallback/LlmFallbackHandlerTest.java` | 降级处理器测试 |
| **新建** | `app/src/test/java/.../fallback/DialogFallbackTemplatesTest.java` | 模板测试 |
| **新建** | `app/src/test/java/.../fallback/EvaluationFallbackServiceTest.java` | 评估降级测试 |

### P0-1 新增/修改文件（feat/metrics-collection）

| 类型 | 文件路径 | 说明 |
|------|---------|------|
| **新建** | `nvcassistant/metrics/MetricsCollector.java` | 指标采集器 |
| **新建** | `nvcassistant/metrics/AgentMetricsEntity.java` | 指标 Entity |
| **新建** | `nvcassistant/metrics/AgentMetricsRepository.java` | 指标 Repository |
| **新建** | `nvcassistant/metrics/MetricsStreamProducer.java` | 异步生产者 |
| **新建** | `nvcassistant/metrics/MetricsStreamConsumer.java` | 异步消费者 |
| **新建** | `nvcassistant/metrics/MetricsStatsService.java` | 统计服务 |
| **新建** | `nvcassistant/metrics/dto/TokenStats.java` | Token 统计 DTO |
| **新建** | `nvcassistant/metrics/dto/LatencyStats.java` | 延迟统计 DTO |
| **新建** | `nvcassistant/metrics/dto/CompressionStats.java` | 压缩统计 DTO |
| **新建** | `nvcassistant/metrics/dto/ToolCallStats.java` | 工具调用统计 DTO |
| **新建** | `nvcassistant/metrics/dto/MetricsOverview.java` | 综合概览 DTO |
| **新建** | `nvcassistant/controller/MetricsController.java` | 指标 API |
| **新建** | `nvcpractice/evaluation/EvaluationConsistencyVerifier.java` | 评估一致性验证 |
| **新建** | `nvcpractice/evaluation/ConsistencyReport.java` | 一致性报告 DTO |
| **新建** | `nvcpractice/evaluation/GoldenSample.java` | Golden Dataset 样本结构 |
| **新建** | `app/src/test/resources/evaluation/golden-dataset.json` | 100 条 Golden Dataset |
| **修改** | `nvcassistant/AgentLoop.java` | LLM 调用埋点 MetricsCollector |
| **修改** | `nvcassistant/ContextManager.java` | 压缩埋点 |
| **修改** | `nvcassistant/ToolExecutor.java` | 工具调用埋点 |
| **修改** | `nvcpractice/NvcAgentOrchestrator.java` | 集成推荐服务 |
| **修改** | `nvcpractice/controller/NvcPracticeController.java` | 新增推荐 API |
| **新建** | `app/src/test/java/.../metrics/MetricsCollectorTest.java` | 采集器测试 |
| **新建** | `app/src/test/java/.../metrics/MetricsStatsServiceTest.java` | 统计服务测试 |
| **新建** | `app/src/test/java/.../evaluation/EvaluationConsistencyVerifierTest.java` | 一致性测试 |

### P0-2 新增/修改文件（feat/trace-observability）

| 类型 | 文件路径 | 说明 |
|------|---------|------|
| **新建** | `nvcassistant/trace/AgentTraceEntity.java` | Trace Entity |
| **新建** | `nvcassistant/trace/AgentSpanEntity.java` | Span Entity |
| **新建** | `nvcassistant/trace/AgentTraceRepository.java` | Trace Repository |
| **新建** | `nvcassistant/trace/AgentSpanRepository.java` | Span Repository |
| **新建** | `nvcassistant/trace/TraceContext.java` | Trace 上下文（ThreadLocal） |
| **新建** | `nvcassistant/trace/TraceManager.java` | Trace 管理器 |
| **新建** | `nvcassistant/trace/TraceStreamProducer.java` | 异步生产者 |
| **新建** | `nvcassistant/trace/TraceStreamConsumer.java` | 异步消费者 |
| **新建** | `nvcassistant/trace/TraceStatsService.java` | Trace 统计服务 |
| **新建** | `nvcassistant/trace/dto/TraceStats.java` | 统计 DTO |
| **新建** | `nvcassistant/controller/TraceController.java` | Trace API |
| **新建** | `nvcassistant/evaluation/OfflineEvaluationService.java` | 离线评估服务 |
| **新建** | `nvcassistant/evaluation/ConversationQualityEvaluator.java` | LLM-as-Judge 评估 |
| **新建** | `nvcassistant/evaluation/OutlierAnalyzer.java` | 异常样本分析 |
| **新建** | `nvcassistant/evaluation/dto/EvaluationReport.java` | 评估报告 DTO |
| **新建** | `nvcassistant/evaluation/dto/ConversationQualityReport.java` | 对话质量报告 |
| **新建** | `nvcassistant/evaluation/dto/OutlierSample.java` | 异常样本 DTO |
| **新建** | `nvcassistant/evaluation/dto/OptimizationSuggestion.java` | 优化建议 DTO |
| **修改** | `nvcassistant/IntentRouter.java` | 埋点 Trace |
| **修改** | `nvcassistant/AgentLoop.java` | 埋点 Trace |
| **修改** | `nvcassistant/ToolExecutor.java` | 埋点 Trace |
| **修改** | `nvcassistant/ContextManager.java` | 埋点 Trace |
| **修改** | `nvcassistant/fallback/LlmFallbackHandler.java` | 埋点 Trace |
| **修改** | `nvcpractice/NvcEvaluationService.java` | 埋点 Trace |
| **修改** | `nvcassistant/metrics/MetricsCollector.java` | 埋点 Trace |
| **修改** | `nvcvoice/NvcVoiceWebSocketHandler.java` | 语音链路 Trace 埋点 |
| **修改** | `nvcvoice/NvcVoiceService.java` | PracticeContext 完善 |
| **修改** | `nvcvoice/NvcVoiceLlmService.java` | AgentConfig 集成 |
| **修改** | `frontend/src/constants/routes.ts` | 新增 Trace 路由 |
| **新建** | `frontend/src/types/trace.ts` | Trace 类型定义 |
| **新建** | `frontend/src/api/trace.ts` | Trace API 封装 |
| **新建** | `frontend/src/pages/TraceListPage.tsx` | Trace 列表页 |
| **新建** | `frontend/src/pages/TraceDetailPage.tsx` | Trace 详情页（时间线） |
| **新建** | `frontend/src/components/nvc/TraceTimeline.tsx` | 时间线主组件 |
| **新建** | `frontend/src/components/nvc/TraceSpanCard.tsx` | Span 卡片组件 |
| **新建** | `frontend/src/components/nvc/TraceSummaryBar.tsx` | 汇总栏组件 |
| **新建** | `frontend/src/components/nvc/TraceFilterBar.tsx` | 筛选栏组件 |
| **新建** | `app/src/test/java/.../trace/TraceManagerTest.java` | Trace 管理器测试 |
| **新建** | `app/src/test/java/.../evaluation/OfflineEvaluationServiceTest.java` | 离线评估测试 |
| **新建** | `app/src/test/java/.../evaluation/ConversationQualityEvaluatorTest.java` | 对话质量测试 |

### 文件统计

| 分支 | 新建文件 | 修改文件 | 合计 |
|------|---------|---------|------|
| feat/fallback-degradation | 9 | 7 | 16 |
| feat/metrics-collection | 14 | 5 | 19 |
| feat/trace-observability | 28 | 11 | 39 |
| **总计** | **51** | **23** | **74** |
