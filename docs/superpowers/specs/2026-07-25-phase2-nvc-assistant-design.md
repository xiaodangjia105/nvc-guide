# Phase 2 主 Agent 全能对话入口设计文档

> 创建时间：2026-07-25
> 分支：feat/nvc-assistant
> 状态：已完成

---

## 一、背景与目标

### 现状

Phase 1 完成了 RAG 知识库集成和工具框架标准化，但用户需要通过练习会话才能与 Agent 交互。缺少一个统一的对话入口，让用户可以直接与 Agent 对话，调用所有已注册的工具。

### 目标

1. 创建主 Agent 对话模块（nvcassistant），作为用户与 NVC Agent 交互的统一入口
2. 实现流式 SSE 逐步推送，提升用户体验
3. 开放所有 10 个工具供 Agent 调用
4. 支持多轮对话历史管理（滑动窗口 20 轮）
5. 提供对话标题自动生成、删除对话、重新生成回复等功能

---

## 二、架构设计

### 模块结构

```
nvcassistant/
├── controller/
│   └── NvcAssistantController.java      # 6 个 REST 端点
├── service/
│   ├── NvcAssistantService.java          # 核心对话服务
│   ├── NvcAssistantMessageService.java   # 对话/消息 CRUD
│   └── agent/
│       ├── AgentLoop.java                # Agent 主循环（多轮工具调用）
│       ├── AgentEvent.java               # SSE 事件类型
│       ├── ContextManager.java           # 上下文管理 + 压缩
│       ├── IntentRouter.java             # 意图预路由
│       ├── PromptBuilder.java            # 系统提示词构建
│       ├── ToolExecutor.java             # 工具执行器
│       └── NvcToolHook.java              # 工具 Hook 接口
├── model/
│   ├── NvcAssistantConversationEntity.java
│   ├── NvcAssistantMessageEntity.java
│   └── NvcAssistantMessageRole.java
├── repository/
│   ├── NvcAssistantConversationRepository.java
│   └── NvcAssistantMessageRepository.java
└── dto/
    ├── AssistantRequest.java
    ├── AssistantResponse.java
    ├── ConversationResponse.java
    ├── MessageResponse.java
    └── ToolCallRecord.java
```

### 核心流程

```
用户消息 → NvcAssistantController
  → NvcAssistantService.chatStreamRaw()
    → getOrCreateConversation()          # 获取/创建对话
    → saveUserMessage()                  # 保存用户消息
    → ContextManager.buildContext()      # 构建上下文（历史 + 摘要）
    → PromptBuilder.buildSystemPrompt()  # 构建系统提示词
    → AgentLoop.executeStream()          # 执行 Agent 循环
      → IntentRouter.match()             # 意图预路由
      → LLM 调用（流式）
      → ToolExecutor.execute()           # 工具执行（如有 toolCalls）
      → 循环直到无 toolCalls
    → 返回 Flux<AgentEvent>             # SSE 事件流
```

---

## 三、关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| Agent 循环 | 自研 AgentLoop | Spring AI 自动工具处理不支持 Hook 链和 Trace 埋点 |
| 流式协议 | SSE (Server-Sent Events) | 浏览器原生支持，无需 WebSocket 额外复杂度 |
| 上下文管理 | 滑动窗口 20 轮 + LLM 摘要压缩 | 平衡对话连贯性和 Token 消耗 |
| 意图路由 | 关键词模式匹配 | mimo-v2.5 工具调用不准确，高置信度意图直接路由 |
| 工具执行 | 并行执行 + 30s 超时 | 提升多工具调用效率 |
| 对话存储 | PostgreSQL | 结构化数据，支持分页查询 |

### AgentLoop 设计

```java
// 核心循环逻辑
while (turns < MAX_TOOL_CALL_TURNS) {
    // 1. 调用 LLM（internalToolExecutionEnabled=false）
    ChatResponse response = callLlm(messages, tools);

    // 2. 如果有 toolCalls → 执行工具 → 结果加入上下文
    if (response.hasToolCalls()) {
        List<ToolCallResult> results = toolExecutor.execute(toolCalls, userId, sessionId);
        messages.add(toolResponseMessage);
        turns++;
        continue;
    }

    // 3. 如果有 content → 流式推送 → 结束
    sink.next(AgentEvent.CONTENT(response.getContent()));
    break;
}
```

### 上下文压缩策略

- 阈值：20 轮消息
- 压缩方式：LLM 摘要（保留最近 10 轮）
- 降级：截断早期 5 轮消息
- 触发：每次 buildContext() 时检查

---

## 四、API 设计

### 端点列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/nvc/assistant/chat` | 非流式对话 |
| POST | `/api/nvc/assistant/chat/stream` | 流式 SSE 对话 |
| GET | `/api/nvc/assistant/conversations` | 获取对话列表 |
| GET | `/api/nvc/assistant/conversations/{id}/messages` | 获取对话消息 |
| DELETE | `/api/nvc/assistant/conversations/{id}` | 删除对话 |
| POST | `/api/nvc/assistant/conversations/{id}/regenerate` | 重新生成回复 |

### SSE 事件类型

```java
public enum EventType {
    CONTENT,        // 文本内容片段
    TOOLCALL_START, // 工具调用开始
    TOOLCALL_END,   // 工具调用结束
    THINKING,       // 思考中
    DONE,           // 完成
    ERROR           // 错误
}
```

---

## 五、前端实现

### 文件清单

```
frontend/src/
├── pages/
│   └── NvcAssistantPage.tsx              # 主页面（侧边栏 + 对话区）
├── components/nvc/
│   ├── NvcAssistantSidebar.tsx           # 对话历史侧边栏
│   ├── NvcAssistantChat.tsx              # 对话组件（Markdown + 流式）
│   ├── NvcToolCallCard.tsx              # 折叠工具调用卡片
│   └── NvcPracticePreviewCard.tsx       # 练习预览卡片
└── api/
    └── nvc-assistant.ts                  # API 层 + SSE 流式调用
```

### 核心特性

- 侧边栏：对话列表、新建对话、删除对话
- 对话区：Markdown 渲染、流式打字效果、工具调用折叠卡片
- 工具卡片：显示工具名称、参数、结果，可折叠/展开
- 练习预览：工具返回的练习数据以卡片形式展示

---

## 六、文件清单

### 后端新建（13 个）

```
app/src/main/java/nvc/guide/modules/nvcassistant/
├── controller/NvcAssistantController.java
├── service/NvcAssistantService.java
├── service/NvcAssistantMessageService.java
├── service/agent/AgentLoop.java
├── service/agent/AgentEvent.java
├── service/agent/ContextManager.java
├── service/agent/IntentRouter.java
├── service/agent/PromptBuilder.java
├── service/agent/ToolExecutor.java
├── service/agent/NvcToolHook.java
├── model/NvcAssistantConversationEntity.java
├── model/NvcAssistantMessageEntity.java
├── model/NvcAssistantMessageRole.java
├── repository/NvcAssistantConversationRepository.java
├── repository/NvcAssistantMessageRepository.java
└── dto/{AssistantRequest,AssistantResponse,ConversationResponse,MessageResponse,ToolCallRecord}.java
```

### 前端新建（6 个）

```
frontend/src/
├── pages/NvcAssistantPage.tsx
├── components/nvc/NvcAssistantSidebar.tsx
├── components/nvc/NvcAssistantChat.tsx
├── components/nvc/NvcToolCallCard.tsx
├── components/nvc/NvcPracticePreviewCard.tsx
└── api/nvc-assistant.ts
```

---

## 七、测试要求

- AgentLoop 单元测试：模拟 LLM 调用，验证多轮工具调用循环
- ContextManager 单元测试：验证压缩阈值和摘要生成
- IntentRouter 单元测试：验证模式匹配准确性
- 集成测试：端到端对话流程（用户消息 → Agent 回复）

---

## 八、验收标准

```
□ 后端
  □ 6 个 REST 端点正常工作
  □ 流式 SSE 逐步推送（thinking/content/done）
  □ 10 个工具全开放可调用
  □ 滑动窗口 20 轮历史
  □ 对话标题自动生成
  □ 删除对话 + 重新生成回复

□ 前端
  □ 侧边栏对话列表
  □ 流式打字效果
  □ 工具调用折叠卡片
  □ Markdown 渲染

□ 端到端
  □ 创建对话 → 发送消息 → Agent 回复
  □ 多轮对话历史保持
  □ 工具调用正常执行并展示结果
```
