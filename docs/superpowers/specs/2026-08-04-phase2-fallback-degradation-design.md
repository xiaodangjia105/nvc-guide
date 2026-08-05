# Phase 2 LLM Fallback 降级体系设计文档

> 创建时间：2026-08-04
> 分支：feat/fallback-degradation
> 状态：已完成

---

## 一、背景与目标

### 现状

LLM 调用可能因超时、限流、格式异常等原因失败，当前没有统一的降级机制。失败时直接抛异常，用户体验差。面试需要展示系统容错和降级设计能力。

### 目标

1. 建立统一的 LLM 降级处理器，覆盖所有 LLM 调用入口
2. 3 次重试 + 分类延迟策略（TIMEOUT 3s / RATE_LIMITED 5s / INVALID_RESPONSE 立即）
3. 对话降级：27 条 NVC 引导话术模板（按步骤×轮次）
4. 评估降级：KeywordScorer 关键词匹配评分 + degraded 标记
5. 场景降级：种子场景库随机分配

---

## 二、架构设计

### 降级流程

```
AgentLoop.callLlm()
  → LlmFallbackHandler.executeWithFallback(llmCall, fallback, context)
    ├── 第 1 次调用 → 成功返回 / 失败等待重试
    ├── 第 2 次调用 → 成功返回 / 失败等待重试
    ├── 第 3 次调用 → 成功返回 / 失败进入降级
    └── 降级路径 → 调用 fallback.get() → 标记 degraded=true → 返回
```

### 模块结构

```
nvcassistant/fallback/
├── LlmFallbackHandler.java          # 统一降级处理器
├── LlmFailureType.java              # 失败类型枚举
├── LlmCallContext.java              # 调用上下文
└── DialogFallbackTemplates.java     # 对话降级模板库（27 条）

nvcpractice/fallback/
└── EvaluationFallbackService.java   # 评估降级服务
```

---

## 三、失败类型分类

```java
public enum LlmFailureType {
    TIMEOUT(3000, true),           // 超时：3s 延迟，可重试
    RATE_LIMITED(5000, true),      // 限流：5s 延迟，可重试
    INVALID_RESPONSE(0, true),     // 格式异常：立即重试
    PROVIDER_ERROR(2000, true),    // 供应商错误：2s 延迟，可重试
    UNKNOWN(1000, false);          // 未知：1s 延迟，不可重试
}
```

### 分类逻辑

```java
private LlmFailureType classifyException(Exception e) {
    String message = e.getMessage().toLowerCase();
    String exceptionType = e.getClass().getSimpleName().toLowerCase();

    if (exceptionType.contains("timeout") || message.contains("timeout"))
        return LlmFailureType.TIMEOUT;
    if (message.contains("rate limit") || message.contains("429"))
        return LlmFailureType.RATE_LIMITED;
    if (message.contains("invalid") || message.contains("json"))
        return LlmFailureType.INVALID_RESPONSE;
    if (message.contains("500") || message.contains("502") || message.contains("503"))
        return LlmFailureType.PROVIDER_ERROR;

    return LlmFailureType.UNKNOWN;
}
```

---

## 四、对话降级模板

### 设计

27 条 NVC 引导话术，按练习步骤分类：

| 步骤 | 模板数 | 示例 |
|------|--------|------|
| OBSERVE | 6 | "让我们先停下来，客观描述一下刚才发生了什么？" |
| FEELING | 6 | "当你经历这些的时候，内心的感受是什么？" |
| NEED | 6 | "这个感受背后，你有什么需要没有被满足呢？" |
| REQUEST | 6 | "基于你的需要，你能提出一个具体、可执行的请求吗？" |
| FREE_DIALOG | 3 | 通用引导话术 |

### 选择逻辑

```java
public String getTemplate(String step) {
    List<String> templates = STEP_TEMPLATES.getOrDefault(step, STEP_TEMPLATES.get("FREE_DIALOG"));
    return templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
}
```

---

## 五、评估降级设计

### KeywordScorer

当 LLM 评估失败时，使用关键词匹配进行降级评估：

```java
@Service
public class KeywordScorer {
    // 4 维度关键词评分：
    // 1. 观察：事实描述词 vs 评价词
    // 2. 感受：情绪词汇
    // 3. 需求：需求词汇
    // 4. 请求：请求句式
}
```

### EvaluationFallbackService

```java
@Service
public class EvaluationFallbackService {
    // 降级评估流程：
    // 1. 尝试 LLM 评估
    // 2. 失败 → KeywordScorer 评分
    // 3. 标记 degraded=true
    // 4. 返回评估结果
}
```

---

## 六、关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 重试策略 | 3 次重试 + 分类延迟 | 平衡成功率和响应时间 |
| 对话降级 | NVC 引导话术模板 | 比"服务不可用"更有价值 |
| 评估降级 | 关键词匹配 | 无需 LLM，确定性高 |
| 降级标记 | degraded 字段 | 前端可展示降级提示 |
| 模板选择 | 随机选择 | 避免重复，增加变化 |

---

## 七、前端集成

### SSE metadata 增加 degraded 字段

```json
{
  "type": "DONE",
  "metadata": {
    "degraded": true,
    "degradedReason": "LLM 调用失败，使用模板回复"
  }
}
```

### 降级模式提示条

```tsx
{degraded && (
  <div className="degraded-banner">
    当前为降级模式，回复基于预设模板
  </div>
)}
```

---

## 八、文件清单

### 新建文件（10 个）

```
# 后端（6 个）
app/src/main/java/nvc/guide/modules/nvcassistant/fallback/
├── LlmFallbackHandler.java
├── LlmFailureType.java
├── LlmCallContext.java
└── DialogFallbackTemplates.java

app/src/main/java/nvc/guide/modules/nvcpractice/fallback/
├── EvaluationFallbackService.java
└── KeywordScorer.java

# 测试（2 个）
app/src/test/java/nvc/guide/modules/nvcassistant/fallback/LlmFallbackHandlerTest.java
app/src/test/java/nvc/guide/modules/nvcpractice/fallback/EvaluationFallbackServiceTest.java
```

### 修改文件（3 个）

```
AgentLoop.java                    — 包装 LLM 调用为 executeWithFallback
NvcEvaluationEntity.java          — 增加 degraded boolean 字段
NvcAssistantChat.tsx              — 增加降级提示条
```

---

## 九、测试要求

- LlmFallbackHandler 单元测试：重试逻辑、异常分类、降级触发
- KeywordScorer 单元测试：4 维度关键词匹配准确性
- EvaluationFallbackService 单元测试：降级评估流程
- 集成测试：LLM 失败 → 降级 → 用户收到引导话术

---

## 十、验收标准

```
□ 重试机制
  □ 3 次重试正常工作
  □ 分类延迟正确（TIMEOUT 3s / RATE_LIMITED 5s）
  □ 不可重试异常直接进入降级

□ 对话降级
  □ 27 条模板按步骤正确选择
  □ 随机选择避免重复
  □ SSE metadata 标记 degraded=true

□ 评估降级
  □ KeywordScorer 4 维度评分正确
  □ degraded 标记写入数据库
  □ 降级评估结果可用

□ 前端
  □ 降级模式提示条正常显示
  □ degraded 字段正确解析

□ 端到端
  □ 模拟 LLM 超时 → 3 次重试 → 降级回复
  □ 降级回复是 NVC 引导话术而非错误信息
  □ 评估降级标记正确
```
