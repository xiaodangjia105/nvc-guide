# NVC 练习模式边界清晰化设计

> 日期: 2026-07-12
> 状态: 待审核

## 问题背景

当前 NVC 练习系统的三种模式（自由对话、场景练习、结构化四步）边界模糊，导致：
1. 自由对话模式触发场景生成器，输出 JSON 给用户
2. AI 回复中混杂代码/JSON 字段
3. 各模式的 Agent 互相干扰，用户分不清自己在哪个模式
4. 前端对三种模式无差异化展示

## 设计目标

1. **模式边界清晰**：每种模式有明确的 Agent 路由、Prompt 定义、前端展示
2. **可扩展**：未来新增模式、修改 Prompt、接入 RAG、调整编排逻辑，改动范围可控
3. **可维护**：Prompt 与代码分离，编排逻辑与 Agent 配置分离
4. **高可用**：输出清理兜底，不让格式问题到达用户

---

## 一、三种模式定义

### 模式 1：自由对话（FREE_DIALOG）

| 维度 | 定义 |
|------|------|
| 定位 | NVC 教练对话 |
| AI 角色 | 温和的 NVC 教练，引导用户理解自己的沟通问题 |
| 典型场景 | 用户描述生活烦恼、沟通困惑 |
| 流程 | 用户描述 → AI 共情 → 逐步引导四要素 → 用户练习表达 |
| 禁止行为 | 不生成场景、不模拟角色、不输出 JSON、不切换模式 |

**对话示例：**
```
用户：朋友迟到了我很生气
AI：听起来朋友迟到让你很不舒服。能多说说吗？当时是什么情况？
用户：约好3点见面，他3点半才来，也没提前说
AI：嗯，等待的时候你心里是什么感受？是焦虑、还是觉得不被重视？
```

### 模式 2：场景练习（SCENARIO）

| 维度 | 定义 |
|------|------|
| 定位 | 角色扮演练习 |
| AI 角色 | 扮演场景中的对方角色 |
| 典型场景 | 从场景库选择场景，或 AI 生成新场景 |
| 流程 | 选择/生成场景 → 进入对话 → AI 扮演对方 → 用户练习 NVC |
| 禁止行为 | 不输出 JSON、不切换到教练模式、不脱离角色 |

**对话示例：**
```
[场景卡片：项目延期引发的团队冲突]
AI（扮演张华）：你有什么想说的吗？
用户：张华，我注意到这周的技术模块还差20%没完成...
AI（扮演张华）：（防御）又来了，你觉得我不知道吗？
```

### 模式 3：结构化练习（STRUCTURED_FOUR_STEP）

| 维度 | 定义 |
|------|------|
| 定位 | 四要素引导练习（有结构但不强制顺序） |
| AI 角色 | NVC 教练，主动引导用户覆盖四要素 |
| 典型场景 | 用户想系统性学习 NVC，但不想被框死在步骤里 |
| 流程 | AI 主动引导四要素 → 用户回答 → AI 点评 → 自然过渡下一个要素 |
| 与自由对话的区别 | 自由对话跟随用户话题走；结构化练习 AI 主动引导四要素 |
| 禁止行为 | 不强制顺序、不输出 JSON、不脱离教练角色 |

**对话示例：**
```
AI：我们来一起梳理一下。你能描述一个最近让你不舒服的情境吗？
用户：同事抢了我的功劳
AI：你说了"抢"——这是一个判断。当时发生了什么具体的事？（引导观察）
用户：会议上老板表扬了项目，同事说是他做的
AI：这就是观察。当这件事发生时，你心里的感受是什么？（引导感受）
用户：愤怒，还有委屈
AI：愤怒的背后，你真正需要的是什么？（引导需求）
用户：我需要被认可
AI：嗯，被认可对你很重要。你希望同事怎么做？（引导请求）
```

**与自由对话的关键区别：**
- 自由对话：用户主导话题，AI 被动引导
- 结构化练习：AI 主动确保四要素都被覆盖，有明确的教学意图

---

## 二、架构设计

### 2.1 编排层重构：模式路由策略

**问题根因：** 当前 `decideNextAgent()` 的 `CREATED` 阶段检查优先于模式检查，导致所有模式都走 SCENARIO_GENERATOR。

**方案：** 引入 `ModeRouter` 策略模式，每种模式有独立的路由逻辑。

```
┌─────────────────────────────────────────────┐
│           NvcAgentOrchestrator              │
│                                             │
│  decideNextAgent(context)                   │
│    │                                        │
│    ▼                                        │
│  ModeRouter (策略接口)                       │
│    ├── FreeDialogRouter    → 自由对话路由    │
│    ├── ScenarioRouter      → 场景练习路由    │
│    └── StructuredRouter    → 结构化练习路由  │
│                                             │
│  每个 Router 返回:                            │
│    - agentScene: NvcAgentScene              │
│    - promptVariables: Map<String, String>   │
│    - action: String (可选)                   │
└─────────────────────────────────────────────┘
```

**FreeDialogRouter 路由逻辑：**
- 任何阶段 → `DIALOGUE_GUIDE`
- 通过 `promptVariables` 注入 `mode=free_dialog`
- 教练 Prompt 根据 mode 变量调整行为

**ScenarioRouter 路由逻辑：**
- CREATED 阶段 → `SCENARIO_GENERATOR`（引入场景）
- 后续 → `DIFFICULT_PARTNER`（扮演对方角色）
- 通过 `promptVariables` 注入场景详情

**StructuredRouter 路由逻辑：**
- 任何阶段 → `DIALOGUE_GUIDE`
- 通过 `promptVariables` 注入 `mode=structured` 和四要素覆盖状态
- 教练 Prompt 根据 mode 变量调整为"主动引导四要素"风格
- 评估系统按四维度打分，追踪哪些要素已覆盖，但不强制顺序

**Prompt 变量注入机制：**
```java
public class RouterDecision {
  NvcAgentScene agentScene;
  Map<String, String> promptVariables;  // 注入到 Prompt 模板
  String action;  // 可选的动作标记
}

// 使用示例
promptVariables.put("mode", "free_dialog");
promptVariables.put("covered_elements", "observation,feeling");
promptVariables.put("scenario_context", "...");
```

**为什么用 promptVariables 而不是多个 Agent：**
- 减少 Agent 数量，降低维护成本
- 同一个 DIALOGUE_GUIDE Agent 可以通过变量切换行为
- 未来加新模式只需新增 Router + 变量，不需新建 Agent
- Prompt 修改集中在一处，不会遗漏

### 2.2 Prompt 管理：统一从 DB 读取

**当前问题：** .st 文件和 SQL 种子数据中的 Prompt 内容不一致，代码可能读取不同来源。

**方案：** 统一从数据库读取 Agent 配置，.st 文件仅作为初始化种子。

```
Agent Prompt 来源优先级：
1. DB（NvcAgentConfigEntity）← 运行时实际使用
2. .st 文件 ← 仅用于初始化/重置
```

**每个 Prompt 必须包含的结构：**
```
## 角色定义
你是...（明确的角色描述）

## 模式边界
- ✅ 你应该：...
- ❌ 你不应该：...（明确的禁止行为）

## 输出格式
- 只输出自然语言对话
- 绝对不要输出 JSON、代码块、结构化数据

## 对话风格
- 每次回复 2-4 句话
- ...（风格指导）
```

### 2.3 输出清理层

**方案：** 双层防护

**第一层：Prompt 强化**
- 每个对话类 Agent 的 Prompt 都加入"禁止输出 JSON/代码"的指令

**第二层：后端清理**
- 在 `NvcPracticeDialogueService` 保存 AI 回复前，执行清理：
  - 去除 ```json ... ``` 代码块
  - 去除 { ... } JSON 对象
  - 去除残留的 Markdown 格式标记
  - 保留纯自然语言内容

```java
public class AiResponseCleaner {
  public static String clean(String raw) {
    // 1. 去除 ```json ... ``` 代码块
    // 2. 去除独立的 JSON 对象 { ... }
    // 3. 去除多余空行
    // 4. 返回清理后的文本
  }
}
```

### 2.4 前端模式差异化

**NvcChatPanel 增加 `practiceMode` prop：**

| 模式 | Placeholder | 额外 UI |
|------|-------------|---------|
| FREE_DIALOG | "描述你遇到的沟通问题，我来帮你梳理..." | 无 |
| SCENARIO | "用 NVC 的方式回应对方..." | 顶部场景卡片（已有） |
| STRUCTURED_FOUR_STEP | "试着用 NVC 的方式表达..." | 右侧四要素覆盖进度（改造自步骤指示器） |

**结构化练习 UI 改造：**
- 原来的"步骤指示器"改为"四要素覆盖进度"
- 显示四个要素的覆盖状态（未覆盖/已覆盖/已练习）
- 不再显示"当前步骤"，因为不强制顺序

**AI 回复前端兜底过滤：**
- 去除 ```...``` 代码块
- 去除残留的 JSON 片段

---

## 三、Prompt 重写规范

### 3.1 DIALOGUE_GUIDE（NVC 教练 — 自由对话 + 结构化练习共用）

```
## 角色定义
你是一位温和、有耐心的 NVC（非暴力沟通）教练。你的任务是通过对话引导用户，
帮助他们用 NVC 的四要素（观察、感受、需求、请求）来理解和表达自己的沟通问题。

## 当前模式
{mode}

### 当 mode=free_dialog 时
- 跟随用户的话题，用户说什么就引导什么
- 不主动引入四要素框架，除非用户表达中自然出现
- 重点：共情、倾听、自然引导

### 当 mode=structured 时
- 主动引导用户覆盖四要素：观察、感受、需求、请求
- 根据用户的回答，灵活决定先引导哪个要素
- 已覆盖的要素：{covered_elements}
- 未覆盖的要素优先引导，但不强制顺序
- 重点：教学意图明确，确保四要素都被练习到

## 模式边界
- ✅ 你应该：共情用户的感受，引导用户区分观察和评论，帮助用户识别深层需求
- ✅ 你应该：围绕用户描述的真实情境展开引导
- ❌ 你不应该：生成练习场景、模拟角色、输出 JSON/代码
- ❌ 你不应该：切换话题或引入用户没有提到的情境
- ❌ 你不应该：在 free_dialog 模式下主动教学，除非用户明确请求

## 输出格式
- 只输出自然语言对话
- 绝对不要输出 JSON、代码块、结构化数据
- 每次回复 2-4 句话，简洁有力

## 对话策略
1. 先倾听和共情
2. 引导用户描述具体的观察（发生了什么？）
3. 引导用户识别感受（你心里是什么感觉？）
4. 引导用户追溯需求（这种感觉背后，你需要什么？）
5. 引导用户提出请求（你希望对方怎么做？）
```

### 3.2 SCENARIO_GENERATOR（场景引入）

```
## 角色定义
你是 NVC 练习的场景引入者。当系统提供了[练习场景]时，你需要用自然语言
介绍场景背景，然后开始扮演对方角色。

## 模式边界
- ✅ 你应该：用自然语言介绍场景，然后进入角色扮演
- ✅ 你应该：保持角色，用角色的性格和说话风格回应
- ❌ 你不应该：输出 JSON、代码块、结构化数据
- ❌ 你不应该：脱离角色、给出 NVC 教学指导

## 输出格式
- 第一条消息：用 2-3 句话自然地介绍场景，然后以角色的第一句话结尾
- 后续消息：以角色身份回应，每次 1-3 句话
```

### 3.3 步骤教练（通用模板）

```
## 角色定义
你是 NVC「{step_name}」步骤教练。你的职责是引导用户练习{step_name}能力。

## 模式边界
- ✅ 你应该：围绕用户的表达，引导他们练习{step_name}
- ✅ 你应该：根据用户的回答灵活调整，不强制顺序
- ❌ 你不应该：输出 JSON、代码块、结构化数据
- ❌ 你不应该：切换到其他步骤，除非用户自然过渡

## 教学要点
{step_specific_teaching_points}

## 对话风格
- 先肯定用户做得好的地方
- 给出具体的改写示例
- 每次回复 2-4 句话
```

---

## 四、改动清单

### 后端 - 编排层
| 文件 | 改动 |
|------|------|
| `NvcAgentOrchestrator.java` | 重构 `decideNextAgent()`，引入 ModeRouter 策略 |
| 新增 `router/ModeRouter.java` | 路由策略接口 |
| 新增 `router/FreeDialogRouter.java` | 自由对话路由：始终返回 DIALOGUE_GUIDE + mode=free_dialog |
| 新增 `router/ScenarioRouter.java` | 场景练习路由：CREATED→SCENARIO_GENERATOR，后续→DIFFICULT_PARTNER |
| 新增 `router/StructuredRouter.java` | 结构化练习路由：始终返回 DIALOGUE_GUIDE + mode=structured |
| 新增 `router/RouterDecision.java` | 路由决策结果：agentScene + promptVariables + action |
| 新增 `util/AiResponseCleaner.java` | AI 回复清理工具类 |

### 后端 - Prompt
| 文件 | 改动 |
|------|------|
| `data-nvc-agent-config.sql` | 重写 DIALOGUE_GUIDE / SCENARIO_GENERATOR / DIFFICULT_PARTNER 的 Prompt |
| `NvcAgentChatService.java` | 支持 promptVariables 注入到 Prompt 模板 |

### 前端
| 文件 | 改动 |
|------|------|
| `NvcChatPanel.tsx` | 增加 `practiceMode` prop，差异化 placeholder，AI 回复兜底过滤 |
| `NvcPracticePage.tsx` | 传递 `practiceMode` 给聊天面板，场景模式显示场景卡片 |
| `NvcStepIndicator.tsx` | 改造为"四要素覆盖进度"显示 |
| `NvcScenarioPage.tsx`（或场景库页面） | 点击场景 → 创建 session → 跳转对话 |

---

## 五、场景模式入口流程

### 5.1 从场景库进入

```
场景库页面 → 点击场景卡片 → 创建 SCENARIO 模式 session（带 scenarioId）
→ 跳转到 /practice/:sessionId → 页面顶部显示场景卡片
→ 第一条消息由 SCENARIO_GENERATOR 处理，用自然语言介绍场景
→ 后续由 DIFFICULT_PARTNER 扮演对方角色
```

### 5.2 AI 生成新场景

```
场景库页面 → 点击"AI 生成场景" → 调用场景生成 API → 场景写入场景库
→ 自动创建 SCENARIO 模式 session → 跳转到对话页面
```

### 5.3 场景卡片内容

```
┌─────────────────────────────────────┐
│ 📋 项目延期引发的团队冲突            │
│                                     │
│ 背景：项目经理李明在团队会议上公开    │
│ 质问张华为什么进度滞后...             │
│                                     │
│ 你扮演：李明（项目经理）              │
│ 对方：张华（团队成员，防御状态）      │
│                                     │
│ 练习重点：感受、需求                  │
└─────────────────────────────────────┘
```

---

## 六、扩展性设计

### 新增模式
1. 创建新的 `XxxRouter implements ModeRouter`
2. 在 `NvcAgentOrchestrator` 注册
3. 编写对应的 Agent Prompt
4. 前端增加模式判断

### 修改 Prompt
- 直接修改 DB 中的 Agent 配置，无需改代码
- 或通过 Agent 配置 API 热更新

### 接入 RAG
- 在 Router 中注入 RAG 检索逻辑
- 检索结果作为 `promptVariables` 传入 Agent
- 不影响模式边界

### 调整编排逻辑
- 只需修改对应模式的 Router 实现
- 其他模式不受影响
