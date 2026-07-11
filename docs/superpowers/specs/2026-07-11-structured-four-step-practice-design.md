# Step 7：结构化四步练习模式设计文档

> 目标：实现 NVC 结构化四步练习 — 观察→感受→需求→请求，每步独立教练 + 评估 + 自动推进
>
> 设计日期：2026-07-11
>
> 设计原则：高可用、高扩展、稳定性

---

## 1. 整体架构设计

### 1.1 核心流程

```
用户选择"结构化四步练习"模式
→ 创建会话，currentStep = OBSERVE
→ 对话循环：
   用户消息 → Agent 调度（根据 currentStep 选择教练）
   → 执行 Agent → 异步评估
   → 检查是否推进（评分 >= 阈值）
   → 如果推进：更新 currentStep，切换到下一步教练
→ 四步完成后：切换到 DIALOGUE_GUIDE，进入自由对话
```

### 1.2 关键设计决策

1. **难度分离**：结构化模式的阈值由 Agent 配置决定，不需要用户选择难度
2. **立即推进**：Agent 判定通过后立即推进，下一轮对话使用新步骤教练
3. **统一封装**：自动推进和手动推进都调用 `NvcStructuredPracticeService.advanceStep()`
4. **可配置阈值**：每个步骤教练可配置不同的推进阈值

### 1.3 技术亮点

- 有限状态机驱动步骤推进
- 步骤级评估与推进
- Agent 自动切换
- 配置热更新

---

## 2. 数据模型扩展

### 2.1 NvcAgentConfigEntity 扩展

```java
// 新增字段
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

### 2.2 字段说明

| 字段名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| step_advance_threshold | Integer | 70 | 步骤推进阈值（0-100） |
| max_step_attempts | Integer | 10 | 每个步骤最大尝试次数 |
| step_timeout_minutes | Integer | 30 | 步骤超时时间（分钟） |

### 2.3 数据库变更

在 `nvc_agent_config` 表中添加以下字段：

```sql
ALTER TABLE nvc_agent_config ADD COLUMN step_advance_threshold INTEGER DEFAULT 70;
ALTER TABLE nvc_agent_config ADD COLUMN max_step_attempts INTEGER DEFAULT 10;
ALTER TABLE nvc_agent_config ADD COLUMN step_timeout_minutes INTEGER DEFAULT 30;
```

### 2.4 高可用设计

- **缓存策略**：Agent 配置使用 Redis 缓存，支持热更新
- **默认值**：所有字段都有合理默认值，避免空指针
- **向后兼容**：新字段可为 NULL，不影响现有数据

### 2.5 高扩展设计

- **步骤级配置**：每个步骤教练独立配置，互不影响
- **配置热更新**：通过 API 动态调整，无需重启
- **预留字段**：为未来扩展预留空间

### 2.6 稳定性设计

- **参数校验**：阈值范围校验（0-100）
- **异常处理**：配置缺失时使用默认值
- **日志记录**：配置变更记录审计日志

---

## 3. 服务层设计

### 3.1 NvcStructuredPracticeService

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcStructuredPracticeService {

    private final NvcPracticeSessionService sessionService;
    private final NvcEvaluationRepository evaluationRepository;
    private final NvcAgentConfigService agentConfigService;
    private final RedisService redisService;

    // 缓存键前缀
    private static final String STEP_PROGRESS_CACHE_PREFIX = "nvc:step:progress:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /**
     * 获取步骤进度（带缓存）
     */
    public StepProgressDTO getStepProgress(Long sessionId) {
        // 1. 尝试从缓存获取
        // 2. 缓存未命中，从数据库查询
        // 3. 计算步骤进度
        // 4. 缓存结果并返回
    }

    /**
     * 推进步骤（统一封装）
     */
    public StepProgressDTO advanceStep(Long sessionId) {
        // 1. 获取当前会话
        // 2. 验证是否可推进
        // 3. 更新 currentStep
        // 4. 清除缓存
        // 5. 记录日志
        // 6. 返回新的步骤进度
    }

    /**
     * 检查是否可推进
     */
    public boolean canAdvance(Long sessionId, Integer currentScore) {
        // 1. 获取当前步骤对应的 Agent 配置
        // 2. 获取推进阈值
        // 3. 比较当前分数与阈值
    }

    /**
     * 重置步骤（用于重试）
     */
    public StepProgressDTO resetStep(Long sessionId) {
        // 1. 将当前步骤重置为第一步
        // 2. 清除缓存
        // 3. 记录日志
    }
}
```

### 3.2 高可用设计

1. **缓存策略**：步骤进度使用 Redis 缓存，减少数据库查询
2. **异步处理**：步骤推进后的日志记录使用异步方式
3. **降级策略**：缓存不可用时直接查询数据库

### 3.3 高扩展设计

1. **接口抽象**：未来可扩展为接口，支持多种实现
2. **配置驱动**：所有阈值从配置读取，便于调整
3. **事件发布**：步骤推进时发布事件，便于扩展（如通知、统计）

### 3.4 稳定性设计

1. **参数校验**：sessionId、currentScore 等参数校验
2. **并发控制**：使用分布式锁防止并发推进
3. **事务管理**：数据库操作使用事务
4. **异常处理**：所有异常统一处理，避免系统崩溃

---

## 4. API 设计

### 4.1 NvcPracticeController 新增 API

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
 * 手动推进到下一步
 */
@PostMapping("/sessions/{sessionId}/advance-step")
@RateLimit(count = 10)
public Result<StepProgressDTO> advanceStep(@PathVariable Long sessionId) {
    StepProgressDTO progress = structuredPracticeService.advanceStep(sessionId);
    return Result.success(progress);
}

/**
 * 重置步骤（重新开始当前步骤）
 */
@PostMapping("/sessions/{sessionId}/reset-step")
@RateLimit(count = 5)
public Result<StepProgressDTO> resetStep(@PathVariable Long sessionId) {
    StepProgressDTO progress = structuredPracticeService.resetStep(sessionId);
    return Result.success(progress);
}
```

### 4.2 StepProgressDTO 设计

```java
public record StepProgressDTO(
    NvcPracticeStep currentStep,      // 当前步骤
    int stepIndex,                    // 步骤索引（0-3）
    int totalSteps,                   // 总步骤数（4）
    Integer currentScore,             // 当前步骤评分
    boolean canAdvance,               // 是否可推进
    String stepDescription,           // 步骤描述
    Integer maxAttempts,              // 最大尝试次数
    Integer attemptsUsed,             // 已使用尝试次数
    LocalDateTime stepStartedAt,      // 步骤开始时间
    Integer timeoutMinutes            // 超时时间（分钟）
) {}
```

### 4.3 高可用设计

1. **限流保护**：使用 `@RateLimit` 注解防止滥用
2. **降级返回**：非结构化模式返回 null，前端可据此判断
3. **缓存友好**：响应数据可被前端缓存

### 4.4 高扩展设计

1. **版本控制**：API 路径支持版本（如 `/v2/sessions/{sessionId}/step-progress`）
2. **分页支持**：未来可扩展查询历史步骤记录
3. **过滤参数**：支持按步骤类型过滤

### 4.5 稳定性设计

1. **参数校验**：sessionId 路径变量校验
2. **权限控制**：验证用户是否有权访问该会话
3. **错误码**：统一错误码，便于前端处理
4. **日志记录**：所有 API 调用记录日志

---

## 5. 对话流程集成

### 5.1 NvcPracticeDialogueService.sendMessage() 修改

```java
public DialogueResponse sendMessage(Long sessionId, String userMessage) {
    // ... 现有逻辑 ...

    // 5. Agent 调度决策
    AgentDecision decision = orchestrator.decideNextAgent(context);
    log.info("Agent decision: session={}, scene={}, reason={}",
        sessionId, decision.scene(), decision.reason());

    // 6. 更新会话的当前 Agent 场景
    sessionService.updateAgentScene(sessionId, decision.scene());

    // 7. 执行 Agent 对话
    String aiReply = orchestrator.executeAgent(
        decision.scene(), context, userMessage);

    // 8. 保存 AI 回复
    NvcPracticeMessageEntity aiMsg = NvcPracticeMessageEntity.builder()
        .sessionId(sessionId)
        .role(NvcMessageRole.ASSISTANT)
        .agentScene(decision.scene())
        .content(aiReply)
        .sequenceNum(nextSeq + 1)
        .step(session.getCurrentStep())
        .build();
    messageRepository.save(aiMsg);

    // 9. 异步触发实时评估（不阻塞对话）
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

    // 10. 结构化四步模式：检查是否需要推进步骤
    if (session.getPracticeMode() == NvcPracticeMode.STRUCTURED_FOUR_STEP
        && decision.action() != null
        && decision.action().equals("STEP_ADVANCE")) {
        // 推进步骤
        structuredPracticeService.advanceStep(sessionId);
        // 重新获取会话（步骤已更新）
        session = sessionService.getSession(sessionId);
        log.info("Step advanced: sessionId={}, newStep={}",
            sessionId, session.getCurrentStep());
    }

    // 11. 返回结果
    return new DialogueResponse(
        sessionId,
        aiReply,
        decision.scene().name(),
        decision.reason(),
        decision.action(),
        session.getCurrentStep() != null
            ? session.getCurrentStep().name() : null
    );
}
```

### 5.2 流式对话同步修改

在 `sendMessageStream()` 的 `doOnComplete()` 回调中添加相同的步骤推进逻辑。

### 5.3 高可用设计

1. **异步评估**：评估不阻塞对话，提高响应速度
2. **容错处理**：评估失败不影响对话流程
3. **缓存更新**：步骤推进后更新缓存

### 5.4 高扩展设计

1. **模式判断**：通过 `practiceMode` 判断是否为结构化模式
2. **动作判断**：通过 `decision.action()` 判断是否需要推进
3. **日志记录**：记录步骤推进日志，便于分析

### 5.5 稳定性设计

1. **事务管理**：步骤推进使用事务
2. **并发控制**：防止并发推进导致数据不一致
3. **异常处理**：推进失败不影响对话流程
4. **数据一致性**：推进后重新获取会话，确保数据最新

---

## 6. Prompt 模板设计

### 6.1 步骤教练 Prompt 模板

#### nvc-step-observe-system.st

- **职责**：引导用户区分"观察"（客观事实）和"评论"（主观判断）
- **教学要点**：观察 vs 评论、常见评判词汇识别
- **引导方式**：给出冲突描述，让用户识别观察和评论

#### nvc-step-feeling-system.st

- **职责**：引导用户识别和表达真实感受，区分感受和想法
- **教学要点**：感受 vs 想法、感受词汇表
- **引导方式**：给出表达，让用户区分感受和想法

#### nvc-step-need-system.st

- **职责**：引导用户从感受追溯到深层需求
- **教学要点**：需求 vs 策略、从感受追溯需求
- **引导方式**：给出感受表达，引导思考需求

#### nvc-step-request-system.st

- **职责**：引导用户将需求转化为具体、可执行、正向的请求
- **教学要点**：请求 vs 命令、正向表达、具体可行
- **引导方式**：给出需求，引导转化为请求

### 6.2 Prompt 模板特点

1. **结构化**：每个模板包含教学要点、引导方式、回复风格
2. **场景感知**：场景信息以 `[练习场景]` 形式提供
3. **回复控制**：每次回复 3-5 句话，避免长篇大论
4. **正向引导**：先肯定用户做得好的地方，再指出改进点

### 6.3 高可用设计

1. **模板缓存**：Prompt 模板编译后缓存，提高性能
2. **版本管理**：支持模板版本管理，便于迭代
3. **降级策略**：模板加载失败时使用默认模板

### 6.4 高扩展设计

1. **参数化**：模板支持参数注入（如场景描述、用户历史）
2. **多语言**：未来可扩展支持多语言模板
3. **自定义**：支持用户自定义模板

### 6.5 稳定性设计

1. **输入校验**：模板参数校验，防止注入攻击
2. **输出过滤**：过滤敏感信息，确保安全
3. **日志记录**：记录模板使用情况，便于分析

---

## 7. 测试策略

### 7.1 单元测试

#### NvcStructuredPracticeServiceTest

- 测试 `getStepProgress()` 返回正确的步骤信息
- 测试 `advanceStep()` 能正确推进步骤
- 测试 `canAdvance()` 正确判断是否可推进
- 测试四步全部完成后返回 COMPLETED

#### NvcPracticeControllerTest

- 测试 `GET /sessions/{sessionId}/step-progress` 返回步骤进度
- 测试 `POST /sessions/{sessionId}/advance-step` 能推进步骤
- 测试 `POST /sessions/{sessionId}/reset-step` 能重置步骤

### 7.2 集成测试

#### 完整流程测试

1. 创建结构化四步练习会话
2. 对话多轮，验证步骤自动推进
3. 验证四步完成后切换到 DIALOGUE_GUIDE

#### 边界条件测试

1. 评分刚好 70 分时是否推进
2. 评分 69 分时不推进
3. 四步完成后继续对话

### 7.3 性能测试

1. **并发测试**：多用户同时推进步骤
2. **压力测试**：大量会话同时进行

### 7.4 稳定性测试

1. **异常测试**：评估服务不可用时的降级策略
2. **恢复测试**：服务重启后状态恢复

---

## 8. 部署和监控

### 8.1 部署策略

1. **数据库迁移**：添加 `step_advance_threshold` 等字段
2. **配置更新**：更新 Agent 配置，设置默认阈值
3. **服务部署**：滚动更新，避免服务中断

### 8.2 监控指标

#### 业务指标

- 步骤推进成功率
- 每个步骤的平均练习轮次
- 四步完成率

#### 性能指标

- API 响应时间
- 缓存命中率
- 数据库查询时间

#### 稳定性指标

- 错误率
- 异常数量
- 服务可用性

### 8.3 告警规则

1. **错误率告警**：错误率 > 5% 时告警
2. **响应时间告警**：P99 > 1s 时告警
3. **缓存命中率告警**：命中率 < 80% 时告警

### 8.4 日志设计

1. **结构化日志**：使用 JSON 格式，便于分析
2. **关键日志**：步骤推进、评估结果、异常信息
3. **日志级别**：INFO 记录正常流程，WARN 记录异常，ERROR 记录错误

---

## 9. 文档中发现的问题

### 9.1 包名不一致

- **问题**：文档中写的是 `interview.guide.modules.nvcpractice`
- **实际**：项目包名是 `nvc.guide.modules.nvcpractice`
- **处理**：设计文档中使用正确的包名

### 9.2 步骤自动推进的时序

- **问题**：`NvcAgentOrchestrator.decideStructuredStep()` 返回 `STEP_ADVANCE` 时，`decision.scene()` 已经是下一步的 Agent，但 `session.getCurrentStep()` 还是旧步骤
- **处理**：在 `sendMessage()` 中推进后重新获取 session，确保数据一致

### 9.3 评估结果的实时性

- **问题**：由于评估是异步的（Redis Stream），步骤推进可能延迟一轮
- **处理**：这是可接受的，因为用户需要多轮练习才能达到 70 分

---

## 10. 验证清单

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
□ Git 提交："Step 7: Add structured 4-step practice mode"
```

---

## 11. 测试场景

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

7. 四步完成后：
   → 切换到 DIALOGUE_GUIDE，进入自由对话模式
```
