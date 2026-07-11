# Step 3: NVC 文字练习核心流程 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现文字对话练习的完整链路 — 创建会话 → 多轮对话 → SSE 流式输出 → 结束会话

**Architecture:** NvcPracticeDialogueService 编排对话流程，复用 Step 2 已有的 NvcAgentOrchestrator（buildPracticeContext/decideNextAgent/executeAgent）和 NvcAgentChatService（chatStream）。SSE 流式响应采用首包 metadata 事件 + 后续纯文本 token + 最终 done 事件的格式。

**Tech Stack:** Spring Boot 4.0, Java 21, Spring WebFlux (Flux<String> SSE), JPA, Redis, Lombok

## Global Constraints

- 包名: `nvc.guide`（非 `interview.guide`）
- 2 空格缩进，列限制 100 字符
- 禁止通配符导入
- 禁止 `throw new RuntimeException(...)`
- 禁止直接返回 Entity 给前端
- Controller 使用 `@RateLimit` 做限流
- `@Transactional` 放 Service 层，禁止事务内调用外部 API
- 使用 SLF4J `@Slf4j` 日志

## File Structure

### 新建文件

| 文件 | 职责 |
|------|------|
| `dto/CreatePracticeSessionRequest.java` | 创建会话请求 record |
| `dto/PracticeSessionResponse.java` | 会话响应 record |
| `dto/SendMessageRequest.java` | 发送消息请求 record |
| `dto/DialogueResponse.java` | 非流式对话响应 record |
| `dto/MessageResponse.java` | 消息响应 DTO（替代 Entity） |
| `dto/StreamMetadata.java` | SSE 首包元数据 record |
| `service/NvcPracticeSessionService.java` | 会话管理（创建/查询/结束/随机场景） |
| `service/NvcPracticeDialogueService.java` | 对话核心逻辑（调度 + 保存 + SSE） |
| `controller/NvcPracticeController.java` | 练习 REST API |
| `resources/prompts/nvc-scenario-generate-system.st` | 场景生成辅助 prompt |

### 修改文件

| 文件 | 修改内容 |
|------|---------|
| `NvcAgentOrchestrator.java` | 补充 `executeAgentStream()` 方法 |
| `NvcScenarioRepository.java` | 补充 `findByDifficulty()` 方法 |

---

### Task 1: DTO Records

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/CreatePracticeSessionRequest.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/PracticeSessionResponse.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/SendMessageRequest.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/DialogueResponse.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/MessageResponse.java`
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/dto/StreamMetadata.java`

**Interfaces:**
- Produces: `CreatePracticeSessionRequest(NvcPracticeMode, Long scenarioId, NvcDifficulty)`
- Produces: `PracticeSessionResponse(Long id, Long userId, NvcPracticeMode, NvcSessionPhase, NvcPracticeStep, String agentScene, NvcDifficulty, LocalDateTime startedAt, LocalDateTime completedAt, LocalDateTime createdAt)`
- Produces: `SendMessageRequest(String content)`
- Produces: `DialogueResponse(Long sessionId, String aiReply, String agentScene, String decisionReason, String action, String currentStep)`
- Produces: `MessageResponse(Long id, Long sessionId, String role, String agentScene, String content, Integer sequenceNum, String step, LocalDateTime createdAt)`
- Produces: `StreamMetadata(String agentScene, String decisionReason, String action, String currentStep)`

- [ ] **Step 1: Create CreatePracticeSessionRequest**

```java
package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import jakarta.validation.constraints.NotNull;

public record CreatePracticeSessionRequest(
    @NotNull NvcPracticeMode practiceMode,
    Long scenarioId,
    NvcDifficulty difficulty
) {}
```

- [ ] **Step 2: Create PracticeSessionResponse**

```java
package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
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

- [ ] **Step 3: Create SendMessageRequest**

```java
package nvc.guide.modules.nvcpractice.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
    @NotBlank String content
) {}
```

- [ ] **Step 4: Create DialogueResponse**

```java
package nvc.guide.modules.nvcpractice.dto;

public record DialogueResponse(
    Long sessionId,
    String aiReply,
    String agentScene,
    String decisionReason,
    String action,
    String currentStep
) {}
```

- [ ] **Step 5: Create MessageResponse**

```java
package nvc.guide.modules.nvcpractice.dto;

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

- [ ] **Step 6: Create StreamMetadata**

```java
package nvc.guide.modules.nvcpractice.dto;

public record StreamMetadata(
    String agentScene,
    String decisionReason,
    String action,
    String currentStep
) {}
```

- [ ] **Step 7: Compile check**

Run: `cd D:\code\nvc-guide && .\gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/dto/
git commit -m "feat(step3): add practice dialogue DTOs"
```

---

### Task 2: Repository Updates

**Files:**
- Modify: `app/src/main/java/nvc/guide/modules/nvcscenario/repository/NvcScenarioRepository.java`

**Interfaces:**
- Produces: `List<NvcScenarioEntity> findByDifficulty(NvcDifficulty difficulty)`

- [ ] **Step 1: Add findByDifficulty to NvcScenarioRepository**

在 `NvcScenarioRepository.java` 接口中添加：

```java
List<NvcScenarioEntity> findByDifficulty(NvcDifficulty difficulty);
```

需要确认 import 已有 `NvcDifficulty`。

- [ ] **Step 2: Compile check**

Run: `cd D:\code\nvc-guide && .\gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcscenario/repository/NvcScenarioRepository.java
git commit -m "feat(step3): add findByDifficulty to NvcScenarioRepository"
```

---

### Task 3: NvcAgentOrchestrator — 补充 executeAgentStream

**Files:**
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentOrchestrator.java`

**Interfaces:**
- Consumes: `NvcAgentConfigService.getConfig(NvcAgentScene)`, `NvcAgentChatService.chatStream(NvcAgentConfigEntity, PracticeContext, String)`
- Produces: `Flux<String> executeAgentStream(NvcAgentScene scene, PracticeContext context, String userMessage)`

- [ ] **Step 1: Read current NvcAgentOrchestrator**

Read `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentOrchestrator.java` to find the `executeAgent` method.

- [ ] **Step 2: Add executeAgentStream method**

在 `executeAgent` 方法之后添加：

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

确认 import 中已有 `reactor.core.publisher.Flux`。

- [ ] **Step 3: Compile check**

Run: `cd D:\code\nvc-guide && .\gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentOrchestrator.java
git commit -m "feat(step3): add executeAgentStream to NvcAgentOrchestrator"
```

---

### Task 4: NvcPracticeSessionService — 会话管理

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeSessionService.java`

**Interfaces:**
- Consumes: `NvcPracticeSessionRepository`, `NvcScenarioRepository.findByDifficulty()`, `RedisService.set()/get()/delete()`
- Produces:
  - `NvcPracticeSessionEntity createSession(Long userId, CreatePracticeSessionRequest request)`
  - `NvcPracticeSessionEntity getSession(Long sessionId)`
  - `List<NvcPracticeSessionEntity> getUserSessions(Long userId, NvcSessionPhase phase)`
  - `NvcPracticeSessionEntity updatePhase(Long sessionId, NvcSessionPhase newPhase)`
  - `NvcPracticeSessionEntity updateStep(Long sessionId, NvcPracticeStep step)`
  - `NvcPracticeSessionEntity updateAgentScene(Long sessionId, NvcAgentScene scene)`
  - `NvcPracticeSessionEntity completeSession(Long sessionId)`

- [ ] **Step 1: Create NvcPracticeSessionService**

```java
package nvc.guide.modules.nvcpractice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcpractice.dto.CreatePracticeSessionRequest;
import nvc.guide.modules.nvcpractice.model.*;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeSessionRepository;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import nvc.guide.modules.nvcscenario.repository.NvcScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcPracticeSessionService {

    private final NvcPracticeSessionRepository sessionRepository;
    private final NvcScenarioRepository scenarioRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY_PREFIX = "nvc:practice:session:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    /**
     * 创建练习会话
     */
    public NvcPracticeSessionEntity createSession(Long userId,
        CreatePracticeSessionRequest request) {
      Long scenarioId = request.scenarioId();
      if (request.practiceMode() == NvcPracticeMode.SCENARIO
          && scenarioId == null) {
        scenarioId = pickRandomScenario(request.difficulty());
      }

      NvcPracticeSessionEntity session = NvcPracticeSessionEntity.builder()
          .userId(userId)
          .practiceMode(request.practiceMode())
          .scenarioId(scenarioId)
          .difficulty(request.difficulty() != null
              ? request.difficulty() : NvcDifficulty.MEDIUM)
          .currentPhase(NvcSessionPhase.CREATED)
          .currentStep(request.practiceMode()
              == NvcPracticeMode.STRUCTURED_FOUR_STEP
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

        String cached = redisService.get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(
                    cached, NvcPracticeSessionEntity.class);
            } catch (Exception e) {
                redisService.delete(cacheKey);
            }
        }

        NvcPracticeSessionEntity session = sessionRepository
            .findById(sessionId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NVC_SESSION_NOT_FOUND,
                "Practice session not found: " + sessionId));

        cacheSession(session);
        return session;
    }

    /**
     * 获取用户的练习会话列表
     */
    public List<NvcPracticeSessionEntity> getUserSessions(
        Long userId, NvcSessionPhase phase) {
      if (phase != null) {
        return sessionRepository
            .findByUserIdAndCurrentPhaseOrderByCreatedAtDesc(
                userId, phase);
      }
      return sessionRepository
          .findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 更新会话状态
     */
    public NvcPracticeSessionEntity updatePhase(
        Long sessionId, NvcSessionPhase newPhase) {
      NvcPracticeSessionEntity session = getSession(sessionId);
      session.setCurrentPhase(newPhase);

      if (newPhase == NvcSessionPhase.IN_PROGRESS
          && session.getStartedAt() == null) {
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
    public NvcPracticeSessionEntity updateStep(
        Long sessionId, NvcPracticeStep step) {
      NvcPracticeSessionEntity session = getSession(sessionId);
      session.setCurrentStep(step);
      NvcPracticeSessionEntity saved = sessionRepository.save(session);
      cacheSession(saved);
      return saved;
    }

    /**
     * 更新当前 Agent 场景
     */
    public NvcPracticeSessionEntity updateAgentScene(
        Long sessionId, NvcAgentScene scene) {
      NvcPracticeSessionEntity session = getSession(sessionId);
      session.setAgentScene(scene);
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
        NvcDifficulty d = difficulty != null
            ? difficulty : NvcDifficulty.MEDIUM;
        List<NvcScenarioEntity> scenarios =
            scenarioRepository.findByDifficulty(d);
        if (scenarios.isEmpty()) {
            scenarios = scenarioRepository.findAll();
        }
        if (scenarios.isEmpty()) {
            throw new BusinessException(
                ErrorCode.NVC_SCENARIO_NOT_FOUND,
                "No scenario available for difficulty: " + d);
        }
        int idx = ThreadLocalRandom.current()
            .nextInt(scenarios.size());
        NvcScenarioEntity picked = scenarios.get(idx);
        log.info("Random scenario picked: id={}, title={}, difficulty={}",
            picked.getId(), picked.getTitle(), picked.getDifficulty());
        return picked.getId();
    }

    private void cacheSession(NvcPracticeSessionEntity session) {
        try {
            String json = objectMapper.writeValueAsString(session);
            redisService.set(
                CACHE_KEY_PREFIX + session.getId(), json, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to cache session: {}",
                session.getId(), e);
        }
    }
}
```

- [ ] **Step 2: Compile check**

Run: `cd D:\code\nvc-guide && .\gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeSessionService.java
git commit -m "feat(step3): add NvcPracticeSessionService with random scenario"
```

---

### Task 5: NvcPracticeDialogueService — 对话核心逻辑

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeDialogueService.java`

**Interfaces:**
- Consumes:
  - `NvcPracticeSessionService.getSession/updatePhase/updateAgentScene()`
  - `NvcAgentOrchestrator.buildPracticeContext/decideNextAgent/executeAgent/executeAgentStream()`
  - `NvcPracticeMessageRepository.findBySessionIdOrderBySequenceNumAsc/countBySessionId/save()`
  - `ObjectMapper.writeValueAsString()`
- Produces:
  - `DialogueResponse sendMessage(Long sessionId, String userMessage)`
  - `Flux<String> sendMessageStream(Long sessionId, String userMessage)` — SSE 事件格式
  - `List<MessageResponse> getMessages(Long sessionId)`

- [ ] **Step 1: Create NvcPracticeDialogueService**

```java
package nvc.guide.modules.nvcpractice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.modules.nvcpractice.dto.*;
import nvc.guide.modules.nvcpractice.model.*;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
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
    public DialogueResponse sendMessage(Long sessionId,
        String userMessage) {
      // 1. 获取会话
      NvcPracticeSessionEntity session =
          sessionService.getSession(sessionId);

      // 2. 如果会话是 CREATED 状态，自动切换到 IN_PROGRESS
      if (session.getCurrentPhase() == NvcSessionPhase.CREATED) {
        session = sessionService.updatePhase(
            sessionId, NvcSessionPhase.IN_PROGRESS);
      }

      // 3. 保存用户消息
      int nextSeq = getNextSequenceNum(sessionId);
      NvcPracticeMessageEntity userMsg =
          NvcPracticeMessageEntity.builder()
              .sessionId(sessionId)
              .role(NvcMessageRole.USER)
              .content(userMessage)
              .sequenceNum(nextSeq)
              .step(session.getCurrentStep())
              .build();
      messageRepository.save(userMsg);

      // 4. 构建练习上下文
      PracticeContext context = orchestrator.buildPracticeContext(
          sessionId, session.getUserId());

      // 5. Agent 调度决策
      AgentDecision decision =
          orchestrator.decideNextAgent(context);
      log.info("Agent decision: session={}, scene={}, reason={}",
          sessionId, decision.scene(), decision.reason());

      // 6. 更新会话的当前 Agent 场景
      sessionService.updateAgentScene(sessionId, decision.scene());

      // 7. 执行 Agent 对话
      String aiReply = orchestrator.executeAgent(
          decision.scene(), context, userMessage);

      // 8. 保存 AI 回复
      NvcPracticeMessageEntity aiMsg =
          NvcPracticeMessageEntity.builder()
              .sessionId(sessionId)
              .role(NvcMessageRole.ASSISTANT)
              .agentScene(decision.scene())
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
          session.getCurrentStep() != null
              ? session.getCurrentStep().name() : null
      );
    }

    /**
     * 发送消息并获取 AI 回复（流式 SSE）
     *
     * 事件格式：
     *   event: metadata  → StreamMetadata JSON
     *   event: message   → 纯文本 token
     *   event: done      → {"length": N}
     */
    public Flux<String> sendMessageStream(Long sessionId,
        String userMessage) {
      // 1. 获取会话 + 自动切换状态
      NvcPracticeSessionEntity session =
          sessionService.getSession(sessionId);
      if (session.getCurrentPhase() == NvcSessionPhase.CREATED) {
        session = sessionService.updatePhase(
            sessionId, NvcSessionPhase.IN_PROGRESS);
      }

      // 2. 保存用户消息
      int nextSeq = getNextSequenceNum(sessionId);
      NvcPracticeMessageEntity userMsg =
          NvcPracticeMessageEntity.builder()
              .sessionId(sessionId)
              .role(NvcMessageRole.USER)
              .content(userMessage)
              .sequenceNum(nextSeq)
              .step(session.getCurrentStep())
              .build();
      messageRepository.save(userMsg);

      // 3. 构建上下文 + Agent 调度
      PracticeContext context = orchestrator.buildPracticeContext(
          sessionId, session.getUserId());
      AgentDecision decision =
          orchestrator.decideNextAgent(context);
      sessionService.updateAgentScene(sessionId, decision.scene());

      // 4. 构建 metadata 事件
      StreamMetadata metadata = new StreamMetadata(
          decision.scene().name(),
          decision.reason(),
          decision.action(),
          session.getCurrentStep() != null
              ? session.getCurrentStep().name() : null
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
      Flux<String> metadataEvent = Flux.just(
          "event: metadata\ndata: " + metadataJson + "\n\n");
      Flux<String> messageEvents = tokenStream
          .doOnNext(fullReply::append)
          .map(token ->
              "event: message\ndata: " + token + "\n\n");
      Flux<String> doneEvent = Flux.defer(() -> Flux.just(
          "event: done\ndata: {\"length\":"
          + fullReply.length() + "}\n\n"));

      return Flux.concat(metadataEvent, messageEvents, doneEvent)
          .doOnComplete(() -> {
            NvcPracticeMessageEntity aiMsg =
                NvcPracticeMessageEntity.builder()
                    .sessionId(sessionId)
                    .role(NvcMessageRole.ASSISTANT)
                    .agentScene(decision.scene())
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
        return messageRepository
            .findBySessionIdOrderBySequenceNumAsc(sessionId)
            .stream()
            .map(this::toMessageResponse)
            .toList();
    }

    private int getNextSequenceNum(Long sessionId) {
        return messageRepository.countBySessionId(sessionId);
    }

    private MessageResponse toMessageResponse(
        NvcPracticeMessageEntity msg) {
      return new MessageResponse(
          msg.getId(),
          msg.getSessionId(),
          msg.getRole().name(),
          msg.getAgentScene() != null
              ? msg.getAgentScene().name() : null,
          msg.getContent(),
          msg.getSequenceNum(),
          msg.getStep() != null ? msg.getStep().name() : null,
          msg.getCreatedAt()
      );
    }
}
```

- [ ] **Step 2: Compile check**

Run: `cd D:\code\nvc-guide && .\gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeDialogueService.java
git commit -m "feat(step3): add NvcPracticeDialogueService with SSE streaming"
```

---

### Task 6: NvcPracticeController — REST API

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcPracticeController.java`

**Interfaces:**
- Consumes: `NvcPracticeSessionService`, `NvcPracticeDialogueService`
- Produces: REST API endpoints under `/api/nvc/practice`

- [ ] **Step 1: Create NvcPracticeController**

```java
package nvc.guide.modules.nvcpractice.controller;

import nvc.guide.common.annotation.RateLimit;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcpractice.dto.*;
import nvc.guide.modules.nvcpractice.model.*;
import nvc.guide.modules.nvcpractice.service.NvcPracticeDialogueService;
import nvc.guide.modules.nvcpractice.service.NvcPracticeSessionService;
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
    @RateLimit(count = 10)
    @PostMapping("/sessions")
    public Result<PracticeSessionResponse> createSession(
            @RequestParam Long userId,
            @Valid @RequestBody CreatePracticeSessionRequest req) {
        NvcPracticeSessionEntity session =
            sessionService.createSession(userId, req);
        return Result.success(toSessionResponse(session));
    }

    /**
     * 获取用户的练习会话列表
     */
    @GetMapping("/sessions")
    public Result<List<PracticeSessionResponse>> getUserSessions(
            @RequestParam Long userId,
            @RequestParam(required = false) NvcSessionPhase phase) {
        var sessions = sessionService
            .getUserSessions(userId, phase).stream()
            .map(this::toSessionResponse)
            .toList();
        return Result.success(sessions);
    }

    /**
     * 获取单个会话详情
     */
    @GetMapping("/sessions/{sessionId}")
    public Result<PracticeSessionResponse> getSession(
            @PathVariable Long sessionId) {
        NvcPracticeSessionEntity session =
            sessionService.getSession(sessionId);
        return Result.success(toSessionResponse(session));
    }

    /**
     * 发送消息（非流式）
     */
    @RateLimit(count = 30)
    @PostMapping("/sessions/{sessionId}/messages")
    public Result<DialogueResponse> sendMessage(
            @PathVariable Long sessionId,
            @RequestBody SendMessageRequest request) {
        DialogueResponse response =
            dialogueService.sendMessage(
                sessionId, request.content());
        return Result.success(response);
    }

    /**
     * 发送消息（流式 SSE）
     */
    @RateLimit(count = 30)
    @PostMapping(
        value = "/sessions/{sessionId}/messages/stream",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> sendMessageStream(
            @PathVariable Long sessionId,
            @RequestBody SendMessageRequest request) {
        return dialogueService.sendMessageStream(
            sessionId, request.content());
    }

    /**
     * 获取对话历史
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<MessageResponse>> getMessages(
            @PathVariable Long sessionId) {
        return Result.success(
            dialogueService.getMessages(sessionId));
    }

    /**
     * 结束会话
     */
    @PostMapping("/sessions/{sessionId}/complete")
    public Result<PracticeSessionResponse> completeSession(
            @PathVariable Long sessionId) {
        NvcPracticeSessionEntity session =
            sessionService.completeSession(sessionId);
        return Result.success(toSessionResponse(session));
    }

    private PracticeSessionResponse toSessionResponse(
        NvcPracticeSessionEntity session) {
      return new PracticeSessionResponse(
          session.getId(),
          session.getUserId(),
          session.getPracticeMode(),
          session.getCurrentPhase(),
          session.getCurrentStep(),
          session.getAgentScene() != null
              ? session.getAgentScene().name() : null,
          session.getDifficulty(),
          session.getStartedAt(),
          session.getCompletedAt(),
          session.getCreatedAt()
      );
    }
}
```

- [ ] **Step 2: Compile check**

Run: `cd D:\code\nvc-guide && .\gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcPracticeController.java
git commit -m "feat(step3): add NvcPracticeController with SSE support"
```

---

### Task 7: Prompt 模板 + 编译验证

**Files:**
- Create: `app/src/main/resources/prompts/nvc-scenario-generate-system.st`

- [ ] **Step 1: Create nvc-scenario-generate-system.st**

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

- [ ] **Step 2: Final compile check**

Run: `cd D:\code\nvc-guide && .\gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/resources/prompts/nvc-scenario-generate-system.st
git commit -m "feat(step3): add scenario generation prompt template"
```

---

### Task 8: Application 启动验证

- [ ] **Step 1: Start the application**

Run: `cd D:\code\nvc-guide && .\gradlew.bat bootRun`

等待启动完成，确认无报错。

- [ ] **Step 2: Test create session with scenario**

```bash
curl -X POST "http://localhost:8080/api/nvc/practice/sessions?userId=1" \
  -H "Content-Type: application/json" \
  -d '{"practiceMode":"SCENARIO","scenarioId":1,"difficulty":"MEDIUM"}'
```

Expected: 返回 Result.success 包裹的 PracticeSessionResponse，currentPhase=CREATED

- [ ] **Step 3: Test create session with random scenario**

```bash
curl -X POST "http://localhost:8080/api/nvc/practice/sessions?userId=1" \
  -H "Content-Type: application/json" \
  -d '{"practiceMode":"SCENARIO","difficulty":"HARD"}'
```

Expected: 返回成功，scenarioId 被自动分配

- [ ] **Step 4: Test send message (non-stream)**

```bash
curl -X POST "http://localhost:8080/api/nvc/practice/sessions/1/messages" \
  -H "Content-Type: application/json" \
  -d '{"content":"开始练习"}'
```

Expected: 返回 DialogueResponse，aiReply 包含 AI 回复内容

- [ ] **Step 5: Test get messages**

```bash
curl "http://localhost:8080/api/nvc/practice/sessions/1/messages"
```

Expected: 返回 MessageResponse 列表（非 Entity），包含 USER 和 ASSISTANT 消息

- [ ] **Step 6: Test SSE stream**

```bash
curl -X POST "http://localhost:8080/api/nvc/practice/sessions/1/messages/stream" \
  -H "Content-Type: application/json" \
  -d '{"content":"你好"}' \
  --no-buffer
```

Expected: 首行 `event: metadata`，后续 `event: message`，最后 `event: done`

- [ ] **Step 7: Test complete session**

```bash
curl -X POST "http://localhost:8080/api/nvc/practice/sessions/1/complete"
```

Expected: 返回 PracticeSessionResponse，currentPhase=COMPLETED

- [ ] **Step 8: Final commit**

```bash
git add -A
git commit -m "Step 3: Add NVC practice dialogue flow with SSE streaming"
```

---

## Self-Review Checklist

- ✅ 所有 DTO 使用 record，不返回 Entity
- ✅ 包名统一 `nvc.guide`
- ✅ 注入 ObjectMapper，不使用 ObjectMapperHolder
- ✅ agentScene 字段直接赋值枚举，非 .name()
- ✅ @RateLimit 补充 count 参数
- ✅ RedisService.set 使用 Duration
- ✅ SSE 事件格式：metadata → message* → done
- ✅ 随机场景：scenarioId 为 null 时按难度随机
- ✅ 评估留占位（不实现）
- ✅ 验证清单覆盖所有 API 端点和 SSE 流
