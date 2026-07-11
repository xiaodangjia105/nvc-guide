# Step 7：结构化四步练习模式

> 目标：实现 NVC 结构化四步练习 — 观察→感受→需求→请求，每步独立教练 + 评估 + 自动推进
>
> 预计耗时：2-3 天
>
> 前置条件：Step 3（对话流程）、Step 4（评估引擎）、Step 2（Agent 调度）已完成
>
> 技术亮点：有限状态机驱动、步骤级评估与推进、Agent 自动切换

---

## 7.1 本步的改动说明

结构化四步练习的核心逻辑**大部分已经在前面的 Step 中实现了**：

- **Step 1** 中创建了 `NvcPracticeStep` 枚举（OBSERVE/FEELING/NEED/REQUEST/COMPLETED）
- **Step 2** 中 `NvcAgentOrchestrator.decideStructuredStep()` 已经实现了步骤推进逻辑
- **Step 2** 中创建了 4 个 Step Coach Agent 的配置
- **Step 4** 中评估引擎已支持按步骤评估

这一步主要是：
1. 创建 4 个 Step Coach 的 Prompt 模板
2. 实现结构化练习的特殊对话流程（步骤切换 + 进度追踪）
3. 在 Controller 层暴露步骤推进 API
4. 前端步骤进度指示器的数据支持

---

## 7.2 本步要创建/修改的文件

```
app/src/main/resources/prompts/
├── nvc-step-observe-system.st              ← 观察步骤教练提示词
├── nvc-step-feeling-system.st              ← 感受步骤教练提示词
├── nvc-step-need-system.st                 ← 需求步骤教练提示词
└── nvc-step-request-system.st              ← 请求步骤教练提示词

app/src/main/java/interview/guide/modules/nvcpractice/
├── service/
│   └── NvcStructuredPracticeService.java   ← 结构化练习专用服务
├── dto/
│   └── StepProgressDTO.java               ← 步骤进度 DTO
└── controller/
    └── NvcPracticeController.java          ← 补充结构化练习 API（修改已有）
```

---

## 7.3 Prompt 模板

### nvc-step-observe-system.st

路径：`app/src/main/resources/prompts/nvc-step-observe-system.st`

```
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

### nvc-step-feeling-system.st

路径：`app/src/main/resources/prompts/nvc-step-feeling-system.st`

```
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

### nvc-step-need-system.st

路径：`app/src/main/resources/prompts/nvc-step-need-system.st`

```
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

### nvc-step-request-system.st

路径：`app/src/main/resources/prompts/nvc-step-request-system.st`

```
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

---

## 7.4 NvcStructuredPracticeService

路径：`app/src/main/java/interview/guide/modules/nvcpractice/service/NvcStructuredPracticeService.java`

### 功能

- 获取当前步骤进度
- 判断步骤是否可推进
- 推进步骤（更新会话状态）
- 生成步骤切换提示

```java
package interview.guide.modules.nvcpractice.service;

import interview.guide.modules.nvcpractice.dto.StepProgressDTO;
import interview.guide.modules.nvcpractice.model.*;
import interview.guide.modules.nvcpractice.repository.NvcEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcStructuredPracticeService {

    private final NvcPracticeSessionService sessionService;
    private final NvcEvaluationRepository evaluationRepository;

    /**
     * 获取步骤进度
     */
    public StepProgressDTO getStepProgress(Long sessionId) {
        NvcPracticeSessionEntity session = sessionService.getSession(sessionId);

        if (session.getPracticeMode() != NvcPracticeMode.STRUCTURED_FOUR_STEP) {
            return null;
        }

        NvcPracticeStep currentStep = session.getCurrentStep();
        NvcEvaluationEntity latestEval = evaluationRepository
            .findBySessionIdAndEvaluationType(sessionId, NvcEvaluationType.REALTIME)
            .orElse(null);

        Integer currentStepScore = null;
        if (latestEval != null && currentStep != null) {
            currentStepScore = switch (currentStep) {
                case OBSERVE -> latestEval.getObservationScore();
                case FEELING -> latestEval.getFeelingScore();
                case NEED -> latestEval.getNeedScore();
                case REQUEST -> latestEval.getRequestScore();
                default -> null;
            };
        }

        return new StepProgressDTO(
            currentStep,
            getStepIndex(currentStep),
            4,  // 总步骤数
            currentStepScore,
            canAdvance(currentStepScore),
            getStepDescription(currentStep)
        );
    }

    /**
     * 推进到下一步
     */
    public StepProgressDTO advanceStep(Long sessionId) {
        NvcPracticeSessionEntity session = sessionService.getSession(sessionId);
        NvcPracticeStep currentStep = session.getCurrentStep();
        NvcPracticeStep nextStep = nextStep(currentStep);

        if (nextStep == NvcPracticeStep.COMPLETED) {
            sessionService.updateStep(sessionId, NvcPracticeStep.COMPLETED);
            return new StepProgressDTO(
                NvcPracticeStep.COMPLETED, 4, 4, null, false,
                "四步练习全部完成！进入综合练习。"
            );
        }

        sessionService.updateStep(sessionId, nextStep);
        log.info("Step advanced: sessionId={}, {} -> {}", sessionId, currentStep, nextStep);

        return new StepProgressDTO(
            nextStep, getStepIndex(nextStep), 4, null, false,
            getStepDescription(nextStep)
        );
    }

    private boolean canAdvance(Integer stepScore) {
        return stepScore != null && stepScore >= 70;
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

    private NvcPracticeStep nextStep(NvcPracticeStep current) {
        return switch (current) {
            case OBSERVE -> NvcPracticeStep.FEELING;
            case FEELING -> NvcPracticeStep.NEED;
            case NEED -> NvcPracticeStep.REQUEST;
            case REQUEST -> NvcPracticeStep.COMPLETED;
            default -> NvcPracticeStep.COMPLETED;
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

---

## 7.5 StepProgressDTO

路径：`app/src/main/java/interview/guide/modules/nvcpractice/dto/StepProgressDTO.java`

```java
package interview.guide.modules.nvcpractice.dto;

import interview.guide.modules.nvcpractice.model.NvcPracticeStep;

public record StepProgressDTO(
    NvcPracticeStep currentStep,
    int stepIndex,          // 0-3
    int totalSteps,         // 4
    Integer currentScore,   // 当前步骤的评分
    boolean canAdvance,     // 是否可以推进到下一步
    String stepDescription  // 步骤描述
) {}
```

---

## 7.6 补充 Controller API

在 `NvcPracticeController.java` 中添加结构化练习相关接口：

```java
/**
 * 获取结构化练习的步骤进度
 */
@GetMapping("/sessions/{sessionId}/step-progress")
public Result<StepProgressDTO> getStepProgress(@PathVariable Long sessionId) {
    StepProgressDTO progress = structuredPracticeService.getStepProgress(sessionId);
    if (progress == null) {
        return Result.success(null);  // 非结构化模式返回 null
    }
    return Result.success(progress);
}

/**
 * 手动推进到下一步（也可以由 Agent 自动推进）
 */
@PostMapping("/sessions/{sessionId}/advance-step")
public Result<StepProgressDTO> advanceStep(@PathVariable Long sessionId) {
    StepProgressDTO progress = structuredPracticeService.advanceStep(sessionId);
    return Result.success(progress);
}
```

需要在 Controller 中注入 `NvcStructuredPracticeService`。

---

## 7.7 集成到对话流程

在 `NvcPracticeDialogueService.sendMessage()` 中，结构化模式的特殊处理：

```java
// 在 sendMessage() 方法中，Agent 决策后添加：

// 结构化四步模式：检查是否需要自动推进步骤
if (session.getPracticeMode() == NvcPracticeMode.STRUCTURED_FOUR_STEP
    && decision.action() != null
    && decision.action().equals("STEP_ADVANCE")) {
    structuredPracticeService.advanceStep(sessionId);
    // 重新获取会话（步骤已更新）
    session = sessionService.getSession(sessionId);
}
```

---

## 7.8 验证清单

```
□ 4 个 Step Coach Prompt 模板创建成功
□ NvcStructuredPracticeService 编译通过
  □ getStepProgress() 返回正确的步骤信息
  □ advanceStep() 能正确推进步骤
  □ 四步全部完成后返回 COMPLETED
□ API 测试：
  □ POST /api/nvc/practice/sessions 创建结构化四步练习会话
  □ GET /api/nvc/practice/sessions/{id}/step-progress 返回步骤进度
  □ 对话后评分 >= 70 时，POST /advance-step 能推进
  □ 四步全部完成后，step-progress 返回 COMPLETED
□ 对话流程验证：
  □ OBSERVE 步骤使用 STEP_OBSERVE_COACH
  □ 评分 >= 70 后自动切换到 STEP_FEELING_COACH
  □ 评分 < 70 时保持当前步骤继续练习
  □ 四步完成后切换到 DIALOGUE_GUIDE 进行综合练习
□ Git 提交："Step 7: Add structured 4-step practice mode"
```

---

## 7.9 测试场景

### 完整的结构化四步练习流程

```
1. 创建会话：practiceMode = STRUCTURED_FOUR_STEP, scenarioId = 1
   → currentStep = OBSERVE

2. 对话第1轮：
   用户："他总是不关心我"
   AI（观察教练）："你说的'总是'和'不关心'是评判，能描述一个具体的事实吗？"
   评估：observation_score = 40

3. 对话第2轮：
   用户："上周三我生病了，他没有问我吃药了没有"
   AI（观察教练）："很好！这是一个客观的观察..."
   评估：observation_score = 85
   → 自动推进到 FEELING

4. 对话第3轮：
   用户："我觉得他不在乎我"
   AI（感受教练）："'不在乎'是想法，你能找到背后的真实感受吗？"
   评估：feeling_score = 45

5. 对话第4轮：
   用户："我感到失落和孤独"
   AI（感受教练）："很好，这是真实感受..."
   评估：feeling_score = 80
   → 自动推进到 NEED

6. ... 以此类推直到 REQUEST 完成
```

---

## 7.10 完成记录

**完成日期：** 2026-07-11

**创建的文件：**

| 文件 | 说明 |
|------|------|
| `nvc-step-observe-system.st` | 观察步骤教练 Prompt |
| `nvc-step-feeling-system.st` | 感受步骤教练 Prompt |
| `nvc-step-need-system.st` | 需求步骤教练 Prompt |
| `nvc-step-request-system.st` | 请求步骤教练 Prompt |
| `StepProgressDTO.java` | 步骤进度 DTO（10 个字段） |
| `NvcStructuredPracticeService.java` | 结构化练习服务（4 个公共方法） |
| `NvcStructuredPracticeServiceTest.java` | 25 个单元测试 |
| `NvcPracticeDialogueServiceTest.java` | 7 个测试 |

**修改的文件：**

| 文件 | 改动 |
|------|------|
| `NvcAgentConfigEntity` | 添加 stepAdvanceThreshold, maxStepAttempts, stepTimeoutMinutes |
| `AgentConfigDTO` | 添加 3 个新字段 |
| `AgentConfigUpdateRequest` | 添加 3 个带验证的新字段 |
| `NvcAgentConfigService` | 更新 toDTO 和 updateConfig 方法 |
| `NvcPracticeController` | 添加 3 个端点 |
| `NvcPracticeDialogueService` | 集成步骤自动推进逻辑 |
| `NvcEvaluationRepository` | 重命名为 findFirstBySessionIdAndEvaluationTypeOrderByCreatedAtDesc |

**API 端点：**

| 方法 | 路径 | 说明 | 限流 |
|------|------|------|------|
| GET | `/api/nvc/practice/sessions/{sessionId}/step-progress` | 获取步骤进度 | - |
| POST | `/api/nvc/practice/sessions/{sessionId}/advance-step` | 手动推进步骤 | 10/min |
| POST | `/api/nvc/practice/sessions/{sessionId}/reset-step` | 重置步骤 | 5/min |

**关键实现细节：**

1. **步骤推进阈值**：通过 Agent 配置可配置（默认 70 分）
2. **错误处理**：使用 `ErrorCode.BAD_REQUEST`（非 `NVC_SESSION_NOT_FOUND`）
3. **事务管理**：`advanceStep()` 和 `resetStep()` 使用 `@Transactional`
4. **异常保护**：`doOnComplete` 中的 `advanceStep` 调用有 try-catch 保护
5. **Repository 优化**：使用 `findFirst` 避免 `IncorrectResultSizeDataAccessException`

**Commits:** `0619adc..51130af` (10 commits)

**测试覆盖：** 69 个测试套件全部通过
