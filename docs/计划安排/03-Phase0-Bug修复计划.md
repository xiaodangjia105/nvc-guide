# NVC 项目问题清单与修复方案

> 排查时间：2026-07-16
>
> 状态：待修复

---

## 一、BUG（代码错误/逻辑错误）

### BUG-1：会话结束后状态永远不会变为 EVALUATED ⭐严重

**文件**：`NvcPracticeController.java:156-176`

**问题**：`completeSession` 方法同步调用 `evaluateFinal` 完成最终评估后，会话状态停留在 `COMPLETED`，永远不更新为 `EVALUATED`。前端 `NvcPracticePage.tsx:114` 和 `NvcHistoryPage.tsx` 都检查 `EVALUATED` 状态，但后端从未设置，导致 UI 条件永远不命中。

**修复方案**：
```java
// NvcPracticeController.completeSession() 中，评估成功后更新状态
evaluationService.evaluateFinal(sessionId, session.getUserId(), messages);
sessionService.updatePhase(sessionId, NvcSessionPhase.EVALUATED);  // 新增
```

**影响**：前端"已完成"状态判断失效，报告页可能显示不正确。

---

### BUG-2：流式模式下用户会"闪现"看到原始 JSON ⭐中等

**文件**：`NvcPracticeDialogueService.java:212-217`

**问题**：流式 token 逐个发送到前端，但 `AiResponseCleaner.clean()` 只在 `doOnComplete` 回调（第 228 行）中执行。用户在流式输出过程中会看到 AI 回复中混杂的 JSON 块，流结束后才被清除，造成内容"闪烁"。

**修复方案**：
- 方案 A（推荐）：在后端流式输出前，对每个 token 做基本清理（移除独立 JSON 行）
- 方案 B：前端在每个 token 到达时做增量清理
- 方案 C：AI Agent 回复时不要输出 JSON（从 Prompt 层面解决）

---

### BUG-3：前端流式请求缺少认证头 ⭐严重

**文件**：`frontend/src/api/nvc.ts:36-45`

**问题**：`sendMessageStream` 使用原生 `fetch()` 调用，没有像 `request` 封装那样附加认证头。在需要认证的生产环境中会失败。

**修复方案**：
```typescript
sendMessageStream: (sessionId: number, content: string) =>
    fetch(buildUrl(`/api/nvc/practice/sessions/${sessionId}/messages/stream`), {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            ...getAuthHeaders(),  // 从 request 模块导出认证头
        },
        body: JSON.stringify({ content }),
    }),
```

---

### BUG-4：完成练习的同步评估失败被静默吞掉 ⭐中等

**文件**：`NvcPracticeController.java:163-173`

**问题**：最终评估的异常被 catch 后只打日志，前端已收到成功响应并跳转到报告页。如果评估失败，报告页会显示全 0 分的默认报告。

**修复方案**：
```java
// 方案 A：评估失败时返回错误响应
try {
    evaluationService.evaluateFinal(sessionId, session.getUserId(), messages);
} catch (Exception e) {
    log.error("Final evaluation failed: sessionId={}", sessionId, e);
    throw new BusinessException(ErrorCode.NVC_EVALUATION_FAILED, "最终评估失败");
}

// 方案 B（推荐）：评估失败时不阻塞完成流程，但标记评估状态
// 前端报告页检查评估状态，未完成则显示"评估中"并轮询
```

---

### BUG-5：评估 Prompt 下划线字段名 vs BeanOutputConverter 驼峰命名 ⭐潜在

**文件**：`nvc-evaluation-system.st:17` + `NvcEvaluationService.java:102-103`

**问题**：Prompt 要求 LLM 返回 `observation_score`（下划线），但 `NvcEvaluationResult` record 可能使用 `observationScore`（驼峰）。如果 Spring AI 的 `BeanOutputConverter` 不能自动映射，评估结果字段全为 null。

**修复方案**：
- 检查 `NvcEvaluationResult` 的字段命名
- 如果是驼峰，修改 Prompt 要求返回驼峰格式
- 或者在 Prompt 中同时说明两种格式

---

### BUG-6：Session 缓存反序列化后丢失 JPA 生命周期 ⭐潜在

**文件**：`NvcPracticeSessionService.java:79-84`

**问题**：从 Redis 缓存反序列化得到的 `NvcPracticeSessionEntity` 不是 JPA 托管实体。后续 `sessionRepository.save()` 时，`@PreUpdate` 生命周期回调行为可能不一致。

**修复方案**：
```java
// 方案 A：缓存只存 ID，每次都从 DB 加载
public NvcPracticeSessionEntity getSession(Long sessionId) {
    return sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(...));
}

// 方案 B（推荐）：缓存命中后，用 findById 重新加载以获得托管实体
public NvcPracticeSessionEntity getSession(Long sessionId) {
    String cached = redisService.get(cacheKey);
    if (cached != null) {
        // 缓存命中只用于快速判断是否存在，仍从 DB 加载托管实体
        return sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException(...));
    }
    // ...
}
```

---

### BUG-7：ScenarioRouter 中未使用的 import ⭐轻微

**文件**：`ScenarioRouter.java:7`

**修复**：删除 `import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;`

---

## 二、设计问题

### DESIGN-1：PracticeContext 的 userProfileSummary 和 ragContext 从未被填充 ⭐重要

**文件**：`NvcAgentOrchestrator.java:82-125`

**问题**：`PracticeContext` 定义了 `userProfileSummary` 和 `ragContext` 字段，`NvcAgentChatService.buildMessages` 也检查并注入这些字段，但 `buildPracticeContext` 方法从未设置这两个值。

**影响**：
- 用户档案个性化功能**完全不生效**
- RAG 知识库检索功能**完全不生效**

**修复方案**：
```java
// buildPracticeContext 中增加：
// 1. 加载用户档案
NvcUserProfileEntity profile = profileRepository.findByUserId(userId).orElse(null);
if (profile != null) {
    context.setUserProfileSummary(formatProfile(profile));
}

// 2. RAG 检索（如果有相关服务）
if (scenarioDescription != null) {
    List<RagResult> results = ragService.retrieve(scenarioDescription, userId, 3);
    context.setRagContext(formatRagResults(results));
}
```

---

### DESIGN-2：Controller 承担了业务逻辑 ⭐中等

**文件**：`NvcPracticeController.java:156-176`

**问题**：`completeSession` 在 Controller 中编排了"完成会话 + 执行最终评估"的完整业务流程。按 CLAUDE.md 分层规范，Controller 应仅做路由和委托。

**修复方案**：将评估逻辑移到 Service 层：
```java
// Controller
@PostMapping("/sessions/{sessionId}/complete")
public Result<PracticeSessionResponse> completeSession(@PathVariable Long sessionId) {
    NvcPracticeSessionEntity session = sessionService.completeSession(sessionId);
    return Result.success(toSessionResponse(session));
}

// Service
public NvcPracticeSessionEntity completeSession(Long sessionId) {
    NvcPracticeSessionEntity session = updatePhase(sessionId, NvcSessionPhase.COMPLETED);
    // 在 Service 中执行最终评估
    try {
        List<NvcPracticeMessageEntity> messages = messageRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);
        if (!messages.isEmpty()) {
            evaluationService.evaluateFinal(sessionId, session.getUserId(), messages);
            updatePhase(sessionId, NvcSessionPhase.EVALUATED);
        }
    } catch (Exception e) {
        log.error("Final evaluation failed: sessionId={}", sessionId, e);
    }
    return session;
}
```

---

### DESIGN-3：NvcDashboardService 统计查询效率极低 ⭐中等

**文件**：`NvcDashboardService.java:28-36`

**问题**：`getUserStats` 加载所有已完成会话的实体列表再取 `.size()`，只是为了获取数量。

**修复方案**：
```java
// 改为 count 查询
long completedSessions = sessionRepository.countByUserIdAndCurrentPhase(userId, NvcSessionPhase.COMPLETED);
long totalScores = abilityScoreRepository.countByUserId(userId);
```

---

### DESIGN-4：@Async 未配置专用线程池 ⭐中等

**文件**：`NvcSummaryService.java:72`

**问题**：`@Async` 使用 Spring 默认的 `SimpleAsyncTaskExecutor`（无界线程池），高并发下可能创建大量线程。

**修复方案**：
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("nvcTaskExecutor")
    public Executor nvcTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("nvc-async-");
        executor.initialize();
        return executor;
    }
}

// 使用时指定线程池
@Async("nvcTaskExecutor")
public void updateSummary(Long sessionId, String userMessage) { ... }
```

---

### DESIGN-5：对话历史每次都全量加载 ⭐轻微

**文件**：`NvcAgentOrchestrator.java:89-94`

**问题**：`buildPracticeContext` 每次都加载全部消息然后截取最近 20 条。

**修复方案**：
```java
// Repository 中添加
List<NvcPracticeMessageEntity> findTop20BySessionIdOrderBySequenceNumDesc(Long sessionId);

// Service 中使用
List<NvcPracticeMessageEntity> recentDesc = messageRepository.findTop20BySessionIdOrderBySequenceNumDesc(sessionId);
List<NvcPracticeMessageEntity> recentMessages = recentDesc.reversed(); // 反转为正序
```

---

### DESIGN-6：ReportService 可能触发同步 LLM 调用 ⭐中等

**文件**：`NvcReportService.java:48-59`

**问题**：当 `getFinalEvaluation` 返回 null 时，`generateReport` 会同步调用 `evaluateFinal`。多个用户同时访问同一 session 的报告会触发多次评估。

**修复方案**：
```java
// 方案 A：检查是否已有评估进行中（Redis 分布式锁）
// 方案 B（推荐）：报告页只读取已有评估，不触发新评估
// 如果没有评估数据，返回"评估中，请稍后刷新"
public NvcPracticeReport generateReport(Long sessionId) {
    NvcEvaluationEntity finalEval = evaluationService.getFinalEvaluation(sessionId);
    if (finalEval == null) {
        // 返回一个"评估中"的报告，而不是触发评估
        return NvcPracticeReport.evaluating(sessionId);
    }
    // ...
}
```

---

### DESIGN-7：完成练习的同步 vs 异步路径存在竞争 ⭐中等

**文件**：`NvcPracticeController.java:156-176` vs `NvcEvaluateStreamConsumer.java:90-108`

**问题**：Controller 的 `completeSession` 同步执行评估，同时 `NvcEvaluateStreamProducer` 也可以发送异步评估任务。两条路径可能同时被触发。

**修复方案**：统一为一条路径。推荐使用异步路径（Redis Stream），Controller 只负责标记完成和发送异步任务。

---

### DESIGN-8：overallScore 整数除法丢失精度 ⭐轻微

**文件**：`NvcProfileService.java:137, 181`

**问题**：`(avgObservation + avgFeeling + avgNeed + avgRequest) / 4` 整数除法截断。

**修复方案**：
```java
int overallAvg = (int) Math.round((avgObservation + avgFeeling + avgNeed + avgRequest) / 4.0);
```

---

### DESIGN-9：前后端重复的 JSON 清理逻辑 ⭐中等

**文件**：`AiResponseCleaner.java` + `NvcChatPanel.tsx:31-74`

**问题**：后端 `AiResponseCleaner` 和前端 `cleanAiResponse` + `removeInlineJson` 功能几乎完全重复，维护成本高。

**修复方案**：统一在后端清理，前端只展示已清理的内容。或者在 Prompt 层面让 AI 不输出 JSON。

---

## 三、Agent 调度问题

### AGENT-1：ScenarioRouter 只有 2 个状态，缺乏深度 ⭐重要

**文件**：`ScenarioRouter.java`

**问题**：场景模式只有 CREATED → SCENARIO_GENERATOR 和后续 → DIFFICULT_PARTNER 两个状态。没有：
- 对话中期的引导（DIALOGUE_GUIDE）
- 练习结束前的评估（NVC_EXPRESSION_EVALUATOR）
- 基于用户表现的难度调整

**修复方案**：增强 ScenarioRouter 路由逻辑：
```java
public AgentDecision route(PracticeContext context) {
    var session = context.getSession();
    int roundCount = context.getRoundCount();

    // 首轮：场景生成官
    if (session.getCurrentPhase() == NvcSessionPhase.CREATED) {
        return new AgentDecision(NvcAgentScene.SCENARIO_GENERATOR, ...);
    }

    // 每 5 轮：评估官介入
    if (roundCount > 0 && roundCount % 5 == 0 && context.getLastEvaluation() != null) {
        if (context.getLastEvaluation().getOverallScore() < 50) {
            return new AgentDecision(NvcAgentScene.NVC_EXPRESSION_EVALUATOR, ...);
        }
    }

    // 默认：困难搭档
    return new AgentDecision(NvcAgentScene.DIFFICULT_PARTNER, ...);
}
```

---

### AGENT-2：StructuredRouter 没有使用步骤教练 ⭐重要

**文件**：`StructuredRouter.java`

**问题**：`NvcAgentScene` 枚举定义了 4 个步骤教练（STEP_OBSERVE_COACH 等），`NvcStructuredPracticeService` 也有 `stepToCoach()` 方法，但 `StructuredRouter` 始终返回 `DIALOGUE_GUIDE`，没有使用步骤教练。

**修复方案**：
```java
public AgentDecision route(PracticeContext context) {
    NvcPracticeStep currentStep = context.getSession().getCurrentStep();
    if (currentStep != null && currentStep != NvcPracticeStep.COMPLETED) {
        // 使用对应的步骤教练
        NvcAgentScene coach = stepToCoach(currentStep);
        return new AgentDecision(coach, "结构化练习：" + currentStep + "步骤教练", ...);
    }
    return new AgentDecision(NvcAgentScene.DIALOGUE_GUIDE, "结构化练习完成，使用通用教练", ...);
}
```

---

### AGENT-3：FreeDialogRouter 完全静态，无评估触发 ⭐中等

**文件**：`FreeDialogRouter.java`

**问题**：始终返回 DIALOGUE_GUIDE，不会在适当时机触发评估或切换到其他 Agent。

**修复方案**：增加基于轮次的评估触发逻辑。

---

## 四、缺失功能

### MISSING-1：无会话状态转换校验 ⭐中等

**问题**：`updatePhase` 不校验状态转换是否合法，理论上可以从任何状态跳到任何状态。

**修复方案**：添加状态机校验：
```java
private static final Map<NvcSessionPhase, Set<NvcSessionPhase>> VALID_TRANSITIONS = Map.of(
    NvcSessionPhase.CREATED, Set.of(NvcSessionPhase.IN_PROGRESS),
    NvcSessionPhase.IN_PROGRESS, Set.of(NvcSessionPhase.PAUSED, NvcSessionPhase.COMPLETED),
    NvcSessionPhase.PAUSED, Set.of(NvcSessionPhase.IN_PROGRESS, NvcSessionPhase.COMPLETED),
    NvcSessionPhase.COMPLETED, Set.of(NvcSessionPhase.EVALUATED)
);

public NvcPracticeSessionEntity updatePhase(Long sessionId, NvcSessionPhase newPhase) {
    NvcPracticeSessionEntity session = getSession(sessionId);
    Set<NvcSessionPhase> allowed = VALID_TRANSITIONS.getOrDefault(session.getCurrentPhase(), Set.of());
    if (!allowed.contains(newPhase)) {
        throw new BusinessException(ErrorCode.BAD_REQUEST,
            "Invalid state transition: " + session.getCurrentPhase() + " -> " + newPhase);
    }
    // ...
}
```

---

### MISSING-2：无并发保护（sequenceNum 冲突）⭐中等

**问题**：同一 session 快速发送多条消息时，`getNextSequenceNum`（基于 count）可能产生重复的 sequenceNum。

**修复方案**：
```java
// 方案 A：使用数据库唯一约束 + 重试
// 方案 B（推荐）：使用 Redis INCR 生成全局递增序列号
private int getNextSequenceNum(Long sessionId) {
    String key = "nvc:seq:" + sessionId;
    return (int) redisService.increment(key);
}
```

---

### MISSING-3：completeSession 未校验当前会话状态 ⭐轻微

**问题**：没有检查 session 是否已经是 COMPLETED 状态，重复调用会重复执行评估。

**修复方案**：
```java
public NvcPracticeSessionEntity completeSession(Long sessionId) {
    NvcPracticeSessionEntity session = getSession(sessionId);
    if (session.getCurrentPhase() == NvcSessionPhase.COMPLETED
        || session.getCurrentPhase() == NvcSessionPhase.EVALUATED) {
        return session; // 已完成，直接返回
    }
    return updatePhase(sessionId, NvcSessionPhase.COMPLETED);
}
```

---

## 五、前端问题

### FE-1：SSE 解析事件和数据同步问题 ⭐中等

**文件**：`NvcChatPanel.tsx:158-179`

**问题**：处理 `data:` 后立即重置 `currentEvent = ''`。如果服务端发送多行 `data:`，只有第一行会匹配事件类型。

**修复方案**：
```typescript
// 按 SSE 规范，data 多行应该拼接，直到遇到空行
for (const line of lines) {
    if (line.startsWith('event:')) {
        currentEvent = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
        const data = line.slice(5).trim();
        if (currentEvent === 'message') {
            const token = data.replace(/\\n/g, '\n').replace(/\\r/g, '\r');
            fullContent += token;
            // ...update UI
        }
    } else if (line === '' && currentEvent) {
        currentEvent = ''; // 空行才重置事件类型
    }
}
```

---

### FE-2：PDF 下载缺少认证 ⭐轻微

**文件**：`NvcReportPage.tsx:150-156`

**问题**：`<a href=...>` 直接导航可能不携带认证头。

**修复方案**：改为 JS 发起带认证的请求，创建 Blob URL 下载。

---

### FE-3：摘要刷新使用固定 2 秒延迟 ⭐轻微

**文件**：`NvcPracticePage.tsx:51-54`

**问题**：固定 2 秒延迟等待 LLM 分析完成，不可靠。

**修复方案**：改为轮询（每 1 秒检查一次，最多 5 次）或 WebSocket 通知。

---

### FE-4：completeSession 后缺少本地状态更新 ⭐轻微

**文件**：`NvcPracticePage.tsx:77-90`

**问题**：`handleComplete` 调用 `completeSession` 后直接 `navigate`，不更新本地 `session` 状态。

**修复方案**：
```typescript
const handleComplete = useCallback(async () => {
    if (!sid) return;
    setCompleting(true);
    setCompletingMessage('正在生成评估报告...');
    try {
        const updated = await practiceApi.completeSession(sid);
        setSession(updated);  // 新增：更新本地状态
        navigate(`/nvc/history/${sid}/report`);
    } catch (err) {
        console.error('Failed to complete session:', err);
        setCompletingMessage('');
    } finally {
        setCompleting(false);
    }
}, [sid, navigate]);
```

---

### FE-5：NvcEvaluationCard 的 passed 属性语义不清 ⭐轻微

**文件**：`NvcEvaluationCard.tsx:74-80`

**问题**：组件使用 `data.observation.passed`，但后端不一定返回 `passed` 字段。

**修复方案**：在前端根据分数计算 `passed`（如 score >= 70）。

---

## 六、问题优先级排序

### P0（必须修复，影响核心功能）
1. BUG-1：会话状态永远不变为 EVALUATED
2. BUG-3：流式请求缺少认证头
3. DESIGN-1：PracticeContext 的 profileSummary 和 ragContext 从未填充
4. AGENT-1：ScenarioRouter 路由过于简单
5. AGENT-2：StructuredRouter 没有使用步骤教练

### P1（应该修复，影响用户体验）
6. BUG-2：流式模式下 JSON 闪烁
7. BUG-4：评估失败被静默吞掉
8. BUG-5：评估 Prompt 字段名不匹配
9. DESIGN-2：Controller 承担业务逻辑
10. DESIGN-3：Dashboard 查询效率低
11. DESIGN-4：@Async 无界线程池
12. DESIGN-7：同步/异步评估路径竞争
13. AGENT-3：FreeDialogRouter 无评估触发
14. MISSING-1：无状态转换校验
15. MISSING-2：无并发保护

### P2（建议修复，代码质量）
16. BUG-6：Session 缓存反序列化问题
17. BUG-7：未使用的 import
18. DESIGN-5：对话历史全量加载
19. DESIGN-6：ReportService 可能触发同步 LLM
20. DESIGN-8：整数除法精度丢失
21. DESIGN-9：前后端重复的 JSON 清理
22. MISSING-3：completeSession 未校验状态
23. FE-1：SSE 解析问题
24. FE-2：PDF 下载缺认证
25. FE-3：摘要刷新延迟
26. FE-4：completeSession 后缺状态更新
27. FE-5：passed 属性语义不清

---

## 七、Phase 0 修复计划（按优先级排序）

### Day 1：核心流程修复

| 序号 | 任务 | 问题编号 | 预估 |
|------|------|---------|------|
| 1 | 会话完成后更新状态为 EVALUATED | BUG-1 | 0.5h |
| 2 | 业务逻辑从 Controller 移到 Service | DESIGN-2 | 1h |
| 3 | 添加状态转换校验 | MISSING-1 | 1h |
| 4 | completeSession 增加重复调用校验 | MISSING-3 | 0.5h |
| 5 | ScenarioRouter 增强路由逻辑 | AGENT-1 | 2h |
| 6 | StructuredRouter 使用步骤教练 | AGENT-2 | 1h |
| 7 | FreeDialogRouter 增加评估触发 | AGENT-3 | 1h |

### Day 2：数据与性能修复

| 序号 | 任务 | 问题编号 | 预估 |
|------|------|---------|------|
| 8 | 填充 PracticeContext 的 profileSummary | DESIGN-1 | 1.5h |
| 9 | 评估 Prompt 字段名对齐 | BUG-5 | 0.5h |
| 10 | 优化评估 Prompt（更具体的反馈） | — | 1h |
| 11 | Dashboard 查询改为 count | DESIGN-3 | 0.5h |
| 12 | @Async 配置专用线程池 | DESIGN-4 | 0.5h |
| 13 | 整数除法改为四舍五入 | DESIGN-8 | 0.5h |
| 14 | 对话历史改为 Top20 查询 | DESIGN-5 | 0.5h |
| 15 | 前端流式请求添加认证头 | BUG-3 | 0.5h |
| 16 | 清理未使用的 import | BUG-7 | 0.1h |

### 可选（时间允许）

| 序号 | 任务 | 问题编号 | 预估 |
|------|------|---------|------|
| 17 | 流式 JSON 闪烁修复 | BUG-2 | 1.5h |
| 18 | 评估失败不静默吞掉 | BUG-4 | 1h |
| 19 | 同步/异步评估路径统一 | DESIGN-7 | 1.5h |
| 20 | sequenceNum 并发保护 | MISSING-2 | 1h |
