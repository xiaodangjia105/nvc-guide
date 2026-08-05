# Phase 2 Trace 可观测体系设计文档

> 创建时间：2026-08-04
> 分支：feat/trace-observability
> 状态：已完成

---

## 一、背景与目标

### 现状

系统缺少全链路追踪能力，无法回答"一次对话经过了哪些组件"、"每个组件耗时多少"、"哪个环节失败了"等问题。面试需要展示可观测性设计能力。

### 目标

1. 建立 Trace/Span 两级追踪体系，记录一次用户交互的全链路信息
2. 7 个组件直接注入 TraceManager 埋点（不用 AOP）
3. 异步落库（Redis Stream），不影响对话主链路性能
4. 提供 4 个查询 API + 离线评估 API
5. 前端完整甘特图时间线（CSS Grid + framer-motion）

---

## 二、架构设计

### 数据模型

```
AgentTraceEntity (1) ──→ (N) AgentSpanEntity

AgentTrace:
  - traceId (PK)
  - sessionId
  - userId
  - mode (FREE_DIALOG / SCENARIO / STRUCTURED)
  - triggerType (USER_MESSAGE / TOOL_CALL / AUTO)
  - totalSpans
  - totalDurationMs
  - totalInputTokens / totalOutputTokens
  - finalStatus (SUCCESS / DEGRADED / FAILED)

AgentSpan:
  - spanId (PK)
  - traceId (FK)
  - sequence (执行顺序)
  - spanType (INTENT_ROUTING / LLM_CALL / TOOL_CALL / COMPRESSION / EVALUATION)
  - componentName (IntentRouter / AgentLoop / ToolExecutor / ContextManager)
  - inputPayload / outputPayload
  - inputTokens / outputTokens
  - status (SUCCESS / DEGRADED / FAILED)
  - failureReason
  - startTime / endTime / durationMs
```

### 采集流程

```
AgentLoop / ToolExecutor / ContextManager / IntentRouter
  → TraceManager.startTrace()           # 开启 Trace
  → TraceManager.startSpan()            # 创建 Span
  → 执行操作
  → TraceManager.endSpan()              # 完成 Span
  → TraceManager.endTrace()             # 完成 Trace
    → TraceStreamProducer.send()        # Redis Stream
      → TraceStreamConsumer 消费
        → AgentTraceRepository.save()   # PostgreSQL
```

### 模块结构

```
nvcassistant/trace/
├── TraceManager.java                 # Trace 管理器（ThreadLocal）
├── TraceContext.java                 # Trace 上下文
├── AgentTraceEntity.java            # Trace 实体
├── AgentSpanEntity.java             # Span 实体
├── AgentTraceRepository.java        # Trace Repository
├── AgentSpanRepository.java         # Span Repository
├── TraceStreamProducer.java         # Redis Stream 生产者
├── TraceStreamConsumer.java         # Redis Stream 消费者
├── TraceStatsService.java           # 统计服务
└── dto/
    └── TraceStats.java
```

---

## 三、关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 追踪方式 | 直接注入 TraceManager | 比 AOP 更精确，可记录输入/输出 payload |
| 上下文传递 | ThreadLocal | 单次请求内组件共享上下文 |
| 异步机制 | Redis Stream | 复用现有基础设施 |
| payload 截断 | 4096 字符 | 避免大 payload 占用存储 |
| 状态传播 | Span 失败 → Trace 标记 FAILED | 级联失败状态 |

### TraceManager 核心 API

```java
// 开启 Trace
AgentTraceEntity trace = traceManager.startTrace(sessionId, userId, mode);

// 创建 Span
AgentSpanEntity span = traceManager.startSpan("LLM_CALL", "AgentLoop");
span.setInputPayload(prompt);
// ... 执行操作 ...
span.setOutputPayload(response);
span.setInputTokens(1200);
span.setOutputTokens(180);
traceManager.endSpan(span, "SUCCESS", null);

// 完成 Trace
traceManager.endTrace(trace);
```

### 埋点组件

| 组件 | Span 类型 | 采集内容 |
|------|----------|---------|
| AgentLoop | LLM_CALL | prompt、response、tokens、延迟 |
| ToolExecutor | TOOL_CALL | toolName、args、result、延迟 |
| ContextManager | COMPRESSION | beforeTokens、afterTokens、summary |
| IntentRouter | INTENT_ROUTING | userMessage、matchResult、reason |

---

## 四、API 设计

### 端点列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/nvc/traces` | 按 sessionId 查询 Trace 列表 |
| GET | `/api/nvc/traces/{traceId}` | 查询单个 Trace 详情（含 Spans） |
| GET | `/api/nvc/traces/search` | 按时间范围查询（支持 status/mode 筛选） |
| GET | `/api/nvc/traces/stats` | Trace 统计概览 |
| POST | `/api/nvc/traces/evaluate` | 运行离线评估（手动触发） |

### 离线评估

```java
@Service
public class OfflineEvaluationService {
    // 4 维度评估：
    // 1. 意图路由准确率（从 INTENT_ROUTING Span 统计）
    // 2. 工具调用稳定性（从 TOOL_CALL Span 统计）
    // 3. 端到端性能（从 LATENCY 指标统计）
    // 4. Token 消耗（从 TOKEN 指标统计）
}
```

---

## 五、前端实现

### 文件清单

```
frontend/src/
├── api/trace.ts                      # API 模块
├── types/trace.ts                    # 类型定义
├── pages/
│   ├── TraceListPage.tsx             # Trace 列表页
│   └── TraceDetailPage.tsx           # Trace 详情页
└── components/nvc/
    ├── TraceTimeline.tsx             # 甘特图时间线
    ├── TraceSpanCard.tsx             # Span 卡片
    ├── TraceSummaryBar.tsx           # 统计摘要栏
    └── TraceFilterBar.tsx            # 筛选栏
```

### 甘特图设计

- CSS Grid 布局，横轴为时间
- 每个 Span 为一个色块，宽度按比例缩放
- 颜色编码：绿色=SUCCESS，黄色=DEGRADED，红色=FAILED
- 点击 Span 展开详情（输入/输出 payload）
- framer-motion 动画过渡

---

## 六、文件清单

### 新建文件（32 个）

```
# 后端（12 个）
app/src/main/java/nvc/guide/modules/nvcassistant/trace/
├── TraceManager.java
├── TraceContext.java
├── AgentTraceEntity.java
├── AgentSpanEntity.java
├── AgentTraceRepository.java
├── AgentSpanRepository.java
├── TraceStreamProducer.java
├── TraceStreamConsumer.java
├── TraceStatsService.java
└── dto/TraceStats.java

app/src/main/java/nvc/guide/modules/nvcassistant/controller/TraceController.java
app/src/main/java/nvc/guide/modules/nvcassistant/evaluation/OfflineEvaluationService.java
app/src/main/java/nvc/guide/modules/nvcassistant/evaluation/dto/EvaluationReport.java

# 前端（8 个）
frontend/src/api/trace.ts
frontend/src/types/trace.ts
frontend/src/pages/TraceListPage.tsx
frontend/src/pages/TraceDetailPage.tsx
frontend/src/components/nvc/TraceTimeline.tsx
frontend/src/components/nvc/TraceSpanCard.tsx
frontend/src/components/nvc/TraceSummaryBar.tsx
frontend/src/components/nvc/TraceFilterBar.tsx
```

### 修改文件（4 个）

```
AgentLoop.java            — 增加 TraceManager 埋点（LLM_CALL Span）
ToolExecutor.java         — 增加 TraceManager 埋点（TOOL_CALL Span）
ContextManager.java       — 增加 TraceManager 埋点（COMPRESSION Span）
IntentRouter.java         — 增加 TraceManager 埋点（INTENT_ROUTING Span）
```

---

## 七、测试要求

- TraceManager 单元测试：Trace/Span 生命周期、ThreadLocal 隔离
- OfflineEvaluationService 单元测试：4 维度评估计算
- 集成测试：对话 → Trace 采集 → 落库 → API 查询

---

## 八、验收标准

```
□ Trace 采集
  □ 7 个组件埋点正常工作
  □ Trace/Span 正确关联
  □ 状态级联（Span 失败 → Trace FAILED）
  □ 异步落库不影响对话性能

□ 查询 API
  □ 4 个查询端点正常返回
  □ 支持时间范围、状态、模式筛选
  □ 分页查询正常

□ 离线评估
  □ 4 维度评估计算正确
  □ 手动触发 API 正常工作
  □ 评估报告格式正确

□ 前端
  □ Trace 列表页正常展示
  □ 甘特图时间线正确渲染
  □ Span 详情展开/折叠
  □ 筛选和搜索功能正常

□ 端到端
  □ 对话 → Trace 采集 → 落库 → 前端展示
  □ 真实数据可用于面试演示
```
