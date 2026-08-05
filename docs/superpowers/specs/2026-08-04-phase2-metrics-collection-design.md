# Phase 2 量化指标采集体系设计文档

> 创建时间：2026-08-04
> 分支：feat/metrics-collection
> 状态：已完成

---

## 一、背景与目标

### 现状

系统缺少量化指标采集，无法回答"Agent 调用消耗多少 Token"、"工具调用成功率多少"、"端到端延迟多少"等问题。面试准备需要真实数字支撑。

### 目标

1. 建立 4 项核心指标采集：Token 消耗、端到端延迟、上下文压缩效果、工具调用统计
2. 异步落库（Redis Stream），不影响对话主链路性能
3. 提供 5 个统计 API，支持按时间范围查询
4. 创建 Golden Dataset（30 条）用于评估一致性验证
5. 集成推荐服务到 Agent 对话流程

---

## 二、架构设计

### 采集流程

```
AgentLoop/ToolExecutor/ContextManager
  → MetricsCollector.recordXxx()
    → MetricsStreamProducer.send()    # Redis Stream
      → MetricsStreamConsumer 消费
        → AgentMetricsRepository.save()  # PostgreSQL
```

### 模块结构

```
nvcassistant/metrics/
├── MetricsCollector.java             # 采集器（4 种指标）
├── MetricType.java                   # 指标类型枚举
├── AgentMetricsEntity.java           # 指标实体
├── AgentMetricsRepository.java       # 指标 Repository
├── MetricsStreamProducer.java        # Redis Stream 生产者
├── MetricsStreamConsumer.java        # Redis Stream 消费者
├── MetricsStatsService.java          # 统计服务
└── dto/
    ├── TokenStats.java
    ├── LatencyStats.java
    ├── CompressionStats.java
    ├── ToolCallStats.java
    └── MetricsOverview.java
```

---

## 三、核心指标设计

### 1. Token 消耗（TOKEN）

```java
metricsCollector.recordLlmCall(sessionId, traceId, inputTokens, outputTokens, model, degraded);
```

**采集点**：AgentLoop 每次 LLM 调用后

**统计维度**：
- totalTokens：总 Token 数
- avgTokensPerSession：每会话平均 Token
- avgInputTokens / avgOutputTokens：平均输入/输出 Token
- degradedCallCount：降级调用次数
- totalLlmCalls：总 LLM 调用次数

### 2. 端到端延迟（LATENCY）

```java
metricsCollector.recordLatency(sessionId, latencyMs, "e2e");
```

**采集点**：AgentLoop 每次对话完成后

**统计维度**：
- avgLatencyMs：平均延迟
- p50LatencyMs / p90LatencyMs / p99LatencyMs：分位数延迟
- maxLatencyMs：最大延迟

### 3. 上下文压缩（COMPRESSION）

```java
metricsCollector.recordCompression(sessionId, beforeTokens, afterTokens, summary);
```

**采集点**：ContextManager 压缩后

**统计维度**：
- avgReductionPercent：平均压缩率
- totalCompressions：压缩次数
- avgBeforeTokens / avgAfterTokens：平均压缩前后 Token

### 4. 工具调用（TOOL_CALL）

```java
metricsCollector.recordToolCall(sessionId, toolName, success, latencyMs, resultCount);
```

**采集点**：ToolExecutor 每次工具调用后

**统计维度**：
- totalCalls：总调用次数
- successRate：成功率
- avgLatencyMs：平均延迟
- byTool：按工具分组统计

---

## 四、关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 异步机制 | Redis Stream | 复用现有基础设施，解耦采集和存储 |
| 采集方式 | 直接注入 MetricsCollector | 比 AOP 更精确，可控性更强 |
| 存储 | PostgreSQL agent_metrics 表 | 结构化查询，支持时间范围筛选 |
| payload | JSON 字符串 | 灵活扩展，不同指标类型字段不同 |
| Golden Dataset | 30 条（LLM 生成 + 人工审核） | 先覆盖核心场景，后续扩充到 100 |

---

## 五、API 设计

### 端点列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/nvc/metrics/token` | Token 统计 |
| GET | `/api/nvc/metrics/latency` | 延迟统计 |
| GET | `/api/nvc/metrics/compression` | 压缩统计 |
| GET | `/api/nvc/metrics/tools` | 工具调用统计 |
| GET | `/api/nvc/metrics/overview` | 综合概览 |

### 请求参数

所有端点统一接受 `from` 和 `to` 参数（ISO DATE_TIME 格式）。

---

## 六、Golden Dataset

### 设计

- 30 条覆盖 NVC 四要素的测试用例
- 每条包含：输入消息、期望意图、期望工具、期望回复要点
- LLM 生成 + 人工审核

### 评估一致性验证

```java
@Service
public class EvaluationConsistencyVerifier {
    // 运行 Golden Dataset 3 次，验证结果一致性
    // 输出：一致率、不一致用例列表
}
```

---

## 七、推荐服务集成

### 改动点

- NvcAgentOrchestrator 集成 NvcScenarioRecommendService
- 新增 `/api/nvc/practice/recommendations` 端点
- 基于用户薄弱要素推荐练习场景

---

## 八、文件清单

### 新建文件（32 个）

```
# 后端（12 个）
app/src/main/java/nvc/guide/modules/nvcassistant/metrics/
├── MetricsCollector.java
├── MetricType.java
├── AgentMetricsEntity.java
├── AgentMetricsRepository.java
├── MetricsStreamProducer.java
├── MetricsStreamConsumer.java
├── MetricsStatsService.java
└── dto/{TokenStats,LatencyStats,CompressionStats,ToolCallStats,MetricsOverview}.java

app/src/main/java/nvc/guide/modules/nvcassistant/controller/MetricsController.java

# 测试（2 个）
app/src/test/java/nvc/guide/modules/nvcassistant/metrics/MetricsCollectorTest.java
app/src/test/java/nvc/guide/modules/nvcassistant/metrics/MetricsStatsServiceTest.java

# Golden Dataset（1 个）
app/src/main/resources/golden-dataset.json

# 评估验证（1 个）
app/src/main/java/nvc/guide/modules/nvcassistant/evaluation/EvaluationConsistencyVerifier.java
```

### 修改文件（3 个）

```
AgentLoop.java                — 增加 MetricsCollector 埋点
ToolExecutor.java             — 增加工具调用指标采集
ContextManager.java           — 增加压缩指标采集
```

---

## 九、测试要求

- MetricsCollector 单元测试：验证 4 种指标采集
- MetricsStatsService 单元测试：验证统计计算
- 集成测试：指标采集 → 异步落库 → API 查询

---

## 十、验收标准

```
□ 指标采集
  □ 4 种指标正常采集（TOKEN/LATENCY/COMPRESSION/TOOL_CALL）
  □ 异步落库不影响对话性能
  □ 采集失败不阻塞主流程

□ 统计 API
  □ 5 个端点正常返回
  □ 支持时间范围筛选
  □ 统计数据准确

□ Golden Dataset
  □ 30 条测试用例创建完成
  □ 评估一致性验证通过

□ 端到端
  □ 对话 → 指标采集 → 落库 → API 查询
  □ 真实数字可用于面试准备
```
