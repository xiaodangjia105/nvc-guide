# Structured Four-Step Practice Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement NVC structured four-step practice mode (Observe→Feeling→Need→Request) with step-level coaching, evaluation, and automatic progression.

**Architecture:** Extend existing NVC practice system with step-specific Agent coaches, configurable progression thresholds, and automatic step advancement based on evaluation scores. Each step has independent coaching, evaluation, and progression logic.

**Tech Stack:** Spring Boot 4.0, Java 21, Spring Data JPA, Redis, StringTemplate (.st files)

## Global Constraints

- Package: `nvc.guide.modules.nvcpractice` (not `interview.guide`)
- Follow existing code patterns: 2-space indent, 100-char line limit
- Use `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` for entities
- Use `record` for DTOs
- Throw `BusinessException(ErrorCode.XXX, message)` for business errors
- Use `@Transactional` on service layer, not in transactions calling external APIs
- Use SLF4J logging with structured format

---

## File Structure

### Files to Create

1. **`app/src/main/resources/prompts/nvc-step-observe-system.st`** - Observe step coach prompt
2. **`app/src/main/resources/prompts/nvc-step-feeling-system.st`** - Feeling step coach prompt
3. **`app/src/main/resources/prompts/nvc-step-need-system.st`** - Need step coach prompt
4. **`app/src/main/resources/prompts/nvc-step-request-system.st`** - Request step coach prompt
5. **`app/src/main/java/nvc/guide/modules/nvcpractice/dto/StepProgressDTO.java`** - Step progress DTO
6. **`app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcStructuredPracticeService.java`** - Structured practice service
7. **`app/src/test/java/nvc/guide/modules/nvcpractice/service/NvcStructuredPracticeServiceTest.java`** - Service tests
8. **`app/src/test/java/nvc/guide/modules/nvcpractice/controller/NvcPracticeControllerStepTest.java`** - Controller tests

### Files to Modify

1. **`app/src/main/java/nvc/guide/modules/nvcpractice/model/NvcAgentConfigEntity.java`** - Add step_advance_threshold, max_step_attempts, step_timeout_minutes fields
2. **`app/src/main/java/nvc/guide/modules/nvcpractice/dto/AgentConfigDTO.java`** - Add new fields to DTO
3. **`app/src/main/java/nvc/guide/modules/nvcpractice/dto/AgentConfigUpdateRequest.java`** - Add new fields to request
4. **`app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentConfigService.java`** - Update config mapping
5. **`app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcPracticeController.java`** - Add step progress APIs
6. **`app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeDialogueService.java`** - Add step advancement logic

---

## Task 1: Create Step Coach Prompt Templates

**Files:**
- Create: `app/src/main/resources/prompts/nvc-step-observe-system.st`
- Create: `app/src/main/resources/prompts/nvc-step-feeling-system.st`
- Create: `app/src/main/resources/prompts/nvc-step-need-system.st`
- Create: `app/src/main/resources/prompts/nvc-step-request-system.st`

**Interfaces:**
- Consumes: None (static template files)
- Produces: 4 prompt template files used by NvcAgentChatService

- [ ] **Step 1: Create nvc-step-observe-system.st**

```st
你是NVC练习的"观察步骤教练"。你的职责是引导用户学会区分"观察"（客观描述事实）和"评论"（带有主观判断）。

## 教学要点

1. **观察**：描述你看到、听到、触摸到的具体事实，不加解释和判断
   - ✅ "你这周有三天都是晚上10点以后回家"
   - ❌ "你总是不着家"

2. **评论**：加入了个人解读、推断、标签
   - ❌ "你不关心这个家"
   - ✅ "上周你没有参加孩子的家长会"

3. **常见评判词汇识别**：
   - "总是"、"从来"、"每次" → 以偏概全
   - "应该"、"必须" → 强加期望
   - "自私"、"懒"、"不负责任" → 人身标签

## 引导方式

1. 给出一段冲突描述，让用户识别其中的观察和评论
2. 用户练习后，逐句分析哪些是观察、哪些是评论
3. 提供改写建议，帮助用户把评论转化为观察
4. 用具体例子帮助用户理解区别

## 回复风格

- 每次回复控制在 3-5 句话
- 先肯定用户做得好的地方，再指出改进点
- 给出具体的改写示例
- 不要长篇大论教学，而是在对话中自然引导

场景信息会以[练习场景]的形式提供。
```

- [ ] **Step 2: Create nvc-step-feeling-system.st**

```st
你是NVC练习的"感受步骤教练"。你的职责是引导用户学会识别和表达真实的感受，而不是想法和判断。

## 教学要点

1. **感受**：描述你内心的情绪状态
   - ✅ "我感到失落"、"我很担心"、"我有些委屈"

2. **想法/判断**：对他人行为的解读
   - ❌ "我觉得你不尊重我"（这是对对方的判断）
   - ❌ "我觉得自己没用"（这是对自己的评判）

3. **常见误区**：
   - 用"我觉得"开头的往往是想法，不是感受
   - "我感到被忽视" → "被忽视"是想法，"感到孤独/失落"才是感受
   - "我感到不公平" → "不公平"是判断，"感到委屈/愤怒"才是感受

4. **感受词汇表**：
   - 需求被满足：开心、感激、温暖、安心、兴奋、满足、欣慰
   - 需求未被满足：失落、焦虑、委屈、疲惫、孤独、沮丧、不安

## 引导方式

1. 给出一段表达，让用户区分哪些是感受、哪些是想法
2. 帮助用户找到更准确的感受词汇
3. 引导用户从"你让我感到..."转向"我感到..."的表达方式
4. 肯定用户表达感受的勇气

场景信息会以[练习场景]的形式提供。
```

- [ ] **Step 3: Create nvc-step-need-system.st**

```st
你是NVC练习的"需求步骤教练"。你的职责是引导用户从感受追溯到深层需求。

## 教学要点

1. **需求**：人类共通的基本需求
   - 安全感、尊重、理解、归属感、自主性、成长、陪伴、认可、公平、自由

2. **策略**：满足需求的具体方式（不是需求本身）
   - 需求："我需要陪伴"
   - 策略："你每天晚上7点陪我吃饭"（这是策略，不是需求）

3. **区分需求和策略**：
   - 需求是普适的（人人都需要陪伴）
   - 策略是具体的（每天7点陪吃饭）
   - 当策略被拒绝时，可以换策略，但需求不变

4. **从感受追溯需求**：
   - 感到失落 → 可能需要被认可、被看见
   - 感到焦虑 → 可能需要安全感、确定性
   - 感到愤怒 → 可能需要公平、尊重
   - 感到孤独 → 可能需要陪伴、连接

## 引导方式

1. 给出一段感受表达，引导用户思考"这种感受背后，你真正需要的是什么？"
2. 帮助用户区分需求和策略
3. 引导用户用"我需要..."的句式表达需求
4. 如果用户把策略当需求，温和地引导他们思考"为什么你需要这个？背后更深层的需要是什么？"

场景信息会以[练习场景]的形式提供。
```

- [ ] **Step 4: Create nvc-step-request-system.st**

```st
你是NVC练习的"请求步骤教练"。你的职责是引导用户将需求转化为具体、可执行、正向的请求。

## 教学要点

1. **请求 vs 命令**：
   - 请求允许对方说"不"，并愿意协商
   - 命令不允许拒绝，对方必须服从
   - 判断标准：如果对方拒绝，你是否会惩罚或施压？

2. **正向表达**：
   - ✅ "你能每周六花一小时陪我散步吗？"
   - ❌ "你不要再加班了"
   - 说你想要什么，而不是你不想要什么

3. **具体可行**：
   - ✅ "你能在下次汇报时提到我负责的部分吗？"
   - ❌ "你要尊重我的付出"
   - 越具体越好，避免模糊

4. **好的请求公式**：
   - "你愿意 + 具体行动 + 吗？"
   - "我希望 + 具体行为 + 你觉得可以吗？"

## 引导方式

1. 给出一个需求，引导用户转化为具体请求
2. 检查请求是否满足：正向、具体、可执行、允许拒绝
3. 帮助用户练习用"你愿意...吗？"的句式
4. 模拟对方可能的回应，练习协商

场景信息会以[练习场景]的形式提供。
```

- [ ] **Step 5: Verify prompt templates exist**

Run: `ls -la app/src/main/resources/prompts/nvc-step-*.st`
Expected: 4 files created with correct content

- [ ] **Step 6: Commit prompt templates**

```bash
git add app/src/main/resources/prompts/nvc-step-*.st
git commit -m "feat(step7): add step coach prompt templates"
```

---

## Task 2: Extend NvcAgentConfigEntity with Step Configuration

**Files:**
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/model/NvcAgentConfigEntity.java`
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/AgentConfigDTO.java`
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/AgentConfigUpdateRequest.java`

**Interfaces:**
- Consumes: Existing NvcAgentConfigEntity
- Produces: Extended entity with step_advance_threshold, max_step_attempts, step_timeout_minutes fields

- [ ] **Step 1: Add fields to NvcAgentConfigEntity**

```java
// Add after line 57 (configMetadata field)

@Column(name = "step_advance_threshold")
@Builder.Default
private Integer stepAdvanceThreshold = 70;

@Column(name = "max_step_attempts")
@Builder.Default
private Integer maxStepAttempts = 10;

@Column(name = "step_timeout_minutes")
@Builder.Default
private Integer stepTimeoutMinutes = 30;
```

- [ ] **Step 2: Update AgentConfigDTO**

```java
package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcAgentScene;

/**
 * Agent 配置响应 DTO
 */
public record AgentConfigDTO(
    NvcAgentScene agentScene,
    String displayName,
    String description,
    String systemPrompt,
    String modelProvider,
    String modelName,
    Double temperature,
    Integer maxTokens,
    Double topP,
    Boolean isEnabled,
    Integer stepAdvanceThreshold,
    Integer maxStepAttempts,
    Integer stepTimeoutMinutes
) {}
```

- [ ] **Step 3: Update AgentConfigUpdateRequest**

```java
package nvc.guide.modules.nvcpractice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Agent 配置更新请求
 * 所有字段可选，非 null 字段才会被更新
 */
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

    Boolean isEnabled,

    @Min(0) @Max(100)
    Integer stepAdvanceThreshold,

    @Min(1) @Max(100)
    Integer maxStepAttempts,

    @Min(1) @Max(1440)
    Integer stepTimeoutMinutes
) {}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileJava`
Expected: Compilation successful

- [ ] **Step 5: Commit entity changes**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/model/NvcAgentConfigEntity.java
git add app/src/main/java/nvc/guide/modules/nvcpractice/dto/AgentConfigDTO.java
git add app/src/main/java/nvc/guide/modules/nvcpractice/dto/AgentConfigUpdateRequest.java
git commit -m "feat(step7): extend NvcAgentConfigEntity with step configuration fields"
```

---

## Task 3: Update NvcAgentConfigService for New Fields

**Files:**
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentConfigService.java`

**Interfaces:**
- Consumes: NvcAgentConfigEntity, AgentConfigDTO, AgentConfigUpdateRequest
- Produces: Updated service with mapping for new fields

- [ ] **Step 1: Read current NvcAgentConfigService**

Read: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentConfigService.java`

- [ ] **Step 2: Update toDTO method**

Add mapping for new fields in toDTO method:

```java
private AgentConfigDTO toDTO(NvcAgentConfigEntity entity) {
    return new AgentConfigDTO(
        entity.getAgentScene(),
        entity.getDisplayName(),
        entity.getDescription(),
        entity.getSystemPrompt(),
        entity.getModelProvider(),
        entity.getModelName(),
        entity.getTemperature(),
        entity.getMaxTokens(),
        entity.getTopP(),
        entity.getIsEnabled(),
        entity.getStepAdvanceThreshold(),
        entity.getMaxStepAttempts(),
        entity.getStepTimeoutMinutes()
    );
}
```

- [ ] **Step 3: Update updateConfig method**

Add logic to update new fields if present in request:

```java
if (request.stepAdvanceThreshold() != null) {
    entity.setStepAdvanceThreshold(request.stepAdvanceThreshold());
}
if (request.maxStepAttempts() != null) {
    entity.setMaxStepAttempts(request.maxStepAttempts());
}
if (request.stepTimeoutMinutes() != null) {
    entity.setStepTimeoutMinutes(request.stepTimeoutMinutes());
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileJava`
Expected: Compilation successful

- [ ] **Step 5: Commit service changes**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentConfigService.java
git commit -m "feat(step7): update NvcAgentConfigService for new step configuration fields"
```

---

## Task 4: Create StepProgressDTO

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/StepProgressDTO.java`

**Interfaces:**
- Consumes: NvcPracticeStep enum
- Produces: StepProgressDTO record

- [ ] **Step 1: Create StepProgressDTO**

```java
package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import java.time.LocalDateTime;

/**
 * 步骤进度 DTO
 */
public record StepProgressDTO(
    NvcPracticeStep currentStep,
    int stepIndex,
    int totalSteps,
    Integer currentScore,
    boolean canAdvance,
    String stepDescription,
    Integer maxAttempts,
    Integer attemptsUsed,
    LocalDateTime stepStartedAt,
    Integer timeoutMinutes
) {}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: Compilation successful

- [ ] **Step 3: Commit DTO**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/dto/StepProgressDTO.java
git commit -m "feat(step7): add StepProgressDTO"
```

---

## Task 5: Create NvcStructuredPracticeService

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcStructuredPracticeService.java`
- Create: `app/src/test/java/nvc/guide/modules/nvcpractice/service/NvcStructuredPracticeServiceTest.java`

**Interfaces:**
- Consumes: NvcPracticeSessionService, NvcEvaluationRepository, NvcAgentConfigService, RedisService
- Produces: NvcStructuredPracticeService with getStepProgress(), advanceStep(), canAdvance(), resetStep()

- [ ] **Step 1: Create NvcStructuredPracticeService**

```java
package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcpractice.dto.StepProgressDTO;
import nvc.guide.modules.nvcpractice.model.*;
import nvc.guide.modules.nvcpractice.repository.NvcEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcStructuredPracticeService {

    private final NvcPracticeSessionService sessionService;
    private final NvcEvaluationRepository evaluationRepository;
    private final NvcAgentConfigService agentConfigService;
    private final RedisService redisService;

    private static final String STEP_PROGRESS_CACHE_PREFIX = "nvc:step:progress:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /**
     * 获取步骤进度
     */
    public StepProgressDTO getStepProgress(Long sessionId) {
        NvcPracticeSessionEntity session = sessionService.getSession(sessionId);

        if (session.getPracticeMode() != NvcPracticeMode.STRUCTURED_FOUR_STEP) {
            return null;
        }

        NvcPracticeStep currentStep = session.getCurrentStep();
        if (currentStep == null || currentStep == NvcPracticeStep.COMPLETED) {
            return new StepProgressDTO(
                NvcPracticeStep.COMPLETED, 4, 4, null, false,
                "四步练习全部完成！进入综合练习。",
                null, null, null, null
            );
        }

        NvcEvaluationEntity latestEval = evaluationRepository
            .findBySessionIdAndEvaluationType(sessionId, NvcEvaluationType.REALTIME)
            .orElse(null);

        Integer currentStepScore = null;
        if (latestEval != null) {
            currentStepScore = getStepScore(latestEval, currentStep);
        }

        NvcAgentConfigEntity config = agentConfigService.getConfig(stepToCoach(currentStep));
        Integer threshold = config.getStepAdvanceThreshold() != null
            ? config.getStepAdvanceThreshold() : 70;

        return new StepProgressDTO(
            currentStep,
            getStepIndex(currentStep),
            4,
            currentStepScore,
            currentStepScore != null && currentStepScore >= threshold,
            getStepDescription(currentStep),
            config.getMaxStepAttempts(),
            null, // TODO: track attempts
            null, // TODO: track step start time
            config.getStepTimeoutMinutes()
        );
    }

    /**
     * 推进步骤
     */
    @Transactional
    public StepProgressDTO advanceStep(Long sessionId) {
        NvcPracticeSessionEntity session = sessionService.getSession(sessionId);
        NvcPracticeStep currentStep = session.getCurrentStep();

        if (session.getPracticeMode() != NvcPracticeMode.STRUCTURED_FOUR_STEP) {
            throw new BusinessException(
                ErrorCode.NVC_SESSION_NOT_FOUND,
                "Session is not in structured four-step mode: " + sessionId);
        }

        if (currentStep == null || currentStep == NvcPracticeStep.COMPLETED) {
            throw new BusinessException(
                ErrorCode.NVC_SESSION_NOT_FOUND,
                "Session already completed: " + sessionId);
        }

        NvcPracticeStep nextStep = nextStep(currentStep);
        sessionService.updateStep(sessionId, nextStep);

        log.info("Step advanced: sessionId={}, {} -> {}", sessionId, currentStep, nextStep);

        if (nextStep == NvcPracticeStep.COMPLETED) {
            return new StepProgressDTO(
                NvcPracticeStep.COMPLETED, 4, 4, null, false,
                "四步练习全部完成！进入综合练习。",
                null, null, null, null
            );
        }

        NvcAgentConfigEntity config = agentConfigService.getConfig(stepToCoach(nextStep));
        return new StepProgressDTO(
            nextStep, getStepIndex(nextStep), 4, null, false,
            getStepDescription(nextStep),
            config.getMaxStepAttempts(), null, null, config.getStepTimeoutMinutes()
        );
    }

    /**
     * 检查是否可推进
     */
    public boolean canAdvance(Long sessionId, Integer currentScore) {
        NvcPracticeSessionEntity session = sessionService.getSession(sessionId);
        NvcPracticeStep currentStep = session.getCurrentStep();

        if (currentStep == null || currentStep == NvcPracticeStep.COMPLETED) {
            return false;
        }

        NvcAgentConfigEntity config = agentConfigService.getConfig(stepToCoach(currentStep));
        Integer threshold = config.getStepAdvanceThreshold() != null
            ? config.getStepAdvanceThreshold() : 70;

        return currentScore != null && currentScore >= threshold;
    }

    /**
     * 重置步骤
     */
    @Transactional
    public StepProgressDTO resetStep(Long sessionId) {
        NvcPracticeSessionEntity session = sessionService.getSession(sessionId);

        if (session.getPracticeMode() != NvcPracticeMode.STRUCTURED_FOUR_STEP) {
            throw new BusinessException(
                ErrorCode.NVC_SESSION_NOT_FOUND,
                "Session is not in structured four-step mode: " + sessionId);
        }

        sessionService.updateStep(sessionId, NvcPracticeStep.OBSERVE);
        log.info("Step reset: sessionId={}", sessionId);

        NvcAgentConfigEntity config = agentConfigService.getConfig(NvcAgentScene.STEP_OBSERVE_COACH);
        return new StepProgressDTO(
            NvcPracticeStep.OBSERVE, 0, 4, null, false,
            getStepDescription(NvcPracticeStep.OBSERVE),
            config.getMaxStepAttempts(), null, null, config.getStepTimeoutMinutes()
        );
    }

    private NvcAgentScene stepToCoach(NvcPracticeStep step) {
        return switch (step) {
            case OBSERVE -> NvcAgentScene.STEP_OBSERVE_COACH;
            case FEELING -> NvcAgentScene.STEP_FEELING_COACH;
            case NEED -> NvcAgentScene.STEP_NEED_COACH;
            case REQUEST -> NvcAgentScene.STEP_REQUEST_COACH;
            default -> NvcAgentScene.DIALOGUE_GUIDE;
        };
    }

    private NvcPracticeStep nextStep(NvcPracticeStep current) {
        return switch (current) {
            case OBSERVE -> NvcPracticeStep.FEELING;
            case FEELING -> NvcPracticeStep.NEED;
            case NEED -> NvcPracticeStep.REQUEST;
            case REQUEST -> NvcPracticeStep.COMPLETED;
            default -> NvcPracticeStep.COMPLETED;
        };
    }

    private int getStepScore(NvcEvaluationEntity eval, NvcPracticeStep step) {
        return switch (step) {
            case OBSERVE -> eval.getObservationScore() != null ? eval.getObservationScore() : 0;
            case FEELING -> eval.getFeelingScore() != null ? eval.getFeelingScore() : 0;
            case NEED -> eval.getNeedScore() != null ? eval.getNeedScore() : 0;
            case REQUEST -> eval.getRequestScore() != null ? eval.getRequestScore() : 0;
            default -> eval.getOverallScore() != null ? eval.getOverallScore() : 0;
        };
    }

    private int getStepIndex(NvcPracticeStep step) {
        return switch (step) {
            case OBSERVE -> 0;
            case FEELING -> 1;
            case NEED -> 2;
            case REQUEST -> 3;
            default -> 4;
        };
    }

    private String getStepDescription(NvcPracticeStep step) {
        return switch (step) {
            case OBSERVE -> "步骤1/4：观察练习 — 学会区分观察（客观事实）和评论（主观判断）";
            case FEELING -> "步骤2/4：感受练习 — 学会识别和表达真实感受，区分感受和想法";
            case NEED -> "步骤3/4：需求练习 — 从感受追溯深层需求，区分需求和策略";
            case REQUEST -> "步骤4/4：请求练习 — 将需求转化为具体、可执行、正向的请求";
            default -> "综合练习 — 将四步串联成完整的NVC表达";
        };
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: Compilation successful

- [ ] **Step 3: Commit service**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcStructuredPracticeService.java
git commit -m "feat(step7): add NvcStructuredPracticeService"
```

---

## Task 6: Add Step Progress APIs to Controller

**Files:**
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcPracticeController.java`

**Interfaces:**
- Consumes: NvcStructuredPracticeService
- Produces: 3 new REST endpoints for step progress management

- [ ] **Step 1: Read current NvcPracticeController**

Read: `app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcPracticeController.java`

- [ ] **Step 2: Add NvcStructuredPracticeService dependency**

```java
private final NvcStructuredPracticeService structuredPracticeService;
```

- [ ] **Step 3: Add getStepProgress endpoint**

```java
/**
 * 获取结构化练习的步骤进度
 */
@GetMapping("/sessions/{sessionId}/step-progress")
public Result<StepProgressDTO> getStepProgress(@PathVariable Long sessionId) {
    StepProgressDTO progress = structuredPracticeService.getStepProgress(sessionId);
    return Result.success(progress);
}
```

- [ ] **Step 4: Add advanceStep endpoint**

```java
/**
 * 手动推进到下一步
 */
@RateLimit(count = 10)
@PostMapping("/sessions/{sessionId}/advance-step")
public Result<StepProgressDTO> advanceStep(@PathVariable Long sessionId) {
    StepProgressDTO progress = structuredPracticeService.advanceStep(sessionId);
    return Result.success(progress);
}
```

- [ ] **Step 5: Add resetStep endpoint**

```java
/**
 * 重置步骤（重新开始）
 */
@RateLimit(count = 5)
@PostMapping("/sessions/{sessionId}/reset-step")
public Result<StepProgressDTO> resetStep(@PathVariable Long sessionId) {
    StepProgressDTO progress = structuredPracticeService.resetStep(sessionId);
    return Result.success(progress);
}
```

- [ ] **Step 6: Add import for StepProgressDTO**

```java
import nvc.guide.modules.nvcpractice.dto.StepProgressDTO;
```

- [ ] **Step 7: Verify compilation**

Run: `./gradlew compileJava`
Expected: Compilation successful

- [ ] **Step 8: Commit controller changes**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcPracticeController.java
git commit -m "feat(step7): add step progress APIs to NvcPracticeController"
```

---

## Task 7: Integrate Step Advancement into Dialogue Service

**Files:**
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeDialogueService.java`

**Interfaces:**
- Consumes: NvcStructuredPracticeService
- Produces: Automatic step advancement in sendMessage() and sendMessageStream()

- [ ] **Step 1: Read current NvcPracticeDialogueService**

Read: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeDialogueService.java`

- [ ] **Step 2: Add NvcStructuredPracticeService dependency**

```java
private final NvcStructuredPracticeService structuredPracticeService;
```

- [ ] **Step 3: Add step advancement logic in sendMessage()**

Add after line 107 (after evaluation try-catch block):

```java
// 结构化四步模式：检查是否需要推进步骤
if (session.getPracticeMode() == NvcPracticeMode.STRUCTURED_FOUR_STEP
    && decision.action() != null
    && decision.action().equals("STEP_ADVANCE")) {
    structuredPracticeService.advanceStep(sessionId);
    session = sessionService.getSession(sessionId);
    log.info("Step advanced: sessionId={}, newStep={}",
        sessionId, session.getCurrentStep());
}
```

- [ ] **Step 4: Add step advancement logic in sendMessageStream()**

Add in the doOnComplete() callback after evaluation try-catch block:

```java
// 结构化四步模式：检查是否需要推进步骤
if (session.getPracticeMode() == NvcPracticeMode.STRUCTURED_FOUR_STEP
    && decision.action() != null
    && decision.action().equals("STEP_ADVANCE")) {
    structuredPracticeService.advanceStep(sessionId);
    log.info("Step advanced in stream: sessionId={}", sessionId);
}
```

- [ ] **Step 5: Add imports**

```java
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew compileJava`
Expected: Compilation successful

- [ ] **Step 7: Commit dialogue service changes**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeDialogueService.java
git commit -m "feat(step7): integrate step advancement into dialogue service"
```

---

## Task 8: Create Unit Tests for NvcStructuredPracticeService

**Files:**
- Create: `app/src/test/java/nvc/guide/modules/nvcpractice/service/NvcStructuredPracticeServiceTest.java`

**Interfaces:**
- Consumes: NvcStructuredPracticeService, mocks for dependencies
- Produces: Unit tests for all service methods

- [ ] **Step 1: Create test class**

```java
package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcpractice.dto.StepProgressDTO;
import nvc.guide.modules.nvcpractice.model.*;
import nvc.guide.modules.nvcpractice.repository.NvcEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NvcStructuredPracticeServiceTest {

    @Mock
    private NvcPracticeSessionService sessionService;

    @Mock
    private NvcEvaluationRepository evaluationRepository;

    @Mock
    private NvcAgentConfigService agentConfigService;

    @Mock
    private RedisService redisService;

    @InjectMocks
    private NvcStructuredPracticeService structuredPracticeService;

    private NvcPracticeSessionEntity session;
    private NvcAgentConfigEntity agentConfig;

    @BeforeEach
    void setUp() {
        session = NvcPracticeSessionEntity.builder()
            .id(1L)
            .userId(1L)
            .practiceMode(NvcPracticeMode.STRUCTURED_FOUR_STEP)
            .currentStep(NvcPracticeStep.OBSERVE)
            .build();

        agentConfig = NvcAgentConfigEntity.builder()
            .id(1L)
            .agentScene(NvcAgentScene.STEP_OBSERVE_COACH)
            .stepAdvanceThreshold(70)
            .maxStepAttempts(10)
            .stepTimeoutMinutes(30)
            .build();
    }

    @Test
    void getStepProgress_nonStructuredMode_returnsNull() {
        session.setPracticeMode(NvcPracticeMode.FREE_DIALOG);
        when(sessionService.getSession(1L)).thenReturn(session);

        StepProgressDTO result = structuredPracticeService.getStepProgress(1L);

        assertNull(result);
    }

    @Test
    void getStepProgress_structuredMode_returnsProgress() {
        when(sessionService.getSession(1L)).thenReturn(session);
        when(evaluationRepository.findBySessionIdAndEvaluationType(1L, NvcEvaluationType.REALTIME))
            .thenReturn(Optional.empty());
        when(agentConfigService.getConfig(NvcAgentScene.STEP_OBSERVE_COACH))
            .thenReturn(agentConfig);

        StepProgressDTO result = structuredPracticeService.getStepProgress(1L);

        assertNotNull(result);
        assertEquals(NvcPracticeStep.OBSERVE, result.currentStep());
        assertEquals(0, result.stepIndex());
        assertEquals(4, result.totalSteps());
        assertFalse(result.canAdvance());
    }

    @Test
    void advanceStep_validSession_advancesToNext() {
        when(sessionService.getSession(1L)).thenReturn(session);
        when(agentConfigService.getConfig(NvcAgentScene.STEP_FEELING_COACH))
            .thenReturn(agentConfig);

        StepProgressDTO result = structuredPracticeService.advanceStep(1L);

        assertNotNull(result);
        assertEquals(NvcPracticeStep.FEELING, result.currentStep());
        verify(sessionService).updateStep(1L, NvcPracticeStep.FEELING);
    }

    @Test
    void advanceStep_completedSession_throwsException() {
        session.setCurrentStep(NvcPracticeStep.COMPLETED);
        when(sessionService.getSession(1L)).thenReturn(session);

        assertThrows(BusinessException.class, () -> {
            structuredPracticeService.advanceStep(1L);
        });
    }

    @Test
    void canAdvance_scoreAboveThreshold_returnsTrue() {
        when(sessionService.getSession(1L)).thenReturn(session);
        when(agentConfigService.getConfig(NvcAgentScene.STEP_OBSERVE_COACH))
            .thenReturn(agentConfig);

        boolean result = structuredPracticeService.canAdvance(1L, 75);

        assertTrue(result);
    }

    @Test
    void canAdvance_scoreBelowThreshold_returnsFalse() {
        when(sessionService.getSession(1L)).thenReturn(session);
        when(agentConfigService.getConfig(NvcAgentScene.STEP_OBSERVE_COACH))
            .thenReturn(agentConfig);

        boolean result = structuredPracticeService.canAdvance(1L, 65);

        assertFalse(result);
    }

    @Test
    void resetStep_validSession_resetsToObserve() {
        session.setCurrentStep(NvcPracticeStep.NEED);
        when(sessionService.getSession(1L)).thenReturn(session);
        when(agentConfigService.getConfig(NvcAgentScene.STEP_OBSERVE_COACH))
            .thenReturn(agentConfig);

        StepProgressDTO result = structuredPracticeService.resetStep(1L);

        assertNotNull(result);
        assertEquals(NvcPracticeStep.OBSERVE, result.currentStep());
        verify(sessionService).updateStep(1L, NvcPracticeStep.OBSERVE);
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "nvc.guide.modules.nvcpractice.service.NvcStructuredPracticeServiceTest"`
Expected: All tests pass

- [ ] **Step 3: Commit tests**

```bash
git add app/src/test/java/nvc/guide/modules/nvcpractice/service/NvcStructuredPracticeServiceTest.java
git commit -m "test(step7): add unit tests for NvcStructuredPracticeService"
```

---

## Task 9: Create Controller Tests

**Files:**
- Create: `app/src/test/java/nvc/guide/modules/nvcpractice/controller/NvcPracticeControllerStepTest.java`

**Interfaces:**
- Consumes: NvcPracticeController, mocks for services
- Produces: Integration tests for step progress APIs

- [ ] **Step 1: Create test class**

```java
package nvc.guide.modules.nvcpractice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.modules.nvcpractice.dto.StepProgressDTO;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.service.NvcStructuredPracticeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mock.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NvcPracticeController.class)
class NvcPracticeControllerStepTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NvcStructuredPracticeService structuredPracticeService;

    @Test
    void getStepProgress_returnsProgress() throws Exception {
        StepProgressDTO progress = new StepProgressDTO(
            NvcPracticeStep.OBSERVE, 0, 4, null, false,
            "步骤1/4：观察练习", 10, null, null, 30
        );
        when(structuredPracticeService.getStepProgress(1L)).thenReturn(progress);

        mockMvc.perform(get("/api/nvc/practice/sessions/1/step-progress"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currentStep").value("OBSERVE"))
            .andExpect(jsonPath("$.data.stepIndex").value(0))
            .andExpect(jsonPath("$.data.totalSteps").value(4));
    }

    @Test
    void advanceStep_returnsNewProgress() throws Exception {
        StepProgressDTO progress = new StepProgressDTO(
            NvcPracticeStep.FEELING, 1, 4, null, false,
            "步骤2/4：感受练习", 10, null, null, 30
        );
        when(structuredPracticeService.advanceStep(1L)).thenReturn(progress);

        mockMvc.perform(post("/api/nvc/practice/sessions/1/advance-step"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currentStep").value("FEELING"))
            .andExpect(jsonPath("$.data.stepIndex").value(1));
    }

    @Test
    void resetStep_returnsResetProgress() throws Exception {
        StepProgressDTO progress = new StepProgressDTO(
            NvcPracticeStep.OBSERVE, 0, 4, null, false,
            "步骤1/4：观察练习", 10, null, null, 30
        );
        when(structuredPracticeService.resetStep(1L)).thenReturn(progress);

        mockMvc.perform(post("/api/nvc/practice/sessions/1/reset-step"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currentStep").value("OBSERVE"));
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "nvc.guide.modules.nvcpractice.controller.NvcPracticeControllerStepTest"`
Expected: All tests pass

- [ ] **Step 3: Commit controller tests**

```bash
git add app/src/test/java/nvc/guide/modules/nvcpractice/controller/NvcPracticeControllerStepTest.java
git commit -m "test(step7): add controller tests for step progress APIs"
```

---

## Task 10: Run All Tests and Verify

**Files:**
- None (verification only)

**Interfaces:**
- Consumes: All implemented code
- Produces: Verification that everything works together

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 2: Run compilation**

Run: `./gradlew build`
Expected: Build successful

- [ ] **Step 3: Verify API endpoints manually**

Start application and test:
1. Create structured four-step session
2. Get step progress
3. Send message and verify step advancement
4. Reset step

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat(step7): complete structured four-step practice mode implementation"
```

---

## Verification Checklist

```
□ 4 个 Step Coach Prompt 模板创建成功
□ NvcAgentConfigEntity 添加 step_advance_threshold 等字段
□ NvcStructuredPracticeService 编译通过
  □ getStepProgress() 返回正确的步骤信息
  □ advanceStep() 能正确推进步骤
  □ canAdvance() 正确判断是否可推进
  □ 四步全部完成后返回 COMPLETED
□ API 测试：
  □ POST /api/nvc/practice/sessions 创建结构化四步练习会话
  □ GET /api/nvc/practice/sessions/{id}/step-progress 返回步骤进度
  □ POST /api/nvc/practice/sessions/{id}/advance-step 能推进步骤
  □ POST /api/nvc/practice/sessions/{id}/reset-step 能重置步骤
  □ 对话后评分 >= 70 时，自动推进
  □ 四步全部完成后，step-progress 返回 COMPLETED
□ 对话流程验证：
  □ OBSERVE 步骤使用 STEP_OBSERVE_COACH
  □ 评分 >= 70 后自动切换到 STEP_FEELING_COACH
  □ 评分 < 70 时保持当前步骤继续练习
  □ 四步完成后切换到 DIALOGUE_GUIDE 进行综合练习
□ 所有单元测试通过
□ Git 提交："Step 7: Add structured 4-step practice mode"
```
