# Code Review: BUG-2/4/6 修复代码审查

**审查日期**: 2026-07-16
**审查范围**: BUG-2 (流式JSON闪烁)、BUG-4 (评估失败静默吞掉)、BUG-6 (Session缓存JPA生命周期)
**审查方法**: 8 角度并行扫描 (line-by-line / removed-behavior / cross-file / reuse / simplification / efficiency / altitude / conventions) + 单轮验证
**审查级别**: high effort (recall-biased)

---

## 发现汇总

| # | 严重度 | 文件 | 问题 |
|---|--------|------|------|
| 1 | 🔴 高 | `AiResponseCleaner.java:55` | StreamingCleaner 根本性缺陷：不完整 JSON 逐字符泄漏到前端 |
| 2 | 🟡 中 | `NvcPracticeController.java:178` | currentPhase 返回陈旧值 (COMPLETED 而非 EVALUATED) |
| 3 | 🟡 中 | `NvcPracticeSessionService.java:76` | getSession 缓存纯开销：命中和未命中都执行 DB 查询 |
| 4 | 🟡 中 | `NvcPracticeController.java:162` | Controller 包含业务逻辑，违反 CLAUDE.md 规范 |
| 5 | 🟡 中 | `NvcReportPage.tsx:130` | isDefaultReport 误报风险：合法极低分可触发警告 |
| 6 | 🟡 中 | `NvcPracticeController.java:228` | N+1 查询：toSessionResponse 对每个 session 单独查场景 |
| 7 | 🟢 低 | `NvcPracticeSessionService.java:87` | 缓存 TTL 不刷新：只读会话 24h 后过期 |
| 8 | 🟢 低 | `NvcPracticeSessionService.java:76` | 无缓存清理机制：DB 删除会话后残留缓存无法自愈 |

---

## 详细发现

### 发现 1: StreamingCleaner 存在根本性缺陷

**文件**: `app/src/main/java/nvc/guide/modules/nvcpractice/util/AiResponseCleaner.java:55`
**分类**: correctness
**判定**: CONFIRMED

**问题**: StreamingCleaner 的 delta 方案无法解决流式 JSON 闪烁问题。

**原理分析**:
- 不完整的 JSON 块（如 `{"key":`）在 `removeInlineJson` 中因 depth 永远不归 0 而逐字符通过
- 这些字符作为 SSE message 事件发送到前端并立即渲染
- 当闭合 `}` 到达后，整个 JSON 块被识别并从 cleaned buffer 中剥离
- 此时 `currentCleaned.length() <= lastCleaned.length()`，delta 为空，停止发送
- 但前端已累积并显示了原始 JSON 片段
- 流结束后前端 `cleanAiResponse()` 清除 JSON，但流式过程中用户看到了闪烁

**复现场景**:
```
AI 输出: "I hear you. {"obs": "late"} Tell me more."
前端流式显示: "I hear you. {"obs": "late"} Tell me more."
流结束后前端清理: "I hear you.  Tell me more."
```

**建议**: StreamingCleaner 的 delta 方案有根本性限制 — 无法撤回已发送内容。应从 Prompt 层面解决（指示 LLM 不要在对话中输出 JSON），或在前端做增量清理。

---

### 发现 2: completeSession 返回陈旧的 currentPhase

**文件**: `app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcPracticeController.java:178`
**分类**: correctness
**判定**: PLAUSIBLE

**问题**: 第 170 行 `sessionService.updatePhase(sessionId, NvcSessionPhase.EVALUATED)` 的返回值被丢弃。`session` 变量仍指向 `completeSession` 返回的实体（currentPhase=COMPLETED）。

**影响条件**:
- Spring Boot 默认启用 OSIV (`spring.jpa.open-in-view=true`)，此时 `session` 和 `updatePhase` 内部操作的是同一个 JPA 托管实体，bug 不触发
- 当 OSIV 被禁用时（生产环境常见优化），`session` 在 `completeSession` 返回后变为 detached，`updatePhase` 修改的是另一个实体，controller 的 `session` 保持 COMPLETED

**复现场景**: OSIV 禁用时，前端收到 `currentPhase=COMPLETED`，NvcHistoryPage 显示错误的徽章颜色（蓝色"已完成"而非绿色"已评估"）。

**修复建议**:
```java
session = sessionService.updatePhase(sessionId, NvcSessionPhase.EVALUATED);
```

---

### 发现 3: getSession 的 Redis 缓存现在是纯开销

**文件**: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeSessionService.java:76`
**分类**: efficiency
**判定**: CONFIRMED

**问题**: BUG-6 修复后，`getSession` 的缓存命中和未命中路径都执行 `sessionRepository.findById(sessionId)`。Redis GET 增加了延迟但从未避免 DB 查询。

**影响**: 一条用户消息触发 3+ 次 `getSession`（dialogueService.sendMessage → updatePhase → updateAgentScene），每次都执行 Redis GET + DB SELECT。DB 读负载相比修复前翻倍。

**修复建议**: 既然缓存不再用于返回数据，应简化为纯 DB 查询（移除 Redis 交互），或改用轻量级存在性检查（Redis SETNX with TTL，不存储完整实体 JSON）。

---

### 发现 4: Controller 包含业务逻辑

**文件**: `app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcPracticeController.java:162`
**分类**: conventions
**判定**: CONFIRMED

**问题**: `completeSession` 方法直接调用 `evaluationService.evaluateFinal()` 和 `sessionService.updatePhase()` 进行业务编排。

**CLAUDE.md 规范**: "Controller 层 - 仅路由和委托，禁止业务逻辑"

**修复建议**: 将评估 + 阶段更新逻辑移入 `NvcPracticeSessionService.completeSession()` 方法，Controller 只做路由委托。

---

### 发现 5: isDefaultReport 误报风险

**文件**: `frontend/src/pages/NvcReportPage.tsx:130`
**分类**: correctness
**判定**: CONFIRMED

**问题**: `isDefaultReport` 使用 `overallScore === 0 && observationScore === 0 && feelingScore === 0` 作为"评估失败"的代理指标。但合法的极低分评估也可触发此条件。

**复现场景**: 用户完成一次非常简短的练习，AI 评估在观察和感受维度给出 0 分（合法结果）。报告页错误显示黄色警告"评估生成失败，以下为默认报告"。

**修复建议**: 在 `NvcPracticeReport` DTO 中添加显式的 `evaluationFailed` 或 `isDefault` 字段，由后端在生成报告时设置，而非前端通过分数推断。

---

### 发现 6: toSessionResponse 的 N+1 查询

**文件**: `app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcPracticeController.java:228`
**分类**: efficiency
**判定**: CONFIRMED

**问题**: `toSessionResponse` 对每个 session 执行 `scenarioRepository.findById(scenarioId)`。当 `getUserSessions` 返回 N 个会话时，触发 N 次额外的 DB 查询。

**影响**: 用户有 50 个练习会话时，GET /api/nvc/practice/sessions 触发 1 次会话列表查询 + 50 次场景详情查询。

**修复建议**: 批量加载场景信息，或在 session 创建时冗余存储 scenarioTitle/scenarioDescription。

---

### 发现 7: 缓存 TTL 不刷新

**文件**: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeSessionService.java:87`
**分类**: efficiency
**判定**: CONFIRMED

**问题**: 缓存未命中时写入缓存，但缓存命中时不刷新 TTL。长期只读会话的缓存条目会在 24h 后过期，即使持续被访问。

**影响**: 只读会话过期后触发不必要的 DB 查询和缓存重建。

---

### 发现 8: 无缓存清理机制

**文件**: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeSessionService.java:76`
**分类**: correctness
**判定**: PLAUSIBLE

**问题**: 原代码在反序列化失败时调用 `redisService.delete(cacheKey)` 清理损坏的缓存条目。新代码移除了此清理路径。如果会话从 DB 中被直接删除，残留的缓存条目持续 24h 无法自愈。

---

## 审查方法

本审查使用 8 个独立角度并行扫描:

1. **Line-by-line diff scan** — 逐行检查每个变更
2. **Removed-behavior auditor** — 审计被删除的行为
3. **Cross-file tracer** — 跨文件影响分析
4. **Reuse check** — 代码复用机会
5. **Simplification check** — 不必要的复杂度
6. **Efficiency check** — 性能浪费
7. **Altitude check** — 修复深度评估
8. **Conventions check** — CLAUDE.md 规范合规

每个候选发现经过单轮验证，输出 CONFIRMED / PLAUSIBLE / REFUTED 判定。
