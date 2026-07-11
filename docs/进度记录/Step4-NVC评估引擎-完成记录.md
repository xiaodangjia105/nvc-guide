# Step 4：NVC 评估引擎 — 完成记录

> 完成日期：2026-07-10
>
> 状态：后端 API 完成，前端未对接

---

## 一、本步实现的功能

### 1. 实时评估（每轮对话后）

- **触发时机**: `sendMessage()` 和 `sendMessageStream()` 保存 AI 回复后自动触发
- **实现方式**: 同步调用 LLM，try-catch 包裹，评估失败不阻塞对话
- **评估维度**: 观察、感受、需求、请求、共情（五维度，0-100 分）
- **结构化输出**: 使用 `StructuredOutputInvoker.invoke()` + `BeanOutputConverter<NvcEvaluationResult>`
- **Prompt 模板**: 复用已有的 `nvc-evaluation-system.st` + `nvc-evaluation-user.st`

### 2. 最终评估（会话结束后）

- **触发时机**: `completeSession()` 时推送 Redis Stream 异步任务
- **实现方式**: `NvcEvaluateStreamProducer` → Redis Stream → `NvcEvaluateStreamConsumer` → `NvcEvaluationService.evaluateFinal()`
- **评估范围**: 对完整对话进行综合评估
- **Prompt 模板**: 复用已有的 `nvc-evaluation-summary-system.st` + `nvc-evaluation-summary-user.st`

### 3. 练习报告生成

- **接口**: `GET /api/nvc/report/sessions/{sessionId}`
- **功能**: 返回 `NvcPracticeReport` DTO，包含会话信息、五维度评分、详细分析、优势/改进建议、参考表达、综合评价
- **自动补全**: 如果还没有最终评估，会先执行一次再返回报告

### 4. PDF 导出

- **接口**: `GET /api/nvc/report/sessions/{sessionId}/pdf`
- **功能**: 使用 iText 8 生成 PDF 文件下载
- **PDF 内容**: 标题、会话信息、五维度评分表格、详细分析、优势、改进建议、参考表达、综合评价

---

## 二、新增/修改的文件

### 新增文件（7 个）

| 文件 | 说明 |
|------|------|
| `dto/NvcEvaluationResult.java` | LLM 结构化输出 DTO（五维度 + detail + 综合） |
| `dto/NvcPracticeReport.java` | 练习报告 DTO（21 个字段） |
| `service/NvcEvaluationService.java` | 评估核心服务（实时 + 最终 + 查询） |
| `service/NvcReportService.java` | 报告生成 + PDF 导出服务 |
| `controller/NvcReportController.java` | 报告 API 控制器（2 个端点） |
| `listener/NvcEvaluateStreamProducer.java` | 异步最终评估生产者 |
| `listener/NvcEvaluateStreamConsumer.java` | 异步最终评估消费者 |

### 修改文件（3 个）

| 文件 | 修改内容 |
|------|---------|
| `NvcPracticeDialogueService.java` | 注入 `NvcEvaluationService`，`sendMessage()` 和 `sendMessageStream()` 末尾加实时评估 |
| `NvcPracticeController.java` | 注入 `NvcEvaluateStreamProducer`，`completeSession()` 推送异步最终评估任务 |
| `PdfExportService.java` | 新增 `exportNvcReport()` 方法，完整 PDF 渲染（五维度表格、详细分析等） |

---

## 三、核心服务说明

### NvcEvaluationService

评估引擎核心，负责：

- `evaluateRealtime(sessionId, userId, userMessage, aiContext, currentStep)` — 实时评估单轮表达
- `evaluateFinal(sessionId, userId, messages)` — 最终评估完整对话
- `getLatestRealtimeEvaluation(sessionId)` — 查询最新实时评估
- `getFinalEvaluation(sessionId)` — 查询最终评估

内部使用 `StructuredOutputInvoker.invoke()` 进行结构化输出，自带重试和 JSON 修复逻辑。

### NvcReportService

报告生成服务，负责：

- `generateReport(sessionId)` — 生成 `NvcPracticeReport` DTO
- `exportReportPdf(sessionId)` — 导出 PDF 文件

### NvcEvaluateStreamProducer / Consumer

Redis Stream 异步评估：

- Producer: `sendEvaluateTask(sessionId, userId)` 发送任务到 `nvc:evaluate:stream`
- Consumer: 消费任务，调用 `NvcEvaluationService.evaluateFinal()`
- 支持重试机制（最多 3 次）

---

## 四、评估流程图

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
           PdfExportService.exportNvcReport()
```

---

## 五、当前状态与限制

### ✅ 已完成

- 五维度 NVC 评估（观察/感受/需求/请求/共情）
- 实时逐轮评估（同步，不阻塞对话）
- 最终综合评估（异步，Redis Stream）
- 练习报告 DTO 生成
- PDF 导出（iText 8）
- 编译通过

### ❌ 未完成（后续步骤）

- **前端未对接**: 前端目前没有报告查看和 PDF 下载页面
- **用户档案**: `userProfile` 暂为空（Step 5 实现），评估 prompt 中传"（暂无）"
- **各轮实时评估汇总**: 最终评估时 `batchResults` 暂为占位文本，后续可聚合各轮实时评估
- **评估结果缓存**: 每次查看报告都会查询 DB，未加 Redis 缓存
- **雷达图**: 五维度评分的雷达图在前端展示（Step 9）

---

## 六、API 测试方法

### 前置条件

1. 确保 Docker 服务运行（PostgreSQL + Redis）
2. 启动应用：`.\gradlew.bat bootRun`

### 测试用例

#### 1. 创建会话并发送消息（会自动触发实时评估）

```bash
# 创建会话
curl -X POST "http://localhost:8080/api/nvc/practice/sessions?userId=1" \
  -H "Content-Type: application/json" \
  -d '{"practiceMode":"SCENARIO","difficulty":"MEDIUM"}'

# 发送消息（会自动评估）
curl -X POST "http://localhost:8080/api/nvc/practice/sessions/1/messages" \
  -H "Content-Type: application/json" \
  -d '{"content":"我注意到你今天迟到了30分钟，我感到有些担心，因为我需要被尊重和准时。你愿意下次提前告诉我如果会迟到吗？"}'
```

#### 2. 结束会话（会触发异步最终评估）

```bash
curl -X POST "http://localhost:8080/api/nvc/practice/sessions/1/complete"
```

#### 3. 获取练习报告

```bash
curl "http://localhost:8080/api/nvc/report/sessions/1"
```

#### 4. 导出 PDF

```bash
curl -o nvc-report.pdf "http://localhost:8080/api/nvc/report/sessions/1/pdf"
```

---

## 七、Git 提交记录

```
2d6cf3e feat(step4): add NvcEvaluationResult DTO with five dimensions
245666a feat(step4): add NvcPracticeReport DTO
80bcf0b feat(step4): add NvcEvaluationService with realtime and final evaluation
9808b2c feat(step4): add NvcEvaluateStreamProducer and Consumer for async final evaluation
a6010cb feat(step4): add NvcReportService with report generation and PDF export
fc3f055 feat(step4): add NvcReportController and PdfExportService NVC report export
6956a96 feat(step4): integrate realtime evaluation into dialogue flow and async final evaluation into completeSession
```
