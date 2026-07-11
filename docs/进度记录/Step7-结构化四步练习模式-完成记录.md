# Step 7: 结构化四步练习模式 - 完成记录

## 完成时间

2026-07-11

## 实现内容

### 新增文件（8个）

| 文件 | 类型 | 职责 |
|------|------|------|
| nvc-step-observe-system.st | Prompt | 观察步骤教练提示词 |
| nvc-step-feeling-system.st | Prompt | 感受步骤教练提示词 |
| nvc-step-need-system.st | Prompt | 需求步骤教练提示词 |
| nvc-step-request-system.st | Prompt | 请求步骤教练提示词 |
| StepProgressDTO.java | DTO | 步骤进度响应（10 个字段） |
| NvcStructuredPracticeService.java | Service | 结构化练习服务（4 个公共方法） |
| NvcStructuredPracticeServiceTest.java | 测试 | Service 单元测试（25 tests） |
| NvcPracticeDialogueServiceTest.java | 测试 | 对话服务测试（7 tests） |

### 修改文件（7个）

| 文件 | 修改内容 |
|------|----------|
| NvcAgentConfigEntity.java | 添加 stepAdvanceThreshold, maxStepAttempts, stepTimeoutMinutes 字段 |
| AgentConfigDTO.java | 添加 3 个新字段到 DTO |
| AgentConfigUpdateRequest.java | 添加 3 个带验证的新字段 |
| NvcAgentConfigService.java | 更新 toDTO 和 updateConfig 方法映射 |
| NvcPracticeController.java | 添加 3 个步骤进度 API 端点 |
| NvcPracticeDialogueService.java | 集成步骤自动推进逻辑 |
| NvcEvaluationRepository.java | 重命名为 findFirstBySessionIdAndEvaluationTypeOrderByCreatedAtDesc |

## API 端点

### NvcPracticeController（新增）

| 方法 | 路径 | 说明 | 限流 |
|------|------|------|------|
| GET | /api/nvc/practice/sessions/{sessionId}/step-progress | 获取步骤进度 | - |
| POST | /api/nvc/practice/sessions/{sessionId}/advance-step | 手动推进到下一步 | 10/min |
| POST | /api/nvc/practice/sessions/{sessionId}/reset-step | 重置步骤（重新开始） | 5/min |

## 核心功能

### 1. 步骤进度查询
- 返回当前步骤、步骤索引、总步骤数、当前分数、是否可推进
- 非结构化模式返回 null
- 四步完成返回 COMPLETED 状态

### 2. 步骤推进
- 自动推进：对话后评分 >= 阈值时自动切换到下一步
- 手动推进：通过 API 调用 advanceStep
- 推进步骤：OBSERVE → FEELING → NEED → REQUEST → COMPLETED

### 3. 步骤重置
- 重置到 OBSERVE 步骤重新开始
- 任何状态都可以重置（包括 COMPLETED）

### 4. 步骤教练 Prompt
- **观察教练**：引导区分观察（客观事实）和评论（主观判断）
- **感受教练**：引导识别和表达真实感受，区分感受和想法
- **需求教练**：引导从感受追溯深层需求，区分需求和策略
- **请求教练**：引导将需求转化为具体、可执行、正向的请求

## 技术实现

### Service 层

```java
NvcStructuredPracticeService {
    getStepProgress(Long sessionId)    // 获取步骤进度
    advanceStep(Long sessionId)        // 推进到下一步
    canAdvance(Long sessionId, Integer currentScore)  // 检查是否可推进
    resetStep(Long sessionId)          // 重置到第一步
}
```

### 步骤推进阈值
- 通过 Agent 配置可配置（`stepAdvanceThreshold`）
- 默认值：70 分
- 每个步骤教练可配置不同阈值

### 对话流程集成

```java
// sendMessage() 和 sendMessageStream() 中
if (session.getPracticeMode() == NvcPracticeMode.STRUCTURED_FOUR_STEP
    && decision.action() != null
    && decision.action().equals("STEP_ADVANCE")) {
    structuredPracticeService.advanceStep(sessionId);
}
```

### 错误处理
- 非结构化模式调用 advanceStep/resetStep：`ErrorCode.BAD_REQUEST`
- 已完成会话调用 advanceStep：`ErrorCode.BAD_REQUEST`
- doOnComplete 中的 advanceStep 调用有 try-catch 保护

## 技术亮点

1. **可配置阈值**：每个步骤教练可独立配置推进阈值
2. **立即推进**：Agent 判定通过后立即切换到下一步教练
3. **统一封装**：自动推进和手动推进都调用同一个 advanceStep 方法
4. **异常保护**：doOnComplete 中的 advanceStep 有 try-catch 防止异常被吞没
5. **Repository 优化**：使用 findFirst 避免 IncorrectResultSizeDataAccessException

## 测试覆盖

### NvcStructuredPracticeServiceTest（25 tests）

**getStepProgress（7 tests）：**
- 非结构化模式返回 null
- 已完成会话返回 COMPLETED
- 步骤为 null 时返回 COMPLETED
- 有评估时返回正确分数
- 无评估时返回 null 分数
- 低于阈值时 canAdvance = false
- 评估中分数为 null 时返回 0

**advanceStep（7 tests）：**
- 错误模式抛出异常
- 已完成会话抛出异常
- 步骤为 null 抛出异常
- OBSERVE → FEELING
- FEELING → NEED
- NEED → REQUEST
- REQUEST → COMPLETED

**canAdvance（7 tests）：**
- 步骤为 null 返回 false
- 已完成步骤返回 false
- 分数为 null 返回 false
- 低于阈值返回 false
- 等于阈值返回 true
- 高于阈值返回 true
- 阈值为 null 时使用默认值 70

**resetStep（4 tests）：**
- 错误模式抛出异常
- 正常重置到 OBSERVE
- 已完成会话可以重置
- NEED 状态重置到 OBSERVE

### NvcPracticeDialogueServiceTest（7 tests）

**sendMessage 步骤推进（4 tests）：**
- STEP_ADVANCE 动作触发推进
- 非 STEP_ADVANCE 动作不触发推进
- 非结构化模式不触发推进
- null 动作不触发推进

**sendMessageStream 步骤推进（3 tests）：**
- STEP_ADVANCE 动作触发推进
- 非 STEP_ADVANCE 动作不触发推进
- 非结构化模式不触发推进

## 编译验证

- ✅ `gradlew compileJava` 编译通过
- ✅ `gradlew test` 全部测试通过（69 个测试套件）
- ✅ `gradlew build` 构建成功
- ✅ 所有文件已提交到 Git

## Git 提交

```
0619adc feat(step7): add step coach prompt templates
b573f6b feat(step7): extend NvcAgentConfigEntity with step configuration fields
d8ca987 feat(step7): add StepProgressDTO
20014e6 feat(step7): add NvcStructuredPracticeService
410dd0d fix(nvcpractice): correct ErrorCode and add NvcStructuredPracticeService tests
ec1c820 feat(step7): add step progress APIs to NvcPracticeController
70a737c feat(step7): integrate step advancement into dialogue service
3d3a878 test(step7): add sendMessageStream() step advancement coverage
492d972 test(step7): add controller tests for step progress APIs
51130af fix(step7): address review issues in structured four-step practice
```

## 后续扩展

1. **步骤计时**：追踪每个步骤的开始时间和持续时间
2. **尝试次数**：记录每个步骤的尝试次数
3. **步骤级评估报告**：生成每个步骤的详细评估报告
4. **缓存优化**：使用 Redis 缓存步骤进度数据
5. **步骤解锁**：支持前置步骤完成后解锁后续步骤
