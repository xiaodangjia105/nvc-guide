# NVC 模式边界修复 — 实现计划

> 基于: `docs/superpowers/specs/2026-07-12-mode-boundary-fix-design.md`

## 实现步骤

### Phase 1: 后端编排层重构

#### Step 1.1: 扩展 AgentDecision 支持 promptVariables

**文件**: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/AgentDecision.java`

将 `AgentDecision` record 扩展，增加 `promptVariables` 字段：

```java
public record AgentDecision(
    NvcAgentScene scene,
    String reason,
    String action,
    Map<String, String> promptVariables  // 新增：注入到 Prompt 模板的变量
) {
  // 便捷构造器，兼容现有调用
  public AgentDecision(NvcAgentScene scene, String reason, String action) {
    this(scene, reason, action, Map.of());
  }
}
```

#### Step 1.2: 创建 ModeRouter 接口和 RouterDecision

**新文件**: `app/src/main/java/nvc/guide/modules/nvcpractice/router/ModeRouter.java`

```java
public interface ModeRouter {
  AgentDecision route(PracticeContext context);
}
```

#### Step 1.3: 实现 FreeDialogRouter

**新文件**: `app/src/main/java/nvc/guide/modules/nvcpractice/router/FreeDialogRouter.java`

逻辑：
- 任何阶段 → 返回 DIALOGUE_GUIDE
- promptVariables: `{mode: "free_dialog"}`
- 不触发 SCENARIO_GENERATOR、不触发步骤教练

#### Step 1.4: 实现 ScenarioRouter

**新文件**: `app/src/main/java/nvc/guide/modules/nvcpractice/router/ScenarioRouter.java`

逻辑：
- CREATED 阶段 → SCENARIO_GENERATOR + 场景详情注入
- 后续 → DIFFICULT_PARTNER + 场景详情注入
- promptVariables 包含 `scenario_context`

#### Step 1.5: 实现 StructuredRouter

**新文件**: `app/src/main/java/nvc/guide/modules/nvcpractice/router/StructuredRouter.java`

逻辑：
- 任何阶段 → DIALOGUE_GUIDE
- promptVariables: `{mode: "structured", covered_elements: "..."}`
- 从评估结果推导已覆盖的要素

#### Step 1.6: 重构 NvcAgentOrchestrator.decideNextAgent()

**文件**: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentOrchestrator.java`

改动：
- 注入三个 Router（Spring 自动装配）
- `decideNextAgent()` 根据 `session.getPracticeMode()` 选择对应 Router
- 删除 `decideFreeDialog()` 和 `decideStructuredStep()` 方法
- 保留 `reflect()` 和辅助方法

```java
private final Map<NvcPracticeMode, ModeRouter> routers;

public NvcAgentOrchestrator(
    FreeDialogRouter freeDialogRouter,
    ScenarioRouter scenarioRouter,
    StructuredRouter structuredRouter) {
  this.routers = Map.of(
      NvcPracticeMode.FREE_DIALOG, freeDialogRouter,
      NvcPracticeMode.SCENARIO, scenarioRouter,
      NvcPracticeMode.STRUCTURED_FOUR_STEP, structuredRouter
  );
}

public AgentDecision decideNextAgent(PracticeContext context) {
  ModeRouter router = routers.get(context.getSession().getPracticeMode());
  return router.route(context);
}
```

### Phase 2: Prompt 变量注入

#### Step 2.1: NvcAgentChatService 支持 promptVariables

**文件**: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentChatService.java`

改动 `buildMessages()` 方法：
- 增加 `Map<String, String> promptVariables` 参数
- 在系统 Prompt 末尾注入变量：`\n\n[当前配置]\nmode=free_dialog\ncovered_elements=...`
- `chat()` 和 `chatStream()` 方法签名增加 promptVariables 参数

#### Step 2.2: 更新 executeAgent / executeAgentStream 调用

**文件**: `NvcAgentOrchestrator.java` 的 `executeAgent()` / `executeAgentStream()`

- 传递 `AgentDecision.promptVariables()` 给 `agentChatService`

### Phase 3: 输出清理

#### Step 3.1: 创建 AiResponseCleaner

**新文件**: `app/src/main/java/nvc/guide/modules/nvcpractice/util/AiResponseCleaner.java`

```java
public class AiResponseCleaner {
  public static String clean(String raw) {
    // 1. 去除 ```json ... ``` 代码块
    // 2. 去除 ```...``` 代码块
    // 3. 去除独立的 JSON 对象（以 { 开头，以 } 结尾的段落）
    // 4. 去除多余空行（连续3行以上空行压缩为1行）
    // 5. trim 后返回
  }
}
```

#### Step 3.2: 在 DialogueService 中调用清理

**文件**: `NvcPracticeDialogueService.java`

- 在保存 AI 回复前调用 `AiResponseCleaner.clean()`
- 流式场景：在 `doOnComplete` 中对 `fullContent` 清理后再保存

### Phase 4: Prompt 重写

#### Step 4.1: 重写 DIALOGUE_GUIDE Prompt

**文件**: `data-nvc-agent-config.sql`

新的 Prompt 结构：
```
## 角色定义
你是一位温和、有耐心的 NVC 教练...

## 当前模式
根据注入的 mode 变量切换行为

## 模式边界
✅ / ❌ 明确的允许/禁止行为

## 输出格式
只输出自然语言，绝对不要输出 JSON/代码
```

#### Step 4.2: 重写 SCENARIO_GENERATOR Prompt

统一 .st 和 SQL 版本，明确：
- 用自然语言介绍场景
- 然后进入角色扮演
- 绝不输出 JSON

#### Step 4.3: 重写 DIFFICULT_PARTNER Prompt

强化角色扮演指令：
- 始终以角色身份回应
- 保持角色性格和情绪
- 绝不输出 JSON/教学内容

### Phase 5: 前端改造

#### Step 5.1: NvcChatPanel 增加 practiceMode prop

**文件**: `frontend/src/components/nvc/NvcChatPanel.tsx`

- 增加 `practiceMode: NvcPracticeMode` prop
- 根据模式设置不同 placeholder
- AI 回复显示前做兜底过滤（去除 ```...``` 代码块和 JSON 片段）

#### Step 5.2: NvcPracticePage 传递 practiceMode

**文件**: `frontend/src/pages/NvcPracticePage.tsx`

- 将 `session.practiceMode` 传递给 `NvcChatPanel`

#### Step 5.3: 结构化练习 UI 改造

**文件**: `NvcStepIndicator.tsx` 或新组件

- 从"步骤指示器"改为"四要素覆盖进度"
- 显示四个要素的覆盖状态
- 不再显示"当前步骤"

### Phase 6: 测试验证

#### Step 6.1: 自由对话模式验证
- 创建 FREE_DIALOG session
- 发送"朋友迟到了我很生气"
- 验证：AI 不生成场景、不输出 JSON、用教练风格引导

#### Step 6.2: 场景模式验证
- 从场景库选择场景 → 创建 SCENARIO session
- 验证：第一条消息用自然语言介绍场景
- 验证：后续消息以角色身份回应

#### Step 6.3: 结构化模式验证
- 创建 STRUCTURED_FOUR_STEP session
- 验证：AI 主动引导四要素
- 验证：不强制顺序，灵活过渡

#### Step 6.4: 输出清理验证
- 验证 AI 回复中不包含 JSON/代码块
- 验证前端兜底过滤正常工作
