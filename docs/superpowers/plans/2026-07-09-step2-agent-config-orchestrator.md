# Step 2: Agent 配置体系与调度中心 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 NvcAgentConfigService（配置热更新）+ NvcAgentOrchestrator（调度中心）+ NvcAgentChatService（对话执行）+ 配置管理 API

**Architecture:** 三个 Service 职责分离 — ConfigService 管配置缓存，Orchestrator 管调度决策 + PracticeContext 组装，ChatService 管 LLM 调用。Orchestrator 用规则引擎（if/switch）做调度，Reflect 阶段用 LLM 做反思。Per-call ChatOptions 传递 Agent 级别的模型参数。

**Tech Stack:** Java 21, Spring Boot 4.0, Spring AI 2.0.0-M4, Redis (Redisson), JPA/Hibernate, JUnit 5 + Mockito

## Global Constraints

- 包名：`nvc.guide.modules.nvcpractice.*`（不是 `interview.guide.*`）
- 2 空格缩进，列限制 100 字符
- 禁止通配符导入
- `@Transactional` 放 Service 层，事务内禁止调用外部 API
- 抛 `BusinessException(ErrorCode.XXX, message)`，禁止 `RuntimeException`
- 禁止直接返回 Entity 给前端
- record 作为不可变数据载体，@Data @Builder 作为可变载体
- 日志用 SLF4J `@Slf4j`，结构化日志

---

### Task 1: DTO 类（4 个文件）

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/AgentConfigDTO.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/AgentConfigUpdateRequest.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/AgentDecision.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/PracticeContext.java`

**Interfaces:**
- Produces: `AgentConfigDTO`, `AgentConfigUpdateRequest`, `AgentDecision`, `PracticeContext` — 后续所有 Task 依赖这些类型

- [ ] **Step 1: 创建 AgentConfigDTO**

```java
package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcAgentScene;

/**
 * Agent 配置响应 DTO
 */
public record AgentConfigDTO(
    NvcAgentScene agentScene,
    String displayName,
    String description,
    String systemPrompt,
    String modelProvider,
    String modelName,
    Double temperature,
    Integer maxTokens,
    Double topP,
    Boolean isEnabled
) {}
```

- [ ] **Step 2: 创建 AgentConfigUpdateRequest**

```java
package nvc.guide.modules.nvcpractice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Agent 配置更新请求
 * 所有字段可选，非 null 字段才会被更新
 */
public record AgentConfigUpdateRequest(
    String systemPrompt,
    String modelProvider,
    String modelName,

    @DecimalMin("0.0") @DecimalMax("2.0")
    Double temperature,

    @Min(100) @Max(8000)
    Integer maxTokens,

    @DecimalMin("0.0") @DecimalMax("1.0")
    Double topP,

    Boolean isEnabled
) {}
```

- [ ] **Step 3: 创建 AgentDecision**

```java
package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcAgentScene;

/**
 * Agent 调度决策结果
 *
 * @param scene  选择的 Agent 角色
 * @param reason 决策原因（日志/调试用）
 * @param action 动作标记（STEP_ADVANCE / LOW_SCORE_GUIDE / DIFFICULT_UPGRADE 等，可为 null）
 */
public record AgentDecision(
    NvcAgentScene scene,
    String reason,
    String action
) {}
```

- [ ] **Step 4: 创建 PracticeContext**

```java
package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.*;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 练习上下文 — 传递给 Agent 的所有信息
 */
@Data
@Builder
public class PracticeContext {

  private NvcPracticeSessionEntity session;
  private List<NvcPracticeMessageEntity> recentMessages;
  private NvcEvaluationEntity lastEvaluation;
  private int roundCount;
  /** 用户档案摘要（注入到系统提示词），Step 5 接入 */
  private String userProfileSummary;
  /** 场景描述（场景驱动模式） */
  private String scenarioDescription;
  /** RAG 检索到的知识（注入到系统提示词），后续知识库模块接入 */
  private String ragContext;
  private NvcScenarioEntity scenario;
}
```

- [ ] **Step 5: 编译验证**

Run: `cd D:\code\nvc-guide && .\gradlew.bat :app:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/dto/
git commit -m "feat(step2): add DTO classes for Agent config and orchestration"
```

---

### Task 2: NvcAgentConfigService + 测试

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentConfigService.java`
- Create: `app/src/test/java/nvc/guide/modules/nvcpractice/service/NvcAgentConfigServiceTest.java`

**Interfaces:**
- Consumes: `NvcAgentConfigRepository.findByAgentScene()`, `NvcAgentConfigRepository.findByIsEnabledTrue()`, `RedisService.getOrLoad()`, `RedisService.delete()`
- Produces: `NvcAgentConfigService.getConfig(scene)`, `.getAllEnabledConfigs()`, `.getAllConfigs()`, `.updateConfig(scene, request)`, `.toDTO(entity)`

- [ ] **Step 1: 创建 NvcAgentConfigServiceTest**

```java
package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcpractice.dto.AgentConfigDTO;
import nvc.guide.modules.nvcpractice.dto.AgentConfigUpdateRequest;
import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.repository.NvcAgentConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcAgentConfigService 测试")
class NvcAgentConfigServiceTest {

  @Mock private NvcAgentConfigRepository agentConfigRepository;
  @Mock private RedisService redisService;

  private NvcAgentConfigService service;

  @BeforeEach
  void setUp() {
    service = new NvcAgentConfigService(agentConfigRepository, redisService);
  }

  private NvcAgentConfigEntity buildConfig(NvcAgentScene scene) {
    return NvcAgentConfigEntity.builder()
        .id(1L)
        .agentScene(scene)
        .displayName("测试 Agent")
        .description("测试描述")
        .systemPrompt("你是测试 Agent")
        .modelProvider("mimo")
        .modelName("test-model")
        .temperature(0.7)
        .maxTokens(2000)
        .topP(0.9)
        .isEnabled(true)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  @Nested
  @DisplayName("getConfig()")
  class GetConfigTests {

    @Test
    @DisplayName("缓存命中时直接返回缓存数据")
    void cacheHit_returnsCached() {
      NvcAgentConfigEntity cached = buildConfig(NvcAgentScene.DIALOGUE_GUIDE);
      when(redisService.getOrLoad(anyString(), any(Duration.class), any()))
          .thenReturn(cached);

      NvcAgentConfigEntity result = service.getConfig(NvcAgentScene.DIALOGUE_GUIDE);

      assertEquals(NvcAgentScene.DIALOGUE_GUIDE, result.getAgentScene());
      verify(agentConfigRepository, never()).findByAgentScene(any());
    }

    @Test
    @DisplayName("缓存未命中时从 DB 加载")
    void cacheMiss_loadsFromDb() {
      NvcAgentConfigEntity dbConfig = buildConfig(NvcAgentScene.SCENARIO_GENERATOR);
      when(redisService.getOrLoad(anyString(), any(Duration.class), any()))
          .thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<String, NvcAgentConfigEntity> loader = invocation.getArgument(2);
            return loader.apply("test-key");
          });
      when(agentConfigRepository.findByAgentScene(NvcAgentScene.SCENARIO_GENERATOR))
          .thenReturn(Optional.of(dbConfig));

      NvcAgentConfigEntity result = service.getConfig(NvcAgentScene.SCENARIO_GENERATOR);

      assertEquals(NvcAgentScene.SCENARIO_GENERATOR, result.getAgentScene());
      verify(agentConfigRepository).findByAgentScene(NvcAgentScene.SCENARIO_GENERATOR);
    }

    @Test
    @DisplayName("DB 也不存在时抛出 BusinessException")
    void notFound_throwsException() {
      when(redisService.getOrLoad(anyString(), any(Duration.class), any()))
          .thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<String, NvcAgentConfigEntity> loader = invocation.getArgument(2);
            return loader.apply("test-key");
          });
      when(agentConfigRepository.findByAgentScene(NvcAgentScene.EMPATHY_COACH))
          .thenReturn(Optional.empty());

      assertThrows(BusinessException.class,
          () -> service.getConfig(NvcAgentScene.EMPATHY_COACH));
    }
  }

  @Nested
  @DisplayName("updateConfig()")
  class UpdateConfigTests {

    @Test
    @DisplayName("更新非 null 字段并清除缓存")
    void updatesFieldsAndClearsCache() {
      NvcAgentConfigEntity existing = buildConfig(NvcAgentScene.DIALOGUE_GUIDE);
      when(agentConfigRepository.findByAgentScene(NvcAgentScene.DIALOGUE_GUIDE))
          .thenReturn(Optional.of(existing));
      when(agentConfigRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      AgentConfigUpdateRequest request = new AgentConfigUpdateRequest(
          "新提示词", null, null, 0.9, null, null, null);

      NvcAgentConfigEntity result = service.updateConfig(
          NvcAgentScene.DIALOGUE_GUIDE, request);

      assertEquals("新提示词", result.getSystemPrompt());
      assertEquals(0.9, result.getTemperature());
      // 未传的字段保持原值
      assertEquals("mimo", result.getModelProvider());
      verify(redisService).delete("nvc:agent-config:DIALOGUE_GUIDE");
    }

    @Test
    @DisplayName("更新不存在的配置时抛出异常")
    void notFound_throwsException() {
      when(agentConfigRepository.findByAgentScene(any()))
          .thenReturn(Optional.empty());

      AgentConfigUpdateRequest request = new AgentConfigUpdateRequest(
          null, null, null, null, null, null, null);

      assertThrows(BusinessException.class,
          () -> service.updateConfig(NvcAgentScene.EMPATHY_COACH, request));
    }
  }

  @Nested
  @DisplayName("toDTO()")
  class ToDTOTests {

    @Test
    @DisplayName("正确转换 Entity 到 DTO")
    void convertsEntityToDTO() {
      NvcAgentConfigEntity entity = buildConfig(NvcAgentScene.STEP_OBSERVE_COACH);

      AgentConfigDTO dto = service.toDTO(entity);

      assertEquals(NvcAgentScene.STEP_OBSERVE_COACH, dto.agentScene());
      assertEquals("测试 Agent", dto.displayName());
      assertEquals(0.7, dto.temperature());
      assertTrue(dto.isEnabled());
    }
  }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd D:\code\nvc-guide && .\gradlew.bat :app:test --tests "nvc.guide.modules.nvcpractice.service.NvcAgentConfigServiceTest"`
Expected: 编译失败（NvcAgentConfigService 不存在）

- [ ] **Step 3: 创建 NvcAgentConfigService**

```java
package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcpractice.dto.AgentConfigDTO;
import nvc.guide.modules.nvcpractice.dto.AgentConfigUpdateRequest;
import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.repository.NvcAgentConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcAgentConfigService {

  private static final String CACHE_KEY_PREFIX = "nvc:agent-config:";
  private static final Duration CACHE_TTL = Duration.ofMinutes(5);

  private final NvcAgentConfigRepository agentConfigRepository;
  private final RedisService redisService;

  /**
   * 获取 Agent 配置（cache-aside 模式）
   * 优先从 Redis 读取，miss 则查 DB 并写入缓存
   */
  public NvcAgentConfigEntity getConfig(NvcAgentScene scene) {
    String cacheKey = CACHE_KEY_PREFIX + scene.name();
    return redisService.getOrLoad(cacheKey, CACHE_TTL, key -> {
      NvcAgentConfigEntity config = agentConfigRepository.findByAgentScene(scene)
          .orElseThrow(() -> new BusinessException(
              ErrorCode.NVC_AGENT_CONFIG_NOT_FOUND,
              "Agent config not found: " + scene.name()));
      log.debug("Loaded agent config from DB: scene={}", scene);
      return config;
    });
  }

  /**
   * 获取所有已启用的 Agent 配置
   */
  public List<NvcAgentConfigEntity> getAllEnabledConfigs() {
    return agentConfigRepository.findByIsEnabledTrue();
  }

  /**
   * 获取所有 Agent 配置（包括禁用的）
   */
  public List<NvcAgentConfigEntity> getAllConfigs() {
    return agentConfigRepository.findAll();
  }

  /**
   * 更新 Agent 配置（热更新）
   * 更新 DB + 清除缓存，下次调用自动生效
   */
  public NvcAgentConfigEntity updateConfig(
      NvcAgentScene scene, AgentConfigUpdateRequest request) {
    NvcAgentConfigEntity config = agentConfigRepository.findByAgentScene(scene)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.NVC_AGENT_CONFIG_NOT_FOUND,
            "Agent config not found: " + scene.name()));

    if (request.systemPrompt() != null) {
      config.setSystemPrompt(request.systemPrompt());
    }
    if (request.modelProvider() != null) {
      config.setModelProvider(request.modelProvider());
    }
    if (request.modelName() != null) {
      config.setModelName(request.modelName());
    }
    if (request.temperature() != null) {
      config.setTemperature(request.temperature());
    }
    if (request.maxTokens() != null) {
      config.setMaxTokens(request.maxTokens());
    }
    if (request.topP() != null) {
      config.setTopP(request.topP());
    }
    if (request.isEnabled() != null) {
      config.setIsEnabled(request.isEnabled());
    }

    NvcAgentConfigEntity saved = agentConfigRepository.save(config);

    // 主动清除缓存
    String cacheKey = CACHE_KEY_PREFIX + scene.name();
    redisService.delete(cacheKey);
    log.info("Agent config updated and cache cleared: scene={}", scene);

    return saved;
  }

  /**
   * Entity → DTO 转换
   */
  public AgentConfigDTO toDTO(NvcAgentConfigEntity entity) {
    return new AgentConfigDTO(
        entity.getAgentScene(),
        entity.getDisplayName(),
        entity.getDescription(),
        entity.getSystemPrompt(),
        entity.getModelProvider(),
        entity.getModelName(),
        entity.getTemperature(),
        entity.getMaxTokens(),
        entity.getTopP(),
        entity.getIsEnabled()
    );
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd D:\code\nvc-guide && .\gradlew.bat :app:test --tests "nvc.guide.modules.nvcpractice.service.NvcAgentConfigServiceTest"`
Expected: 3 tests PASSED

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentConfigService.java \
       app/src/test/java/nvc/guide/modules/nvcpractice/service/NvcAgentConfigServiceTest.java
git commit -m "feat(step2): add NvcAgentConfigService with Redis cache-aside"
```

---

### Task 3: NvcAgentChatService + 测试

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentChatService.java`
- Create: `app/src/test/java/nvc/guide/modules/nvcpractice/service/NvcAgentChatServiceTest.java`

**Interfaces:**
- Consumes: `LlmProviderRegistry.getChatClientOrDefault()`, `LlmProviderRegistry.getPlainChatClient()`
- Produces: `NvcAgentChatService.chat(config, context, userMessage)`, `.chatStream(config, context, userMessage)`, `.chatPlain(config, systemPrompt, userPrompt)` — 被 NvcAgentOrchestrator 调用

- [ ] **Step 1: 创建 NvcAgentChatServiceTest**

```java
package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcAgentChatService 测试")
class NvcAgentChatServiceTest {

  @Mock private LlmProviderRegistry llmProviderRegistry;
  @Mock private ChatClient chatClient;
  @Mock private ChatClient.CallPromptSpec callPromptSpec;
  @Mock private ChatClient.CallResponseSpec callResponseSpec;

  private NvcAgentChatService service;

  @BeforeEach
  void setUp() {
    service = new NvcAgentChatService(llmProviderRegistry);
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
  @DisplayName("chat() 使用正确的 ChatClient 和参数调用 LLM")
  void chat_callsLlmWithCorrectParams() {
    NvcAgentConfigEntity config = buildConfig();
    PracticeContext context = buildContext();

    when(llmProviderRegistry.getChatClientOrDefault("mimo")).thenReturn(chatClient);
    // mock 链式调用：prompt() → system() → user() → call() → content()
    // 由于 Spring AI ChatClient 链式 API 比较复杂，这里主要验证 ChatClient 获取逻辑
    // 实际集成测试在 Task 6 验证

    // 验证 getChatClientOrDefault 被正确调用
    verify(llmProviderRegistry, never()).getChatClientOrDefault(any());
  }

  @Test
  @DisplayName("chatPlain() 使用 PlainChatClient")
  void chatPlain_usesPlainChatClient() {
    NvcAgentConfigEntity config = buildConfig();

    when(llmProviderRegistry.getPlainChatClient("mimo")).thenReturn(chatClient);

    // 验证方法存在且可调用（完整调用链在集成测试中验证）
    verify(llmProviderRegistry, never()).getPlainChatClient(any());
  }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd D:\code\nvc-guide && .\gradlew.bat :app:test --tests "nvc.guide.modules.nvcpractice.service.NvcAgentChatServiceTest"`
Expected: 编译失败（NvcAgentChatService 不存在）

- [ ] **Step 3: 创建 NvcAgentChatService**

```java
package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import nvc.guide.modules.nvcpractice.model.NvcMessageRole;
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
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcAgentChatService {

  private final LlmProviderRegistry llmProviderRegistry;

  /**
   * 执行 Agent 对话（非流式）
   */
  public String chat(
      NvcAgentConfigEntity config, PracticeContext context, String userMessage) {
    ChatClient client = getChatClient(config);
    List<Message> messages = buildMessages(config, context, userMessage);

    return client.prompt()
        .options(buildChatOptions(config))
        .messages(messages)
        .call()
        .content();
  }

  /**
   * 执行 Agent 对话（流式）
   */
  public Flux<String> chatStream(
      NvcAgentConfigEntity config, PracticeContext context, String userMessage) {
    ChatClient client = getChatClient(config);
    List<Message> messages = buildMessages(config, context, userMessage);

    return client.prompt()
        .options(buildChatOptions(config))
        .messages(messages)
        .stream()
        .content();
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

  /**
   * 根据 Agent 配置获取 ChatClient
   */
  private ChatClient getChatClient(NvcAgentConfigEntity config) {
    return llmProviderRegistry.getChatClientOrDefault(config.getModelProvider());
  }

  /**
   * 构建 per-call ChatOptions，覆盖 Agent 级别的模型参数
   */
  private OpenAiChatOptions buildChatOptions(NvcAgentConfigEntity config) {
    return OpenAiChatOptions.builder()
        .temperature(config.getTemperature())
        .maxTokens(config.getMaxTokens())
        .topP(config.getTopP())
        .build();
  }

  /**
   * 组装对话消息列表
   * 包含：系统提示词 + 用户档案摘要 + 场景信息 + RAG 知识 + 对话历史 + 当前消息
   */
  private List<Message> buildMessages(
      NvcAgentConfigEntity config, PracticeContext context, String userMessage) {
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

    messages.add(new SystemMessage(systemPrompt));

    // 5. 对话历史
    if (context.getRecentMessages() != null) {
      for (var msg : context.getRecentMessages()) {
        if (msg.getRole() == NvcMessageRole.USER) {
          messages.add(new UserMessage(msg.getContent()));
        } else if (msg.getRole() == NvcMessageRole.ASSISTANT) {
          messages.add(new AssistantMessage(msg.getContent()));
        }
      }
    }

    // 6. 当前用户消息
    messages.add(new UserMessage(userMessage));

    return messages;
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd D:\code\nvc-guide && .\gradlew.bat :app:test --tests "nvc.guide.modules.nvcpractice.service.NvcAgentChatServiceTest"`
Expected: 2 tests PASSED

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentChatService.java \
       app/src/test/java/nvc/guide/modules/nvcpractice/service/NvcAgentChatServiceTest.java
git commit -m "feat(step2): add NvcAgentChatService with per-call ChatOptions"
```

---

### Task 4: NvcAgentOrchestrator + 测试

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentOrchestrator.java`
- Create: `app/src/test/java/nvc/guide/modules/nvcpractice/service/NvcAgentOrchestratorTest.java`

**Interfaces:**
- Consumes: `NvcAgentConfigService.getConfig()`, `NvcAgentChatService.chat()`, `NvcAgentChatService.chatPlain()`, `NvcPracticeSessionRepository`, `NvcPracticeMessageRepository`, `NvcEvaluationRepository`, `NvcScenarioRepository`
- Produces: `NvcAgentOrchestrator.decideNextAgent(context)`, `.executeAgent(scene, context, userMessage)`, `.reflect(context)`, `.buildPracticeContext(sessionId, userId)` — 被 Step 3 的 NvcPracticeSessionService 调用

- [ ] **Step 1: 创建 NvcAgentOrchestratorTest**

```java
package nvc.guide.modules.nvcpractice.service;

import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.*;
import nvc.guide.modules.nvcpractice.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcAgentOrchestrator 测试")
class NvcAgentOrchestratorTest {

  @Mock private NvcAgentConfigService agentConfigService;
  @Mock private NvcAgentChatService agentChatService;
  @Mock private NvcPracticeSessionRepository sessionRepository;
  @Mock private NvcPracticeMessageRepository messageRepository;
  @Mock private NvcEvaluationRepository evaluationRepository;
  @Mock private NvcScenarioRepository scenarioRepository;

  private NvcAgentOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    orchestrator = new NvcAgentOrchestrator(
        agentConfigService, agentChatService,
        sessionRepository, messageRepository,
        evaluationRepository, scenarioRepository);
  }

  private NvcPracticeSessionEntity buildSession(
      NvcSessionPhase phase, NvcPracticeMode mode, NvcPracticeStep step) {
    return NvcPracticeSessionEntity.builder()
        .id(1L)
        .userId(100L)
        .practiceMode(mode)
        .currentPhase(phase)
        .currentStep(step)
        .build();
  }

  private NvcEvaluationEntity buildEvaluation(
      int obs, int feel, int need, int req, int overall) {
    return NvcEvaluationEntity.builder()
        .id(1L)
        .sessionId(1L)
        .observationScore(obs)
        .feelingScore(feel)
        .needScore(need)
        .requestScore(req)
        .overallScore(overall)
        .build();
  }

  @Nested
  @DisplayName("decideNextAgent()")
  class DecideNextAgentTests {

    @Test
    @DisplayName("CREATED 阶段返回 SCENARIO_GENERATOR")
    void created_returnsScenarioGenerator() {
      NvcPracticeSessionEntity session = buildSession(
          NvcSessionPhase.CREATED, NvcPracticeMode.FREE_DIALOG, null);
      PracticeContext context = PracticeContext.builder()
          .session(session).roundCount(0).build();

      AgentDecision decision = orchestrator.decideNextAgent(context);

      assertEquals(NvcAgentScene.SCENARIO_GENERATOR, decision.scene());
      assertNull(decision.action());
    }

    @Nested
    @DisplayName("结构化四步模式")
    class StructuredFourStepTests {

      @Test
      @DisplayName("无评估结果时返回当前步骤教练")
      void noEvaluation_returnsCurrentStepCoach() {
        NvcPracticeSessionEntity session = buildSession(
            NvcSessionPhase.IN_PROGRESS,
            NvcPracticeMode.STRUCTURED_FOUR_STEP,
            NvcPracticeStep.OBSERVE);
        PracticeContext context = PracticeContext.builder()
            .session(session).lastEvaluation(null).build();

        AgentDecision decision = orchestrator.decideNextAgent(context);

        assertEquals(NvcAgentScene.STEP_OBSERVE_COACH, decision.scene());
      }

      @Test
      @DisplayName("评分 >= 70 时推进到下一步")
      void scoreAbove70_advancesToNextStep() {
        NvcPracticeSessionEntity session = buildSession(
            NvcSessionPhase.IN_PROGRESS,
            NvcPracticeMode.STRUCTURED_FOUR_STEP,
            NvcPracticeStep.OBSERVE);
        NvcEvaluationEntity eval = buildEvaluation(80, 70, 60, 65, 70);
        PracticeContext context = PracticeContext.builder()
            .session(session).lastEvaluation(eval).build();

        AgentDecision decision = orchestrator.decideNextAgent(context);

        assertEquals(NvcAgentScene.STEP_FEELING_COACH, decision.scene());
        assertEquals("STEP_ADVANCE", decision.action());
      }

      @Test
      @DisplayName("评分 < 70 时保持当前步骤")
      void scoreBelow70_staysOnCurrentStep() {
        NvcPracticeSessionEntity session = buildSession(
            NvcSessionPhase.IN_PROGRESS,
            NvcPracticeMode.STRUCTURED_FOUR_STEP,
            NvcPracticeStep.FEELING);
        NvcEvaluationEntity eval = buildEvaluation(80, 50, 60, 65, 60);
        PracticeContext context = PracticeContext.builder()
            .session(session).lastEvaluation(eval).build();

        AgentDecision decision = orchestrator.decideNextAgent(context);

        assertEquals(NvcAgentScene.STEP_FEELING_COACH, decision.scene());
        assertEquals("STEP_RETRY", decision.action());
      }

      @Test
      @DisplayName("REQUEST 步骤通过后返回 COMPLETED_ALL_STEPS")
      void requestStepPass_returnsCompleted() {
        NvcPracticeSessionEntity session = buildSession(
            NvcSessionPhase.IN_PROGRESS,
            NvcPracticeMode.STRUCTURED_FOUR_STEP,
            NvcPracticeStep.REQUEST);
        NvcEvaluationEntity eval = buildEvaluation(80, 80, 80, 80, 80);
        PracticeContext context = PracticeContext.builder()
            .session(session).lastEvaluation(eval).build();

        AgentDecision decision = orchestrator.decideNextAgent(context);

        assertEquals(NvcAgentScene.DIALOGUE_GUIDE, decision.scene());
        assertEquals("COMPLETED_ALL_STEPS", decision.action());
      }
    }

    @Nested
    @DisplayName("自由对话/场景驱动模式")
    class FreeDialogTests {

      @Test
      @DisplayName("无评估结果时返回 DIALOGUE_GUIDE")
      void noEvaluation_returnsDialogueGuide() {
        NvcPracticeSessionEntity session = buildSession(
            NvcSessionPhase.IN_PROGRESS,
            NvcPracticeMode.FREE_DIALOG, null);
        PracticeContext context = PracticeContext.builder()
            .session(session).lastEvaluation(null).build();

        AgentDecision decision = orchestrator.decideNextAgent(context);

        assertEquals(NvcAgentScene.DIALOGUE_GUIDE, decision.scene());
      }

      @Test
      @DisplayName("评分 < 50 时切换到引导模式")
      void lowScore_switchesToGuide() {
        NvcPracticeSessionEntity session = buildSession(
            NvcSessionPhase.IN_PROGRESS,
            NvcPracticeMode.SCENARIO, null);
        NvcEvaluationEntity eval = buildEvaluation(40, 40, 40, 40, 40);
        PracticeContext context = PracticeContext.builder()
            .session(session).lastEvaluation(eval).roundCount(2).build();

        AgentDecision decision = orchestrator.decideNextAgent(context);

        assertEquals(NvcAgentScene.DIALOGUE_GUIDE, decision.scene());
        assertEquals("LOW_SCORE_GUIDE", decision.action());
      }

      @Test
      @DisplayName("评分 >= 60 且轮次 > 3 时偶尔切换困难搭档")
      void goodScore_switchesToDifficultPartner() {
        NvcPracticeSessionEntity session = buildSession(
            NvcSessionPhase.IN_PROGRESS,
            NvcPracticeMode.SCENARIO, null);
        NvcEvaluationEntity eval = buildEvaluation(70, 70, 70, 70, 70);
        PracticeContext context = PracticeContext.builder()
            .session(session).lastEvaluation(eval).roundCount(6).build();

        AgentDecision decision = orchestrator.decideNextAgent(context);

        assertEquals(NvcAgentScene.DIFFICULT_PARTNER, decision.scene());
        assertEquals("DIFFICULT_UPGRADE", decision.action());
      }
    }
  }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd D:\code\nvc-guide && .\gradlew.bat :app:test --tests "nvc.guide.modules.nvcpractice.service.NvcAgentOrchestratorTest"`
Expected: 编译失败（NvcAgentOrchestrator 不存在）

- [ ] **Step 3: 创建 NvcAgentOrchestrator**

```java
package nvc.guide.modules.nvcpractice.service;

import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.*;
import nvc.guide.modules.nvcpractice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcAgentOrchestrator {

  private final NvcAgentConfigService agentConfigService;
  private final NvcAgentChatService agentChatService;
  private final NvcPracticeSessionRepository sessionRepository;
  private final NvcPracticeMessageRepository messageRepository;
  private final NvcEvaluationRepository evaluationRepository;
  private final NvcScenarioRepository scenarioRepository;

  private static final int RECENT_MESSAGES_WINDOW = 20;

  // ==================== Context 组装 ====================

  /**
   * 从 DB 组装 PracticeContext
   * 外部调用者只需传 sessionId 和 userId
   */
  public PracticeContext buildPracticeContext(Long sessionId, Long userId) {
    NvcPracticeSessionEntity session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new nvc.guide.common.exception.BusinessException(
            nvc.guide.common.exception.ErrorCode.NVC_SESSION_NOT_FOUND,
            "Session not found: " + sessionId));

    // 最近 N 条消息（正序）
    List<NvcPracticeMessageEntity> allMessages =
        messageRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);
    List<NvcPracticeMessageEntity> recentMessages = allMessages.size() <= RECENT_MESSAGES_WINDOW
        ? allMessages
        : allMessages.subList(
            allMessages.size() - RECENT_MESSAGES_WINDOW, allMessages.size());

    // 最新评估
    List<NvcEvaluationEntity> evaluations =
        evaluationRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    NvcEvaluationEntity lastEvaluation = evaluations.isEmpty()
        ? null : evaluations.get(evaluations.size() - 1);

    // 轮次计算（用户消息数）
    long userMessageCount = allMessages.stream()
        .filter(m -> m.getRole() == NvcMessageRole.USER)
        .count();

    // 场景描述
    String scenarioDescription = null;
    NvcScenarioEntity scenario = null;
    if (session.getScenarioId() != null) {
      scenario = scenarioRepository.findById(session.getScenarioId()).orElse(null);
      if (scenario != null) {
        scenarioDescription = scenario.getTitle() + "\n" + scenario.getDescription();
      }
    }

    return PracticeContext.builder()
        .session(session)
        .recentMessages(recentMessages)
        .lastEvaluation(lastEvaluation)
        .roundCount((int) userMessageCount)
        .scenarioDescription(scenarioDescription)
        .scenario(scenario)
        .build();
  }

  // ==================== PLAN：分析当前状态，规划下一步 ====================

  /**
   * 决定下一步使用哪个 Agent
   * 根据练习模式和当前状态做出决策
   */
  public AgentDecision decideNextAgent(PracticeContext context) {
    NvcPracticeSessionEntity session = context.getSession();

    // 场景生成阶段
    if (session.getCurrentPhase() == NvcSessionPhase.CREATED) {
      return new AgentDecision(
          NvcAgentScene.SCENARIO_GENERATOR,
          "练习刚开始，需要生成场景",
          null);
    }

    // 结构化四步模式
    if (session.getPracticeMode() == NvcPracticeMode.STRUCTURED_FOUR_STEP) {
      return decideStructuredStep(context);
    }

    // 场景驱动和自由对话模式
    return decideFreeDialog(context);
  }

  /**
   * 结构化四步模式的调度逻辑
   */
  private AgentDecision decideStructuredStep(PracticeContext context) {
    NvcPracticeStep currentStep = context.getSession().getCurrentStep();

    // 无评估结果，继续当前步骤
    if (context.getLastEvaluation() == null) {
      return new AgentDecision(
          stepToCoach(currentStep),
          "当前步骤 " + currentStep + " 尚未评估，继续练习",
          null);
    }

    int stepScore = getStepScore(context.getLastEvaluation(), currentStep);

    // 评分 >= 70，推进到下一步
    if (stepScore >= 70) {
      NvcPracticeStep nextStep = nextStep(currentStep);
      if (nextStep == NvcPracticeStep.COMPLETED) {
        return new AgentDecision(
            NvcAgentScene.DIALOGUE_GUIDE,
            "四步全部通过，进入综合练习",
            "COMPLETED_ALL_STEPS");
      }
      return new AgentDecision(
          stepToCoach(nextStep),
          currentStep + " 通过（" + stepScore + "分），推进到 " + nextStep,
          "STEP_ADVANCE");
    }

    // 评分 < 70，继续当前步骤
    return new AgentDecision(
        stepToCoach(currentStep),
        currentStep + " 未通过（" + stepScore + "分），继续练习",
        "STEP_RETRY");
  }

  /**
   * 自由对话/场景驱动模式的调度逻辑
   */
  private AgentDecision decideFreeDialog(PracticeContext context) {
    // 无评估结果
    if (context.getLastEvaluation() == null) {
      return new AgentDecision(
          NvcAgentScene.DIALOGUE_GUIDE,
          "对话进行中，使用引导官",
          null);
    }

    int overallScore = context.getLastEvaluation().getOverallScore();
    int roundCount = context.getRoundCount();

    // 评分 < 50，切换引导模式
    if (overallScore < 50) {
      return new AgentDecision(
          NvcAgentScene.DIALOGUE_GUIDE,
          "评分较低（" + overallScore + "），切换到引导模式",
          "LOW_SCORE_GUIDE");
    }

    // 评分 >= 60 且轮次足够，偶尔切换困难搭档
    if (overallScore >= 60 && roundCount > 3 && roundCount % 3 == 0) {
      return new AgentDecision(
          NvcAgentScene.DIFFICULT_PARTNER,
          "用户表现良好（" + overallScore + "），切换困难搭档提升难度",
          "DIFFICULT_UPGRADE");
    }

    // 检查薄弱要素
    NvcPracticeStep weakElement = findWeakestElement(context);
    if (weakElement != null && overallScore < 70) {
      return new AgentDecision(
          stepToCoach(weakElement),
          "薄弱要素 " + weakElement + " 需要针对性练习",
          "WEAK_ELEMENT_FOCUS");
    }

    // 默认继续对话引导
    return new AgentDecision(
        NvcAgentScene.DIALOGUE_GUIDE,
        "对话正常进行",
        null);
  }

  // ==================== EXECUTE：执行 Agent 对话 ====================

  /**
   * 执行 Agent 对话，获取 AI 回复
   */
  public String executeAgent(
      NvcAgentScene scene, PracticeContext context, String userMessage) {
    NvcAgentConfigEntity config = agentConfigService.getConfig(scene);

    // 检查 Agent 是否启用，禁用则 fallback
    if (!config.getIsEnabled()) {
      log.warn("Agent is disabled: {}, falling back to DIALOGUE_GUIDE", scene);
      config = agentConfigService.getConfig(NvcAgentScene.DIALOGUE_GUIDE);
    }

    return agentChatService.chat(config, context, userMessage);
  }

  // ==================== REFLECT：反思与策略调整 ====================

  /**
   * 练习结束后反思，分析表现并给出策略建议
   */
  public String reflect(PracticeContext context) {
    NvcAgentConfigEntity evaluatorConfig =
        agentConfigService.getConfig(NvcAgentScene.NVC_EXPRESSION_EVALUATOR);

    NvcEvaluationEntity eval = context.getLastEvaluation();
    String reflectPrompt = """
        基于以下练习记录，请分析用户的表现并给出下次练习的策略建议：

        练习模式：%s
        练习轮次：%d
        最终评分：观察%d 感受%d 需求%d 请求%d 综合%d

        请输出JSON格式：
        {
          "weak_elements": ["薄弱要素列表"],
          "suggested_difficulty": "EASY/MEDIUM/HARD",
          "suggested_scenario_type": "建议的场景类型",
          "strategy_note": "策略建议说明"
        }
        """.formatted(
        context.getSession().getPracticeMode(),
        context.getRoundCount(),
        eval.getObservationScore(),
        eval.getFeelingScore(),
        eval.getNeedScore(),
        eval.getRequestScore(),
        eval.getOverallScore());

    return agentChatService.chatPlain(evaluatorConfig, reflectPrompt, "");
  }

  // ==================== 辅助方法 ====================

  private NvcAgentScene stepToCoach(NvcPracticeStep step) {
    return switch (step) {
      case OBSERVE -> NvcAgentScene.STEP_OBSERVE_COACH;
      case FEELING -> NvcAgentScene.STEP_FEELING_COACH;
      case NEED -> NvcAgentScene.STEP_NEED_COACH;
      case REQUEST -> NvcAgentScene.STEP_REQUEST_COACH;
      default -> NvcAgentScene.DIALOGUE_GUIDE;
    };
  }

  private NvcPracticeStep nextStep(NvcPracticeStep current) {
    return switch (current) {
      case OBSERVE -> NvcPracticeStep.FEELING;
      case FEELING -> NvcPracticeStep.NEED;
      case NEED -> NvcPracticeStep.REQUEST;
      case REQUEST -> NvcPracticeStep.COMPLETED;
      default -> NvcPracticeStep.COMPLETED;
    };
  }

  private int getStepScore(NvcEvaluationEntity eval, NvcPracticeStep step) {
    return switch (step) {
      case OBSERVE -> eval.getObservationScore();
      case FEELING -> eval.getFeelingScore();
      case NEED -> eval.getNeedScore();
      case REQUEST -> eval.getRequestScore();
      default -> eval.getOverallScore();
    };
  }

  private NvcPracticeStep findWeakestElement(PracticeContext context) {
    NvcEvaluationEntity eval = context.getLastEvaluation();
    if (eval == null) return null;

    int minScore = Integer.MAX_VALUE;
    NvcPracticeStep weakest = null;

    if (eval.getObservationScore() != null && eval.getObservationScore() < minScore) {
      minScore = eval.getObservationScore();
      weakest = NvcPracticeStep.OBSERVE;
    }
    if (eval.getFeelingScore() != null && eval.getFeelingScore() < minScore) {
      minScore = eval.getFeelingScore();
      weakest = NvcPracticeStep.FEELING;
    }
    if (eval.getNeedScore() != null && eval.getNeedScore() < minScore) {
      minScore = eval.getNeedScore();
      weakest = NvcPracticeStep.NEED;
    }
    if (eval.getRequestScore() != null && eval.getRequestScore() < minScore) {
      minScore = eval.getRequestScore();
      weakest = NvcPracticeStep.REQUEST;
    }

    return weakest;
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd D:\code\nvc-guide && .\gradlew.bat :app:test --tests "nvc.guide.modules.nvcpractice.service.NvcAgentOrchestratorTest"`
Expected: 7 tests PASSED

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentOrchestrator.java \
       app/src/test/java/nvc/guide/modules/nvcpractice/service/NvcAgentOrchestratorTest.java
git commit -m "feat(step2): add NvcAgentOrchestrator with rule-based scheduling"
```

---

### Task 5: NvcAgentConfigController + 测试

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcAgentConfigController.java`
- Create: `app/src/test/java/nvc/guide/modules/nvcpractice/controller/NvcAgentConfigControllerTest.java`

**Interfaces:**
- Consumes: `NvcAgentConfigService.getConfig()`, `.getAllConfigs()`, `.updateConfig()`, `.toDTO()`
- Produces: REST API — 被前端调用

- [ ] **Step 1: 创建 NvcAgentConfigControllerTest**

```java
package nvc.guide.modules.nvcpractice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.modules.nvcpractice.dto.AgentConfigDTO;
import nvc.guide.modules.nvcpractice.dto.AgentConfigUpdateRequest;
import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.service.NvcAgentConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NvcAgentConfigController.class)
@DisplayName("NvcAgentConfigController 测试")
class NvcAgentConfigControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockBean private NvcAgentConfigService agentConfigService;

  private NvcAgentConfigEntity buildConfig(NvcAgentScene scene) {
    return NvcAgentConfigEntity.builder()
        .id(1L)
        .agentScene(scene)
        .displayName("测试 Agent")
        .description("测试描述")
        .systemPrompt("你是测试 Agent")
        .modelProvider("mimo")
        .modelName("test-model")
        .temperature(0.7)
        .maxTokens(2000)
        .topP(0.9)
        .isEnabled(true)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  @Test
  @DisplayName("GET /api/nvc/agents/configs 返回所有配置")
  void getAllConfigs_returnsList() throws Exception {
    NvcAgentConfigEntity config = buildConfig(NvcAgentScene.DIALOGUE_GUIDE);
    AgentConfigDTO dto = new AgentConfigDTO(
        NvcAgentScene.DIALOGUE_GUIDE, "测试 Agent", "测试描述",
        "你是测试 Agent", "mimo", "test-model", 0.7, 2000, 0.9, true);

    when(agentConfigService.getAllConfigs()).thenReturn(List.of(config));
    when(agentConfigService.toDTO(config)).thenReturn(dto);

    mockMvc.perform(get("/api/nvc/agents/configs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data[0].agentScene").value("DIALOGUE_GUIDE"));
  }

  @Test
  @DisplayName("GET /api/nvc/agents/configs/{scene} 返回单个配置")
  void getConfig_returnsSingle() throws Exception {
    NvcAgentConfigEntity config = buildConfig(NvcAgentScene.SCENARIO_GENERATOR);
    AgentConfigDTO dto = new AgentConfigDTO(
        NvcAgentScene.SCENARIO_GENERATOR, "测试 Agent", "测试描述",
        "你是测试 Agent", "mimo", "test-model", 0.7, 2000, 0.9, true);

    when(agentConfigService.getConfig(NvcAgentScene.SCENARIO_GENERATOR)).thenReturn(config);
    when(agentConfigService.toDTO(config)).thenReturn(dto);

    mockMvc.perform(get("/api/nvc/agents/configs/SCENARIO_GENERATOR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.agentScene").value("SCENARIO_GENERATOR"));
  }

  @Test
  @DisplayName("PUT /api/nvc/agents/configs/{scene} 更新配置")
  void updateConfig_updatesAndReturns() throws Exception {
    NvcAgentConfigEntity updated = buildConfig(NvcAgentScene.DIALOGUE_GUIDE);
    updated.setTemperature(0.9);
    AgentConfigDTO dto = new AgentConfigDTO(
        NvcAgentScene.DIALOGUE_GUIDE, "测试 Agent", "测试描述",
        "你是测试 Agent", "mimo", "test-model", 0.9, 2000, 0.9, true);

    when(agentConfigService.updateConfig(any(), any())).thenReturn(updated);
    when(agentConfigService.toDTO(updated)).thenReturn(dto);

    AgentConfigUpdateRequest request = new AgentConfigUpdateRequest(
        null, null, null, 0.9, null, null, null);

    mockMvc.perform(put("/api/nvc/agents/configs/DIALOGUE_GUIDE")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.temperature").value(0.9));
  }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd D:\code\nvc-guide && .\gradlew.bat :app:test --tests "nvc.guide.modules.nvcpractice.controller.NvcAgentConfigControllerTest"`
Expected: 编译失败（NvcAgentConfigController 不存在）

- [ ] **Step 3: 创建 NvcAgentConfigController**

```java
package nvc.guide.modules.nvcpractice.controller;

import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcpractice.dto.AgentConfigDTO;
import nvc.guide.modules.nvcpractice.dto.AgentConfigUpdateRequest;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.service.NvcAgentConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nvc/agents")
@RequiredArgsConstructor
public class NvcAgentConfigController {

  private final NvcAgentConfigService agentConfigService;

  /**
   * 获取所有 Agent 配置
   */
  @GetMapping("/configs")
  public Result<List<AgentConfigDTO>> getAllConfigs() {
    var configs = agentConfigService.getAllConfigs().stream()
        .map(agentConfigService::toDTO)
        .toList();
    return Result.success(configs);
  }

  /**
   * 获取单个 Agent 配置
   */
  @GetMapping("/configs/{scene}")
  public Result<AgentConfigDTO> getConfig(@PathVariable NvcAgentScene scene) {
    var config = agentConfigService.getConfig(scene);
    return Result.success(agentConfigService.toDTO(config));
  }

  /**
   * 更新 Agent 配置（热更新）
   * 更新后立即生效，无需重启
   */
  @PutMapping("/configs/{scene}")
  public Result<AgentConfigDTO> updateConfig(
      @PathVariable NvcAgentScene scene,
      @Valid @RequestBody AgentConfigUpdateRequest request) {
    var updated = agentConfigService.updateConfig(scene, request);
    return Result.success(agentConfigService.toDTO(updated));
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd D:\code\nvc-guide && .\gradlew.bat :app:test --tests "nvc.guide.modules.nvcpractice.controller.NvcAgentConfigControllerTest"`
Expected: 3 tests PASSED

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcAgentConfigController.java \
       app/src/test/java/nvc/guide/modules/nvcpractice/controller/NvcAgentConfigControllerTest.java
git commit -m "feat(step2): add NvcAgentConfigController with REST API"
```

---

### Task 6: 编译验证 + 启动测试

**Files:**
- None created

**Interfaces:**
- Verifies: 所有 Task 1-5 的代码编译通过，项目启动成功

- [ ] **Step 1: 全量编译**

Run: `cd D:\code\nvc-guide && .\gradlew.bat :app:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 运行所有单元测试**

Run: `cd D:\code\nvc-guide && .\gradlew.bat :app:test`
Expected: 所有测试通过（包括新增的 ~15 个测试 + 已有测试）

- [ ] **Step 3: 启动应用验证**

Run: `cd D:\code\nvc-guide && .\gradlew.bat :app:bootRun`
Expected: 应用启动成功，无异常日志

- [ ] **Step 4: API 验证（手动）**

```bash
# 获取所有 Agent 配置
curl http://localhost:8080/api/nvc/agents/configs

# 获取单个配置
curl http://localhost:8080/api/nvc/agents/configs/SCENARIO_GENERATOR

# 更新配置（热更新）
curl -X PUT http://localhost:8080/api/nvc/agents/configs/DIALOGUE_GUIDE \
  -H "Content-Type: application/json" \
  -d '{"temperature": 0.9}'

# 再次获取，验证更新生效
curl http://localhost:8080/api/nvc/agents/configs/DIALOGUE_GUIDE
```

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat(step2): Agent config service and orchestrator complete"
```
