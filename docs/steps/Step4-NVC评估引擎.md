# Step 4：NVC 评估引擎

> 目标：实现 NVC 表达评估系统 — 实时逐轮评估 + 最终综合评估 + 练习报告生成 + PDF 导出
>
> 预计耗时：2-3 天
>
> 前置条件：Step 3 已完成，对话流程能跑通
>
> 技术亮点：实时流式评估、StructuredOutputInvoker 结构化输出、异步评估 + PDF 导出

---

## 4.1 本步要创建的文件清单

```
app/src/main/java/interview/guide/modules/nvcpractice/
├── service/
│   ├── NvcEvaluationService.java          ← 评估核心（调用 LLM 评估四要素）
│   └── NvcReportService.java              ← 练习报告生成 + PDF 导出
├── dto/
│   ├── NvcEvaluationResult.java           ← 评估结果 DTO（结构化输出）
│   └── NvcPracticeReport.java             ← 练习报告 DTO
├── controller/
│   └── NvcReportController.java           ← 报告 API

app/src/main/java/interview/guide/modules/nvcpractice/listener/
├── NvcEvaluateStreamProducer.java         ← 异步评估任务生产者
└── NvcEvaluateStreamConsumer.java         ← 异步评估任务消费者

app/src/main/resources/prompts/
├── nvc-evaluation-system.st               ← 评估系统提示词
└── nvc-evaluation-user.st                 ← 评估用户提示词模板
```

---

## 4.2 NvcEvaluationResult — 评估结果 DTO

路径：`app/src/main/java/interview/guide/modules/nvcpractice/dto/NvcEvaluationResult.java`

```java
package interview.guide.modules.nvcpractice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * NVC 评估结果 - 由 LLM 结构化输出
 */
public record NvcEvaluationResult(
    @JsonProperty("observation_score")
    Integer observationScore,

    @JsonProperty("feeling_score")
    Integer feelingScore,

    @JsonProperty("need_score")
    Integer needScore,

    @JsonProperty("request_score")
    Integer requestScore,

    @JsonProperty("overall_score")
    Integer overallScore,

    @JsonProperty("observation_detail")
    String observationDetail,

    @JsonProperty("feeling_detail")
    String feelingDetail,

    @JsonProperty("need_detail")
    String needDetail,

    @JsonProperty("request_detail")
    String requestDetail,

    String strengths,
    String improvements,

    @JsonProperty("reference_expressions")
    String referenceExpressions,

    String summary
) {}
```

---

## 4.3 NvcEvaluationService — 评估核心

路径：`app/src/main/java/interview/guide/modules/nvcpractice/service/NvcEvaluationService.java`

### 功能

- 实时评估：每轮用户回复后，评估其 NVC 表达质量
- 最终评估：练习结束后，对整体对话进行综合评估
- 使用 StructuredOutputInvoker 保证输出可解析
- 评估结果保存到数据库

### 实现要点

```java
package interview.guide.modules.nvcpractice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.nvcpractice.dto.NvcEvaluationResult;
import interview.guide.modules.nvcpractice.model.*;
import interview.guide.modules.nvcpractice.repository.NvcEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcEvaluationService {

    private final LlmProviderRegistry llmProviderRegistry;
    private final NvcEvaluationRepository evaluationRepository;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final ObjectMapper objectMapper;

    private String evaluationSystemPrompt;

    /**
     * 加载评估系统提示词（懒加载）
     */
    private String getEvaluationSystemPrompt() {
        if (evaluationSystemPrompt == null) {
            try {
                ClassPathResource resource = new ClassPathResource("prompts/nvc-evaluation-system.st");
                evaluationSystemPrompt = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Failed to load evaluation system prompt", e);
                evaluationSystemPrompt = getDefaultEvaluationSystemPrompt();
            }
        }
        return evaluationSystemPrompt;
    }

    /**
     * 实时评估：评估用户单轮回复的 NVC 表达质量
     */
    public NvcEvaluationEntity evaluateRealtime(Long sessionId, Long userId, String userMessage,
                                                  String aiContext, NvcPracticeStep currentStep) {
        String prompt = buildRealtimeEvaluationPrompt(userMessage, aiContext, currentStep);
        NvcEvaluationResult result = invokeEvaluation(prompt);

        NvcEvaluationEntity entity = buildEvaluationEntity(sessionId, userId, result, NvcEvaluationType.REALTIME);
        NvcEvaluationEntity saved = evaluationRepository.save(entity);
        log.info("Realtime evaluation saved: sessionId={}, overallScore={}", sessionId, result.overallScore());
        return saved;
    }

    /**
     * 最终评估：对整个练习对话进行综合评估
     */
    public NvcEvaluationEntity evaluateFinal(Long sessionId, Long userId, List<NvcPracticeMessageEntity> messages) {
        String prompt = buildFinalEvaluationPrompt(messages);
        NvcEvaluationResult result = invokeEvaluation(prompt);

        NvcEvaluationEntity entity = buildEvaluationEntity(sessionId, userId, result, NvcEvaluationType.FINAL);
        NvcEvaluationEntity saved = evaluationRepository.save(entity);
        log.info("Final evaluation saved: sessionId={}, overallScore={}", sessionId, result.overallScore());
        return saved;
    }

    /**
     * 获取会话的最新实时评估
     */
    public NvcEvaluationEntity getLatestRealtimeEvaluation(Long sessionId) {
        return evaluationRepository.findBySessionIdAndEvaluationType(sessionId, NvcEvaluationType.REALTIME)
            .orElse(null);
    }

    /**
     * 获取会话的最终评估
     */
    public NvcEvaluationEntity getFinalEvaluation(Long sessionId) {
        return evaluationRepository.findBySessionIdAndEvaluationType(sessionId, NvcEvaluationType.FINAL)
            .orElse(null);
    }

    /**
     * 调用 LLM 进行评估（使用 StructuredOutputInvoker）
     */
    private NvcEvaluationResult invokeEvaluation(String userPrompt) {
        ChatClient chatClient = llmProviderRegistry.getPlainChatClient(null);

        String result = structuredOutputInvoker.invokeStructuredOutput(
            getEvaluationSystemPrompt(),
            chatClient,
            output -> {
                // BeanOutputConverter 会自动解析 JSON
                return output;
            }
        );

        try {
            return objectMapper.readValue(result, NvcEvaluationResult.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse evaluation result: {}", result, e);
            throw new BusinessException(ErrorCode.NVC_EVALUATION_FAILED,
                "评估结果解析失败，请重试");
        }
    }

    /**
     * 构建实时评估的用户提示词
     */
    private String buildRealtimeEvaluationPrompt(String userMessage, String aiContext, NvcPracticeStep currentStep) {
        return """
            请评估以下用户表达的NVC质量：

            [对话上下文]
            %s

            [用户最新表达]
            %s

            当前练习步骤：%s

            请严格按照JSON格式返回评估结果。评估时重点关注：
            1. 用户是否区分了观察和评论
            2. 用户是否表达了真实感受而非想法
            3. 用户是否识别了深层需求
            4. 用户是否提出了具体可执行的请求

            如果当前是结构化四步练习的特定步骤，请重点评估该步骤对应的要素。
            """.formatted(
                aiContext != null ? aiContext : "（首次对话）",
                userMessage,
                currentStep != null ? currentStep : "自由练习"
            );
    }

    /**
     * 构建最终评估的用户提示词
     */
    private String buildFinalEvaluationPrompt(List<NvcPracticeMessageEntity> messages) {
        StringBuilder dialogue = new StringBuilder();
        for (NvcPracticeMessageEntity msg : messages) {
            String role = msg.getRole() == NvcMessageRole.USER ? "用户" : "AI";
            dialogue.append(String.format("[%s] %s\n", role, msg.getContent()));
        }

        return """
            请对以下完整的NVC练习对话进行综合评估：

            [完整对话记录]
            %s

            请从以下维度进行综合评估：
            1. 观察：用户在整个对话中是否能区分观察和评论
            2. 感受：用户是否能准确识别和表达感受
            3. 需求：用户是否能发现深层需求
            4. 请求：用户是否能提出具体可执行的请求
            5. 共情：用户是否展现了倾听和理解对方的能力

            请综合所有轮次的表现给出整体评价，而不是只看最后一轮。
            """.formatted(dialogue);
    }

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
     * 默认评估系统提示词（兜底）
     */
    private String getDefaultEvaluationSystemPrompt() {
        return """
            你是NVC表达评估专家。请分析用户的表达，评估其NVC四要素的质量。

            请以JSON格式返回：
            {
              "observation_score": 0-100,
              "feeling_score": 0-100,
              "need_score": 0-100,
              "request_score": 0-100,
              "overall_score": 0-100,
              "observation_detail": "观察维度分析",
              "feeling_detail": "感受维度分析",
              "need_detail": "需求维度分析",
              "request_detail": "请求维度分析",
              "strengths": "优势总结",
              "improvements": "改进建议",
              "reference_expressions": "参考NVC表达",
              "summary": "综合评价"
            }
            """;
    }
}
```

---

## 4.4 NvcEvaluationPrompt 模板

### nvc-evaluation-system.st

路径：`app/src/main/resources/prompts/nvc-evaluation-system.st`

```
你是NVC（非暴力沟通）表达评估专家。你的职责是严格、客观地分析用户的表达，评估其NVC四要素的质量。

评估维度和标准：

1. 观察（Observation）0-100分：
   - 90-100：清晰描述客观事实，完全区分了观察和评论
   - 70-89：基本描述了事实，但混入少量主观判断
   - 50-69：事实和判断混杂，需要改进
   - 0-49：主要是评判性语言，缺乏客观观察

2. 感受（Feeling）0-100分：
   - 90-100：准确表达了真实感受，使用了具体的情绪词汇
   - 70-89：表达了感受，但不够具体或混入了想法
   - 50-69：用想法代替感受（如"我觉得你不尊重我"）
   - 0-49：没有表达感受，或完全用评判代替

3. 需求（Need）0-100分：
   - 90-100：清晰识别了深层需求，区分了需求和策略
   - 70-89：表达了需求，但可能不够深入或混入策略
   - 50-69：隐含了需求但没有明确表达
   - 0-49：没有识别需求，或把策略当需求

4. 请求（Request）0-100分：
   - 90-100：提出了具体、可执行、正向的请求，允许对方拒绝
   - 70-89：有请求但不够具体或带有命令意味
   - 50-69：请求模糊或是否定式表达
   - 0-49：没有请求，或是指责/命令

5. 综合得分 = 四项平均分（四舍五入）

请严格以以下JSON格式返回评估结果，不要包含任何其他文字：

{
  "observation_score": 85,
  "feeling_score": 70,
  "need_score": 65,
  "request_score": 55,
  "overall_score": 69,
  "observation_detail": "用户描述了具体事实...",
  "feeling_detail": "用户表达了失落感...",
  "need_detail": "用户隐含了被认可的需求...",
  "request_detail": "用户的请求不够具体...",
  "strengths": "观察维度表达清晰，能区分事实和评判",
  "improvements": "请求需要更具体，建议用'你愿意...'的句式",
  "reference_expressions": "参考表达：'我注意到...（观察），我感到...（感受），因为我需要...（需求），你愿意...吗？（请求）'",
  "summary": "整体NVC表达中等偏上，观察和感受维度较好，请求维度需要加强"
}
```

### nvc-evaluation-user.st

路径：`app/src/main/resources/prompts/nvc-evaluation-user.st`

```
请评估以下用户表达的NVC质量：

[对话上下文]
{context}

[用户最新表达]
{userMessage}

当前练习模式：{practiceMode}
当前练习步骤：{currentStep}

请严格按照JSON格式返回评估结果。
```

---

## 4.5 异步评估 — Redis Stream

### NvcEvaluateStreamProducer

路径：`app/src/main/java/interview/guide/modules/nvcpractice/listener/NvcEvaluateStreamProducer.java`

```java
package interview.guide.modules.nvcpractice.listener;

import interview.guide.common.async.AbstractStreamProducer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * NVC 练习评估异步任务生产者
 * 练习结束后推送评估任务到 Redis Stream
 */
@Component
@Slf4j
public class NvcEvaluateStreamProducer extends AbstractStreamProducer<NvcEvaluateTask> {

    @Override
    protected String getStreamKey() {
        return "nvc:evaluate:stream";
    }

    public void sendEvaluateTask(Long sessionId, Long userId) {
        NvcEvaluateTask task = new NvcEvaluateTask(sessionId, userId);
        send(task);
        log.info("NVC evaluate task sent: sessionId={}, userId={}", sessionId, userId);
    }
}

/**
 * 评估任务数据
 */
record NvcEvaluateTask(Long sessionId, Long userId) {}
```

### NvcEvaluateStreamConsumer

路径：`app/src/main/java/interview/guide/modules/nvcpractice/listener/NvcEvaluateStreamConsumer.java`

```java
package interview.guide.modules.nvcpractice.listener;

import interview.guide.common.async.AbstractStreamConsumer;
import interview.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import interview.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import interview.guide.modules.nvcpractice.service.NvcEvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class NvcEvaluateStreamConsumer extends AbstractStreamConsumer<NvcEvaluateTask> {

    private final NvcEvaluationService evaluationService;
    private final NvcPracticeMessageRepository messageRepository;

    public NvcEvaluateStreamConsumer(NvcEvaluationService evaluationService,
                                      NvcPracticeMessageRepository messageRepository) {
        this.evaluationService = evaluationService;
        this.messageRepository = messageRepository;
    }

    @Override
    protected String getStreamKey() {
        return "nvc:evaluate:stream";
    }

    @Override
    protected String getConsumerGroup() {
        return "nvc-evaluate-group";
    }

    @Override
    protected void processMessage(NvcEvaluateTask task) {
        log.info("Processing NVC evaluate task: sessionId={}", task.sessionId());

        try {
            // 加载完整对话记录
            List<NvcPracticeMessageEntity> messages =
                messageRepository.findBySessionIdOrderBySequenceNumAsc(task.sessionId());

            if (messages.isEmpty()) {
                log.warn("No messages found for session: {}", task.sessionId());
                return;
            }

            // 执行最终评估
            evaluationService.evaluateFinal(task.sessionId(), task.userId(), messages);
            log.info("NVC final evaluation completed: sessionId={}", task.sessionId());

        } catch (Exception e) {
            log.error("NVC evaluation failed: sessionId={}", task.sessionId(), e);
            throw e; // 让 AbstractStreamConsumer 处理重试
        }
    }
}
```

---

## 4.6 NvcReportService — 练习报告生成

路径：`app/src/main/java/interview/guide/modules/nvcpractice/service/NvcReportService.java`

```java
package interview.guide.modules.nvcpractice.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.export.PdfExportService;
import interview.guide.modules.nvcpractice.dto.NvcPracticeReport;
import interview.guide.modules.nvcpractice.model.*;
import interview.guide.modules.nvcpractice.repository.NvcEvaluationRepository;
import interview.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import interview.guide.modules.nvcpractice.repository.NvcPracticeSessionRepository;
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
    private final NvcEvaluationRepository evaluationRepository;
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
            finalEval = evaluationService.evaluateFinal(sessionId, session.getUserId(), messages);
        }

        // 统计信息
        int totalRounds = messages.stream()
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
            finalEval.getOverallScore(),
            finalEval.getObservationDetail(),
            finalEval.getFeelingDetail(),
            finalEval.getNeedDetail(),
            finalEval.getRequestDetail(),
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

---

## 4.7 NvcPracticeReport DTO

路径：`app/src/main/java/interview/guide/modules/nvcpractice/dto/NvcPracticeReport.java`

```java
package interview.guide.modules.nvcpractice.dto;

import interview.guide.modules.nvcpractice.model.NvcDifficulty;
import interview.guide.modules.nvcpractice.model.NvcPracticeMode;

import java.time.LocalDateTime;

public record NvcPracticeReport(
    Long sessionId,
    NvcPracticeMode practiceMode,
    NvcDifficulty difficulty,
    long totalRounds,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    Integer observationScore,
    Integer feelingScore,
    Integer needScore,
    Integer requestScore,
    Integer overallScore,
    String observationDetail,
    String feelingDetail,
    String needDetail,
    String requestDetail,
    String strengths,
    String improvements,
    String referenceExpressions,
    String summary
) {}
```

---

## 4.8 NvcReportController — 报告 API

路径：`app/src/main/java/interview/guide/modules/nvcpractice/controller/NvcReportController.java`

```java
package interview.guide.modules.nvcpractice.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.nvcpractice.dto.NvcPracticeReport;
import interview.guide.modules.nvcpractice.service.NvcReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

---

## 4.9 集成到对话流程

在 `NvcPracticeDialogueService.sendMessage()` 方法中，**每轮对话后**异步触发实时评估：

```java
// 在 sendMessage() 方法的最后，保存 AI 回复之后添加：

// 异步触发实时评估（不阻塞对话）
// 注意：这里用同步方式简化，实际可以用 @Async 或线程池
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
    // 评估失败不影响对话流程
}
```

在 `NvcPracticeController.completeSession()` 方法中，结束会话时推送异步最终评估：

```java
// 在 completeSession() 方法中添加：
@PostMapping("/sessions/{sessionId}/complete")
public Result<PracticeSessionResponse> completeSession(@PathVariable Long sessionId) {
    NvcPracticeSessionEntity session = sessionService.completeSession(sessionId);

    // 推送异步最终评估任务
    evaluateStreamProducer.sendEvaluateTask(sessionId, session.getUserId());

    return Result.success(toSessionResponse(session));
}
```

---

## 4.10 PdfExportService 扩展

在 `infrastructure/export/PdfExportService.java` 中添加 NVC 报告导出方法：

```java
/**
 * 导出 NVC 练习报告
 */
public byte[] exportNvcReport(NvcPracticeReport report) {
    // 使用 iText 8 生成 PDF
    // 内容包括：
    // 1. 报告标题 + 会话信息
    // 2. 四维度评分雷达图（可以用表格替代，雷达图在前端展示）
    // 3. 各维度详细分析
    // 4. 优势和改进建议
    // 5. 参考 NVC 表达
    // 6. 综合评价

    // 具体实现参考 interview-guide 中现有的 PDF 导出逻辑
    // ...
}
```

---

## 4.11 验证清单

```
□ NvcEvaluationService 编译通过
  □ evaluateRealtime() 能正确评估单轮表达并返回结构化结果
  □ evaluateFinal() 能正确评估完整对话
  □ 评估结果四维度分数合理（0-100）
  □ 评估结果包含详细分析和建议
□ NvcReportService 编译通过
  □ generateReport() 能生成完整报告
  □ exportReportPdf() 能生成 PDF 文件
□ 异步评估编译通过
  □ NvcEvaluateStreamProducer 能发送任务
  □ NvcEvaluateStreamConsumer 能接收并执行评估
□ 对话流程集成验证：
  □ 每轮对话后数据库中有对应的实时评估记录
  □ 结束会话后有最终评估记录
  □ 评估失败不影响对话流程
□ API 测试：
  □ GET /api/nvc/report/sessions/{id} 返回报告
  □ GET /api/nvc/report/sessions/{id}/pdf 下载 PDF
□ Git 提交："Step 4: Add NVC evaluation engine with async evaluation and PDF export"
```

---

## 4.12 测试场景

### 测试：评估质量验证

用一段已知质量的 NVC 表达测试评估准确性：

**高质量表达（预期 80+ 分）**：
> "我注意到上次汇报中你提到了三个技术方案，但没有提及这些方案是我设计和实现的。（观察）我感到有些失落和不公平，因为我的贡献没有被看到。（感受）我需要我的工作得到认可和尊重。（需求）你能在下次汇报时，各自介绍自己负责的部分吗？（请求）"

**低质量表达（预期 40-60 分）**：
> "你总是抢我的功劳，太自私了。你应该在汇报时提到我。"

验证评估结果的分数差异是否合理。
