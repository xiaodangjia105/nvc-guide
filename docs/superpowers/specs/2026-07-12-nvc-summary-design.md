# NVC 结构化摘要设计

> 日期: 2026-07-12
> 状态: 待审核

## 问题背景

当前右侧面板的"实时评估"存在多个问题：
1. 每次对话后轮询评估数据，阻塞输入框最多 4.5 秒
2. 评估是同步 LLM 调用，卡在 SSE 的 doOnComplete 里
3. 只显示最后一次评估，历史评分丢失
4. 用户不需要对话中评估，只需要结束时评估

## 设计目标

1. 对话中：右侧显示 NVC 结构化摘要（四要素提取），不阻塞输入
2. 结束时：保留评估报告（后续可优化）
3. 摘要更新不阻塞对话流程

---

## 一、产品设计

### 右侧摘要面板

```
┌──────────────────────────┐
│  你的表达梳理              │
│                           │
│  👁 观察                   │
│  约好3点，他3点半才来，     │
│  没提前通知                │
│                           │
│  💗 感受                   │
│  失落、焦虑                │
│                           │
│  🎯 需求                   │
│  （尚未表达）              │
│                           │
│  🙏 请求                   │
│  （尚未表达）              │
│                           │
│  ────────────────────── │
│  💡 提示                   │
│  试试说说失落的背后        │
│  你需要什么？              │
└──────────────────────────┘
```

**要素提取规则：**
- 从用户的消息中提取已表达的观察/感受/需求/请求
- 未表达的显示"（尚未表达）"
- 已表达的显示用户的原话（或简洁概括）
- 提示区域根据缺失的要素给出引导建议

**更新时机：**
- 用户发消息后，AI 回复完成
- 后台 LLM 分析用户最新消息，更新摘要
- 摘要更新不阻塞输入框

---

## 二、架构设计

### 2.1 移除实时评估

**NvcChatPanel.tsx：**
- 删除评估轮询逻辑（3 次重试、1.5s 延迟）
- 删除 `onEvaluation` 回调
- 流式结束后立即 `setIsStreaming(false)`，不等待评估

**NvcPracticeDialogueService.java：**
- 移除 `evaluateRealtime()` 调用
- 流式 `doOnComplete` 中只保存消息，不触发评估

### 2.2 新增 NVC 摘要服务

**新文件：NvcSummaryService.java**

职责：分析用户消息，提取 NVC 四要素，更新摘要

```java
@Service
public class NvcSummaryService {

  /**
   * 分析用户消息，更新摘要
   * 异步执行，不阻塞对话
   */
  @Async
  public void updateSummary(Long sessionId, String userMessage) {
    // 1. 获取当前摘要（或创建新的）
    // 2. 用 LLM 分析用户消息，提取四要素
    // 3. 合并到现有摘要（增量更新）
    // 4. 保存到 DB
  }

  /**
   * 获取当前摘要
   */
  public NvcSummaryDTO getSummary(Long sessionId) {
    // 从 DB 读取最新摘要
  }
}
```

### 2.3 摘要数据模型

**新实体：NvcSummaryEntity.java**

```java
@Entity
@Table(name = "nvc_summary")
public class NvcSummaryEntity {
  @Id @GeneratedValue
  private Long id;
  private Long sessionId;
  private String observation;   // 已提取的观察
  private String feeling;       // 已提取的感受
  private String need;          // 已提取的需求
  private String request;       // 已提取的请求
  private String hint;          // 引导提示
  @Column(columnDefinition = "TEXT")
  private String rawAnalysis;   // LLM 原始分析结果
  private LocalDateTime updatedAt;
}
```

### 2.4 摘要提取 Prompt

```
你是 NVC（非暴力沟通）表达分析助手。分析用户的最新消息，提取其中的四要素。

当前对话历史：
{conversation}

用户最新消息：
{userMessage}

已有摘要：
{existing_summary}

请提取用户表达中的四要素：
1. 观察：用户描述的客观事实（不含评判）
2. 感受：用户表达的真实情绪
3. 需求：用户表达或暗示的深层需求
4. 请求：用户提出的具体请求

返回 JSON：
{
  "observation": "提取的观察内容，或 null",
  "feeling": "提取的感受内容，或 null",
  "need": "提取的需求内容，或 null",
  "request": "提取的请求内容，或 null",
  "hint": "引导提示，建议用户下一步可以表达什么"
}

规则：
- 只提取用户明确表达的内容，不要推断
- 如果用户没有表达某个要素，返回 null
- hint 应该根据缺失的要素，给出具体的引导建议
```

### 2.5 前端改造

**NvcPracticePage.tsx：**
- 替换 `NvcEvaluationCard` 为 `NvcSummaryPanel`
- 删除评估相关 state
- 定时轮询摘要（或用 WebSocket 推送）

**新增 NvcSummaryPanel.tsx：**
- 显示四要素摘要
- 未表达的显示"（尚未表达）"
- 显示引导提示

---

## 三、API 设计

### 3.1 获取摘要

```
GET /api/nvc/practice/sessions/{sessionId}/summary

Response:
{
  "observation": "约好3点，他3点半才来，没提前通知",
  "feeling": "失落、焦虑",
  "need": null,
  "request": null,
  "hint": "试试说说失落的背后你需要什么？"
}
```

### 3.2 摘要更新流程

```
用户发消息 → AI 回复（流式）
  ↓
后台 @Async 触发摘要更新
  ↓
LLM 分析用户消息
  ↓
更新 DB 中的摘要
  ↓
前端轮询获取最新摘要（或 WebSocket 推送）
```

---

## 四、改动清单

### 后端
| 文件 | 改动 |
|------|------|
| 新增 `NvcSummaryEntity.java` | 摘要实体 |
| 新增 `NvcSummaryRepository.java` | 摘要 Repository |
| 新增 `NvcSummaryService.java` | 摘要提取服务 |
| 修改 `NvcPracticeController.java` | 新增 GET /summary 接口 |
| 修改 `NvcPracticeDialogueService.java` | 移除 evaluateRealtime，触发摘要更新 |

### 前端
| 文件 | 改动 |
|------|------|
| 新增 `NvcSummaryPanel.tsx` | 摘要显示组件 |
| 修改 `NvcPracticePage.tsx` | 替换评估卡为摘要面板 |
| 修改 `NvcChatPanel.tsx` | 移除评估轮询，不再阻塞输入 |
| 修改 `api/nvc.ts` | 新增 getSummary API |

---

## 五、扩展性

- 摘要可以后续接入 RAG（从知识库检索 NVC 表达示例）
- 提示可以个性化（根据用户档案的薄弱要素）
- 评估可以在结束时触发，不影响对话流程
- WebSocket 推送可以替代轮询
