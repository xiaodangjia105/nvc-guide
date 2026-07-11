# Step 4: NVC 评估引擎 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 NVC 表达评估系统 — 实时逐轮评估 + 最终综合评估 + 练习报告生成 + PDF 导出

**Architecture:** 同步实时评估（每轮对话后 LLM 评估，try-catch 不阻塞对话）+ 异步最终评估（Redis Stream）+ 报告生成（DTO + PDF）。使用 `StructuredOutputInvoker.invoke()` 进行结构化输出，复用已有的 4 个 prompt 模板。

**Tech Stack:** Spring Boot 4.0, Java 21, Spring AI 2.0, Redis Stream, iText 8

## Global Constraints

- 包名：`nvc.guide.*`（非 `interview.guide`）
- 禁止 `throw new RuntimeException(...)`，使用 `BusinessException(ErrorCode.XXX)`
- 禁止直接返回 Entity 给前端
- 2 空格缩进，列限制 100 字符
- 禁止通配符导入
- 使用 SLF4J `@Slf4j` 日志
- `@Transactional` 放 Service 层，事务内禁止调用外部 API

---

## File Structure

### 新增文件（7 个）

| 文件 | 职责 |
|------|------|
| `nvc/guide/modules/nvcpractice/dto/NvcEvaluationResult.java` | LLM 结构化输出 DTO（五维度） |
| `nvc/guide/modules/nvcpractice/dto/NvcPracticeReport.java` | 练习报告 DTO |
| `nvc/guide/modules/nvcpractice/service/NvcEvaluationService.java` | 评估核心（实时 + 最终） |
| `nvc/guide/modules/nvcpractice/service/NvcReportService.java` | 报告生成 + PDF 导出 |
| `nvc/guide/modules/nvcpractice/controller/NvcReportController.java` | 报告 API |
| `nvc/guide/modules/nvcpractice/listener/NvcEvaluateStreamProducer.java` | 异步最终评估生产者 |
| `nvc/guide/modules/nvcpractice/listener/NvcEvaluateStreamConsumer.java` | 异步最终评估消费者 |

### 修改文件（3 个）

| 文件 | 修改内容 |
|------|---------|
| `nvc/guide/modules/nvcpractice/service/NvcPracticeDialogueService.java:86-98` | `sendMessage()` 和 `sendMessageStream()` 末尾加实时评估 |
| `nvc/guide/modules/nvcpractice/controller/NvcPracticeController.java:115-121` | `completeSession()` 加异步评估任务 |
| `nvc/guide/infrastructure/export/PdfExportService.java:125-127` | 新增 `exportNvcReport()` 方法 |

---

### Task 1: NvcEvaluationResult DTO

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/NvcEvaluationResult.java`

**Interfaces:**
- Produces: `NvcEvaluationResult` record — 被 Task 3 (NvcEvaluationService) 和 Task 5 (NvcReportService) 使用

- [ ] **Step 1: 创建 NvcEvaluationResult.java**

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

- [ ] **Step 2: 编译验证**

Run: `cd D:\code\nvc-guide && .\gradlew.bat compileJava 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/dto/NvcEvaluationResult.java
git commit -m "feat(step4): add NvcEvaluationResult DTO with five dimensions"
```

---

### Task 2: NvcPracticeReport DTO

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/NvcPracticeReport.java`

**Interfaces:**
- Produces: `NvcPracticeReport` record — 被 Task 5 (NvcReportService) 和 Task 6 (NvcReportController) 使用

- [ ] **Step 1: 创建 NvcPracticeReport.java**

```java
package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;

import java.time.LocalDateTime;

/**
 * NVC 练习报告 — 包含会话信息、五维度评分、详细分析
 */
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

- [ ] **Step 2: 编译验证**

Run: `cd D:\code\nvc-guide && .\gradlew.bat compileJava 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/dto/NvcPracticeReport.java
git commit -m "feat(step4): add NvcPracticeReport DTO"
```

---

### Task 3: NvcEvaluationService — 评估核心

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcEvaluationService.java`

**Interfaces:**
- Consumes: `NvcEvaluationResult` (Task 1), `StructuredOutputInvoker.invoke()`, `LlmProviderRegistry.getPlainChatClient()`, `NvcEvaluationRepository`, prompt 模板文件
- Produces: `NvcEvaluationService` — 被 Task 4 (StreamConsumer), Task 5 (ReportService), Task 7 (DialogueService 集成) 使用

- [ ] **Step 1: 创建 NvcEvaluationService.java**

```java
package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.common.ai.StructuredOutputInvoker;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcpractice.dto.NvcEvaluationResult;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationType;
import nvc.guide.modules.nvcpractice.model.NvcMessageRole;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.repository.NvcEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcEvaluationService {

    private final LlmProviderRegistry llmProviderRegistry;
    private final NvcEvaluationRepository evaluationRepository;
    private final StructuredOutputInvoker structuredOutputInvoker;

    // ==================== 实时评估 ====================

    /**
     * 实时评估：评估用户单轮回复的 NVC 表达质量
     */
    public NvcEvaluationEntity evaluateRealtime(Long sessionId, Long userId,
                                                 String userMessage, String aiContext,
                                                 NvcPracticeStep currentStep) {
        String systemPrompt = loadPrompt("prompts/nvc-evaluation-system.st");
        String userPrompt = buildRealtimeUserPrompt(userMessage, aiContext, currentStep);

        NvcEvaluationResult result = invokeEvaluation(systemPrompt, userPrompt);

        NvcEvaluationEntity entity = buildEvaluationEntity(
            sessionId, userId, result, NvcEvaluationType.REALTIME);
        NvcEvaluationEntity saved = evaluationRepository.save(entity);
        log.info("Realtime evaluation saved: sessionId={}, overallScore={}",
            sessionId, result.overallScore());
        return saved;
    }

    // ==================== 最终评估 ====================

    /**
     * 最终评估：对整个练习对话进行综合评估
     */
    public NvcEvaluationEntity evaluateFinal(Long sessionId, Long userId,
                                              List<NvcPracticeMessageEntity> messages) {
        String systemPrompt = loadPrompt("prompts/nvc-evaluation-summary-system.st");
        String userPrompt = buildFinalUserPrompt(messages);

        NvcEvaluationResult result = invokeEvaluation(systemPrompt, userPrompt);

        NvcEvaluationEntity entity = buildEvaluationEntity(
            sessionId, userId, result, NvcEvaluationType.FINAL);
        NvcEvaluationEntity saved = evaluationRepository.save(entity);
        log.info("Final evaluation saved: sessionId={}, overallScore={}",
            sessionId, result.overallScore());
        return saved;
    }

    // ==================== 查询 ====================

    /**
     * 获取会话的最新实时评估
     */
    public NvcEvaluationEntity getLatestRealtimeEvaluation(Long sessionId) {
        return evaluationRepository
            .findBySessionIdAndEvaluationType(sessionId, NvcEvaluationType.REALTIME)
            .orElse(null);
    }

    /**
     * 获取会话的最终评估
     */
    public NvcEvaluationEntity getFinalEvaluation(Long sessionId) {
        return evaluationRepository
            .findBySessionIdAndEvaluationType(sessionId, NvcEvaluationType.FINAL)
            .orElse(null);
    }

    // ==================== 内部方法 ====================

    /**
     * 调用 LLM 进行结构化评估
     */
    private NvcEvaluationResult invokeEvaluation(String systemPrompt, String userPrompt) {
        ChatClient chatClient = llmProviderRegistry.getPlainChatClient(null);
        BeanOutputConverter<NvcEvaluationResult> converter =
            new BeanOutputConverter<>(NvcEvaluationResult.class);

        return structuredOutputInvoker.invoke(
            chatClient,
            systemPrompt,
            userPrompt,
            converter,
            ErrorCode.NVC_EVALUATION_FAILED,
            "NVC评估失败: ",
            "NvcEvaluation",
            log
        );
    }

    /**
     * 构建实时评估的用户提示词
     */
    private String buildRealtimeUserPrompt(String userMessage, String aiContext,
                                            NvcPracticeStep currentStep) {
        StringBuilder conversation = new StringBuilder();
        if (aiContext != null && !aiContext.isBlank()) {
            conversation.append("[AI 上一轮回复]\n").append(aiContext).append("\n\n");
        }
        conversation.append("[用户最新表达]\n").append(userMessage);

        if (currentStep != null) {
            conversation.append("\n\n[当前练习步骤]\n").append(currentStep);
        }

        return loadPrompt("prompts/nvc-evaluation-user.st")
            .replace("{conversation}", conversation.toString())
            .replace("{userProfile}", "（暂无）");
    }

    /**
     * 构建最终评估的用户提示词
     */
    private String buildFinalUserPrompt(List<NvcPracticeMessageEntity> messages) {
        StringBuilder dialogue = new StringBuilder();
        for (NvcPracticeMessageEntity msg : messages) {
            String role = msg.getRole() == NvcMessageRole.USER ? "用户" : "AI";
            dialogue.append(String.format("[%s] %s\n", role, msg.getContent()));
        }

        return loadPrompt("prompts/nvc-evaluation-summary-user.st")
            .replace("{batchResults}", "（首轮综合评估，无各轮实时评估数据）")
            .replace("{conversation}", dialogue.toString());
    }

    /**
     * 构建评估实体
     */
    private NvcEvaluationEntity buildEvaluationEntity(Long sessionId, Long userId,
                                                        NvcEvaluationResult result,
                                                        NvcEvaluationType type) {
        return NvcEvaluationEntity.builder()
            .sessionId(sessionId)
            .userId(userId)
            .observationScore(result.observationScore())
            .feelingScore(result.feelingScore())
            .needScore(result.needScore())
            .requestScore(result.requestScore())
            .empathyScore(result.empathyScore())
            .overallScore(result.overallScore())
            .observationDetail(result.observationDetail())
            .feelingDetail(result.feelingDetail())
            .needDetail(result.needDetail())
            .requestDetail(result.requestDetail())
            .strengths(result.strengths())
            .improvements(result.improvements())
            .referenceExpressions(result.referenceExpressions())
            .summary(result.summary())
            .evaluationType(type)
            .build();
    }

    /**
     * 加载 prompt 模板文件
     */
    private String loadPrompt(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load prompt: {}", path, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                "评估提示词加载失败: " + path);
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd D:\code\nvc-guide && .\gradlew.bat compileJava 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcEvaluationService.java
git commit -m "feat(step4): add NvcEvaluationService with realtime and final evaluation"
```

---

### Task 4: NvcEvaluateStreamProducer + NvcEvaluateStreamConsumer

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/listener/NvcEvaluateStreamProducer.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/listener/NvcEvaluateStreamConsumer.java`

**Interfaces:**
- Consumes: `NvcEvaluationService.evaluateFinal()` (Task 3), `NvcPracticeMessageRepository`, `AbstractStreamProducer`, `AbstractStreamConsumer`, `RedisService`
- Produces: `NvcEvaluateStreamProducer.sendEvaluateTask(sessionId, userId)` — 被 Task 7 (NvcPracticeController) 使用

- [ ] **Step 1: 创建 NvcEvaluateStreamProducer.java**

```java
package nvc.guide.modules.nvcpractice.listener;

import nvc.guide.common.async.AbstractStreamProducer;
import nvc.guide.infrastructure.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * NVC 练习评估异步任务生产者
 * 练习结束后推送最终评估任务到 Redis Stream
 */
@Component
@Slf4j
public class NvcEvaluateStreamProducer extends AbstractStreamProducer<NvcEvaluateStreamProducer.NvcEvaluateTask> {

    public NvcEvaluateStreamProducer(RedisService redisService) {
        super(redisService);
    }

    public record NvcEvaluateTask(Long sessionId, Long userId) {}

    /**
     * 发送最终评估任务
     */
    public void sendEvaluateTask(Long sessionId, Long userId) {
        sendTask(new NvcEvaluateTask(sessionId, userId));
    }

    @Override
    protected String taskDisplayName() {
        return "NVC最终评估";
    }

    @Override
    protected String streamKey() {
        return "nvc:evaluate:stream";
    }

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
}
```

- [ ] **Step 2: 创建 NvcEvaluateStreamConsumer.java**

```java
package nvc.guide.modules.nvcpractice.listener;

import nvc.guide.common.async.AbstractStreamConsumer;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import nvc.guide.modules.nvcpractice.service.NvcEvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * NVC 练习评估异步任务消费者
 * 从 Redis Stream 消费最终评估任务
 */
@Component
@Slf4j
public class NvcEvaluateStreamConsumer extends AbstractStreamConsumer<NvcEvaluateStreamProducer.NvcEvaluateTask> {

    private final NvcEvaluationService evaluationService;
    private final NvcPracticeMessageRepository messageRepository;

    public NvcEvaluateStreamConsumer(RedisService redisService,
                                      NvcEvaluationService evaluationService,
                                      NvcPracticeMessageRepository messageRepository) {
        super(redisService);
        this.evaluationService = evaluationService;
        this.messageRepository = messageRepository;
    }

    @Override
    protected String taskDisplayName() {
        return "NVC最终评估";
    }

    @Override
    protected String streamKey() {
        return "nvc:evaluate:stream";
    }

    @Override
    protected String groupName() {
        return "nvc-evaluate-group";
    }

    @Override
    protected String consumerPrefix() {
        return "nvc-evaluate-";
    }

    @Override
    protected String threadName() {
        return "nvc-evaluate-consumer";
    }

    @Override
    protected NvcEvaluateStreamProducer.NvcEvaluateTask parsePayload(
            StreamMessageId messageId, Map<String, String> data) {
        try {
            return new NvcEvaluateStreamProducer.NvcEvaluateTask(
                Long.parseLong(data.get("sessionId")),
                Long.parseLong(data.get("userId"))
            );
        } catch (Exception e) {
            log.error("Failed to parse evaluate task: data={}", data, e);
            return null;
        }
    }

    @Override
    protected String payloadIdentifier(NvcEvaluateStreamProducer.NvcEvaluateTask task) {
        return "sessionId=" + task.sessionId();
    }

    @Override
    protected void markProcessing(NvcEvaluateStreamProducer.NvcEvaluateTask task) {
        log.info("NVC最终评估开始处理: sessionId={}", task.sessionId());
    }

    @Override
    protected void processBusiness(NvcEvaluateStreamProducer.NvcEvaluateTask task) {
        List<NvcPracticeMessageEntity> messages =
            messageRepository.findBySessionIdOrderBySequenceNumAsc(task.sessionId());

        if (messages.isEmpty()) {
            log.warn("No messages found for session: {}", task.sessionId());
            return;
        }

        evaluationService.evaluateFinal(task.sessionId(), task.userId(), messages);
    }

    @Override
    protected void markCompleted(NvcEvaluateStreamProducer.NvcEvaluateTask task) {
        log.info("NVC最终评估完成: sessionId={}", task.sessionId());
    }

    @Override
    protected void markFailed(NvcEvaluateStreamProducer.NvcEvaluateTask task, String error) {
        log.error("NVC最终评估失败: sessionId={}, error={}", task.sessionId(), error);
    }

    @Override
    protected void retryMessage(NvcEvaluateStreamProducer.NvcEvaluateTask task, int retryCount) {
        log.warn("NVC最终评估重试: sessionId={}, retryCount={}", task.sessionId(), retryCount);
        // 重新发送到 Stream
        redisService().streamAdd(streamKey(), Map.of(
            "sessionId", String.valueOf(task.sessionId()),
            "userId", String.valueOf(task.userId()),
            "retryCount", String.valueOf(retryCount)
        ));
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd D:\code\nvc-guide && .\gradlew.bat compileJava 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/listener/
git commit -m "feat(step4): add NvcEvaluateStreamProducer and Consumer for async final evaluation"
```

---

### Task 5: NvcReportService — 报告生成 + PDF 导出

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcReportService.java`

**Interfaces:**
- Consumes: `NvcPracticeReport` (Task 2), `NvcEvaluationService` (Task 3), `NvcPracticeSessionRepository`, `NvcPracticeMessageRepository`, `PdfExportService`
- Produces: `NvcReportService.generateReport(sessionId)`, `NvcReportService.exportReportPdf(sessionId)` — 被 Task 6 (NvcReportController) 使用

- [ ] **Step 1: 创建 NvcReportService.java**

```java
package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.infrastructure.export.PdfExportService;
import nvc.guide.modules.nvcpractice.dto.NvcPracticeReport;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcMessageRole;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcReportService {

    private final NvcPracticeSessionRepository sessionRepository;
    private final NvcPracticeMessageRepository messageRepository;
    private final NvcEvaluationService evaluationService;
    private final PdfExportService pdfExportService;

    /**
     * 生成练习报告
     */
    public NvcPracticeReport generateReport(Long sessionId) {
        NvcPracticeSessionEntity session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NVC_SESSION_NOT_FOUND, "Session not found: " + sessionId));

        List<NvcPracticeMessageEntity> messages =
            messageRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);

        NvcEvaluationEntity finalEval = evaluationService.getFinalEvaluation(sessionId);

        // 如果还没有最终评估，先执行一次
        if (finalEval == null) {
            finalEval = evaluationService.evaluateFinal(
                sessionId, session.getUserId(), messages);
        }

        // 统计用户消息轮次
        long totalRounds = messages.stream()
            .filter(m -> m.getRole() == NvcMessageRole.USER)
            .count();

        return new NvcPracticeReport(
            sessionId,
            session.getPracticeMode(),
            session.getDifficulty(),
            totalRounds,
            session.getStartedAt(),
            session.getCompletedAt(),
            finalEval.getObservationScore(),
            finalEval.getFeelingScore(),
            finalEval.getNeedScore(),
            finalEval.getRequestScore(),
            finalEval.getEmpathyScore(),
            finalEval.getOverallScore(),
            finalEval.getObservationDetail(),
            finalEval.getFeelingDetail(),
            finalEval.getNeedDetail(),
            finalEval.getRequestDetail(),
            null, // empathyDetail — Entity 没有此字段，暂为 null
            finalEval.getStrengths(),
            finalEval.getImprovements(),
            finalEval.getReferenceExpressions(),
            finalEval.getSummary()
        );
    }

    /**
     * 导出练习报告为 PDF
     */
    public byte[] exportReportPdf(Long sessionId) {
        NvcPracticeReport report = generateReport(sessionId);
        return pdfExportService.exportNvcReport(report);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd D:\code\nvc-guide && .\gradlew.bat compileJava 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcReportService.java
git commit -m "feat(step4): add NvcReportService with report generation and PDF export"
```

---

### Task 6: PdfExportService 扩展 + NvcReportController

**Files:**
- Modify: `app/src/main/java/nvc/guide/infrastructure/export/PdfExportService.java:125-127`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcReportController.java`

**Interfaces:**
- Consumes: `NvcReportService.generateReport()`, `NvcReportService.exportReportPdf()` (Task 5), `NvcPracticeReport` (Task 2)
- Produces: REST API — `GET /api/nvc/report/sessions/{sessionId}`, `GET /api/nvc/report/sessions/{sessionId}/pdf`

- [ ] **Step 1: 在 PdfExportService 中添加 exportNvcReport 方法**

在 `PdfExportService.java` 中，将第 125-127 行的 TODO 注释替换为：

```java
    /**
     * 导出 NVC 练习报告为 PDF
     */
    public byte[] exportNvcReport(nvc.guide.modules.nvcpractice.dto.NvcPracticeReport report) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfDocument pdf = createPdfDocument(outputStream);
            Document document = new Document(pdf);
            PdfFont font = createChineseFont();

            // 1. 标题
            document.add(createTitle("NVC 练习报告", font));

            // 2. 会话信息
            document.add(createSubtitle("会话信息", font));
            document.add(createBody(String.format(
                "练习模式：%s  |  难度：%s  |  总轮次：%d",
                report.practiceMode(), report.difficulty(), report.totalRounds()), font));
            if (report.startedAt() != null) {
                document.add(createBody(String.format(
                    "开始时间：%s  |  完成时间：%s",
                    report.startedAt().format(DATE_FORMAT),
                    report.completedAt() != null
                        ? report.completedAt().format(DATE_FORMAT) : "进行中"), font));
            }

            // 3. 五维度评分表格
            document.add(createSubtitle("五维度评分", font));
            Table scoreTable = new Table(UnitValue.createPercentArray(
                new float[]{25, 15, 60}))
                .useAllAvailableWidth();
            scoreTable.addHeaderCell(createHeaderCell("维度", font));
            scoreTable.addHeaderCell(createHeaderCell("分数", font));
            scoreTable.addHeaderCell(createHeaderCell("分析", font));

            addScoreRow(scoreTable, "观察", report.observationScore(),
                report.observationDetail(), font);
            addScoreRow(scoreTable, "感受", report.feelingScore(),
                report.feelingDetail(), font);
            addScoreRow(scoreTable, "需求", report.needScore(),
                report.needDetail(), font);
            addScoreRow(scoreTable, "请求", report.requestScore(),
                report.requestDetail(), font);
            addScoreRow(scoreTable, "共情", report.empathyScore(),
                report.empathyDetail(), font);
            addScoreRow(scoreTable, "综合", report.overallScore(),
                null, font);
            document.add(scoreTable);

            // 4. 优势
            if (report.strengths() != null) {
                document.add(createSubtitle("优势", font));
                document.add(createBody(report.strengths(), font));
            }

            // 5. 改进建议
            if (report.improvements() != null) {
                document.add(createSubtitle("改进建议", font));
                document.add(createBody(report.improvements(), font));
            }

            // 6. 参考表达
            if (report.referenceExpressions() != null) {
                document.add(createSubtitle("参考 NVC 表达", font));
                document.add(createBody(report.referenceExpressions(), font));
            }

            // 7. 综合评价
            if (report.summary() != null) {
                document.add(createSubtitle("综合评价", font));
                document.add(createBody(report.summary(), font));
            }

            document.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("NVC报告PDF导出失败: sessionId={}", report.sessionId(), e);
            throw new nvc.guide.common.exception.BusinessException(
                nvc.guide.common.exception.ErrorCode.EXPORT_PDF_FAILED,
                "NVC报告PDF导出失败: " + e.getMessage());
        }
    }

    private void addScoreRow(Table table, String dimension, Integer score,
                              String detail, PdfFont font) {
        table.addCell(createDataCell(dimension, font));
        table.addCell(createDataCell(
            score != null ? String.valueOf(score) : "-", font));
        table.addCell(createDataCell(
            detail != null ? detail : "-", font));
    }
```

- [ ] **Step 2: 创建 NvcReportController.java**

```java
package nvc.guide.modules.nvcpractice.controller;

import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcpractice.dto.NvcPracticeReport;
import nvc.guide.modules.nvcpractice.service.NvcReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nvc/report")
@RequiredArgsConstructor
public class NvcReportController {

    private final NvcReportService reportService;

    /**
     * 获取练习报告
     */
    @GetMapping("/sessions/{sessionId}")
    public Result<NvcPracticeReport> getReport(@PathVariable Long sessionId) {
        NvcPracticeReport report = reportService.generateReport(sessionId);
        return Result.success(report);
    }

    /**
     * 导出练习报告为 PDF
     */
    @GetMapping("/sessions/{sessionId}/pdf")
    public ResponseEntity<byte[]> exportReportPdf(@PathVariable Long sessionId) {
        byte[] pdf = reportService.exportReportPdf(sessionId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=nvc-report-" + sessionId + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd D:\code\nvc-guide && .\gradlew.bat compileJava 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nvc/guide/infrastructure/export/PdfExportService.java
git add app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcReportController.java
git commit -m "feat(step4): add NvcReportController and PdfExportService NVC report export"
```

---

### Task 7: 集成到对话流程 — NvcPracticeDialogueService + NvcPracticeController

**Files:**
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeDialogueService.java:86-98`
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcPracticeController.java:115-121`

**Interfaces:**
- Consumes: `NvcEvaluationService.evaluateRealtime()` (Task 3), `NvcEvaluateStreamProducer.sendEvaluateTask()` (Task 4)

- [ ] **Step 1: 修改 NvcPracticeDialogueService — 注入 NvcEvaluationService**

在 `NvcPracticeDialogueService` 的字段声明中添加：

```java
private final NvcEvaluationService evaluationService;
```

- [ ] **Step 2: 在 sendMessage() 末尾添加实时评估**

在 `sendMessage()` 方法的 `return new DialogueResponse(...)` 之前，添加：

```java
    // 异步触发实时评估（不阻塞对话）
    try {
      evaluationService.evaluateRealtime(
          sessionId,
          session.getUserId(),
          userMessage,
          aiReply,
          session.getCurrentStep());
    } catch (Exception e) {
      log.warn("Realtime evaluation failed (non-blocking): sessionId={}",
          sessionId, e);
    }
```

注意：`aiReply` 变量在第 73 行已赋值（`String aiReply = orchestrator.executeAgent(...)`），`session` 变量在第 39 行已获取。

- [ ] **Step 3: 在 sendMessageStream() 的 doOnComplete 中添加实时评估**

在 `sendMessageStream()` 方法的 `doOnComplete()` 回调中，在 `messageRepository.save(aiMsg)` 之后添加：

```java
            // 实时评估（不阻塞流式输出）
            try {
              evaluationService.evaluateRealtime(
                  sessionId,
                  session.getUserId(),
                  userMessage,
                  fullReply.toString(),
                  currentStep);
            } catch (Exception e) {
              log.warn("Realtime evaluation failed in stream (non-blocking): sessionId={}",
                  sessionId, e);
            }
```

- [ ] **Step 4: 修改 NvcPracticeController — 注入 NvcEvaluateStreamProducer**

在 `NvcPracticeController` 的字段声明中添加：

```java
private final NvcEvaluateStreamProducer evaluateStreamProducer;
```

并在文件顶部添加 import：

```java
import nvc.guide.modules.nvcpractice.listener.NvcEvaluateStreamProducer;
```

- [ ] **Step 5: 在 completeSession() 中添加异步最终评估**

将 `completeSession()` 方法修改为：

```java
  @PostMapping("/sessions/{sessionId}/complete")
  public Result<PracticeSessionResponse> completeSession(
      @PathVariable Long sessionId) {
    NvcPracticeSessionEntity session =
        sessionService.completeSession(sessionId);

    // 推送异步最终评估任务
    try {
      evaluateStreamProducer.sendEvaluateTask(
          sessionId, session.getUserId());
    } catch (Exception e) {
      log.warn("Failed to send final evaluation task: sessionId={}",
          sessionId, e);
    }

    return Result.success(toSessionResponse(session));
  }
```

- [ ] **Step 6: 编译验证**

Run: `cd D:\code\nvc-guide && .\gradlew.bat compileJava 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeDialogueService.java
git add app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcPracticeController.java
git commit -m "feat(step4): integrate realtime evaluation into dialogue flow and async final evaluation into completeSession"
```

---

### Task 8: 最终验证 + 总提交

- [ ] **Step 1: 完整编译**

Run: `cd D:\code\nvc-guide && .\gradlew.bat clean compileJava 2>&1 | Select-Object -Last 15`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 检查所有新增文件**

Run: `git status`
Expected: 7 个新文件 + 3 个修改文件

- [ ] **Step 3: 最终提交（如有遗漏）**

```bash
git add -A
git commit -m "feat(step4): complete NVC evaluation engine — realtime evaluation, async final evaluation, report generation, PDF export"
```
