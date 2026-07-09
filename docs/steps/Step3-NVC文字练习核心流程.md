# Step 3：NVC 文字练习核心流程

> 目标：实现文字对话练习的完整链路 — 创建会话 → 多轮对话 → SSE 流式输出 → 结束会话
>
> 预计耗时：3-5 天
>
> 前置条件：Step 2 已完成，Agent 调度中心就绪
>
> 技术亮点：SSE 流式对话、Agent 自动调度、多轮上下文管理

---

## 3.1 本步要创建的文件清单

```
app/src/main/java/interview/guide/modules/nvcpractice/
├── service/
│   ├── NvcPracticeSessionService.java     ← 会话管理（创建/查询/结束）
│   ├── NvcPracticeDialogueService.java    ← 对话核心逻辑（调度 Agent + 保存消息）
│   └── NvcContextBuilder.java             ← 练习上下文构建（组装 PracticeContext）
├── dto/
│   ├── CreatePracticeSessionRequest.java  ← 创建会话请求
│   ├── PracticeSessionResponse.java       ← 会话响应
│   ├── SendMessageRequest.java            ← 发送消息请求
│   └── DialogueResponse.java              ← 对话响应（含 Agent 决策信息）
└── controller/
    └── NvcPracticeController.java         ← 练习 API（创建/对话/结束）
```

app/src/main/resources/prompts/ 下新增：
```
nvc-scenario-generate-system.st            ← 场景生成提示词
nvc-dialogue-guide-system.st               ← 对话引导提示词
nvc-difficult-partner-system.st            ← 困难搭档提示词
```

---

## 3.2 NvcPracticeSessionService — 会话管理

路径：`app/src/main/java/interview/guide/modules/nvcpractice/service/NvcPracticeSessionService.java`

### 功能

- 创建练习会话（根据模式初始化不同状态）
- 查询会话列表（按用户、按状态）
- 查询单个会话详情
- 结束/暂停会话
- 会话状态管理

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcPracticeSessionService {

    private final NvcPracticeSessionRepository sessionRepository;
    private final RedisService redisService;

    private static final String CACHE_KEY_PREFIX = "nvc:practice:session:";
    private static final long CACHE_TTL_HOURS = 24;

    /**
     * 创建练习会话
     */
    public NvcPracticeSessionEntity createSession(Long userId, CreatePracticeSessionRequest request) {
        NvcPracticeSessionEntity session = NvcPracticeSessionEntity.builder()
            .userId(userId)
            .practiceMode(request.practiceMode())
            .scenarioId(request.scenarioId())
            .difficulty(request.difficulty() != null ? request.difficulty() : NvcDifficulty.MEDIUM)
            .currentPhase(NvcSessionPhase.CREATED)
            // 结构化四步模式初始步骤为 OBSERVE
            .currentStep(request.practiceMode() == NvcPracticeMode.STRUCTURED_FOUR_STEP
                ? NvcPracticeStep.OBSERVE : null)
            .build();

        NvcPracticeSessionEntity saved = sessionRepository.save(session);
        log.info("NVC practice session created: sessionId={}, mode={}, userId={}",
            saved.getId(), request.practiceMode(), userId);

        // 写入 Redis 缓存
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

---

## 3.3 NvcPracticeDialogueService — 对话核心逻辑

路径：`app/src/main/java/interview/guide/modules/nvcpractice/service/NvcPracticeDialogueService.java`

### 功能

- 接收用户消息 → 调度 Agent → 获取 AI 回复 → 保存消息 → 返回结果
- 支持非流式和流式（SSE）两种模式
- 管理消息序号
- 对话开始时自动进入 IN_PROGRESS 状态

### 实现要点

```java
package interview.guide.modules.nvcpractice.service;

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
    private final NvcContextBuilder contextBuilder;

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
        PracticeContext context = contextBuilder.build(session);

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
     * 注意：流式模式下，先保存用户消息，AI 回复在流完成后保存
     */
    public Flux<String> sendMessageStream(Long sessionId, String userMessage) {
        // 1-4 同上（获取会话、保存用户消息、构建上下文、Agent 调度）
        NvcPracticeSessionEntity session = sessionService.getSession(sessionId);

        if (session.getCurrentPhase() == NvcSessionPhase.CREATED) {
            session = sessionService.updatePhase(sessionId, NvcSessionPhase.IN_PROGRESS);
        }

        int nextSeq = getNextSequenceNum(sessionId);
        NvcPracticeMessageEntity userMsg = NvcPracticeMessageEntity.builder()
            .sessionId(sessionId)
            .role(NvcMessageRole.USER)
            .content(userMessage)
            .sequenceNum(nextSeq)
            .step(session.getCurrentStep())
            .build();
        messageRepository.save(userMsg);

        PracticeContext context = contextBuilder.build(session);
        AgentDecision decision = orchestrator.decideNextAgent(context);
        sessionService.updateAgentScene(sessionId, decision.scene());

        // 5. 流式获取 AI 回复
        NvcAgentConfigEntity config = orchestrator.getAgentConfig(decision.scene());
        Flux<String> stream = orchestrator.executeAgentStream(decision.scene(), context, userMessage);

        // 6. 收集完整回复并保存（在流结束后）
        // 使用 doOnComplete 或 collect 模式
        StringBuilder fullReply = new StringBuilder();
        return stream.doOnNext(token -> fullReply.append(token))
            .doOnComplete(() -> {
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
    public List<NvcPracticeMessageEntity> getMessages(Long sessionId) {
        return messageRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);
    }

    private int getNextSequenceNum(Long sessionId) {
        return messageRepository.countBySessionId(sessionId);
    }
}
```

---

## 3.4 NvcContextBuilder — 练习上下文构建

路径：`app/src/main/java/interview/guide/modules/nvcpractice/service/NvcContextBuilder.java`

### 功能

- 组装 PracticeContext：加载会话信息、对话历史、用户档案、场景信息、RAG 知识
- 对话历史使用滑动窗口（最近 20 条）

### 实现要点

```java
package interview.guide.modules.nvcpractice.service;

import interview.guide.modules.nvcpractice.dto.PracticeContext;
import interview.guide.modules.nvcpractice.model.*;
import interview.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import interview.guide.modules.nvcpractice.repository.NvcEvaluationRepository;
import interview.guide.modules.nvcscenario.model.NvcScenarioEntity;
import interview.guide.modules.nvcscenario.repository.NvcScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcContextBuilder {

    private final NvcPracticeMessageRepository messageRepository;
    private final NvcEvaluationRepository evaluationRepository;
    private final NvcScenarioRepository scenarioRepository;

    private static final int MAX_HISTORY_MESSAGES = 20;

    /**
     * 构建练习上下文
     */
    public PracticeContext build(NvcPracticeSessionEntity session) {
        // 1. 加载最近的对话消息（滑动窗口）
        List<NvcPracticeMessageEntity> allMessages =
            messageRepository.findBySessionIdOrderBySequenceNumAsc(session.getId());
        List<NvcPracticeMessageEntity> recentMessages = allMessages.size() > MAX_HISTORY_MESSAGES
            ? allMessages.subList(allMessages.size() - MAX_HISTORY_MESSAGES, allMessages.size())
            : allMessages;

        // 2. 加载最近的评估结果
        NvcEvaluationEntity lastEvaluation = evaluationRepository
            .findBySessionIdAndEvaluationType(session.getId(), NvcEvaluationType.REALTIME)
            .orElse(null);

        // 3. 加载场景信息（如果有）
        NvcScenarioEntity scenario = null;
        String scenarioDescription = null;
        if (session.getScenarioId() != null) {
            scenario = scenarioRepository.findById(session.getScenarioId()).orElse(null);
            if (scenario != null) {
                scenarioDescription = buildScenarioDescription(scenario);
            }
        }

        // 4. 构建用户档案摘要（暂时为空，Step 5 实现后填充）
        String userProfileSummary = null;

        // 5. 构建 RAG 上下文（暂时为空，Step 10 实现后填充）
        String ragContext = null;

        return PracticeContext.builder()
            .session(session)
            .recentMessages(recentMessages)
            .lastEvaluation(lastEvaluation)
            .roundCount(allMessages.size() / 2)  // 用户消息数 = 轮次数
            .userProfileSummary(userProfileSummary)
            .scenarioDescription(scenarioDescription)
            .ragContext(ragContext)
            .scenario(scenario)
            .build();
    }

    private String buildScenarioDescription(NvcScenarioEntity scenario) {
        StringBuilder sb = new StringBuilder();
        sb.append("场景：").append(scenario.getTitle()).append("\n");
        sb.append("背景：").append(scenario.getDescription()).append("\n");
        if (scenario.getContext() != null) {
            sb.append("详细背景：").append(scenario.getContext()).append("\n");
        }
        if (scenario.getFocusElements() != null) {
            sb.append("重点练习要素：").append(scenario.getFocusElements()).append("\n");
        }
        sb.append("难度：").append(scenario.getDifficulty()).append("\n");
        return sb.toString();
    }
}
```

---

## 3.5 NvcPracticeController — 练习 API

路径：`app/src/main/java/interview/guide/modules/nvcpractice/controller/NvcPracticeController.java`

```java
package interview.guide.modules.nvcpractice.controller;

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
    @PostMapping("/sessions/{sessionId}/messages")
    public Result<DialogueResponse> sendMessage(
            @PathVariable Long sessionId,
            @RequestBody SendMessageRequest request) {
        DialogueResponse response = dialogueService.sendMessage(sessionId, request.content());
        return Result.success(response);
    }

    /**
     * 发送消息（流式 SSE）
     * 返回 Flux<String>，Spring 自动转为 SSE
     */
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
    public Result<List<NvcPracticeMessageEntity>> getMessages(@PathVariable Long sessionId) {
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
    Long scenarioId,
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

### nvc-dialogue-guide-system.st

路径：`app/src/main/resources/prompts/nvc-dialogue-guide-system.st`

```
你是NVC非暴力沟通练习的对话引导官。你将扮演一个真实的对话对象，与用户进行模拟对话练习。

你的行为准则：
1. 扮演场景中设定的角色，保持角色一致性，用自然真实的语气回应
2. 不要暴露你是AI，也不要突然跳出角色
3. 当用户的NVC表达不完整时，用提问自然引导（如："你能具体说说是什么让你有这种感觉吗？"）
4. 当用户使用评判性语言时，温和引导他们转向观察性表达（如："你刚才说的'总是'，能举个具体的例子吗？"）
5. 当用户表达了好的NVC表达时，给予积极自然的回应（如对方感受到被理解后的软化）
6. 适时表达你自己的感受和需求，为用户示范NVC表达
7. 对话要自然流畅，像真实的人际对话，不要像在上课
8. 每次回复控制在2-4句话，不要太长

场景信息会以[练习场景]的形式提供。
用户档案信息会以[用户档案]的形式提供。
对话历史会自动加载。

注意：你的核心目标是让用户在自然对话中练习NVC，而不是教用户NVC理论。
```

### nvc-difficult-partner-system.st

路径：`app/src/main/resources/prompts/nvc-difficult-partner-system.st`

```
你是NVC非暴力沟通练习中的"困难搭档"。你会扮演一个带有情绪、会用评判和指责方式说话的对话对象。

你的行为准则：
1. 用带有评判、指责、情绪化的语言回应，模拟真实中人们在冲突中的常见表达
2. 常用表达模式：
   - 以偏概全："你总是..."、"你从来不..."
   - 人身攻击："你怎么这么自私/不负责任/不讲道理"
   - 情绪宣泄："我受够了！"、"你根本不在乎！"
   - 反击："你自己呢？你有资格说我吗？"
3. 不要太极端（不要辱骂或威胁），保持在真实可信的范围内
4. 当用户坚持用NVC方式表达时，逐渐软化态度（表现出被理解后的变化）
5. 当用户被激怒或放弃NVC回到对抗模式时，适度收敛，给用户练习空间
6. 在3-4轮对抗后，如果用户一直在努力用NVC，可以表现出明显的态度转变

场景信息会以[练习场景]的形式提供。

重要提醒：你的"困难"表现是有目的的训练设计，帮助用户练习在对抗中保持NVC。
```

---

## 3.8 补充 NvcAgentOrchestrator 的流式方法

在 Step 2 的 `NvcAgentOrchestrator` 中补充以下方法（如果 Step 2 没有加）：

```java
/**
 * 获取 Agent 配置（供 DialogueService 使用）
 */
public NvcAgentConfigEntity getAgentConfig(NvcAgentScene scene) {
    return agentConfigService.getConfig(scene);
}

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

## 3.9 验证清单

```
□ 所有新增类编译通过
□ 项目启动成功
□ API 测试：
  □ POST /api/nvc/practice/sessions 创建会话成功，返回会话信息
  □ GET /api/nvc/practice/sessions?userId=1 返回会话列表
  □ POST /api/nvc/practice/sessions/{id}/messages 发送消息成功，返回 AI 回复
  □ AI 回复内容合理（符合对话引导官的角色设定）
  □ GET /api/nvc/practice/sessions/{id}/messages 返回完整对话历史
  □ POST /api/nvc/practice/sessions/{id}/complete 结束会话成功
□ 流式测试：
  □ POST /api/nvc/practice/sessions/{id}/messages/stream 返回 SSE 流
  □ 流式输出逐字显示，内容与非流式一致
  □ 流结束后消息正确保存到数据库
□ Agent 调度测试：
  □ 首次对话使用 SCENARIO_GENERATOR（如果是场景驱动模式）
  □ 后续对话使用 DIALOGUE_GUIDE
  □ 对话历史正确传递给 Agent（上下文连续）
□ Git 提交："Step 3: Add NVC practice dialogue flow with SSE streaming"
```

---

## 3.10 测试场景

### 测试 1：场景驱动练习端到端

```
1. POST /api/nvc/practice/sessions
   body: { "practiceMode": "SCENARIO", "scenarioId": 1, "difficulty": "MEDIUM" }
   → 创建会话，状态为 CREATED

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

### 测试 2：自由对话练习

```
1. POST /api/nvc/practice/sessions
   body: { "practiceMode": "FREE_DIALOG" }
   → 创建会话

2. POST /api/nvc/practice/sessions/{id}/messages
   body: { "content": "我和我妈总是因为她催婚吵架，我想用NVC的方式来和她沟通" }
   → AI 引导用户用NVC框架描述问题

3. 继续对话...
```
