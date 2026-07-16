# Phase 0：修 Bug + 稳定现有（2 天）

> 详见 [01-现状诊断.md](01-现状诊断.md) 获取完整问题清单
>
> 本文件只记录修复任务和方案

---

## Day 1：核心流程修复

| # | 任务 | 问题编号 | 修改文件 | 方案 |
|---|------|---------|---------|------|
| 1 | 会话完成后更新状态为 EVALUATED | BUG-1 | NvcPracticeController | evaluateFinal 成功后调 updatePhase(EVALUATED) |
| 2 | 业务逻辑从 Controller 移到 Service | DESIGN-2 | NvcPracticeController, NvcPracticeSessionService | completeSession 编排移到 Service 层 |
| 3 | 添加状态转换校验 | MISSING-1 | NvcPracticeSessionService | ValidTransition Map 校验 |
| 4 | completeSession 增加重复调用校验 | MISSING-3 | NvcPracticeSessionService | 已 COMPLETED/EVALUATED 则直接返回 |
| 5 | ScenarioRouter 增强路由逻辑 | AGENT-1 | ScenarioRouter.java | CREATED→GENERATOR, 每5轮低分→EVALUATOR, 默认→DIFFICULT_PARTNER |
| 6 | StructuredRouter 使用步骤教练 | AGENT-2 | StructuredRouter.java | OBSERVE→STEP_OBSERVE_COACH 等 |
| 7 | FreeDialogRouter 增加评估触发 | AGENT-3 | FreeDialogRouter.java | 每 5 轮触发 NVC_EXPRESSION_EVALUATOR |

---

## Day 2：数据与性能修复

| # | 任务 | 问题编号 | 修改文件 | 方案 |
|---|------|---------|---------|------|
| 8 | 填充 PracticeContext 的 profileSummary | DESIGN-1 | NvcAgentOrchestrator | buildPracticeContext 中加载用户档案 |
| 9 | 评估 Prompt 字段名对齐 | BUG-5 | nvc-evaluation-system.st | 检查 NvcEvaluationResult 字段命名，修改 Prompt |
| 10 | 优化评估 Prompt（更具体反馈） | P1-16 | nvc-evaluation-system.st | 要求输出可操作改进建议 + 下一步建议 |
| 11 | Dashboard 查询改为 countBy | DESIGN-3 | NvcDashboardService | countByUserIdAndCurrentPhase |
| 12 | @Async 配置专用线程池 | DESIGN-4 | AsyncConfig + NvcSummaryService | ThreadPoolTaskExecutor(2,5,100) |
| 13 | 整数除法改为四舍五入 | DESIGN-8 | NvcProfileService | Math.round |
| 14 | 对话历史改为 Top20 查询 | DESIGN-5 | NvcAgentOrchestrator + Repository | findTop20BySessionIdOrderBySequenceNumDesc |
| 15 | 前端流式请求添加认证头 | BUG-3 | frontend/src/api/nvc.ts | 从 request 模块导出认证头 |
| 16 | 清理未使用的 import | BUG-7 | ScenarioRouter.java | 删除 NvcEvaluationEntity import |

---

## 可选（时间允许）

| # | 任务 | 问题编号 | 预估 |
|---|------|---------|------|
| 17 | 流式 JSON 闪烁修复 | BUG-2 | 1.5h |
| 18 | 评估失败不静默吞掉 | BUG-4 | 1h |
| 19 | 同步/异步评估路径统一 | DESIGN-7 | 1.5h |
| 20 | sequenceNum 并发保护 | MISSING-2 | 1h |

---

## 关键修复代码示例

### 修复 1：会话状态更新为 EVALUATED

```java
// NvcPracticeSessionService.java
public NvcPracticeSessionEntity completeSession(Long sessionId) {
    NvcPracticeSessionEntity session = getSession(sessionId);
    if (session.getCurrentPhase() == NvcSessionPhase.COMPLETED
        || session.getCurrentPhase() == NvcSessionPhase.EVALUATED) {
        return session; // 已完成，直接返回
    }
    return updatePhase(sessionId, NvcSessionPhase.COMPLETED);
}

// NvcPracticeController.java
@PostMapping("/sessions/{sessionId}/complete")
public Result<PracticeSessionResponse> completeSession(@PathVariable Long sessionId) {
    NvcPracticeSessionEntity session = sessionService.completeSession(sessionId);

    // 同步执行最终评估
    try {
        List<NvcPracticeMessageEntity> messages =
            messageRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);
        if (!messages.isEmpty()) {
            evaluationService.evaluateFinal(sessionId, session.getUserId(), messages);
            // 关键修复：评估成功后更新状态为 EVALUATED
            session = sessionService.updatePhase(sessionId, NvcSessionPhase.EVALUATED);
        }
    } catch (Exception e) {
        log.error("Final evaluation failed: sessionId={}", sessionId, e);
    }

    return Result.success(toSessionResponse(session));
}
```

### 修复 5：ScenarioRouter 增强

```java
@Component
public class ScenarioRouter implements ModeRouter {

    @Override
    public AgentDecision route(PracticeContext context) {
        var session = context.getSession();
        int roundCount = context.getRoundCount();
        Map<String, String> vars = new HashMap<>();
        vars.put("mode", "scenario");

        // CREATED 阶段：场景生成官引入场景
        if (session.getCurrentPhase() == NvcSessionPhase.CREATED) {
            return new AgentDecision(
                NvcAgentScene.SCENARIO_GENERATOR,
                "场景模式首轮，使用场景生成官引入场景",
                null, vars);
        }

        // 每 5 轮且分数低：评估官介入
        if (roundCount > 0 && roundCount % 5 == 0) {
            NvcEvaluationEntity eval = context.getLastEvaluation();
            if (eval != null && eval.getOverallScore() != null
                && eval.getOverallScore() < 50) {
                return new AgentDecision(
                    NvcAgentScene.NVC_EXPRESSION_EVALUATOR,
                    "场景模式，评估官介入（分数低于50）",
                    null, vars);
            }
        }

        // 默认：困难搭档
        return new AgentDecision(
            NvcAgentScene.DIFFICULT_PARTNER,
            "场景模式，使用困难搭档扮演对方",
            null, vars);
    }
}
```

### 修复 6：StructuredRouter 使用步骤教练

```java
@Component
public class StructuredRouter implements ModeRouter {

    @Override
    public AgentDecision route(PracticeContext context) {
        NvcPracticeStep currentStep = context.getSession().getCurrentStep();
        Map<String, String> vars = new HashMap<>();
        vars.put("mode", "structured");

        // 使用对应的步骤教练
        if (currentStep != null && currentStep != NvcPracticeStep.COMPLETED) {
            NvcAgentScene coach = stepToCoach(currentStep);
            return new AgentDecision(
                coach,
                "结构化练习：" + currentStep + "步骤教练",
                null, vars);
        }

        // 练习完成，使用通用教练
        return new AgentDecision(
            NvcAgentScene.DIALOGUE_GUIDE,
            "结构化练习完成，使用通用教练",
            null, vars);
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
}
```

### 修复 8：填充 PracticeContext 的 profileSummary

```java
// NvcAgentOrchestrator.buildPracticeContext() 中增加
String userProfileSummary = null;
NvcUserProfileEntity profile = profileRepository.findByUserId(userId).orElse(null);
if (profile != null) {
    userProfileSummary = formatProfile(profile);
}

return PracticeContext.builder()
    .session(session)
    .recentMessages(recentMessages)
    .lastEvaluation(lastEvaluation)
    .roundCount((int) userMessageCount)
    .scenarioDescription(scenarioDescription)
    .scenario(scenario)
    .userProfileSummary(userProfileSummary)  // 新增
    .build();
```

---

## 验收标准

```
□ BUG-1：会话完成后状态变为 EVALUATED
□ BUG-3：前端流式请求带认证头
□ BUG-5：评估 Prompt 字段名匹配
□ DESIGN-1：PracticeContext 填充 profileSummary
□ DESIGN-2：业务逻辑在 Service 层
□ DESIGN-3：Dashboard 查询用 countBy
□ DESIGN-4：@Async 用有界线程池
□ AGENT-1：ScenarioRouter 有 3 种路由
□ AGENT-2：StructuredRouter 使用步骤教练
□ AGENT-3：FreeDialogRouter 有评估触发
□ MISSING-1：状态转换有校验
```
