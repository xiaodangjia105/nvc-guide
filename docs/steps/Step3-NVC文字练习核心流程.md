# Step 3：NVC 文字练习核心流程

> 目标：实现文字对话练习的完整链路 — 创建会话 → 多轮对话 → SSE 流式输出 → 结束会话
>
> 预计耗时：3-5 天
>
> 前置条件：Step 2 已完成，Agent 调度中心就绪（NvcAgentOrchestrator、NvcAgentChatService 已实现）
>
> 技术亮点：SSE 流式对话、Agent 自动调度、多轮上下文管理

---

## 3.1 设计决策

### 决策 1：复用 NvcAgentOrchestrator 的上下文构建

`NvcAgentOrchestrator.buildPracticeContext(sessionId, userId)` 已实现完整的上下文加载（session、messages、evaluation、scenario）。**不创建 NvcContextBuilder**，对话服务直接调用 orchestrator。

### 决策 2：SSE 流式元数据传递

首包发送 JSON 元数据事件，后续纯文本 token，结束时发送 done 事件：

```
event: metadata
data: {"agentScene":"DIALOGUE_GUIDE","decisionReason":"...","currentStep":"OBSERVE"}

event: message
data: 你

event: message
data: 好

event: done
data: {"length":3}
```

### 决策 3：Step 3 不实现评估

对话流程先跑通，评估在后续步骤实现。`lastEvaluation` 先返回 null，不影响 Agent 调度。

### 决策 4：场景驱动模式的场景选择

- `scenarioId` 有值 → 从 DB 加载指定场景（13 条种子数据已就绪）
- `scenarioId` 为 null → 从 DB 中按难度随机分配一个场景
- AI 生成场景（SCENARIO_GENERATOR）作为后续增强，Step 3 不实现

### 决策 5：Agent prompt 存储方式

Agent 对话的 system_prompt 已存储在 DB `nvc_agent_config` 表中（种子数据 `data-nvc-agent-config.sql` 包含 11 个 Agent 的完整 prompt）。`.st` 文件仅用于场景生成等辅助 prompt。

---

## 3.2 本步要创建的文件清单

```
app/src/main/java/interview/guide/modules/nvcpractice/
├── service/
│   ├── NvcPracticeSessionService.java     ← 会话管理（创建/查询/结束/随机场景）
│   └── NvcPracticeDialogueService.java    ← 对话核心逻辑（调度 Agent + 保存消息 + SSE）
├── dto/
│   ├── CreatePracticeSessionRequest.java  ← 创建会话请求
│   ├── PracticeSessionResponse.java       ← 会话响应
│   ├── SendMessageRequest.java            ← 发送消息请求
│   ├── DialogueResponse.java              ← 对话响应（含 Agent 决策信息）
│   ├── MessageResponse.java               ← 消息响应 DTO（替代直接返回 Entity）
│   └── StreamMetadata.java                ← SSE 首包元数据
└── controller/
    └── NvcPracticeController.java         ← 练习 API（创建/对话/结束）
```

app/src/main/resources/prompts/ 下新增：
```
nvc-scenario-generate-system.st            ← 场景生成提示词（AI 生成场景的后备方案）
```

### 不需要创建的文件

| 文件 | 原因 |
|------|------|
| NvcContextBuilder | orchestrator.buildPracticeContext() 已有完整实现 |
| nvc-dialogue-guide-system.st | prompt 已在 DB 的 nvc_agent_config 表中 |
| nvc-difficult-partner-system.st | prompt 已在 DB 的 nvc_agent_config 表中 |

### 需要修改的已有文件

| 文件 | 修改内容 |
|------|---------|
| NvcAgentOrchestrator | 补充 `executeAgentStream()` 方法（流式执行） |
| NvcPracticeMessageRepository | 补充按 sessionId 分页查询方法 |
| NvcScenarioRepository | 补充 `findByDifficulty()` 随机查询方法 |

---

## 3.3 NvcPracticeSessionService — 会话管理

路径：`app/src/main/java/interview/guide/modules/nvcpractice/service/NvcPracticeSessionService.java`

### 功能

- 创建练习会话（根据模式初始化不同状态）
- 查询会话列表（按用户、按状态）
- 查询单个会话详情
- 结束/暂停会话
- 会话状态管理
- 随机场景分配（scenarioId 为 null 时）

### 实现要点

```java
package interview.guide.modules.nvcpractice.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.nvcpractice.dto.CreatePracticeSessionRequest;
import interview.guide.modules.nvcpractice.dto.PracticeSessionResponse;
import interview.guide.modules.nvcpractice.model.*;
import interview.guide.modules.nvcpractice.repository.NvcPracticeSessionRepository;
import interview.guide.modules.nvcscenario.model.NvcScenarioEntity;
import interview.guide.modules.nvcscenario.repository.NvcScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcPracticeSessionService {

    private final NvcPracticeSessionRepository sessionRepository;
    private final NvcScenarioRepository scenarioRepository;
    private final RedisService redisService;

    private static final String CACHE_KEY_PREFIX = "nvc:practice:session:";
    private static final long CACHE_TTL_HOURS = 24;

    /**
     * 创建练习会话
     */
    public NvcPracticeSessionEntity createSession(Long userId, CreatePracticeSessionRequest request) {
        // 场景驱动模式：如果未指定场景，按难度随机分配
        Long scenarioId = request.scenarioId();
        if (request.practiceMode() == NvcPracticeMode.SCENARIO && scenarioId == null) {
            scenarioId = pickRandomScenario(request.difficulty());
        }

        NvcPracticeSessionEntity session = NvcPracticeSessionEntity.builder()
            .userId(userId)
            .practiceMode(request.practiceMode())
            .scenarioId(scenarioId)
            .difficulty(request.difficulty() != null ? request.difficulty() : NvcDifficulty.MEDIUM)
            .currentPhase(NvcSessionPhase.CREATED)
            // 结构化四步模式初始步骤为 OBSERVE
            .currentStep(request.practiceMode() == NvcPracticeMode.STRUCTURED_FOUR_STEP
                ? NvcPracticeStep.OBSERVE : null)
            .build();

        NvcPracticeSessionEntity saved = sessionRepository.save(session);
        log.info("NVC practice session created: sessionId={}, mode={}, userId={}",
            saved.getId(), request.practiceMode(), userId);

        cacheSession(saved);
        return saved;
    }

    /**
     * 获取会话（带缓存）
     */
    public NvcPracticeSessionEntity getSession(Long sessionId) {
        String cacheKey = CACHE_KEY_PREFIX + sessionId;

        // 尝试从缓存读取
        String cached = redisService.get(cacheKey);
        if (cached != null) {
            try {
                return com.fasterxml.jackson.databind.ObjectMapperHolder.OBJECT_MAPPER
                    .readValue(cached, NvcPracticeSessionEntity.class);
            } catch (Exception e) {
                redisService.delete(cacheKey);
            }
        }

        // 从 DB 读取
        NvcPracticeSessionEntity session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NVC_SESSION_NOT_FOUND,
                "Practice session not found: " + sessionId));

        cacheSession(session);
        return session;
    }

    /**
     * 获取用户的练习会话列表
     */
    public List<NvcPracticeSessionEntity> getUserSessions(Long userId, NvcSessionPhase phase) {
        if (phase != null) {
            return sessionRepository.findByUserIdAndCurrentPhaseOrderByCreatedAtDesc(userId, phase);
        }
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 更新会话状态
     */
    public NvcPracticeSessionEntity updatePhase(Long sessionId, NvcSessionPhase newPhase) {
        NvcPracticeSessionEntity session = getSession(sessionId);
        session.setCurrentPhase(newPhase);

        if (newPhase == NvcSessionPhase.IN_PROGRESS && session.getStartedAt() == null) {
            session.setStartedAt(LocalDateTime.now());
        }
        if (newPhase == NvcSessionPhase.COMPLETED) {
            session.setCompletedAt(LocalDateTime.now());
        }

        NvcPracticeSessionEntity saved = sessionRepository.save(session);
        cacheSession(saved);
        return saved;
    }

    /**
     * 更新当前步骤（结构化四步模式）
     */
    public NvcPracticeSessionEntity updateStep(Long sessionId, NvcPracticeStep step) {
        NvcPracticeSessionEntity session = getSession(sessionId);
        session.setCurrentStep(step);
        NvcPracticeSessionEntity saved = sessionRepository.save(session);
        cacheSession(saved);
        return saved;
    }

    /**
     * 更新当前 Agent 场景
     */
    public NvcPracticeSessionEntity updateAgentScene(Long sessionId, NvcAgentScene scene) {
        NvcPracticeSessionEntity session = getSession(sessionId);
        session.setAgentScene(scene.name());
        NvcPracticeSessionEntity saved = sessionRepository.save(session);
        cacheSession(saved);
        return saved;
    }

    /**
     * 结束会话
     */
    public NvcPracticeSessionEntity completeSession(Long sessionId) {
        return updatePhase(sessionId, NvcSessionPhase.COMPLETED);
    }

    /**
     * 从 DB 中按难度随机分配一个场景
     */
    private Long pickRandomScenario(NvcDifficulty difficulty) {
        NvcDifficulty d = difficulty != null ? difficulty : NvcDifficulty.MEDIUM;
        List<NvcScenarioEntity> scenarios = scenarioRepository.findByDifficulty(d);
        if (scenarios.isEmpty()) {
            // 降级：取任意场景
            scenarios = scenarioRepository.findAll();
        }
        if (scenarios.isEmpty()) {
            throw new BusinessException(ErrorCode.NVC_SCENARIO_NOT_FOUND,
                "No scenario available for difficulty: " + d);
        }
        int idx = ThreadLocalRandom.current().nextInt(scenarios.size());
        NvcScenarioEntity picked = scenarios.get(idx);
        log.info("Random scenario picked: id={}, title={}, difficulty={}",
            picked.getId(), picked.getTitle(), picked.getDifficulty());
        return picked.getId();
    }

    private void cacheSession(NvcPracticeSessionEntity session) {
        try {
            String json = com.fasterxml.jackson.databind.ObjectMapperHolder.OBJECT_MAPPER
                .writeValueAsString(session);
            redisService.set(CACHE_KEY_PREFIX + session.getId(), json,
                CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to cache session: {}", session.getId(), e);
        }
    }
}
```

### NvcScenarioRepository 补充

```java
// 在 NvcScenarioRepository 中补充
List<NvcScenarioEntity> findByDifficulty(NvcDifficulty difficulty);
```

---

## 3.4 NvcPracticeDialogueService — 对话核心逻辑

路径：`app/src/main/java/interview/guide/modules/nvcpractice/service/NvcPracticeDialogueService.java`

### 功能

- 接收用户消息 → 调度 Agent → 获取 AI 回复 → 保存消息 → 返回结果
- 支持非流式和流式（SSE）两种模式
- 流式模式：首包发送 metadata 事件，后续纯文本 token
- 管理消息序号
- 对话开始时自动进入 IN_PROGRESS 状态

### 实现要点

```java
package interview.guide.modules.nvcpractice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.modules.nvcpractice.dto.*;
import interview.guide.modules.nvcpractice.model.*;
import interview.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcPracticeDialogueService {

    private final NvcPracticeSessionService sessionService;
    private final NvcPracticeMessageRepository messageRepository;
    private final NvcAgentOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    /**
     * 发送消息并获取 AI 回复（非流式）
     */
    public DialogueResponse sendMessage(Long sessionId, String userMessage) {
        // 1. 获取会话
        NvcPracticeSessionEntity session = sessionService.getSession(sessionId);

        // 2. 如果会话是 CREATED 状态，自动切换到 IN_PROGRESS
        if (session.getCurrentPhase() == NvcSessionPhase.CREATED) {
            session = sessionService.updatePhase(sessionId, NvcSessionPhase.IN_PROGRESS);
        }

        // 3. 保存用户消息
        int nextSeq = getNextSequenceNum(sessionId);
        NvcPracticeMessageEntity userMsg = NvcPracticeMessageEntity.builder()
            .sessionId(sessionId)
            .role(NvcMessageRole.USER)
            .content(userMessage)
            .sequenceNum(nextSeq)
            .step(session.getCurrentStep())
            .build();
        messageRepository.save(userMsg);

        // 4. 构建练习上下文
        PracticeContext context = orchestrator.buildPracticeContext(sessionId, session.getUserId());

        // 5. Agent 调度决策
        AgentDecision decision = orchestrator.decideNextAgent(context);
        log.info("Agent decision: session={}, scene={}, reason={}",
            sessionId, decision.scene(), decision.reason());

        // 6. 更新会话的当前 Agent 场景
        sessionService.updateAgentScene(sessionId, decision.scene());

        // 7. 执行 Agent 对话
        String aiReply = orchestrator.executeAgent(decision.scene(), context, userMessage);

        // 8. 保存 AI 回复
        NvcPracticeMessageEntity aiMsg = NvcPracticeMessageEntity.builder()
            .sessionId(sessionId)
            .role(NvcMessageRole.ASSISTANT)
            .agentScene(decision.scene().name())
            .content(aiReply)
            .sequenceNum(nextSeq + 1)
            .step(session.getCurrentStep())
            .build();
        messageRepository.save(aiMsg);

        // 9. 返回结果
        return new DialogueResponse(
            sessionId,
            aiReply,
            decision.scene().name(),
            decision.reason(),
            decision.action(),
            session.getCurrentStep() != null ? session.getCurrentStep().name() : null
        );
    }

    /**
     * 发送消息并获取 AI 回复（流式 SSE）
     *
     * 事件格式：
     *   event: metadata  → StreamMetadata JSON（agentScene, decisionReason, currentStep）
     *   event: message   → 纯文本 token
     *   event: done      → {"length": N}
     */
    public Flux<String> sendMessageStream(Long sessionId, String userMessage) {
        // 1. 获取会话 + 自动切换状态
        NvcPracticeSessionEntity session = sessionService.getSession(sessionId);
        if (session.getCurrentPhase() == NvcSessionPhase.CREATED) {
            session = sessionService.updatePhase(sessionId, NvcSessionPhase.IN_PROGRESS);
        }

        // 2. 保存用户消息
        int nextSeq = getNextSequenceNum(sessionId);
        NvcPracticeMessageEntity userMsg = NvcPracticeMessageEntity.builder()
            .sessionId(sessionId)
            .role(NvcMessageRole.USER)
            .content(userMessage)
            .sequenceNum(nextSeq)
            .step(session.getCurrentStep())
            .build();
        messageRepository.save(userMsg);

        // 3. 构建上下文 + Agent 调度
        PracticeContext context = orchestrator.buildPracticeContext(sessionId, session.getUserId());
        AgentDecision decision = orchestrator.decideNextAgent(context);
        sessionService.updateAgentScene(sessionId, decision.scene());

        // 4. 构建 metadata 事件
        StreamMetadata metadata = new StreamMetadata(
            decision.scene().name(),
            decision.reason(),
            decision.action(),
            session.getCurrentStep() != null ? session.getCurrentStep().name() : null
        );
        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            metadataJson = "{}";
        }

        // 5. 流式获取 AI 回复
        Flux<String> tokenStream = orchestrator.executeAgentStream(
            decision.scene(), context, userMessage);

        // 6. 组装 SSE 事件流
        StringBuilder fullReply = new StringBuilder();
        Flux<String> metadataEvent = Flux.just("event: metadata\ndata: " + metadataJson + "\n\n");
        Flux<String> messageEvents = tokenStream
            .doOnNext(token -> fullReply.append(token))
            .map(token -> "event: message\ndata: " + token + "\n\n");
        Flux<String> doneEvent = Flux.defer(() ->
            Flux.just("event: done\ndata: {\"length\":" + fullReply.length() + "}\n\n"));

        return Flux.concat(metadataEvent, messageEvents, doneEvent)
            .doOnComplete(() -> {
                // 流结束后保存 AI 回复
                NvcPracticeMessageEntity aiMsg = NvcPracticeMessageEntity.builder()
                    .sessionId(sessionId)
                    .role(NvcMessageRole.ASSISTANT)
                    .agentScene(decision.scene().name())
                    .content(fullReply.toString())
                    .sequenceNum(nextSeq + 1)
                    .step(session.getCurrentStep())
                    .build();
                messageRepository.save(aiMsg);
                log.info("Stream reply saved: sessionId={}, length={}",
                    sessionId, fullReply.length());
            });
    }

    /**
     * 获取对话历史
     */
    public List<MessageResponse> getMessages(Long sessionId) {
        return messageRepository.findBySessionIdOrderBySequenceNumAsc(sessionId).stream()
            .map(this::toMessageResponse)
            .toList();
    }

    private int getNextSequenceNum(Long sessionId) {
        return messageRepository.countBySessionId(sessionId);
    }

    private MessageResponse toMessageResponse(NvcPracticeMessageEntity msg) {
        return new MessageResponse(
            msg.getId(),
            msg.getSessionId(),
            msg.getRole().name(),
            msg.getAgentScene(),
            msg.getContent(),
            msg.getSequenceNum(),
            msg.getStep() != null ? msg.getStep().name() : null,
            msg.getCreatedAt()
        );
    }
}
```

### NvcAgentOrchestrator 补充方法

在 Step 2 的 `NvcAgentOrchestrator` 中补充流式执行方法（如果未实现）：

```java
/**
 * 流式执行 Agent 对话
 */
public Flux<String> executeAgentStream(NvcAgentScene scene, PracticeContext context, String userMessage) {
    NvcAgentConfigEntity config = agentConfigService.getConfig(scene);
    if (!config.getIsEnabled()) {
        config = agentConfigService.getConfig(NvcAgentScene.DIALOGUE_GUIDE);
    }
    return agentChatService.chatStream(config, context, userMessage);
}
```

---

## 3.5 NvcPracticeController — 练习 API

路径：`app/src/main/java/interview/guide/modules/nvcpractice/controller/NvcPracticeController.java`

```java
package interview.guide.modules.nvcpractice.controller;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.modules.nvcpractice.dto.*;
import interview.guide.modules.nvcpractice.model.*;
import interview.guide.modules.nvcpractice.service.NvcPracticeDialogueService;
import interview.guide.modules.nvcpractice.service.NvcPracticeSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/nvc/practice")
@RequiredArgsConstructor
public class NvcPracticeController {

    private final NvcPracticeSessionService sessionService;
    private final NvcPracticeDialogueService dialogueService;

    /**
     * 创建练习会话
     */
    @RateLimit
    @PostMapping("/sessions")
    public Result<PracticeSessionResponse> createSession(
            @RequestParam Long userId,
            @Valid @RequestBody CreatePracticeSessionRequest request) {
        NvcPracticeSessionEntity session = sessionService.createSession(userId, request);
        return Result.success(toSessionResponse(session));
    }

    /**
     * 获取用户的练习会话列表
     */
    @GetMapping("/sessions")
    public Result<List<PracticeSessionResponse>> getUserSessions(
            @RequestParam Long userId,
            @RequestParam(required = false) NvcSessionPhase phase) {
        var sessions = sessionService.getUserSessions(userId, phase).stream()
            .map(this::toSessionResponse)
            .toList();
        return Result.success(sessions);
    }

    /**
     * 获取单个会话详情
     */
    @GetMapping("/sessions/{sessionId}")
    public Result<PracticeSessionResponse> getSession(@PathVariable Long sessionId) {
        NvcPracticeSessionEntity session = sessionService.getSession(sessionId);
        return Result.success(toSessionResponse(session));
    }

    /**
     * 发送消息（非流式）
     */
    @RateLimit
    @PostMapping("/sessions/{sessionId}/messages")
    public Result<DialogueResponse> sendMessage(
            @PathVariable Long sessionId,
            @RequestBody SendMessageRequest request) {
        DialogueResponse response = dialogueService.sendMessage(sessionId, request.content());
        return Result.success(response);
    }

    /**
     * 发送消息（流式 SSE）
     *
     * 事件格式：
     *   event: metadata  → StreamMetadata JSON
     *   event: message   → 纯文本 token
     *   event: done      → {"length": N}
     */
    @RateLimit
    @PostMapping(value = "/sessions/{sessionId}/messages/stream",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> sendMessageStream(
            @PathVariable Long sessionId,
            @RequestBody SendMessageRequest request) {
        return dialogueService.sendMessageStream(sessionId, request.content());
    }

    /**
     * 获取对话历史
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<MessageResponse>> getMessages(@PathVariable Long sessionId) {
        return Result.success(dialogueService.getMessages(sessionId));
    }

    /**
     * 结束会话
     */
    @PostMapping("/sessions/{sessionId}/complete")
    public Result<PracticeSessionResponse> completeSession(@PathVariable Long sessionId) {
        NvcPracticeSessionEntity session = sessionService.completeSession(sessionId);
        return Result.success(toSessionResponse(session));
    }

    private PracticeSessionResponse toSessionResponse(NvcPracticeSessionEntity session) {
        return new PracticeSessionResponse(
            session.getId(),
            session.getUserId(),
            session.getPracticeMode(),
            session.getCurrentPhase(),
            session.getCurrentStep(),
            session.getAgentScene(),
            session.getDifficulty(),
            session.getStartedAt(),
            session.getCompletedAt(),
            session.getCreatedAt()
        );
    }
}
```

---

## 3.6 DTO 类

### CreatePracticeSessionRequest

```java
package interview.guide.modules.nvcpractice.dto;

import interview.guide.modules.nvcpractice.model.*;
import jakarta.validation.constraints.NotNull;

public record CreatePracticeSessionRequest(
    @NotNull NvcPracticeMode practiceMode,
    Long scenarioId,           // 场景驱动模式下可为 null（随机分配）
    NvcDifficulty difficulty
) {}
```

### PracticeSessionResponse

```java
package interview.guide.modules.nvcpractice.dto;

import interview.guide.modules.nvcpractice.model.*;
import java.time.LocalDateTime;

public record PracticeSessionResponse(
    Long id,
    Long userId,
    NvcPracticeMode practiceMode,
    NvcSessionPhase currentPhase,
    NvcPracticeStep currentStep,
    String agentScene,
    NvcDifficulty difficulty,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    LocalDateTime createdAt
) {}
```

### SendMessageRequest

```java
package interview.guide.modules.nvcpractice.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
    @NotBlank String content
) {}
```

### DialogueResponse

```java
package interview.guide.modules.nvcpractice.dto;

public record DialogueResponse(
    Long sessionId,
    String aiReply,
    String agentScene,
    String decisionReason,
    String action,
    String currentStep
) {}
```

### MessageResponse（新增）

替代直接返回 Entity 给前端：

```java
package interview.guide.modules.nvcpractice.dto;

import java.time.LocalDateTime;

public record MessageResponse(
    Long id,
    Long sessionId,
    String role,
    String agentScene,
    String content,
    Integer sequenceNum,
    String step,
    LocalDateTime createdAt
) {}
```

### StreamMetadata（新增）

SSE 首包事件数据：

```java
package interview.guide.modules.nvcpractice.dto;

public record StreamMetadata(
    String agentScene,
    String decisionReason,
    String action,
    String currentStep
) {}
```

---

## 3.7 Prompt 模板

### nvc-scenario-generate-system.st

路径：`app/src/main/resources/prompts/nvc-scenario-generate-system.st`

```
你是NVC非暴力沟通练习的场景设计师。你的职责是根据用户的偏好和练习历史，生成真实、有代入感的NVC练习场景。

生成场景时请包含以下信息，以JSON格式返回：

{
  "title": "场景标题",
  "description": "场景描述（简洁版）",
  "context": "详细背景信息（人物关系、具体情境、冲突起因）",
  "opponent_character": "对方角色设定（性格、说话风格、情绪状态）",
  "focus_elements": ["重点练习的NVC要素"],
  "difficulty": "EASY/MEDIUM/HARD",
  "opening_line": "对方的第一句话（开始对话用）"
}

场景要求：
1. 场景要真实可信，贴近日常生活
2. 冲突要有一定复杂度，不能太简单
3. 对方角色要有明确的性格和立场
4. 要有明确的NVC练习目标
5. 场景类型可以是：职场冲突、家庭矛盾、亲密关系、社交困境、自我对话

用户偏好信息会以[用户档案]的形式提供，请尽量匹配用户的薄弱要素和常遇到的场景类型。
```

### 关于其他 prompt 文件

`nvc-dialogue-guide-system.st` 和 `nvc-difficult-partner-system.st` **不需要创建**。这些 prompt 已存储在 DB 的 `nvc_agent_config.system_prompt` 字段中（种子数据 `data-nvc-agent-config.sql` 包含 11 个 Agent 的完整 prompt）。

---

## 3.8 验证清单

```
□ 所有新增类编译通过
□ 项目启动成功
□ API 测试：
  □ POST /api/nvc/practice/sessions 创建会话成功，返回会话信息
  □ POST /api/nvc/practice/sessions（scenarioId=null）随机分配场景成功
  □ GET /api/nvc/practice/sessions?userId=1 返回会话列表
  □ POST /api/nvc/practice/sessions/{id}/messages 发送消息成功，返回 AI 回复
  □ AI 回复内容合理（符合对话引导官的角色设定）
  □ GET /api/nvc/practice/sessions/{id}/messages 返回 MessageResponse 列表（非 Entity）
  □ POST /api/nvc/practice/sessions/{id}/complete 结束会话成功
□ 流式测试：
  □ POST /api/nvc/practice/sessions/{id}/messages/stream 返回 SSE 流
  □ 首包为 metadata 事件，包含 agentScene/decisionReason/currentStep
  □ 后续为 message 事件，逐字输出文本 token
  □ 结束时发送 done 事件，包含 length
  □ 流结束后消息正确保存到数据库
□ Agent 调度测试：
  □ 场景驱动模式首次对话使用 SCENARIO_GENERATOR 或从 DB 加载场景
  □ 后续对话使用 DIALOGUE_GUIDE
  □ 对话历史正确传递给 Agent（上下文连续）
□ Git 提交："Step 3: Add NVC practice dialogue flow with SSE streaming"
```

---

## 3.9 测试场景

### 测试 1：场景驱动练习端到端

```
1. POST /api/nvc/practice/sessions?userId=1
   body: { "practiceMode": "SCENARIO", "scenarioId": 1, "difficulty": "MEDIUM" }
   → 创建会话，状态为 CREATED，关联场景"同事抢功"

2. POST /api/nvc/practice/sessions/{id}/messages
   body: { "content": "开始练习" }
   → AI 回复场景描述 + 对方的第一句话
   → 会话状态变为 IN_PROGRESS

3. POST /api/nvc/practice/sessions/{id}/messages
   body: { "content": "我注意到上次项目汇报中，你提到的三个技术方案都是我独立完成的，但你没有提及我的贡献。" }
   → AI（扮演同事）回复

4. 继续多轮对话...

5. POST /api/nvc/practice/sessions/{id}/complete
   → 会话状态变为 COMPLETED
```

### 测试 2：随机场景模式

```
1. POST /api/nvc/practice/sessions?userId=1
   body: { "practiceMode": "SCENARIO", "difficulty": "HARD" }
   → 创建会话，自动随机分配一个 HARD 难度场景

2. GET /api/nvc/practice/sessions/{id}
   → 确认 scenarioId 已被设置
```

### 测试 3：自由对话练习

```
1. POST /api/nvc/practice/sessions?userId=1
   body: { "practiceMode": "FREE_DIALOG" }
   → 创建会话

2. POST /api/nvc/practice/sessions/{id}/messages
   body: { "content": "我和我妈总是因为她催婚吵架，我想用NVC的方式来和她沟通" }
   → AI 引导用户用NVC框架描述问题

3. 继续对话...
```

### 测试 4：SSE 流式对话

```
1. POST /api/nvc/practice/sessions/{id}/messages/stream
   body: { "content": "你好" }

   响应 SSE 流：
   event: metadata
   data: {"agentScene":"DIALOGUE_GUIDE","decisionReason":"...","action":null,"currentStep":null}

   event: message
   data: 你

   event: message
   data: 好

   event: message
   data: ！

   event: done
   data: {"length":3}

2. GET /api/nvc/practice/sessions/{id}/messages
   → 确认最后一条 ASSISTANT 消息已保存
```
