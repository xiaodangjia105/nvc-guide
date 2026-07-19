# Phase 1.2：Agent 工具调用框架 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 NvcTool 接口 + NvcToolRegistry + 10 个核心工具，为练习 Agent 和主 Agent 提供统一的工具调用能力。

**Architecture:** 自定义 NvcTool 接口通过 NvcToolRegistry 适配为 Spring AI FunctionCallback，通过 NvcChatRequest 解耦编排层与工具层。不同 Agent 场景通过 NvcToolSceneMapping 映射表选择工具子集。

**Tech Stack:** Spring Boot 4.0.1, Spring AI 2.0.0-M4, Java 21, JUnit 5 + Mockito

## Global Constraints

- 所有新文件放在 `app/src/main/java/nvc/guide/modules/nvcpractice/` 下
- 工具类加 `@Component` 注解，Spring 自动注册
- 原有 `chat(config, context, userMessage, promptVariables)` 签名不变，向后兼容
- `chatPlain()` 不注入工具，保持不变
- 使用 `org.springframework.ai.tool.function.FunctionToolCallback` 做适配
- 使用 `org.springframework.ai.util.json.JsonSchemaGenerator` 生成 JSON Schema
- 使用 `org.springframework.ai.util.json.JsonParser` 解析 JSON 输入

---

## 文件结构总览

### 新建文件（17 个）

```
app/src/main/java/nvc/guide/modules/nvcpractice/
├── tool/
│   ├── NvcTool.java                    # 工具接口
│   ├── NvcToolResult.java              # 工具执行结果 record
│   ├── NvcToolContext.java             # 工具执行上下文
│   ├── NvcToolRegistry.java            # 工具注册中心 + Spring AI 适配
│   ├── NvcToolSceneMapping.java        # 场景-工具映射表
│   ├── RagSearchTool.java              # RAG 知识检索
│   ├── ProfileQueryTool.java           # 用户档案查询
│   ├── ProfileUpdateTool.java          # 用户档案更新
│   ├── DashboardQueryTool.java         # 仪表盘数据查询
│   ├── ScenarioSearchTool.java         # 场景搜索
│   ├── ScenarioGenerateTool.java       # 场景生成
│   ├── EvaluateNvcTool.java            # NVC 表达评估
│   ├── WikiSearchTool.java             # Wiki 搜索（骨架）
│   ├── WikiWriteTool.java              # Wiki 写入（骨架）
│   └── PracticeStartTool.java          # 启动练习
└── dto/
    ├── NvcChatRequest.java             # 统一对话请求对象
    └── NvcToolCallConfig.java          # 工具调用配置
```

### 修改文件（5 个）

```
├── NvcAgentChatService.java            # 注入 NvcToolRegistry + NvcChatRequest 支持
├── AgentDecision.java                  # 新增 availableTools 字段
├── FreeDialogRouter.java              # 注入 NvcToolSceneMapping
├── ScenarioRouter.java                # 注入 NvcToolSceneMapping
└── StructuredRouter.java              # 注入 NvcToolSceneMapping
```

---

### Task 1: 核心接口 — NvcTool + NvcToolResult + NvcToolContext

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/tool/NvcTool.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/tool/NvcToolResult.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/tool/NvcToolContext.java`

**Interfaces:**
- Produces: `NvcTool` — 所有工具实现此接口
- Produces: `NvcToolResult` — 工具执行结果，`success(String)` / `failure(String)` 工厂方法
- Produces: `NvcToolContext` — 工具执行上下文，含 `userId`、`sessionId`、`practiceContext`

- [ ] **Step 1: 创建 NvcToolResult.java**

```java
package nvc.guide.modules.nvcpractice.tool;

/**
 * 工具执行结果
 */
public record NvcToolResult(boolean success, String data, String errorMessage) {

    public static NvcToolResult success(String data) {
        return new NvcToolResult(true, data, null);
    }

    public static NvcToolResult failure(String errorMessage) {
        return new NvcToolResult(false, null, errorMessage);
    }
}
```

- [ ] **Step 2: 创建 NvcToolContext.java**

```java
package nvc.guide.modules.nvcpractice.tool;

import lombok.Builder;
import lombok.Data;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;

/**
 * 工具执行上下文 — 从 Spring AI ToolContext Map 中提取
 */
@Data
@Builder
public class NvcToolContext {
    private Long userId;
    private Long sessionId;
    private PracticeContext practiceContext;
}
```

- [ ] **Step 3: 创建 NvcTool.java**

```java
package nvc.guide.modules.nvcpractice.tool;

/**
 * NVC 工具接口 — Agent 可调用的外部能力。
 * 实现此接口并加上 @Component 注解即可自动注册到 NvcToolRegistry。
 */
public interface NvcTool {

    /** 工具名称，用于 Function Calling */
    String name();

    /** 工具描述，告诉 LLM 这个工具能做什么 */
    String description();

    /** 输入参数的 JSON Schema（字符串格式，与 Spring AI ToolDefinition 一致） */
    String inputSchema();

    /** 执行工具 */
    NvcToolResult execute(String input, NvcToolContext context);
}
```

- [ ] **Step 4: 验证编译**

Run: `cd D:/code/nvc-guide/.claude/worktrees/phase12-tool-framework && ./gradlew.bat :app:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/tool/NvcTool.java \
       app/src/main/java/nvc/guide/modules/nvcpractice/tool/NvcToolResult.java \
       app/src/main/java/nvc/guide/modules/nvcpractice/tool/NvcToolContext.java
git commit -m "feat(tool): add NvcTool interface, NvcToolResult, NvcToolContext"
```

---

### Task 2: DTO 层 — NvcChatRequest + NvcToolCallConfig

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/NvcChatRequest.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/NvcToolCallConfig.java`

**Interfaces:**
- Produces: `NvcChatRequest` — 统一对话请求对象，Builder 模式
- Produces: `NvcToolCallConfig` — 工具调用配置，`disabled()` 工厂方法，`isEnabled()` 判断

- [ ] **Step 1: 创建 NvcToolCallConfig.java**

```java
package nvc.guide.modules.nvcpractice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工具调用配置 — 独立于编排层的可选扩展点
 */
@Data
@Builder
public class NvcToolCallConfig {

    /** 可用工具名称列表 */
    private final List<String> toolNames;

    /** 额外上下文（传递给 Spring AI ToolContext） */
    @Builder.Default
    private final Map<String, Object> extraContext = Map.of();

    public static NvcToolCallConfig disabled() {
        return new NvcToolCallConfig(List.of(), Map.of());
    }

    public boolean isEnabled() {
        return toolNames != null && !toolNames.isEmpty();
    }
}
```

- [ ] **Step 2: 创建 NvcChatRequest.java**

```java
package nvc.guide.modules.nvcpractice.dto;

import lombok.Builder;
import lombok.Data;
import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;

import java.util.Map;

/**
 * 统一对话请求对象 — 解耦编排层与 ChatService
 */
@Data
@Builder
public class NvcChatRequest {

    private final NvcAgentConfigEntity agentConfig;
    private final PracticeContext practiceContext;
    private final String userMessage;
    private final Map<String, String> promptVariables;

    /** 工具配置（可选）— 不传则无工具调用 */
    @Builder.Default
    private final NvcToolCallConfig toolConfig = NvcToolCallConfig.disabled();
}
```

- [ ] **Step 3: 验证编译**

Run: `cd D:/code/nvc-guide/.claude/worktrees/phase12-tool-framework && ./gradlew.bat :app:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/dto/NvcChatRequest.java \
       app/src/main/java/nvc/guide/modules/nvcpractice/dto/NvcToolCallConfig.java
git commit -m "feat(tool): add NvcChatRequest and NvcToolCallConfig DTOs"
```

---

### Task 3: NvcToolRegistry — 工具注册中心

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/tool/NvcToolRegistry.java`
- Create: `app/src/test/java/nvc/guide/modules/nvcpractice/tool/NvcToolRegistryTest.java`

**Interfaces:**
- Consumes: `NvcTool`, `NvcToolResult`, `NvcToolContext` (Task 1)
- Produces: `NvcToolRegistry.getTool(String)`, `getAllTools()`, `toFunctionCallbacks()`, `toFunctionCallbacks(List<String>)`

- [ ] **Step 1: 创建 NvcToolRegistryTest.java**

```java
package nvc.guide.modules.nvcpractice.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NvcToolRegistry 测试")
class NvcToolRegistryTest {

    private NvcTool createDummyTool(String name) {
        return new NvcTool() {
            @Override public String name() { return name; }
            @Override public String description() { return "desc-" + name; }
            @Override public String inputSchema() { return "{}"; }
            @Override public NvcToolResult execute(String input, NvcToolContext context) {
                return NvcToolResult.success("ok");
            }
        };
    }

    @Test
    @DisplayName("自动注册所有 NvcTool 实现")
    void registerAllTools() {
        NvcTool t1 = createDummyTool("tool_a");
        NvcTool t2 = createDummyTool("tool_b");

        NvcToolRegistry registry = new NvcToolRegistry(List.of(t1, t2));

        assertEquals(2, registry.getAllTools().size());
        assertNotNull(registry.getTool("tool_a"));
        assertNotNull(registry.getTool("tool_b"));
        assertNull(registry.getTool("nonexistent"));
    }

    @Test
    @DisplayName("toFunctionCallbacks() 返回全量工具")
    void toFunctionCallbacks_all() {
        NvcTool t1 = createDummyTool("tool_a");
        NvcToolRegistry registry = new NvcToolRegistry(List.of(t1));

        List<ToolCallback> callbacks = registry.toFunctionCallbacks();
        assertEquals(1, callbacks.size());
    }

    @Test
    @DisplayName("toFunctionCallbacks(List) 按名称过滤")
    void toFunctionCallbacks_filtered() {
        NvcTool t1 = createDummyTool("tool_a");
        NvcTool t2 = createDummyTool("tool_b");
        NvcToolRegistry registry = new NvcToolRegistry(List.of(t1, t2));

        List<ToolCallback> callbacks = registry.toFunctionCallbacks(List.of("tool_a"));
        assertEquals(1, callbacks.size());
    }

    @Test
    @DisplayName("toFunctionCallbacks(List) 忽略不存在的工具名")
    void toFunctionCallbacks_ignoreUnknown() {
        NvcTool t1 = createDummyTool("tool_a");
        NvcToolRegistry registry = new NvcToolRegistry(List.of(t1));

        List<ToolCallback> callbacks = registry.toFunctionCallbacks(List.of("tool_a", "nonexistent"));
        assertEquals(1, callbacks.size());
    }

    @Test
    @DisplayName("toFunctionCallbacks 空列表返回空")
    void toFunctionCallbacks_empty() {
        NvcToolRegistry registry = new NvcToolRegistry(List.of());
        assertTrue(registry.toFunctionCallbacks().isEmpty());
        assertTrue(registry.toFunctionCallbacks(List.of("x")).isEmpty());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd D:/code/nvc-guide/.claude/worktrees/phase12-tool-framework && ./gradlew.bat :app:test --tests "nvc.guide.modules.nvcpractice.tool.NvcToolRegistryTest"`
Expected: FAIL — NvcToolRegistry 类不存在

- [ ] **Step 3: 创建 NvcToolRegistry.java**

```java
package nvc.guide.modules.nvcpractice.tool;

import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 工具注册中心 — Spring 自动注入所有 NvcTool 实现，转换为 Spring AI FunctionCallback
 */
@Service
@Slf4j
public class NvcToolRegistry {

    private final Map<String, NvcTool> tools;

    /**
     * Spring 自动注入所有 NvcTool 实现
     */
    public NvcToolRegistry(List<NvcTool> toolList) {
        this.tools = toolList.stream()
            .collect(Collectors.toMap(NvcTool::name, t -> t));
        log.info("[NvcToolRegistry] Registered {} tools: {}", tools.size(), tools.keySet());
    }

    public NvcTool getTool(String name) {
        return tools.get(name);
    }

    public List<NvcTool> getAllTools() {
        return List.copyOf(tools.values());
    }

    /**
     * 全量转换 — 主 Agent 使用
     */
    public List<ToolCallback> toFunctionCallbacks() {
        return tools.values().stream().map(this::toFunctionCallback).toList();
    }

    /**
     * 按名称子集 — 练习 Agent 按场景过滤
     */
    public List<ToolCallback> toFunctionCallbacks(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        return toolNames.stream()
            .map(tools::get)
            .filter(Objects::nonNull)
            .map(this::toFunctionCallback)
            .toList();
    }

    /**
     * NvcTool → Spring AI FunctionToolCallback 适配
     */
    private ToolCallback toFunctionCallback(NvcTool tool) {
        return FunctionToolCallback.builder(tool.name(),
                (String input, ToolContext aiContext) -> {
                    try {
                        NvcToolContext nvcContext = extractNvcContext(aiContext);
                        NvcToolResult result = tool.execute(input, nvcContext);
                        return result.success() ? result.data() : "Error: " + result.errorMessage();
                    } catch (Exception e) {
                        log.error("[NvcToolRegistry] Tool execution failed: tool={}", tool.name(), e);
                        return "Error: 工具执行异常: " + e.getMessage();
                    }
                })
            .description(tool.description())
            .inputSchema(tool.inputSchema())
            .inputType(String.class)
            .build();
    }

    /**
     * 从 Spring AI ToolContext Map 中提取 NvcToolContext
     */
    private NvcToolContext extractNvcContext(ToolContext aiContext) {
        if (aiContext == null || aiContext.getContext().isEmpty()) {
            return NvcToolContext.builder().build();
        }
        Map<String, Object> map = aiContext.getContext();
        return NvcToolContext.builder()
            .userId((Long) map.get("nvc.userId"))
            .sessionId((Long) map.get("nvc.sessionId"))
            .practiceContext((PracticeContext) map.get("nvc.practiceContext"))
            .build();
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd D:/code/nvc-guide/.claude/worktrees/phase12-tool-framework && ./gradlew.bat :app:test --tests "nvc.guide.modules.nvcpractice.tool.NvcToolRegistryTest"`
Expected: ALL TESTS PASSED

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/tool/NvcToolRegistry.java \
       app/src/test/java/nvc/guide/modules/nvcpractice/tool/NvcToolRegistryTest.java
git commit -m "feat(tool): add NvcToolRegistry with Spring AI FunctionCallback adapter"
```

---

### Task 4: NvcToolSceneMapping — 场景-工具映射表

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/tool/NvcToolSceneMapping.java`

**Interfaces:**
- Consumes: `NvcAgentScene` (existing enum)
- Produces: `NvcToolSceneMapping.getDefaultTools(NvcAgentScene)`, `getAllTools()`

- [ ] **Step 1: 创建 NvcToolSceneMapping.java**

```java
package nvc.guide.modules.nvcpractice.tool;

import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 场景-工具映射表 — 定义每个 Agent 场景可用的工具子集
 */
@Component
public class NvcToolSceneMapping {

    private static final Map<NvcAgentScene, List<String>> SCENE_TOOLS = Map.ofEntries(
        // 练习 Agent 场景
        Map.entry(NvcAgentScene.NVC_EXPRESSION_EVALUATOR,
            List.of("evaluate_nvc", "rag_search")),
        Map.entry(NvcAgentScene.DIALOGUE_GUIDE,
            List.of("rag_search", "profile_query")),
        Map.entry(NvcAgentScene.SCENARIO_GENERATOR,
            List.of("scenario_search", "scenario_generate", "profile_query")),
        Map.entry(NvcAgentScene.EMPATHY_COACH,
            List.of("rag_search", "profile_query")),
        Map.entry(NvcAgentScene.NVC_KNOWLEDGE_ADVISOR,
            List.of("rag_search", "wiki_search")),
        // 步骤教练
        Map.entry(NvcAgentScene.STEP_OBSERVE_COACH, List.of("rag_search")),
        Map.entry(NvcAgentScene.STEP_FEELING_COACH, List.of("rag_search")),
        Map.entry(NvcAgentScene.STEP_NEED_COACH, List.of("rag_search")),
        Map.entry(NvcAgentScene.STEP_REQUEST_COACH, List.of("rag_search")),
        // 困难搭档 — 不给工具，纯角色扮演
        Map.entry(NvcAgentScene.DIFFICULT_PARTNER, List.of()),
        // 档案分析 — 只读
        Map.entry(NvcAgentScene.PROFILE_ANALYZER,
            List.of("profile_query", "dashboard_query"))
    );

    /**
     * 获取场景的默认工具列表
     */
    public List<String> getDefaultTools(NvcAgentScene scene) {
        return SCENE_TOOLS.getOrDefault(scene, List.of());
    }

    /**
     * 主 Agent — 全量工具（Phase 2 使用）
     */
    public List<String> getAllTools() {
        return List.of(
            "rag_search", "wiki_search", "wiki_write",
            "profile_query", "profile_update", "evaluate_nvc",
            "scenario_search", "scenario_generate", "dashboard_query",
            "practice_start"
        );
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd D:/code/nvc-guide/.claude/worktrees/phase12-tool-framework && ./gradlew.bat :app:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/tool/NvcToolSceneMapping.java
git commit -m "feat(tool): add NvcToolSceneMapping with per-scene tool lists"
```

---

### Task 5: AgentDecision 扩展 — 新增 availableTools 字段

**Files:**
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/AgentDecision.java`

**Interfaces:**
- Produces: `AgentDecision.availableTools()` — 新增字段，向后兼容

- [ ] **Step 1: 修改 AgentDecision.java — 新增 availableTools 字段**

将当前内容替换为：

```java
package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcAgentScene;

import java.util.List;
import java.util.Map;

/**
 * Agent 调度决策结果
 *
 * @param scene            选择的 Agent 角色
 * @param reason           决策原因（日志/调试用）
 * @param action           动作标记（STEP_ADVANCE / LOW_SCORE_GUIDE / DIFFICULT_UPGRADE 等，可为 null）
 * @param promptVariables  注入到 Prompt 模板的变量（mode、covered_elements 等）
 * @param availableTools   该 Agent 可用的工具名称列表（为空则不注入工具）
 */
public record AgentDecision(
    NvcAgentScene scene,
    String reason,
    String action,
    Map<String, String> promptVariables,
    List<String> availableTools
) {

  /**
   * 便捷构造器（无 promptVariables、无工具），兼容现有调用
   */
  public AgentDecision(NvcAgentScene scene, String reason, String action) {
    this(scene, reason, action, Map.of(), List.of());
  }

  /**
   * 便捷构造器（无工具），兼容现有调用
   */
  public AgentDecision(NvcAgentScene scene, String reason, String action,
                       Map<String, String> promptVariables) {
    this(scene, reason, action, promptVariables, List.of());
  }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd D:/code/nvc-guide/.claude/worktrees/phase12-tool-framework && ./gradlew.bat :app:compileJava`
Expected: BUILD SUCCESSFUL（现有调用点使用便捷构造器，无需修改）

- [ ] **Step 3: 运行现有测试确认无破坏**

Run: `cd D:/code/nvc-guide/.claude/worktrees/phase12-tool-framework && ./gradlew.bat :app:test --tests "nvc.guide.modules.nvcpractice.*"`
Expected: ALL TESTS PASSED

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/dto/AgentDecision.java
git commit -m "feat(tool): add availableTools field to AgentDecision"
```

---

### Task 6: NvcAgentChatService 升级 — 注入工具 + NvcChatRequest 支持

**Files:**
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentChatService.java`
- Modify: `app/src/test/java/nvc/guide/modules/nvcpractice/service/NvcAgentChatServiceTest.java`

**Interfaces:**
- Consumes: `NvcToolRegistry` (Task 3), `NvcChatRequest` / `NvcToolCallConfig` (Task 2)
- Produces: `chat(NvcChatRequest)`, `chatStream(NvcChatRequest)` — 新增重载方法

- [ ] **Step 1: 修改 NvcAgentChatService.java**

将当前内容替换为：

```java
package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.modules.nvcpractice.dto.NvcChatRequest;
import nvc.guide.modules.nvcpractice.dto.NvcToolCallConfig;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import nvc.guide.modules.nvcpractice.model.NvcMessageRole;
import nvc.guide.modules.nvcpractice.tool.NvcToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcAgentChatService {

  private final LlmProviderRegistry llmProviderRegistry;
  private final NvcToolRegistry toolRegistry;

  // ═══ 原有方法 — 保持不变，向后兼容 ═══

  /**
   * 执行 Agent 对话（非流式）
   */
  public String chat(
      NvcAgentConfigEntity config, PracticeContext context,
      String userMessage, Map<String, String> promptVariables) {
    return chat(NvcChatRequest.builder()
        .agentConfig(config)
        .practiceContext(context)
        .userMessage(userMessage)
        .promptVariables(promptVariables)
        .build());
  }

  /**
   * 执行 Agent 对话（流式）
   */
  public Flux<String> chatStream(
      NvcAgentConfigEntity config, PracticeContext context,
      String userMessage, Map<String, String> promptVariables) {
    return chatStream(NvcChatRequest.builder()
        .agentConfig(config)
        .practiceContext(context)
        .userMessage(userMessage)
        .promptVariables(promptVariables)
        .build());
  }

  /**
   * 使用 Plain ChatClient（无工具、无记忆）执行简单对话
   * 用于评估、反思等需要结构化输出的场景
   */
  public String chatPlain(
      NvcAgentConfigEntity config, String systemPrompt, String userPrompt) {
    ChatClient client = llmProviderRegistry.getPlainChatClient(
        config.getModelProvider());

    return client.prompt()
        .options(buildChatOptions(config))
        .system(systemPrompt)
        .user(userPrompt)
        .call()
        .content();
  }

  // ═══ 新方法 — 统一入口 ═══

  /**
   * 执行 Agent 对话（非流式）— 统一入口，支持工具调用
   */
  public String chat(NvcChatRequest request) {
    ChatClient client = getChatClient(request.getAgentConfig());
    List<Message> messages = buildMessages(
        request.getAgentConfig(), request.getPracticeContext(),
        request.getUserMessage(), request.getPromptVariables());

    var spec = client.prompt()
        .options(buildChatOptions(request.getAgentConfig()))
        .messages(messages);

    if (request.getToolConfig().isEnabled()) {
      spec = spec.toolContext(buildToolContextMap(request));
      spec = spec.functionCallbacks(
          toolRegistry.toFunctionCallbacks(request.getToolConfig().getToolNames()));
    }

    return spec.call().content();
  }

  /**
   * 执行 Agent 对话（流式）— 统一入口，支持工具调用
   */
  public Flux<String> chatStream(NvcChatRequest request) {
    ChatClient client = getChatClient(request.getAgentConfig());
    List<Message> messages = buildMessages(
        request.getAgentConfig(), request.getPracticeContext(),
        request.getUserMessage(), request.getPromptVariables());

    var spec = client.prompt()
        .options(buildChatOptions(request.getAgentConfig()))
        .messages(messages);

    if (request.getToolConfig().isEnabled()) {
      spec = spec.toolContext(buildToolContextMap(request));
      spec = spec.functionCallbacks(
          toolRegistry.toFunctionCallbacks(request.getToolConfig().getToolNames()));
    }

    return spec.stream().content();
  }

  // ═══ 内部方法 ═══

  private ChatClient getChatClient(NvcAgentConfigEntity config) {
    return llmProviderRegistry.getChatClientOrDefault(config.getModelProvider());
  }

  private OpenAiChatOptions buildChatOptions(NvcAgentConfigEntity config) {
    return OpenAiChatOptions.builder()
        .temperature(config.getTemperature())
        .maxTokens(config.getMaxTokens())
        .topP(config.getTopP())
        .build();
  }

  /**
   * 构建 Spring AI ToolContext Map — 将 NVC 上下文注入工具执行环境
   */
  private Map<String, Object> buildToolContextMap(NvcChatRequest request) {
    Map<String, Object> map = new HashMap<>();
    PracticeContext ctx = request.getPracticeContext();
    if (ctx != null && ctx.getSession() != null) {
      map.put("nvc.userId", ctx.getSession().getUserId());
      map.put("nvc.sessionId", ctx.getSession().getId());
      map.put("nvc.practiceContext", ctx);
    }
    map.putAll(request.getToolConfig().getExtraContext());
    return map;
  }

  private List<Message> buildMessages(
      NvcAgentConfigEntity config, PracticeContext context,
      String userMessage, Map<String, String> promptVariables) {
    List<Message> messages = new ArrayList<>();

    // 1. 系统提示词
    String systemPrompt = config.getSystemPrompt();

    // 2. 注入用户档案信息（如有）
    if (context.getUserProfileSummary() != null) {
      systemPrompt += "\n\n[用户档案]\n" + context.getUserProfileSummary();
    }

    // 3. 注入场景信息（如有）
    if (context.getScenarioDescription() != null) {
      systemPrompt += "\n\n[练习场景]\n" + context.getScenarioDescription();
    }

    // 4. 注入 RAG 知识（如有）
    if (context.getRagContext() != null) {
      systemPrompt += "\n\n[参考资料]\n" + context.getRagContext();
    }

    // 5. 注入路由变量（mode、covered_elements 等）
    if (promptVariables != null && !promptVariables.isEmpty()) {
      StringBuilder sb = new StringBuilder("\n\n[当前配置]");
      promptVariables.forEach((key, value) ->
          sb.append("\n").append(key).append("=").append(value));
      systemPrompt += sb;
    }

    messages.add(new SystemMessage(systemPrompt));

    // 6. 对话历史
    if (context.getRecentMessages() != null) {
      for (var msg : context.getRecentMessages()) {
        if (msg.getRole() == NvcMessageRole.USER) {
          messages.add(new UserMessage(msg.getContent()));
        } else if (msg.getRole() == NvcMessageRole.ASSISTANT) {
          messages.add(new AssistantMessage(msg.getContent()));
        }
      }
    }

    // 7. 当前用户消息（防御性去重）
    boolean alreadyIncluded = context.getRecentMessages() != null
        && !context.getRecentMessages().isEmpty()
        && context.getRecentMessages().getLast().getRole() == NvcMessageRole.USER
        && context.getRecentMessages().getLast().getContent().equals(userMessage);
    if (!alreadyIncluded) {
      messages.add(new UserMessage(userMessage));
    }

    return messages;
  }
}
```

- [ ] **Step 2: 更新 NvcAgentChatServiceTest.java**

将当前内容替换为：

```java
package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.modules.nvcpractice.dto.NvcChatRequest;
import nvc.guide.modules.nvcpractice.dto.NvcToolCallConfig;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.tool.NvcToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcAgentChatService 测试")
class NvcAgentChatServiceTest {

  @Mock private LlmProviderRegistry llmProviderRegistry;

  private NvcToolRegistry toolRegistry;
  private NvcAgentChatService service;

  @BeforeEach
  void setUp() {
    toolRegistry = new NvcToolRegistry(List.of());
    service = new NvcAgentChatService(llmProviderRegistry, toolRegistry);
  }

  private NvcAgentConfigEntity buildConfig() {
    return NvcAgentConfigEntity.builder()
        .id(1L)
        .agentScene(NvcAgentScene.DIALOGUE_GUIDE)
        .displayName("对话引导官")
        .systemPrompt("你是 NVC 对话引导官")
        .modelProvider("mimo")
        .temperature(0.7)
        .maxTokens(2000)
        .topP(0.9)
        .isEnabled(true)
        .build();
  }

  private PracticeContext buildContext() {
    NvcPracticeSessionEntity session = NvcPracticeSessionEntity.builder()
        .id(1L)
        .userId(100L)
        .practiceMode(NvcPracticeMode.FREE_DIALOG)
        .currentPhase(NvcSessionPhase.IN_PROGRESS)
        .build();
    return PracticeContext.builder()
        .session(session)
        .roundCount(2)
        .build();
  }

  @Test
  @DisplayName("chat() 旧签名使用 getChatClientOrDefault 获取 ChatClient")
  void chat_oldSignature_usesChatClientOrDefault() {
    NvcAgentConfigEntity config = buildConfig();
    PracticeContext context = buildContext();

    ChatClient mockClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    when(llmProviderRegistry.getChatClientOrDefault("mimo")).thenReturn(mockClient);
    when(mockClient.prompt().options(any()).messages(anyList()).call().content())
        .thenReturn("AI 回复");

    String result = service.chat(config, context, "你好", Map.of());

    assertEquals("AI 回复", result);
    verify(llmProviderRegistry).getChatClientOrDefault("mimo");
    verify(llmProviderRegistry, never()).getPlainChatClient(any());
  }

  @Test
  @DisplayName("chat(NvcChatRequest) 无工具配置时不注入工具")
  void chatRequest_noTools() {
    NvcAgentConfigEntity config = buildConfig();
    PracticeContext context = buildContext();

    ChatClient mockClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    when(llmProviderRegistry.getChatClientOrDefault("mimo")).thenReturn(mockClient);
    when(mockClient.prompt().options(any()).messages(anyList()).call().content())
        .thenReturn("AI 回复");

    NvcChatRequest request = NvcChatRequest.builder()
        .agentConfig(config)
        .practiceContext(context)
        .userMessage("你好")
        .build();

    String result = service.chat(request);

    assertEquals("AI 回复", result);
  }

  @Test
  @DisplayName("chatPlain() 使用 getPlainChatClient 获取 ChatClient")
  void chatPlain_usesPlainChatClient() {
    NvcAgentConfigEntity config = buildConfig();

    ChatClient mockClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    when(llmProviderRegistry.getPlainChatClient("mimo")).thenReturn(mockClient);
    when(mockClient.prompt().options(any()).system(anyString()).user(anyString())
        .call().content()).thenReturn("评估结果");

    String result = service.chatPlain(config, "系统提示词", "用户提示词");

    assertEquals("评估结果", result);
    verify(llmProviderRegistry).getPlainChatClient("mimo");
    verify(llmProviderRegistry, never()).getChatClientOrDefault(any());
  }

  @Test
  @DisplayName("chatStream() 旧签名使用 getChatClientOrDefault 获取 ChatClient")
  void chatStream_oldSignature_usesChatClientOrDefault() {
    NvcAgentConfigEntity config = buildConfig();
    PracticeContext context = buildContext();

    ChatClient mockClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    when(llmProviderRegistry.getChatClientOrDefault("mimo")).thenReturn(mockClient);

    assertDoesNotThrow(() -> service.chatStream(config, context, "你好", Map.of()));
    verify(llmProviderRegistry).getChatClientOrDefault("mimo");
  }
}
```

- [ ] **Step 3: 运行测试**

Run: `cd D:/code/nvc-guide/.claude/worktrees/phase12-tool-framework && ./gradlew.bat :app:test --tests "nvc.guide.modules.nvcpractice.service.NvcAgentChatServiceTest"`
Expected: ALL TESTS PASSED

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentChatService.java \
       app/src/test/java/nvc/guide/modules/nvcpractice/service/NvcAgentChatServiceTest.java
git commit -m "feat(tool): upgrade NvcAgentChatService with NvcChatRequest + tool injection"
```

---

### Task 7: ModeRouter 更新 — 注入工具选择

**Files:**
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/router/FreeDialogRouter.java`
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/router/ScenarioRouter.java`
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/router/StructuredRouter.java`

**Interfaces:**
- Consumes: `NvcToolSceneMapping` (Task 4), `AgentDecision.availableTools` (Task 5)

- [ ] **Step 1: 更新 FreeDialogRouter.java**

将当前内容替换为：

```java
package nvc.guide.modules.nvcpractice.router;

import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.tool.NvcToolSceneMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class FreeDialogRouter implements ModeRouter {

    private final NvcToolSceneMapping toolSceneMapping;

    @Override
    public AgentDecision route(PracticeContext context) {
        return new AgentDecision(
            NvcAgentScene.DIALOGUE_GUIDE,
            "自由对话模式",
            null,
            Map.of("mode", "free_dialog"),
            toolSceneMapping.getDefaultTools(NvcAgentScene.DIALOGUE_GUIDE)
        );
    }
}
```

- [ ] **Step 2: 更新 ScenarioRouter.java**

将当前内容替换为：

```java
package nvc.guide.modules.nvcpractice.router;

import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.tool.NvcToolSceneMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ScenarioRouter implements ModeRouter {

    private final NvcToolSceneMapping toolSceneMapping;

    @Override
    public AgentDecision route(PracticeContext context) {
        NvcSessionPhase phase = context.getSession().getCurrentPhase();

        // CREATED 阶段 → 场景生成官引入场景
        if (phase == NvcSessionPhase.CREATED) {
            return new AgentDecision(
                NvcAgentScene.SCENARIO_GENERATOR,
                "场景驱动模式：生成场景",
                null,
                Map.of("mode", "scenario"),
                toolSceneMapping.getDefaultTools(NvcAgentScene.SCENARIO_GENERATOR)
            );
        }

        // 后续阶段 → 困难搭档（角色扮演）
        return new AgentDecision(
            NvcAgentScene.DIFFICULT_PARTNER,
            "场景驱动模式：困难搭档角色扮演",
            null,
            Map.of("mode", "scenario"),
            toolSceneMapping.getDefaultTools(NvcAgentScene.DIFFICULT_PARTNER)
        );
    }
}
```

- [ ] **Step 3: 更新 StructuredRouter.java**

将当前内容替换为：

```java
package nvc.guide.modules.nvcpractice.router;

import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.tool.NvcToolSceneMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class StructuredRouter implements ModeRouter {

    private final NvcToolSceneMapping toolSceneMapping;

    @Override
    public AgentDecision route(PracticeContext context) {
        String coveredElements = buildCoveredElements(context.getLastEvaluation());

        return new AgentDecision(
            NvcAgentScene.DIALOGUE_GUIDE,
            "结构化四步模式",
            null,
            Map.of("mode", "structured", "covered_elements", coveredElements),
            toolSceneMapping.getDefaultTools(NvcAgentScene.DIALOGUE_GUIDE)
        );
    }

    private String buildCoveredElements(NvcEvaluationEntity eval) {
        if (eval == null) return "";

        StringBuilder sb = new StringBuilder();
        appendIfNotEmpty(sb, "observation", eval.getObservationScore());
        appendIfNotEmpty(sb, "feeling", eval.getFeelingScore());
        appendIfNotEmpty(sb, "need", eval.getNeedScore());
        appendIfNotEmpty(sb, "request", eval.getRequestScore());
        return sb.toString();
    }

    private void appendIfNotEmpty(StringBuilder sb, String element, Integer score) {
        if (score != null && score >= 50) {
            if (!sb.isEmpty()) sb.append(",");
            sb.append(element);
        }
    }
}
```

- [ ] **Step 4: 验证编译**

Run: `cd D:/code/nvc-guide/.claude/worktrees/phase12-tool-framework && ./gradlew.bat :app:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 运行现有测试确认无破坏**

Run: `cd D:/code/nvc-guide/.claude/worktrees/phase12-tool-framework && ./gradlew.bat :app:test --tests "nvc.guide.modules.nvcpractice.*"`
Expected: ALL TESTS PASSED

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/router/FreeDialogRouter.java \
       app/src/main/java/nvc/guide/modules/nvcpractice/router/ScenarioRouter.java \
       app/src/main/java/nvc/guide/modules/nvcpractice/router/StructuredRouter.java
git commit -m "feat(tool): wire NvcToolSceneMapping into all ModeRouter implementations"
```

---

### Task 8: 简单工具（第一批）— rag_search, profile_query, dashboard_query

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/tool/RagSearchTool.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/tool/ProfileQueryTool.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/tool/DashboardQueryTool.java`

**Interfaces:**
- Consumes: `NvcTool` (Task 1), `NvcRagService`, `NvcProfileService`, `NvcDashboardService`

- [ ] **Step 1: 创建 RagSearchTool.java**

```java
package nvc.guide.modules.nvcpractice.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.knowledgebase.model.KnowledgeBaseType;
import nvc.guide.modules.nvcpractice.dto.RagResult;
import nvc.guide.modules.nvcpractice.service.NvcRagService;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class RagSearchTool implements NvcTool {

    private final NvcRagService ragService;

    @Override
    public String name() { return "rag_search"; }

    @Override
    public String description() {
        return "搜索 NVC 知识库，检索相关理论、话术模板、情绪词汇等信息。当用户询问 NVC 相关知识或需要参考资料时使用。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(RagSearchInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            RagSearchInput params = JsonParser.fromJson(input, RagSearchInput.class);
            int topK = params.topK() != null ? params.topK() : 5;

            List<RagResult> results = ragService.retrieve(
                params.query(),
                List.of(KnowledgeBaseType.NVC_THEORY,
                        KnowledgeBaseType.SPEECH_TEMPLATE,
                        KnowledgeBaseType.EMOTION_VOCAB),
                topK
            );

            String formatted = results.stream()
                .map(r -> "- " + r.text())
                .collect(Collectors.joining("\n"));

            return NvcToolResult.success(formatted.isEmpty() ? "未找到相关知识" : formatted);
        } catch (Exception e) {
            log.error("[RagSearchTool] Execution failed", e);
            return NvcToolResult.failure("知识检索失败: " + e.getMessage());
        }
    }

    record RagSearchInput(String query, Integer topK) {}
}
```

- [ ] **Step 2: 创建 ProfileQueryTool.java**

```java
package nvc.guide.modules.nvcpractice.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcprofile.service.NvcProfileService;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileQueryTool implements NvcTool {

    private final NvcProfileService profileService;

    @Override
    public String name() { return "profile_query"; }

    @Override
    public String description() {
        return "查询用户 NVC 档案，包括等级、沟通背景、性格特征、能力雷达等信息。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(ProfileQueryInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            Long userId = context.getUserId();
            if (userId == null) {
                ProfileQueryInput params = JsonParser.fromJson(input, ProfileQueryInput.class);
                userId = params.userId();
            }
            if (userId == null) {
                return NvcToolResult.failure("缺少用户ID");
            }

            String profilePrompt = profileService.getUserProfilePrompt(userId);
            return NvcToolResult.success(
                profilePrompt != null ? profilePrompt : "用户暂无档案信息");
        } catch (Exception e) {
            log.error("[ProfileQueryTool] Execution failed", e);
            return NvcToolResult.failure("档案查询失败: " + e.getMessage());
        }
    }

    record ProfileQueryInput(Long userId) {}
}
```

- [ ] **Step 3: 创建 DashboardQueryTool.java**

```java
package nvc.guide.modules.nvcpractice.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcprofile.service.NvcDashboardService;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardQueryTool implements NvcTool {

    private final NvcDashboardService dashboardService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() { return "dashboard_query"; }

    @Override
    public String description() {
        return "查询用户练习统计数据，包括总练习次数、完成次数、总分等。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(DashboardQueryInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            Long userId = context.getUserId();
            if (userId == null) {
                return NvcToolResult.failure("缺少用户ID");
            }

            Map<String, Object> stats = dashboardService.getUserStats(userId);
            String json = objectMapper.writeValueAsString(stats);
            return NvcToolResult.success(json);
        } catch (Exception e) {
            log.error("[DashboardQueryTool] Execution failed", e);
            return NvcToolResult.failure("统计数据查询失败: " + e.getMessage());
        }
    }

    record DashboardQueryInput(String period) {}
}
```

- [ ] **Step 4: 验证编译**

Run: `cd D:/code/nvc-guide/.claude/worktrees/phase12-tool-framework && ./gradlew.bat :app:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/tool/RagSearchTool.java \
       app/src/main/java/nvc/guide/modules/nvcpractice/tool/ProfileQueryTool.java \
       app/src/main/java/nvc/guide/modules/nvcpractice/tool/DashboardQueryTool.java
git commit -m "feat(tool): implement rag_search, profile_query, dashboard_query tools"
```

---

### Task 9: 简单工具（第二批）— scenario_search, practice_start

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/tool/ScenarioSearchTool.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/tool/PracticeStartTool.java`

**Interfaces:**
- Consumes: `NvcTool` (Task 1), `NvcScenarioService`, `NvcPracticeSessionService`

- [ ] **Step 1: 创建 ScenarioSearchTool.java**

```java
package nvc.guide.modules.nvcpractice.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcscenario.model.ScenarioQueryRequest;
import nvc.guide.modules.nvcscenario.service.NvcScenarioService;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScenarioSearchTool implements NvcTool {

    private final NvcScenarioService scenarioService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() { return "scenario_search"; }

    @Override
    public String description() {
        return "搜索 NVC 练习场景，可按关键词、难度筛选。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(ScenarioSearchInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            ScenarioSearchInput params = JsonParser.fromJson(input, ScenarioSearchInput.class);

            ScenarioQueryRequest query = new ScenarioQueryRequest();
            query.setKeyword(params.keyword());
            query.setDifficulty(params.difficulty());
            query.setLimit(params.limit() != null ? params.limit() : 5);

            var scenarios = scenarioService.queryScenarios(query);
            String json = objectMapper.writeValueAsString(scenarios);
            return NvcToolResult.success(json);
        } catch (Exception e) {
            log.error("[ScenarioSearchTool] Execution failed", e);
            return NvcToolResult.failure("场景搜索失败: " + e.getMessage());
        }
    }

    record ScenarioSearchInput(String keyword, String difficulty, Integer limit) {}
}
```

- [ ] **Step 2: 创建 PracticeStartTool.java**

```java
package nvc.guide.modules.nvcpractice.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.service.NvcPracticeSessionService;
import nvc.guide.modules.nvcscenario.model.CreatePracticeSessionRequest;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PracticeStartTool implements NvcTool {

    private final NvcPracticeSessionService sessionService;

    @Override
    public String name() { return "practice_start"; }

    @Override
    public String description() {
        return "启动一个新的 NVC 练习会话。需要指定场景ID和练习模式。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(PracticeStartInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            PracticeStartInput params = JsonParser.fromJson(input, PracticeStartInput.class);
            Long userId = context.getUserId();
            if (userId == null) {
                return NvcToolResult.failure("缺少用户ID");
            }

            CreatePracticeSessionRequest request = new CreatePracticeSessionRequest();
            request.setScenarioId(params.scenarioId());
            request.setPracticeMode(params.mode());

            NvcPracticeSessionEntity session = sessionService.createSession(userId, request);
            return NvcToolResult.success("练习会话已创建，sessionId=" + session.getId());
        } catch (Exception e) {
            log.error("[PracticeStartTool] Execution failed", e);
            return NvcToolResult.failure("启动练习失败: " + e.getMessage());
        }
    }

    record PracticeStartInput(Long scenarioId, String mode) {}
}
```

- [ ] **Step 3: 验证编译**

Run: `cd D:/code/nvc-guide/.claude/worktrees/phase12-tool-framework && ./gradlew.bat :app:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/tool/ScenarioSearchTool.java \
       app/src/main/java/nvc/guide/modules/nvcpractice/tool/PracticeStartTool.java
git commit -m "feat(tool): implement scenario_search and practice_start tools"
```

---

### Task 10: 中等工具 — evaluate_nvc, scenario_generate

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/tool/EvaluateNvcTool.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/tool/ScenarioGenerateTool.java`

**Interfaces:**
- Consumes: `NvcTool` (Task 1), `NvcEvaluationService`, `NvcScenarioService`

- [ ] **Step 1: 创建 EvaluateNvcTool.java**

```java
package nvc.guide.modules.nvcpractice.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.service.NvcEvaluationService;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EvaluateNvcTool implements NvcTool {

    private final NvcEvaluationService evaluationService;

    @Override
    public String name() { return "evaluate_nvc"; }

    @Override
    public String description() {
        return "评估用户的 NVC 表达质量，返回观察、感受、需求、请求四个维度的评分和反馈。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(EvaluateNvcInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            EvaluateNvcInput params = JsonParser.fromJson(input, EvaluateNvcInput.class);
            Long sessionId = context.getSessionId();
            Long userId = context.getUserId();

            if (sessionId == null || userId == null) {
                return NvcToolResult.failure("缺少会话ID或用户ID");
            }

            NvcEvaluationEntity eval = evaluationService.evaluateRealtime(
                sessionId, userId, params.expression(), params.scenario(), null);

            String result = String.format(
                "观察: %d/100, 感受: %d/100, 需求: %d/100, 请求: %d/100\n反馈: %s",
                eval.getObservationScore() != null ? eval.getObservationScore() : 0,
                eval.getFeelingScore() != null ? eval.getFeelingScore() : 0,
                eval.getNeedScore() != null ? eval.getNeedScore() : 0,
                eval.getRequestScore() != null ? eval.getRequestScore() : 0,
                eval.getSummaryFeedback() != null ? eval.getSummaryFeedback() : "暂无反馈"
            );

            return NvcToolResult.success(result);
        } catch (Exception e) {
            log.error("[EvaluateNvcTool] Execution failed", e);
            return NvcToolResult.failure("评估失败: " + e.getMessage());
        }
    }

    record EvaluateNvcInput(String expression, String scenario) {}
}
```

- [ ] **Step 2: 创建 ScenarioGenerateTool.java**

```java
package nvc.guide.modules.nvcpractice.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcscenario.model.ScenarioGenerateRequest;
import nvc.guide.modules.nvcscenario.service.NvcScenarioService;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScenarioGenerateTool implements NvcTool {

    private final NvcScenarioService scenarioService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() { return "scenario_generate"; }

    @Override
    public String description() {
        return "AI 生成一个新的 NVC 练习场景。需要指定情境、关系和关注的 NVC 要素。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(ScenarioGenerateInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            ScenarioGenerateInput params = JsonParser.fromJson(input, ScenarioGenerateInput.class);

            ScenarioGenerateRequest request = new ScenarioGenerateRequest();
            request.setSituation(params.situation());
            request.setRelationship(params.relationship());
            request.setFocusElement(params.focusElement());

            var scenario = scenarioService.generateScenario(request);
            String json = objectMapper.writeValueAsString(scenario);
            return NvcToolResult.success(json);
        } catch (Exception e) {
            log.error("[ScenarioGenerateTool] Execution failed", e);
            return NvcToolResult.failure("场景生成失败: " + e.getMessage());
        }
    }

    record ScenarioGenerateInput(String situation, String relationship, String focusElement) {}
}
```

- [ ] **Step 3: 验证编译**

Run: `cd D:/code/nvc-guide/.claude/worktrees/phase12-tool-framework && ./gradlew.bat :app:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/tool/EvaluateNvcTool.java \
       app/src/main/java/nvc/guide/modules/nvcpractice/tool/ScenarioGenerateTool.java
git commit -m "feat(tool): implement evaluate_nvc and scenario_generate tools"
```

---

### Task 11: 骨架工具 + profile_update — wiki_search, wiki_write, profile_update

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/tool/WikiSearchTool.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/tool/WikiWriteTool.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/tool/ProfileUpdateTool.java`

**Interfaces:**
- Consumes: `NvcTool` (Task 1), `NvcProfileService`

- [ ] **Step 1: 创建 WikiSearchTool.java（骨架）**

```java
package nvc.guide.modules.nvcpractice.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WikiSearchTool implements NvcTool {

    @Override
    public String name() { return "wiki_search"; }

    @Override
    public String description() { return "搜索个人 Wiki 知识库"; }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(WikiSearchInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        return NvcToolResult.failure("Wiki 功能暂未开放，敬请期待");
    }

    record WikiSearchInput(String query, Integer topK) {}
}
```

- [ ] **Step 2: 创建 WikiWriteTool.java（骨架）**

```java
package nvc.guide.modules.nvcpractice.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WikiWriteTool implements NvcTool {

    @Override
    public String name() { return "wiki_write"; }

    @Override
    public String description() { return "写入个人 Wiki 知识条目"; }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(WikiWriteInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        return NvcToolResult.failure("Wiki 功能暂未开放，敬请期待");
    }

    record WikiWriteInput(String title, String content, String category) {}
}
```

- [ ] **Step 3: 创建 ProfileUpdateTool.java**

```java
package nvc.guide.modules.nvcpractice.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcprofile.model.UserProfileUpdateRequest;
import nvc.guide.modules.nvcprofile.service.NvcProfileService;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileUpdateTool implements NvcTool {

    private final NvcProfileService profileService;

    @Override
    public String name() { return "profile_update"; }

    @Override
    public String description() {
        return "更新用户 NVC 档案字段，如沟通背景、性格特征、沟通风格等。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(ProfileUpdateInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            ProfileUpdateInput params = JsonParser.fromJson(input, ProfileUpdateInput.class);
            Long userId = context.getUserId();
            if (userId == null) {
                return NvcToolResult.failure("缺少用户ID");
            }

            UserProfileUpdateRequest request = new UserProfileUpdateRequest();
            switch (params.field()) {
                case "communicationBackground" -> request.setCommunicationBackground(params.value());
                case "personalityTraits" -> request.setPersonalityTraits(params.value());
                case "communicationStyle" -> request.setCommunicationStyle(params.value());
                case "emotionTriggers" -> request.setEmotionTriggers(params.value());
                default -> {
                    return NvcToolResult.failure("不支持的字段: " + params.field()
                        + "，支持: communicationBackground, personalityTraits, communicationStyle, emotionTriggers");
                }
            }

            profileService.updateProfile(userId, request);
            return NvcToolResult.success("档案字段 " + params.field() + " 已更新");
        } catch (Exception e) {
            log.error("[ProfileUpdateTool] Execution failed", e);
            return NvcToolResult.failure("档案更新失败: " + e.getMessage());
        }
    }

    record ProfileUpdateInput(String field, String value) {}
}
```

- [ ] **Step 4: 验证编译**

Run: `cd D:/code/nvc-guide/.claude/worktrees/phase12-tool-framework && ./gradlew.bat :app:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/tool/WikiSearchTool.java \
       app/src/main/java/nvc/guide/modules/nvcpractice/tool/WikiWriteTool.java \
       app/src/main/java/nvc/guide/modules/nvcpractice/tool/ProfileUpdateTool.java
git commit -m "feat(tool): implement wiki_search, wiki_write (skeleton), profile_update tools"
```

---

### Task 12: 集成验证 — 编译 + 全量测试

**Files:**
- No new files

- [ ] **Step 1: 全量编译**

Run: `cd D:/code/nvc-guide/.claude/worktrees/phase12-tool-framework && ./gradlew.bat :app:compileJava :app:compileTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 全量测试**

Run: `cd D:/code/nvc-guide/.claude/worktrees/phase12-tool-framework && ./gradlew.bat :app:test`
Expected: ALL TESTS PASSED

- [ ] **Step 3: 最终 Commit（如有修复）**

```bash
git add -A
git commit -m "feat(tool): Phase 1.2 Agent tool framework complete — 10 tools + registry + scene mapping"
```
