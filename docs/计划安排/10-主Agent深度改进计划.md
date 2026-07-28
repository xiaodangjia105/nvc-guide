# 主 Agent 深度改进计划

> 编写日期：2026-07-28
> 参考项目：`D:\code\pi`（Pi Agent Harness）
> 前置条件：NvcAssistant 基础版已完成（feat/nvc-assistant 分支）

---

## 一、现状分析

### 1.1 当前 NvcAssistant 实现

| 文件 | 职责 | 现状 |
|------|------|------|
| `NvcAssistantService.java` | 核心对话服务 | 单次 LLM 调用，Spring AI 自动处理工具 |
| `NvcAssistantController.java` | REST 端点 | 6 个 API（chat/stream/conversations/messages/delete/regenerate） |
| `NvcAssistantMessageService.java` | 消息 CRUD | 对话+消息管理，20 轮滑窗 |
| `nvc-assistant-system.st` | 系统 Prompt | 简单的工具列表 + 基本指令 |

### 1.2 当前不足

| 问题 | 影响 |
|------|------|
| 无 Agent Loop | 无法控制工具调用流程，无法插入钩子 |
| 工具调用对用户不可见 | 用户只看到"正在思考..."，体验差 |
| 无上下文压缩 | 20 轮后直接截断，早期对话丢失 |
| 无 CoT 引导 | Agent 意图识别和工具选择不够智能 |
| 工具调用记录未持久化 | 无法回溯和分析 |

---

## 二、改进方案对比

### 2.1 原方案 vs 新方案

| 维度 | 原方案（04-Phase2） | 新方案（借鉴 Pi） |
|------|---------------------|-------------------|
| **Agent Loop** | Spring AI 自动处理 | 自己实现双层循环 |
| **工具调用** | 黑盒，用户不可见 | 流式事件展示每步调用 |
| **上下文** | 固定 20 轮滑窗 | LLM 摘要压缩 |
| **CoT** | 无 | Prompt 引导 CoT |
| **工具钩子** | 无 | beforeToolCall/afterToolCall |
| **可观测性** | 低 | 高（事件流 + 持久化） |

### 2.2 Pi 项目参考路径

| 机制 | Pi 文件路径 | 核心代码行数 |
|------|------------|-------------|
| **Agent Loop** | `D:\code\pi\packages\agent\src\agent-loop.ts` | ~800 行 |
| **Agent 类** | `D:\code\pi\packages\agent\src\agent.ts` | ~580 行 |
| **工具定义** | `D:\code\pi\packages\coding-agent\src\core\tools\index.ts` | 7 个内置工具 |
| **工具执行** | `D:\code\pi\packages\coding-agent\src\core\agent-session.ts` | ~3000 行 |
| **上下文压缩** | `D:\code\pi\packages\coding-agent\src\core\compaction\compaction.ts` | ~800 行 |
| **流式事件** | `D:\code\pi\packages\agent\src\types.ts` | AgentEvent 定义 |
| **Thinking Level** | `D:\code\pi\packages\agent\src\types.ts` | 7 级定义 |
| **系统 Prompt** | `D:\code\pi\packages\coding-agent\src\core\system-prompt.ts` | 动态构建 |

---

## 三、改进架构设计

### 3.1 新架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                      NvcAssistantService (重构)                  │
│                                                                  │
│  ┌──────────────┐    ┌───────────────┐    ┌──────────────────┐  │
│  │ AgentLoop    │───▶│ ToolExecutor  │───▶│ ContextManager   │  │
│  │ (新建)       │    │ (新建)        │    │ (新建)           │  │
│  │              │    │               │    │                  │  │
│  │ while循环    │    │ 工具调用      │    │ 上下文压缩       │  │
│  │ 最大轮数控制 │    │ 钩子机制      │    │ 摘要生成         │  │
│  │ 超时控制     │    │ 并行/串行     │    │ 消息管理         │  │
│  └──────────────┘    └───────────────┘    └──────────────────┘  │
│         │                    │                       │           │
│         ▼                    ▼                       ▼           │
│  ┌──────────────┐    ┌───────────────┐    ┌──────────────────┐  │
│  │ SSE Events   │    │ ToolHooks     │    │ PromptBuilder    │  │
│  │ (增强)       │    │ (新建)        │    │ (增强)           │  │
│  │              │    │               │    │                  │  │
│  │ thinking     │    │ beforeCall    │    │ 系统Prompt       │  │
│  │ toolcall_*   │    │ afterCall     │    │ CoT引导          │  │
│  │ content      │    │ 日志/缓存     │    │ 上下文摘要       │  │
│  │ done/error   │    │               │    │ 用户档案         │  │
│  └──────────────┘    └───────────────┘    └──────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 核心组件职责

| 组件 | 职责 | Pi 参考 |
|------|------|---------|
| **AgentLoop** | 控制多轮工具调用循环，管理最大轮数和超时 | `agent-loop.ts` |
| **ToolExecutor** | 执行工具调用，支持钩子，并行/串行控制 | `agent-session.ts` |
| **ContextManager** | 上下文压缩，消息管理，摘要生成 | `compaction/compaction.ts` |
| **SSE Events** | 流式事件发射，格式化 | `types.ts` (AgentEvent) |
| **ToolHooks** | 工具调用前后钩子，日志，缓存 | `tools/index.ts` |
| **PromptBuilder** | 动态构建系统 Prompt，注入 CoT/摘要/档案 | `system-prompt.ts` |

---

## 四、详细实现计划

### 4.1 Phase 1：Agent Loop + 流式事件（2 天）

#### 4.1.1 AgentLoop 核心类

**新建文件**：`nvcassistant/service/agent/AgentLoop.java`

```java
/**
 * Agent 主循环 — 控制多轮工具调用
 * 
 * 参考：D:\code\pi\packages\agent\src\agent-loop.ts
 * 
 * 核心逻辑：
 * 1. 构建上下文（系统Prompt + 历史 + 用户消息）
 * 2. 调用 LLM
 * 3. 如果 LLM 返回 toolCall → 执行工具 → 结果加入上下文 → 回到步骤 2
 * 4. 如果 LLM 返回 content → 结束循环
 * 5. 最大轮数限制 + 超时控制
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentLoop {

    private final LlmProviderRegistry llmProviderRegistry;
    private final ToolExecutor toolExecutor;
    private final ContextManager contextManager;
    private final PromptBuilder promptBuilder;

    /** 最大工具调用轮数 */
    private static final int MAX_TOOL_CALL_TURNS = 10;
    /** 单轮超时（毫秒） */
    private static final long TURN_TIMEOUT_MS = 30_000;
    /** 总超时（毫秒） */
    private static final long TOTAL_TIMEOUT_MS = 120_000;

    /**
     * 执行 Agent 循环（流式）
     * 
     * @return Flux<SSE事件>
     */
    public Flux<AgentEvent> executeStream(Long userId, Long conversationId, String userMessage) {
        return Flux.create(sink -> {
            try {
                // 1. 构建初始上下文
                List<Message> context = contextManager.buildContext(conversationId, userId);
                context.add(new UserMessage(userMessage));

                // 2. 发送 thinking 事件
                sink.next(AgentEvent.thinking("正在思考..."));

                // 3. 循环调用 LLM
                int turn = 0;
                long startTime = System.currentTimeMillis();

                while (turn < MAX_TOOL_CALL_TURNS) {
                    // 检查总超时
                    if (System.currentTimeMillis() - startTime > TOTAL_TIMEOUT_MS) {
                        sink.next(AgentEvent.error("对话超时，请重试"));
                        break;
                    }

                    // 调用 LLM
                    LlmResponse response = callLlm(context, userId);

                    if (response.hasToolCalls()) {
                        // 发送工具调用开始事件
                        for (ToolCall tc : response.toolCalls()) {
                            sink.next(AgentEvent.toolcallStart(tc.name(), tc.arguments()));
                        }

                        // 执行工具
                        List<ToolResult> results = toolExecutor.execute(
                            response.toolCalls(), userId, conversationId);

                        // 发送工具调用结束事件
                        for (ToolResult result : results) {
                            sink.next(AgentEvent.toolcallEnd(
                                result.toolName(), result.success(), result.data()));
                        }

                        // 将工具结果加入上下文
                        context.add(response.toAssistantMessage());
                        for (ToolResult result : results) {
                            context.add(result.toToolResultMessage());
                        }

                        turn++;
                    } else {
                        // 无工具调用，发送内容并结束
                        sink.next(AgentEvent.content(response.content()));
                        sink.next(AgentEvent.done(conversationId));
                        break;
                    }
                }

                // 检查是否达到最大轮数
                if (turn >= MAX_TOOL_CALL_TURNS) {
                    sink.next(AgentEvent.error("工具调用次数过多，请简化请求"));
                }

                sink.complete();
            } catch (Exception e) {
                log.error("Agent loop failed: userId={}, conversationId={}", userId, conversationId, e);
                sink.next(AgentEvent.error("对话出错: " + e.getMessage()));
                sink.complete();
            }
        });
    }

    private LlmResponse callLlm(List<Message> context, Long userId) {
        ChatClient client = llmProviderRegistry.getDefaultChatClient();
        // 调用 LLM，解析响应
        // 返回 LlmResponse（包含 content 或 toolCalls）
        // ...
    }
}
```

#### 4.1.2 AgentEvent 事件类型

**新建文件**：`nvcassistant/service/agent/AgentEvent.java`

```java
/**
 * Agent 事件 — 用于 SSE 流式传输
 * 
 * 参考：D:\code\pi\packages\agent\src\types.ts (AgentEvent)
 */
public record AgentEvent(
    AgentEventType type,
    String data,
    Map<String, Object> metadata
) {
    public enum AgentEventType {
        THINKING,        // 思考中
        TOOLCALL_START,  // 工具调用开始
        TOOLCALL_DELTA,  // 工具调用中间结果（可选）
        TOOLCALL_END,    // 工具调用结束
        CONTENT,         // 回复内容
        DONE,            // 完成
        ERROR            // 错误
    }

    // 工厂方法
    public static AgentEvent thinking(String message) {
        return new AgentEvent(AgentEventType.THINKING, message, null);
    }

    public static AgentEvent toolcallStart(String toolName, String arguments) {
        return new AgentEvent(AgentEventType.TOOLCALL_START, toolName,
            Map.of("arguments", arguments));
    }

    public static AgentEvent toolcallEnd(String toolName, boolean success, String result) {
        return new AgentEvent(AgentEventType.TOOLCALL_END, toolName,
            Map.of("success", success, "result", result));
    }

    public static AgentEvent content(String text) {
        return new AgentEvent(AgentEventType.CONTENT, text, null);
    }

    public static AgentEvent done(Long conversationId) {
        return new AgentEvent(AgentEventType.DONE, conversationId.toString(), null);
    }

    public static AgentEvent error(String message) {
        return new AgentEvent(AgentEventType.ERROR, message, null);
    }
}
```

#### 4.1.3 ToolExecutor 工具执行器

**新建文件**：`nvcassistant/service/agent/ToolExecutor.java`

```java
/**
 * 工具执行器 — 支持钩子、并行/串行、超时
 * 
 * 参考：D:\code\pi\packages\coding-agent\src\core\agent-session.ts
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ToolExecutor {

    private final NvcToolRegistry toolRegistry;
    private final List<NvcToolHook> hooks;  // Spring 自动注入所有钩子

    /** 单个工具超时（毫秒） */
    private static final long TOOL_TIMEOUT_MS = 10_000;

    /**
     * 执行工具调用列表
     */
    public List<ToolResult> execute(List<ToolCall> toolCalls, Long userId, Long conversationId) {
        ToolContext context = ToolContext.builder()
            .userId(userId)
            .sessionId(conversationId)
            .build();

        // 并行执行（可配置）
        return toolCalls.parallelStream()
            .map(tc -> executeSingle(tc, context))
            .toList();
    }

    private ToolResult executeSingle(ToolCall toolCall, ToolContext context) {
        String toolName = toolCall.name();
        long startTime = System.currentTimeMillis();

        try {
            // 1. beforeToolCall 钩子
            for (NvcToolHook hook : hooks) {
                ToolCallDecision decision = hook.beforeToolCall(toolName, toolCall.arguments(), context);
                if (decision == ToolCallDecision.SKIP) {
                    return ToolResult.skipped(toolName, "Hook skipped");
                }
            }

            // 2. 执行工具
            NvcTool tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                return ToolResult.failure(toolName, "工具不存在: " + toolName);
            }

            JsonNode input = new ObjectMapper().readTree(toolCall.arguments());
            NvcToolResult result = tool.execute(input, context);

            // 3. afterToolCall 钩子
            String processedResult = result.success() ? result.data() : result.errorMessage();
            for (NvcToolHook hook : hooks) {
                processedResult = hook.afterToolCall(toolName, processedResult, context);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Tool executed: tool={}, success={}, duration={}ms", toolName, result.success(), duration);

            return ToolResult.success(toolName, processedResult, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Tool execution failed: tool={}, duration={}ms", toolName, duration, e);
            return ToolResult.failure(toolName, e.getMessage());
        }
    }
}
```

#### 4.1.4 SSE 事件格式

**前端接收的 SSE 事件格式**：

```
event: thinking
data: 正在思考...

event: toolcall_start
data: {"tool":"rag_search","arguments":"{\"query\":\"NVC观察技巧\"}"}

event: toolcall_end
data: {"tool":"rag_search","success":true,"result":"观察是指客观描述事实..."}

event: toolcall_start
data: {"tool":"profile_query","arguments":"{}"}

event: toolcall_end
data: {"tool":"profile_query","success":true,"result":"{\"nvcLevel\":\"BEGINNER\"...}"}

event: content
data: 根据你的档案和NVC知识...

event: done
data: {"conversationId":123}
```

---

### 4.2 Phase 2：上下文压缩（1 天）

#### 4.2.1 ContextManager 上下文管理器

**新建文件**：`nvcassistant/service/agent/ContextManager.java`

```java
/**
 * 上下文管理器 — 消息管理 + 压缩
 * 
 * 参考：D:\code\pi\packages\coding-agent\src\core\compaction\compaction.ts
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContextManager {

    private final NvcAssistantMessageService messageService;
    private final LlmProviderRegistry llmProviderRegistry;

    /** 消息轮数阈值，超过此值触发压缩 */
    private static final int COMPRESSION_THRESHOLD = 20;
    /** 压缩后保留的最近消息数 */
    private static final int KEEP_RECENT_MESSAGES = 10;

    /**
     * 构建上下文消息列表
     * 如果消息数超过阈值，自动压缩早期消息
     */
    public List<Message> buildContext(Long conversationId, Long userId) {
        List<Message> messages = new ArrayList<>();

        // 1. 获取所有消息
        List<NvcAssistantMessageEntity> allMessages = messageService.getMessages(conversationId);

        if (allMessages.size() <= COMPRESSION_THRESHOLD) {
            // 未超过阈值，直接返回
            for (NvcAssistantMessageEntity msg : allMessages) {
                messages.add(toMessage(msg));
            }
            return messages;
        }

        // 2. 超过阈值，需要压缩
        // 分离早期消息和最近消息
        List<NvcAssistantMessageEntity> earlyMessages = allMessages.subList(0, 
            allMessages.size() - KEEP_RECENT_MESSAGES);
        List<NvcAssistantMessageEntity> recentMessages = allMessages.subList(
            allMessages.size() - KEEP_RECENT_MESSAGES, allMessages.size());

        // 3. 生成早期消息的摘要
        String summary = generateSummary(earlyMessages);

        // 4. 构建上下文：摘要 + 最近消息
        messages.add(new SystemMessage("以下是之前对话的摘要：\n" + summary));
        for (NvcAssistantMessageEntity msg : recentMessages) {
            messages.add(toMessage(msg));
        }

        log.info("Context compressed: total={}, early={}, recent={}, summaryLength={}",
            allMessages.size(), earlyMessages.size(), recentMessages.size(), summary.length());

        return messages;
    }

    /**
     * 生成对话摘要
     */
    private String generateSummary(List<NvcAssistantMessageEntity> messages) {
        // 构建摘要请求
        StringBuilder conversationText = new StringBuilder();
        for (NvcAssistantMessageEntity msg : messages) {
            conversationText.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        String prompt = """
            请将以下对话压缩为简洁的摘要，保留关键信息：
            1. 用户的主要问题和需求
            2. 调用了哪些工具，获得了什么关键结果
            3. 给出了什么建议或结论
            
            对话内容：
            %s
            
            请用中文输出摘要，不超过 500 字。
            """.formatted(conversationText);

        try {
            ChatClient client = llmProviderRegistry.getDefaultChatClient();
            return client.prompt()
                .user(prompt)
                .call()
                .content();
        } catch (Exception e) {
            log.error("Failed to generate summary", e);
            return "对话摘要生成失败";
        }
    }

    private Message toMessage(NvcAssistantMessageEntity entity) {
        return switch (entity.getRole()) {
            case USER -> new UserMessage(entity.getContent());
            case ASSISTANT -> new AssistantMessage(entity.getContent());
            case SYSTEM -> new SystemMessage(entity.getContent());
        };
    }
}
```

---

### 4.3 Phase 3：PromptBuilder + CoT 引导（0.5 天）

#### 4.3.1 PromptBuilder 动态 Prompt 构建

**新建文件**：`nvcassistant/service/agent/PromptBuilder.java`

```java
/**
 * Prompt 构建器 — 动态组装系统 Prompt
 * 
 * 参考：D:\code\pi\packages\coding-agent\src\core\system-prompt.ts
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PromptBuilder {

    private final NvcProfileService profileService;

    @Value("classpath:prompts/nvc-assistant-system-v2.st")
    private Resource systemPromptResource;

    /**
     * 构建系统 Prompt
     * 
     * @param userId 用户ID
     * @param contextSummary 上下文摘要（压缩后才有）
     * @return 完整的系统 Prompt
     */
    public String buildSystemPrompt(Long userId, String contextSummary) {
        String template = loadTemplate();

        // 1. 注入用户档案
        String profileSummary = profileService.getUserProfilePrompt(userId);
        template = template.replace("{userProfileSummary}", 
            profileSummary != null ? profileSummary : "暂无档案");

        // 2. 注入上下文摘要（如果有）
        template = template.replace("{contextSummary}", 
            contextSummary != null ? contextSummary : "");

        // 3. 注入当前时间
        template = template.replace("{currentTime}", 
            java.time.LocalDateTime.now().toString());

        return template;
    }

    private String loadTemplate() {
        try {
            return systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load system prompt", e);
            return "你是 NVC 非暴力沟通练习平台的 AI 助手。";
        }
    }
}
```

#### 4.3.2 新版系统 Prompt（带 CoT 引导）

**新建文件**：`resources/prompts/nvc-assistant-system-v2.st`

```
你是 NVC 非暴力沟通练习平台的 AI 助手。

## 你的能力

你可以帮助用户：
1. 查看练习数据和进度（调用 dashboard_query）
2. 搜索练习场景（调用 scenario_search）
3. 生成新的练习场景（调用 scenario_generate）
4. 评估 NVC 表达质量（调用 evaluate_nvc）
5. 查询/写入个人知识 Wiki（调用 wiki_search / wiki_write）
6. 搜索 NVC 知识库（调用 rag_search）
7. 查询/更新用户档案（调用 profile_query / profile_update）
8. 启动练习会话（调用 practice_start）

## 用户档案

{userProfileSummary}

## 之前的对话摘要

{contextSummary}

## 思考过程

处理用户请求时，请按以下步骤思考：

1. **理解意图**：用户想要什么？是查询数据、学习知识、还是执行操作？
2. **判断工具**：是否需要调用工具？
   - 需要数据 → 调用相应工具
   - 需要知识 → 调用 rag_search 或 wiki_search
   - 纯聊天 → 直接回复
3. **执行**：如果需要工具，调用工具获取数据
4. **回复**：基于工具结果或直接生成回复

## 回复风格

- 查数据时：简洁高效，直接给出结果和分析
- 涉及 NVC 学习时：引导用户思考，给出建议
- 工具调用失败时：用自然语言告知用户，建议稍后重试
- 回复要简洁、友好、专业

## 当前时间

{currentTime}
```

---

### 4.4 Phase 4：ToolHook 机制 + 持久化（0.5 天）

#### 4.4.1 NvcToolHook 接口

**新建文件**：`nvcassistant/service/agent/NvcToolHook.java`

```java
/**
 * 工具调用钩子接口
 * 
 * 参考：D:\code\pi\packages\coding-agent\src\core\tools\index.ts (beforeToolCall/afterToolCall)
 */
public interface NvcToolHook {

    /**
     * 工具调用前
     * 
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @param context 工具上下文
     * @return 决策：PROCEED 继续执行，SKIP 跳过
     */
    default ToolCallDecision beforeToolCall(String toolName, JsonNode arguments, ToolContext context) {
        return ToolCallDecision.PROCEED;
    }

    /**
     * 工具调用后
     * 
     * @param toolName 工具名称
     * @param result 工具执行结果
     * @param context 工具上下文
     * @return 处理后的结果（可修改）
     */
    default String afterToolCall(String toolName, String result, ToolContext context) {
        return result;
    }

    enum ToolCallDecision {
        PROCEED,  // 继续执行
        SKIP      // 跳过此工具
    }
}
```

#### 4.4.2 日志钩子实现

**新建文件**：`nvcassistant/service/agent/LoggingToolHook.java`

```java
/**
 * 日志钩子 — 记录工具调用详情
 */
@Component
@Slf4j
public class LoggingToolHook implements NvcToolHook {

    @Override
    public ToolCallDecision beforeToolCall(String toolName, JsonNode arguments, ToolContext context) {
        log.info("[ToolCall] BEFORE: tool={}, userId={}, args={}", 
            toolName, context.getUserId(), arguments);
        return ToolCallDecision.PROCEED;
    }

    @Override
    public String afterToolCall(String toolName, String result, ToolContext context) {
        log.info("[ToolCall] AFTER: tool={}, resultLength={}", 
            toolName, result != null ? result.length() : 0);
        return result;
    }
}
```

#### 4.4.3 工具调用记录持久化钩子

**新建文件**：`nvcassistant/service/agent/PersistToolHook.java`

```java
/**
 * 持久化钩子 — 保存工具调用记录到数据库
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PersistToolHook implements NvcToolHook {

    private final NvcToolCallRecordRepository recordRepository;

    @Override
    public ToolCallDecision beforeToolCall(String toolName, JsonNode arguments, ToolContext context) {
        // 记录开始时间
        context.setAttribute("startTime", System.currentTimeMillis());
        return ToolCallDecision.PROCEED;
    }

    @Override
    public String afterCall(String toolName, String result, ToolContext context) {
        Long startTime = (Long) context.getAttribute("startTime");
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;

        NvcToolCallRecordEntity record = NvcToolCallRecordEntity.builder()
            .userId(context.getUserId())
            .sessionId(context.getSessionId())
            .toolName(toolName)
            .arguments(context.getAttribute("arguments") != null ? 
                context.getAttribute("arguments").toString() : "")
            .result(result)
            .success(true)
            .durationMs(duration)
            .createdAt(LocalDateTime.now())
            .build();

        recordRepository.save(record);
        return result;
    }
}
```

#### 4.4.4 工具调用记录实体

**新建文件**：`nvcassistant/model/NvcToolCallRecordEntity.java`

```java
@Entity
@Table(name = "nvc_tool_call_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcToolCallRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long sessionId;
    private String toolName;

    @Column(columnDefinition = "TEXT")
    private String arguments;

    @Column(columnDefinition = "TEXT")
    private String result;

    private Boolean success;
    private Long durationMs;
    private LocalDateTime createdAt;
}
```

#### 4.4.5 权限控制钩子

**新建文件**：`nvcassistant/service/agent/PermissionToolHook.java`

```java
/**
 * 权限控制钩子 — 功能分级、成本控制
 * 
 * 场景：
 * - scenario_generate 消耗 AI token，限制免费用户调用
 * - evaluate_nvc 需要用户完成至少一次练习
 * - wiki_write 需要用户登录
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PermissionToolHook implements NvcToolHook {

    private final NvcProfileService profileService;
    private final NvcPracticeSessionService sessionService;

    /** 需要付费的工具 */
    private static final Set<String> PREMIUM_TOOLS = Set.of(
        "scenario_generate"  // AI 生成场景，消耗 token
    );

    /** 需要完成至少一次练习的工具 */
    private static final Set<String> NEED_PRACTICE_TOOLS = Set.of(
        "evaluate_nvc"       // 评估需要有练习经验
    );

    @Override
    public ToolCallDecision beforeToolCall(String toolName, JsonNode arguments, ToolContext context) {
        Long userId = context.getUserId();

        // 1. 付费工具检查
        if (PREMIUM_TOOLS.contains(toolName)) {
            if (!isPremiumUser(userId)) {
                log.warn("[Permission] Premium tool blocked: tool={}, userId={}", toolName, userId);
                context.setAttribute("skipReason", "此功能需要升级到专业版");
                return ToolCallDecision.SKIP;
            }
        }

        // 2. 练习经验检查
        if (NEED_PRACTICE_TOOLS.contains(toolName)) {
            if (!hasCompletedPractice(userId)) {
                log.warn("[Permission] Need practice: tool={}, userId={}", toolName, userId);
                context.setAttribute("skipReason", "请先完成一次练习后再使用此功能");
                return ToolCallDecision.SKIP;
            }
        }

        return ToolCallDecision.PROCEED;
    }

    private boolean isPremiumUser(Long userId) {
        // TODO: 实现付费用户检查逻辑
        // 可以检查 NvcUserProfileEntity 的 subscriptionLevel 字段
        return profileService.getProfile(userId)
            .map(p -> "PREMIUM".equals(p.getSubscriptionLevel()))
            .orElse(false);
    }

    private boolean hasCompletedPractice(Long userId) {
        return sessionService.getCompletedSessionCount(userId) > 0;
    }
}
```

#### 4.4.6 缓存钩子

**新建文件**：`nvcassistant/service/agent/CacheToolHook.java`

```java
/**
 * 缓存钩子 — 减少重复查询，提升响应速度
 * 
 * 适用场景：
 * - dashboard_query：统计数据变化不频繁，可缓存 5 分钟
 * - profile_query：用户档案变化不频繁，可缓存 10 分钟
 * - rag_search：相同查询可缓存 30 分钟
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CacheToolHook implements NvcToolHook {

    private final CacheManager cacheManager;

    /** 工具缓存配置：工具名 → 缓存时间 */
    private static final Map<String, Duration> CACHE_CONFIG = Map.of(
        "dashboard_query", Duration.ofMinutes(5),
        "profile_query", Duration.ofMinutes(10),
        "rag_search", Duration.ofMinutes(30),
        "wiki_search", Duration.ofMinutes(30)
    );

    @Override
    public ToolCallDecision beforeToolCall(String toolName, JsonNode arguments, ToolContext context) {
        if (!CACHE_CONFIG.containsKey(toolName)) {
            return ToolCallDecision.PROCEED;
        }

        // 检查缓存
        String cacheKey = buildCacheKey(toolName, arguments, context.getUserId());
        String cached = cacheManager.get(cacheKey, String.class);

        if (cached != null) {
            log.info("[Cache] HIT: tool={}, key={}", toolName, cacheKey);
            context.setAttribute("cachedResult", cached);
            context.setAttribute("fromCache", true);
            return ToolCallDecision.SKIP;  // 跳过实际执行，使用缓存结果
        }

        log.info("[Cache] MISS: tool={}, key={}", toolName, cacheKey);
        return ToolCallDecision.PROCEED;
    }

    @Override
    public String afterToolCall(String toolName, String result, ToolContext context) {
        if (!CACHE_CONFIG.containsKey(toolName)) {
            return result;
        }

        // 如果不是从缓存返回的，存入缓存
        Boolean fromCache = (Boolean) context.getAttribute("fromCache");
        if (fromCache != null && fromCache) {
            return (String) context.getAttribute("cachedResult");
        }

        String cacheKey = buildCacheKey(toolName, null, context.getUserId());
        Duration ttl = CACHE_CONFIG.get(toolName);
        cacheManager.put(cacheKey, result, ttl);

        log.info("[Cache] PUT: tool={}, key={}, ttl={}", toolName, cacheKey, ttl);
        return result;
    }

    private String buildCacheKey(String toolName, JsonNode arguments, Long userId) {
        // 对于 dashboard_query 和 profile_query，只用 userId 作为 key
        if (toolName.equals("dashboard_query") || toolName.equals("profile_query")) {
            return toolName + ":" + userId;
        }
        // 对于 rag_search 和 wiki_search，用 userId + query 作为 key
        String query = arguments != null && arguments.has("query") ? 
            arguments.get("query").asText() : "default";
        return toolName + ":" + userId + ":" + query.hashCode();
    }
}
```

#### 4.4.7 评估触发钩子

**新建文件**：`nvcassistant/service/agent/EvaluationTriggerHook.java`

```java
/**
 * 评估触发钩子 — evaluate_nvc 完成后自动触发 Wiki 生成
 * 
 * 流程：
 * 1. 用户调用 evaluate_nvc 评估 NVC 表达
 * 2. 评估完成后，自动触发 Wiki 自动生成
 * 3. 生成学习笔记，记录评估结果和改进建议
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EvaluationTriggerHook implements NvcToolHook {

    private final NvcWikiAutoGenerateService wikiAutoGenerateService;
    private final NvcUserProfileService profileService;

    @Override
    public String afterToolCall(String toolName, String result, ToolContext context) {
        // 只处理 evaluate_nvc 工具
        if (!"evaluate_nvc".equals(toolName)) {
            return result;
        }

        // 检查用户是否开启了自动生成 Wiki 偏好
        if (!isAutoGenerateEnabled(context.getUserId())) {
            return result;
        }

        // 异步触发 Wiki 自动生成（不阻塞当前对话）
        try {
            wikiAutoGenerateService.scheduleGenerationFromEvaluation(
                context.getSessionId(),
                context.getUserId(),
                result  // 评估结果
            );
            log.info("[EvaluationTrigger] Wiki generation scheduled: userId={}, sessionId={}",
                context.getUserId(), context.getSessionId());
        } catch (Exception e) {
            log.error("[EvaluationTrigger] Failed to schedule Wiki generation", e);
            // 不影响主流程，只记录日志
        }

        return result;
    }

    private boolean isAutoGenerateEnabled(Long userId) {
        return profileService.getProfile(userId)
            .map(p -> p.getPreferences() != null && 
                      Boolean.TRUE.equals(p.getPreferences().getAutoGenerateWiki()))
            .orElse(false);
    }
}
```

#### 4.4.8 限流钩子

**新建文件**：`nvcassistant/service/agent/RateLimitToolHook.java`

```java
/**
 * 限流钩子 — 防止单用户过度调用工具
 * 
 * 限流策略：
 * - scenario_generate：每小时最多 5 次（消耗 AI token）
 * - evaluate_nvc：每小时最多 20 次
 * - 其他工具：每分钟最多 30 次
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitToolHook implements NvcToolHook {

    private final RedisTemplate<String, String> redisTemplate;

    /** 限流配置：工具名 → (时间窗口秒, 最大次数) */
    private static final Map<String, int[]> RATE_LIMIT_CONFIG = Map.of(
        "scenario_generate", new int[]{3600, 5},    // 每小时 5 次
        "evaluate_nvc", new int[]{3600, 20},         // 每小时 20 次
        "rag_search", new int[]{60, 30},             // 每分钟 30 次
        "wiki_search", new int[]{60, 30},            // 每分钟 30 次
        "wiki_write", new int[]{60, 10}              // 每分钟 10 次
    );

    /** 默认限流：每分钟 30 次 */
    private static final int[] DEFAULT_RATE_LIMIT = {60, 30};

    @Override
    public ToolCallDecision beforeToolCall(String toolName, JsonNode arguments, ToolContext context) {
        int[] config = RATE_LIMIT_CONFIG.getOrDefault(toolName, DEFAULT_RATE_LIMIT);
        int windowSeconds = config[0];
        int maxCalls = config[1];

        String key = "rate_limit:" + toolName + ":" + context.getUserId();

        try {
            // 使用 Redis INCR + EXPIRE 实现滑动窗口限流
            Long currentCount = redisTemplate.opsForValue().increment(key);
            if (currentCount != null && currentCount == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }

            if (currentCount != null && currentCount > maxCalls) {
                log.warn("[RateLimit] Exceeded: tool={}, userId={}, count={}/{}", 
                    toolName, context.getUserId(), currentCount, maxCalls);
                context.setAttribute("skipReason", 
                    String.format("调用过于频繁，请 %d 秒后再试", windowSeconds));
                return ToolCallDecision.SKIP;
            }

            log.debug("[RateLimit] OK: tool={}, userId={}, count={}/{}", 
                toolName, context.getUserId(), currentCount, maxCalls);
            return ToolCallDecision.PROCEED;

        } catch (Exception e) {
            // Redis 异常时放行，不影响用户体验
            log.error("[RateLimit] Redis error, allowing call", e);
            return ToolCallDecision.PROCEED;
        }
    }
}
```

#### 4.4.9 错误增强钩子

**新建文件**：`nvcassistant/service/agent/ErrorEnhanceHook.java`

```java
/**
 * 错误增强钩子 — 工具失败时注入 RAG 知识帮助 LLM 理解
 * 
 * 场景：
 * - 工具调用失败时，RAG 检索相关知识，帮助 LLM 生成更好的错误回复
 * - 例如：rag_search 失败时，检索 NVC 基础知识作为兜底
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ErrorEnhanceHook implements NvcToolHook {

    private final NvcRagService ragService;

    /** 需要错误增强的工具 */
    private static final Set<String> ENHANCED_TOOLS = Set.of(
        "rag_search",
        "wiki_search",
        "evaluate_nvc"
    );

    @Override
    public String afterToolCall(String toolName, String result, ToolContext context) {
        // 只处理失败的工具调用
        if (result == null || !result.startsWith("Error:")) {
            return result;
        }

        // 只增强特定工具
        if (!ENHANCED_TOOLS.contains(toolName)) {
            return result;
        }

        try {
            // RAG 检索相关知识
            String query = "工具调用失败 " + toolName + " " + result;
            List<RagResult> ragResults = ragService.retrieve(
                query, context.getUserId(), 
                List.of(KnowledgeBaseType.NVC_THEORY), 
                2
            );

            if (!ragResults.isEmpty()) {
                String knowledge = ragResults.stream()
                    .map(RagResult::text)
                    .collect(Collectors.joining("\n\n"));

                String enhancedResult = result + "\n\n【参考知识】\n" + knowledge;
                log.info("[ErrorEnhance] Enhanced: tool={}, knowledgeLength={}", 
                    toolName, knowledge.length());
                return enhancedResult;
            }
        } catch (Exception e) {
            log.error("[ErrorEnhance] Failed to enhance error", e);
        }

        return result;
    }
}
```

#### 4.4.10 钩子优先级与执行顺序

```java
/**
 * 钩子执行顺序说明
 * 
 * beforeToolCall 执行顺序（按 @Order 注解）：
 * 1. RateLimitToolHook (Order=1) — 限流检查，最先执行
 * 2. PermissionToolHook (Order=2) — 权限检查
 * 3. CacheToolHook (Order=3) — 缓存检查（命中则跳过后续）
 * 
 * afterToolCall 执行顺序（逆序）：
 * 1. ErrorEnhanceHook (Order=4) — 错误增强
 * 2. EvaluationTriggerHook (Order=5) — 评估触发 Wiki
 * 3. CacheToolHook (Order=3) — 缓存结果
 * 4. PersistToolHook (Order=6) — 持久化记录
 * 5. LoggingToolHook (Order=7) — 日志记录
 */
```

---

## 五、文件清单

### 5.1 新建文件

```
app/src/main/java/nvc/guide/modules/nvcassistant/service/agent/
├── AgentLoop.java                    # Agent 主循环
├── AgentEvent.java                   # SSE 事件类型
├── ToolExecutor.java                 # 工具执行器
├── ContextManager.java               # 上下文管理器（含压缩）
├── PromptBuilder.java                # Prompt 构建器
├── NvcToolHook.java                  # 工具钩子接口
├── LoggingToolHook.java              # 日志钩子（Order=7）
├── PersistToolHook.java              # 持久化钩子（Order=6）
├── PermissionToolHook.java           # 权限控制钩子（Order=2）
├── CacheToolHook.java                # 缓存钩子（Order=3）
├── EvaluationTriggerHook.java        # 评估触发钩子（Order=5）
├── RateLimitToolHook.java            # 限流钩子（Order=1）
└── ErrorEnhanceHook.java             # 错误增强钩子（Order=4）

app/src/main/java/nvc/guide/modules/nvcassistant/model/
└── NvcToolCallRecordEntity.java      # 工具调用记录实体

app/src/main/java/nvc/guide/modules/nvcassistant/repository/
└── NvcToolCallRecordRepository.java  # 工具调用记录仓库

app/src/main/resources/prompts/
└── nvc-assistant-system-v2.st        # 新版系统 Prompt（带 CoT）
```

### 5.2 修改文件

```
app/src/main/java/nvc/guide/modules/nvcassistant/service/
└── NvcAssistantService.java          # 重构：使用 AgentLoop

app/src/main/java/nvc/guide/modules/nvcassistant/controller/
└── NvcAssistantController.java       # 适配新的 SSE 事件格式

app/src/main/java/nvc/guide/modules/nvcpractice/tool/
└── NvcToolContext.java               # 增加 attribute 存储
```

### 5.3 前端修改

```
frontend/src/api/
└── nvc-assistant.ts                  # 适配新的 SSE 事件类型

frontend/src/components/nvc/
└── NvcToolCallCard.tsx               # 增强：展示工具调用详情
└── NvcAssistantChat.tsx              # 适配新的事件流
```

---

## 六、实施顺序

```
Day 1: Phase 1 — Agent Loop 核心
       ├── AgentLoop.java（主循环逻辑）
       ├── AgentEvent.java（事件类型）
       ├── ToolExecutor.java（工具执行器）
       └── 单元测试

Day 2: Phase 1 — 流式事件 + Controller 适配
       ├── AgentLoop 流式输出
       ├── NvcAssistantController 适配
       ├── 前端 SSE 事件处理
       └── 集成测试

Day 3: Phase 2 — 上下文压缩
       ├── ContextManager.java
       ├── 摘要生成逻辑
       └── 测试长对话场景

Day 4: Phase 3 + 4 — Prompt + Hook（核心）
       ├── PromptBuilder.java
       ├── nvc-assistant-system-v2.st
       ├── NvcToolHook 接口
       ├── LoggingToolHook（日志）
       ├── PersistToolHook（持久化）
       └── NvcToolCallRecordEntity

Day 5: Phase 4 — Hook（业务增强）
       ├── PermissionToolHook（权限控制）
       ├── CacheToolHook（缓存）
       ├── RateLimitToolHook（限流）
       ├── EvaluationTriggerHook（评估触发）
       └── ErrorEnhanceHook（错误增强）
```

---

## 七、验收标准

```
□ Agent Loop
  □ 多轮工具调用正常（最多 10 轮）
  □ 超时控制生效（单轮 30s，总 120s）
  □ 工具调用失败时优雅降级

□ 流式事件
  □ thinking 事件正常发送
  □ toolcall_start 事件包含工具名和参数
  □ toolcall_end 事件包含结果和成功状态
  □ content 事件正常流式输出
  □ done 事件包含 conversationId

□ 上下文压缩
  □ 超过 20 轮时自动触发压缩
  □ 摘要生成正常
  □ 压缩后对话质量不下降

□ CoT 引导
  □ Agent 能正确理解用户意图
  □ 工具选择准确率提升

□ 工具钩子 — 基础
  □ LoggingToolHook：日志正常记录
  □ PersistToolHook：工具调用记录持久化正常
  □ NvcToolCallRecordEntity：数据表创建正常

□ 工具钩子 — 业务增强
  □ PermissionToolHook：付费工具拦截正常
  □ CacheToolHook：缓存命中/未命中正常
  □ RateLimitToolHook：限流生效（Redis）
  □ EvaluationTriggerHook：评估后自动触发 Wiki 生成
  □ ErrorEnhanceHook：错误时注入 RAG 知识

□ 前端展示
  □ 工具调用卡片正常显示
  □ 流式对话无 JSON 闪烁
  □ 长对话上下文保持连贯
```

---

## 八、与原方案对比总结

| 维度 | 原方案 | 新方案 | 提升 |
|------|--------|--------|------|
| **可控性** | 低（Spring AI 黑盒） | 高（自己控制 Loop） | ⬆️⬆️⬆️ |
| **可观测性** | 低（只看最终结果） | 高（每步事件流） | ⬆️⬆️⬆️ |
| **长对话质量** | 中（20 轮截断） | 高（LLM 摘要压缩） | ⬆️⬆️ |
| **Agent 智能** | 中（无 CoT） | 高（CoT 引导） | ⬆️⬆️ |
| **可扩展性** | 低（无钩子） | 高（Hook 机制） | ⬆️⬆️⬆️ |
| **代码复杂度** | 低 | 中 | ⬇️ |
| **开发工作量** | 2 天 | 4 天 | ⬇️ |

**结论**：新方案开发量增加 2 天，但可控性、可观测性、长对话质量、Agent 智能都有显著提升，值得投入。
