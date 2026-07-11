# Step 4：NVC 评估引擎 — 设计文档

> 日期：2026-07-10
>
> 状态：待实现
>
> 前置：Step 3 已完成（对话流程跑通）

---

## 1. 目标

实现 NVC 表达评估系统：

- **实时评估**：每轮用户回复后，同步调用 LLM 评估 NVC 四要素 + 共情质量
- **最终评估**：练习结束后，通过 Redis Stream 异步执行综合评估
- **练习报告**：生成结构化报告 DTO + PDF 导出

---

## 2. 架构

```
用户发消息 → NvcPracticeDialogueService.sendMessage()
                ↓ (同步, try-catch)
           NvcEvaluationService.evaluateRealtime()
                ↓
           StructuredOutputInvoker.invoke() → LLM → NvcEvaluationResult
                ↓
           保存 NvcEvaluationEntity (REALTIME)

用户结束会话 → NvcPracticeController.completeSession()
                ↓
           NvcEvaluateStreamProducer.sendTask()
                ↓ (Redis Stream)
           NvcEvaluateStreamConsumer.processBusiness()
                ↓
           NvcEvaluationService.evaluateFinal()
                ↓
           保存 NvcEvaluationEntity (FINAL)

用户查看报告 → NvcReportController.getReport()
                ↓
           NvcReportService.generateReport()
                ↓
           NvcPracticeReport DTO

用户导出 PDF → NvcReportController.exportReportPdf()
                ↓
           NvcReportService.exportReportPdf()
                ↓
           PdfExportService.exportNvcReport()
```

---

## 3. 文件清单

### 3.1 新增文件（7 个）

```
app/src/main/java/nvc/guide/modules/nvcpractice/
├── dto/
│   ├── NvcEvaluationResult.java           ← LLM 结构化输出 DTO
│   └── NvcPracticeReport.java             ← 练习报告 DTO
├── service/
│   ├── NvcEvaluationService.java          ← 评估核心
│   └── NvcReportService.java              ← 报告生成 + PDF 导出
├── controller/
│   └── NvcReportController.java           ← 报告 API
└── listener/
    ├── NvcEvaluateStreamProducer.java     ← 异步最终评估生产者
    └── NvcEvaluateStreamConsumer.java     ← 异步最终评估消费者
```

### 3.2 修改文件（3 个）

```
app/src/main/java/nvc/guide/modules/nvcpractice/
├── service/NvcPracticeDialogueService.java   ← 集成实时评估
└── controller/NvcPracticeController.java     ← 集成异步最终评估

app/src/main/java/nvc/guide/infrastructure/export/
└── PdfExportService.java                     ← 新增 exportNvcReport()
```

### 3.3 复用已有文件（不修改）

```
app/src/main/resources/prompts/
├── nvc-evaluation-system.st               ← 实时评估系统提示词
├── nvc-evaluation-user.st                 ← 实时评估用户提示词
├── nvc-evaluation-summary-system.st       ← 最终评估系统提示词
└── nvc-evaluation-summary-user.st         ← 最终评估用户提示词
```

---

## 4. 详细设计

### 4.1 NvcEvaluationResult — LLM 结构化输出 DTO

**路径**：`dto/NvcEvaluationResult.java`

```java
package nvc.guide.modules.nvcpractice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * NVC 评估结果 - 由 LLM 结构化输出
 */
public record NvcEvaluationResult(
    @JsonProperty("observation_score") Integer observationScore,
    @JsonProperty("feeling_score") Integer feelingScore,
    @JsonProperty("need_score") Integer needScore,
    @JsonProperty("request_score") Integer requestScore,
    @JsonProperty("empathy_score") Integer empathyScore,
    @JsonProperty("overall_score") Integer overallScore,
    @JsonProperty("observation_detail") String observationDetail,
    @JsonProperty("feeling_detail") String feelingDetail,
    @JsonProperty("need_detail") String needDetail,
    @JsonProperty("request_detail") String requestDetail,
    @JsonProperty("empathy_detail") String empathyDetail,
    String strengths,
    String improvements,
    @JsonProperty("reference_expressions") String referenceExpressions,
    String summary
) {}
```

**与设计文档差异**：增加 `empathyScore` 和 `empathyDetail`，匹配已有的 `NvcEvaluationEntity.empathyScore`。

---

### 4.2 NvcEvaluationService — 评估核心

**路径**：`service/NvcEvaluationService.java`

#### 依赖

```java
private final LlmProviderRegistry llmProviderRegistry;
private final NvcEvaluationRepository evaluationRepository;
private final StructuredOutputInvoker structuredOutputInvoker;
```

#### 核心方法

**evaluateRealtime(sessionId, userId, userMessage, aiContext, currentStep)**

- 加载 `nvc-evaluation-system.st` + `nvc-evaluation-user.st` 模板
- 用 `StructuredOutputInvoker.invoke()` 调用 LLM
- 使用 `BeanOutputConverter<NvcEvaluationResult>` 解析结构化输出
- 构建 `NvcEvaluationEntity`（type=REALTIME）并保存
- 返回 Entity

**evaluateFinal(sessionId, userId, messages)**

- 加载 `nvc-evaluation-summary-system.st` + `nvc-evaluation-summary-user.st` 模板
- 构建完整对话记录文本
- 用 `StructuredOutputInvoker.invoke()` 调用 LLM
- 构建 `NvcEvaluationEntity`（type=FINAL）并保存
- 返回 Entity

**getLatestRealtimeEvaluation(sessionId)**

- 查询 `NvcEvaluationRepository.findBySessionIdAndEvaluationType(sessionId, REALTIME)`

**getFinalEvaluation(sessionId)**

- 查询 `NvcEvaluationRepository.findBySessionIdAndEvaluationType(sessionId, FINAL)`

#### StructuredOutputInvoker 调用方式

```java
BeanOutputConverter<NvcEvaluationResult> converter =
    new BeanOutputConverter<>(NvcEvaluationResult.class);

NvcEvaluationResult result = structuredOutputInvoker.invoke(
    chatClient,
    systemPrompt,           // nvc-evaluation-system.st 内容
    userPrompt,             // 填充变量后的 nvc-evaluation-user.st 内容
    converter,
    ErrorCode.NVC_EVALUATION_FAILED,
    "NVC评估失败: ",
    "NvcEvaluation",
    log
);
```

#### Prompt 变量

**实时评估** (`nvc-evaluation-user.st`)：
- `{conversation}` — 当前对话上下文（最近几轮）
- `{userProfile}` — 用户档案（暂为空，Step 5 实现）

**最终评估** (`nvc-evaluation-summary-user.st`)：
- `{batchResults}` — 各轮实时评估的汇总（如有）
- `{conversation}` — 完整对话记录

---

### 4.3 异步评估 — Redis Stream

#### NvcEvaluateStreamProducer

**路径**：`listener/NvcEvaluateStreamProducer.java`

继承 `AbstractStreamProducer<NvcEvaluateTask>`，实现以下抽象方法：

```java
record NvcEvaluateTask(Long sessionId, Long userId) {}

@Override protected String taskDisplayName() { return "NVC最终评估"; }
@Override protected String streamKey() { return "nvc:evaluate:stream"; }

@Override
protected Map<String, String> buildMessage(NvcEvaluateTask task) {
    return Map.of(
        "sessionId", String.valueOf(task.sessionId()),
        "userId", String.valueOf(task.userId())
    );
}

@Override
protected String payloadIdentifier(NvcEvaluateTask task) {
    return "sessionId=" + task.sessionId();
}

@Override
protected void onSendFailed(NvcEvaluateTask task, String error) {
    log.error("NVC评估任务发送失败: sessionId={}, error={}", task.sessionId(), error);
}
```

公开方法 `sendEvaluateTask(sessionId, userId)` 调用 `sendTask()`。

#### NvcEvaluateStreamConsumer

**路径**：`listener/NvcEvaluateStreamConsumer.java`

继承 `AbstractStreamConsumer<NvcEvaluateTask>`，实现以下抽象方法：

```java
@Override protected String taskDisplayName() { return "NVC最终评估"; }
@Override protected String streamKey() { return "nvc:evaluate:stream"; }
@Override protected String groupName() { return "nvc-evaluate-group"; }
@Override protected String consumerPrefix() { return "nvc-evaluate-"; }
@Override protected String threadName() { return "nvc-evaluate-consumer"; }

@Override
protected NvcEvaluateTask parsePayload(StreamMessageId messageId, Map<String, String> data) {
    return new NvcEvaluateTask(
        Long.parseLong(data.get("sessionId")),
        Long.parseLong(data.get("userId"))
    );
}

@Override
protected void processBusiness(NvcEvaluateTask task) {
    // 加载完整对话记录
    List<NvcPracticeMessageEntity> messages =
        messageRepository.findBySessionIdOrderBySequenceNumAsc(task.sessionId());
    // 执行最终评估
    evaluationService.evaluateFinal(task.sessionId(), task.userId(), messages);
}

// markProcessing/markCompleted/markFailed/retryMessage 使用 RedisService 状态管理
```

---

### 4.4 NvcPracticeReport — 练习报告 DTO

**路径**：`dto/NvcPracticeReport.java`

```java
package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import java.time.LocalDateTime;

public record NvcPracticeReport(
    Long sessionId,
    NvcPracticeMode practiceMode,
    NvcDifficulty difficulty,
    long totalRounds,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    // 五维度评分
    Integer observationScore,
    Integer feelingScore,
    Integer needScore,
    Integer requestScore,
    Integer empathyScore,
    Integer overallScore,
    // 五维度详细分析
    String observationDetail,
    String feelingDetail,
    String needDetail,
    String requestDetail,
    String empathyDetail,
    // 综合
    String strengths,
    String improvements,
    String referenceExpressions,
    String summary
) {}
```

---

### 4.5 NvcReportService — 报告生成

**路径**：`service/NvcReportService.java`

#### 依赖

```java
private final NvcPracticeSessionRepository sessionRepository;
private final NvcPracticeMessageRepository messageRepository;
private final NvcEvaluationService evaluationService;
private final PdfExportService pdfExportService;
```

#### 方法

**generateReport(sessionId)**

1. 查询 session（不存在抛 `NVC_SESSION_NOT_FOUND`）
2. 查询 messages
3. 获取 finalEvaluation（如无则先执行一次）
4. 统计 totalRounds（USER 角色消息数）
5. 构建 `NvcPracticeReport` DTO 返回

**exportReportPdf(sessionId)**

1. 调用 `generateReport()` 获取报告
2. 调用 `pdfExportService.exportNvcReport(report)` 生成 PDF
3. 返回 `byte[]`

---

### 4.6 PdfExportService 扩展

在 `PdfExportService` 中新增 `exportNvcReport(NvcPracticeReport report)` 方法：

- 使用已有的 `createPdfDocument()`, `createChineseFont()`, `createTitle()`, `createSubtitle()`, `createBody()` 等辅助方法
- 内容结构：
  1. 标题："NVC 练习报告"
  2. 会话信息（模式、难度、轮次、时间）
  3. 五维度评分表格
  4. 各维度详细分析
  5. 优势和改进建议
  6. 参考 NVC 表达
  7. 综合评价

---

### 4.7 NvcReportController — 报告 API

**路径**：`controller/NvcReportController.java`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/nvc/report/sessions/{sessionId}` | 返回 `Result<NvcPracticeReport>` |
| GET | `/api/nvc/report/sessions/{sessionId}/pdf` | 返回 PDF 文件下载 |

---

### 4.8 集成到现有流程

#### NvcPracticeDialogueService 修改

在 `sendMessage()` 方法末尾（保存 AI 回复之后）添加：

```java
// 异步触发实时评估（不阻塞对话）
try {
    evaluationService.evaluateRealtime(
        sessionId,
        session.getUserId(),
        userMessage,
        aiReply,
        session.getCurrentStep()
    );
} catch (Exception e) {
    log.warn("Realtime evaluation failed (non-blocking): sessionId={}", sessionId, e);
}
```

在 `sendMessageStream()` 的 `doOnComplete()` 中添加同样的调用。

#### NvcPracticeController 修改

注入 `NvcEvaluateStreamProducer`，在 `completeSession()` 中添加：

```java
@PostMapping("/sessions/{sessionId}/complete")
public Result<PracticeSessionResponse> completeSession(@PathVariable Long sessionId) {
    NvcPracticeSessionEntity session = sessionService.completeSession(sessionId);
    // 推送异步最终评估任务
    evaluateStreamProducer.sendEvaluateTask(sessionId, session.getUserId());
    return Result.success(toSessionResponse(session));
}
```

---

## 5. 验证清单

```
□ NvcEvaluationService 编译通过
  □ evaluateRealtime() 使用 StructuredOutputInvoker.invoke() + BeanOutputConverter
  □ evaluateFinal() 使用 summary 模板
  □ 评估结果包含五维度分数（含 empathyScore）
  □ 评估结果保存到数据库
□ NvcReportService 编译通过
  □ generateReport() 返回完整 NvcPracticeReport
  □ exportReportPdf() 生成可下载的 PDF
□ 异步评估编译通过
  □ NvcEvaluateStreamProducer 实现正确的抽象方法
  □ NvcEvaluateStreamConsumer 实现正确的抽象方法
  □ Redis Stream 消息能正确传递
□ 对话流程集成
  □ sendMessage() 末尾有实时评估调用（try-catch）
  □ sendMessageStream() 末尾有实时评估调用（try-catch）
  □ completeSession() 推送异步最终评估
□ API 测试
  □ GET /api/nvc/report/sessions/{id} 返回报告
  □ GET /api/nvc/report/sessions/{id}/pdf 下载 PDF
□ Git 提交："Step 4: Add NVC evaluation engine with async evaluation and PDF export"
```
