# NVC 语音练习模块设计文档

> 日期：2026-07-12
>
> 状态：已确认
>
> 参考项目：interview-guide（语音模块）、AI-Meeting（语音模块）

---

## 1. 设计目标

基于 interview-guide 的 voiceinterview 模块，改造为 NVC 语音练习模块。要求：

- **高可用**：ASR 断连自动重连、LLM/TTS 超时优雅降级、WebSocket 断连自动清理
- **高扩展**：ASR/TTS Provider 接口化，未来可换 Provider；Agent 调度复用 nvcpractice 模块
- **可维护**：Handler 瘦身（~300行）、语音管线独立编排、职责清晰分离

---

## 2. 架构总览

### 2.1 改造策略

从 voiceinterview 模块完整复制到 nvcvoice，然后逐文件改为 NVC 逻辑。不修改原模块。

### 2.2 模块依赖关系

```
nvcvoice
├── 复用（不修改）：QwenAsrService, QwenTtsService
├── 复制改造：DashscopeLlmService → NvcVoiceLlmService
├── 复制改造：VoiceInterviewPromptService → NvcVoicePromptService
├── 复制改造：VoiceInterviewWebSocketHandler → NvcVoiceWebSocketHandler（瘦身为~300行）
├── 复制改造：VoiceInterviewService → NvcVoiceService
├── 复制改造：VoiceInterviewEvaluationService → NvcVoiceEvaluationService
├── 新增：VoicePipelineCoordinator（管线编排器）
├── 新增：AsrProvider/TtsProvider 接口 + Qwen 实现
├── 新增集成：NvcAgentOrchestrator（在 Service 层调用）
└── 复制改造：所有 Entity/DTO/Repository/Listener/Config
```

### 2.3 核心设计原则

1. **Handler 瘦身** — Handler 只做 WebSocket 协议层（连接管理、消息路由），业务逻辑委托给 Service
2. **语音管线提取** — 从 Handler 中提取 `VoicePipelineCoordinator`，编排 ASR→合并→LLM→TTS 流程
3. **Provider 接口** — 为 ASR/TTS 定义接口，当前用 Qwen 实现，未来可换
4. **复用已有模式** — 继续用 `AbstractStreamProducer/Consumer`、`LlmProviderRegistry`

### 2.4 两个参考项目的改进

| 维度 | interview-guide 问题 | AI-Meeting 问题 | 本方案改进 |
|------|---------------------|-----------------|-----------|
| Handler 规模 | 1485 行单体 | 540 行单体 | ~300 行，业务逻辑提取到 Pipeline |
| ASR/TTS 抽象 | ❌ 无接口 | ❌ 无接口 | ✅ AsrProvider/TtsProvider 接口 |
| 管线模式 | ❌ Handler 内硬编码 | ❌ ASR/TTS 独立不成管道 | ✅ VoicePipelineCoordinator 编排 |
| 架构分层 | Handler 直接调 Service | api/application/infrastructure 三层 | Handler → Pipeline → Service |

---

## 3. 包结构

```
modules/nvcvoice/
├── config/
│   ├── NvcVoiceProperties.java              # 语音配置（prefix: app.nvc.voice）
│   └── NvcVoiceWebSocketConfig.java         # WebSocket 注册
│
├── controller/
│   └── NvcVoiceController.java              # REST API（会话 CRUD + 评估）
│
├── handler/
│   └── NvcVoiceWebSocketHandler.java        # WebSocket 协议层（~300行，只做路由）
│
├── pipeline/                                # 语音管线
│   ├── VoicePipelineCoordinator.java        # 管线编排器
│   ├── UtteranceMergeBuffer.java            # ASR 语句合并（从 Handler 提取）
│   └── OrderedTtsChunkEmitter.java          # TTS 分片排序（从 Handler 提取）
│
├── service/
│   ├── NvcVoiceService.java                 # 会话生命周期 + 持久化
│   ├── NvcVoiceEvaluationService.java       # NVC 四维度语音评估
│   ├── NvcVoicePromptService.java           # 语音 Prompt 构建
│   ├── NvcVoiceLlmService.java              # LLM 流式调用（句子分割、语音优化）
│   └── provider/                            # Provider 接口层
│       ├── AsrProvider.java                 # ASR 接口
│       ├── TtsProvider.java                 # TTS 接口
│       ├── AsrCallbacks.java                # ASR 回调 record
│       ├── QwenAsrProvider.java             # Qwen ASR 实现
│       └── QwenTtsProvider.java             # Qwen TTS 实现
│
├── model/
│   ├── NvcVoiceSessionEntity.java           # 语音练习会话实体
│   ├── NvcVoiceMessageEntity.java           # 语音对话消息实体
│   ├── NvcVoiceEvaluationEntity.java        # 语音评估结果实体
│   ├── NvcVoiceSessionStatus.java           # 会话状态枚举
│   └── NvcVoiceSessionPhase.java            # 会话阶段枚举
│
├── repository/
│   ├── NvcVoiceSessionRepository.java
│   ├── NvcVoiceMessageRepository.java
│   └── NvcVoiceEvaluationRepository.java
│
├── dto/
│   ├── CreateVoiceSessionRequest.java       # 创建会话请求
│   ├── VoiceSessionResponse.java            # 会话响应
│   ├── VoiceMessageDTO.java                 # 消息 DTO
│   ├── VoiceEvaluationDetailDTO.java        # 评估详情 DTO
│   ├── VoiceEvaluationStatusDTO.java        # 评估状态 DTO
│   ├── WebSocketControlMessage.java         # WebSocket 控制消息
│   └── WebSocketSubtitleMessage.java        # WebSocket 字幕消息
│
└── listener/
    ├── NvcVoiceEvaluateStreamProducer.java  # 评估任务生产者
    └── NvcVoiceEvaluateStreamConsumer.java  # 评估任务消费者
```

**复用的文件（不修改）：**
- `QwenAsrService.java` — ASR 语音识别
- `QwenTtsService.java` — TTS 语音合成
- `AudioRecorder.tsx` / `AudioPlayer.tsx` / `RealtimeSubtitle.tsx` — 前端组件

**复用的枚举（来自 nvcpractice 模块）：**
- `NvcPracticeMode` — SCENARIO / FREE_DIALOG / STRUCTURED_FOUR_STEP
- `NvcDifficulty` — EASY / MEDIUM / HARD
- `NvcAgentScene` — 所有 Agent 场景
- `AsyncTaskStatus` — 评估状态

---

## 4. WebSocket 消息协议

### 4.1 消息类型

| 方向 | type | 说明 | payload |
|------|------|------|---------|
| C→S | `audio` | PCM 音频数据 | `{data: "base64..."}` |
| C→S | `control` | 控制指令 | `{action: "submit"/"pause"/"resume"/"end"}` |
| S→C | `subtitle` | 实时字幕 | `{text: "...", source: "user"/"ai", partial: bool}` |
| S→C | `text` | AI 回复文本 | `{text: "...", agentScene: "..."}` |
| S→C | `audio` | 完整 TTS 音频 | `{data: "base64...", format: "wav"}` |
| S→C | `audio_chunk` | 分片 TTS 音频 | `{data: "base64...", seq: 1, last: false}` |
| S→C | `control` | 服务端控制 | `{action: "pause_warning"/"paused"/"error"}` |

### 4.2 完整数据流

```
用户说话
  ↓ (PCM 16kHz)
AudioRecorder.tsx
  ↓ (WebSocket JSON {type:"audio"})
NvcVoiceWebSocketHandler.handleTextMessage()
  ↓ 路由到 VoicePipelineCoordinator
  ↓
VoicePipelineCoordinator.handleAudioData()
  → AsrProvider.sendAudio(pcm)
  → ASR 回调: onPartial → 发送 subtitle(partial=true)
             onFinal  → 合并到 UtteranceMergeBuffer
  ↓
用户松手（VAD 检测静音 或 手动提交）
  ↓ {type:"control", action:"submit"}
VoicePipelineCoordinator.handleSubmit()
  → 从 MergeBuffer 取出完整文本
  → NvcAgentOrchestrator.decideNextAgent(context)
  → NvcVoiceLlmService.chatStreamSentences(prompt)
    → 每句触发 TtsProvider.synthesize(sentence)
    → OrderedTtsChunkEmitter 按序发送 audio_chunk
  → 保存消息到 DB
  → 发送 subtitle(final) + text(ai reply)
  → 触发实时评估（Redis Stream）
```

---

## 5. 数据模型

### 5.1 NvcVoiceSessionEntity

```java
@Entity
@Table(name = "nvc_voice_sessions")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NvcVoiceSessionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;                          // 用户ID

    @Enumerated(EnumType.STRING)
    private NvcPracticeMode practiceMode;          // SCENARIO / FREE_DIALOG / STRUCTURED_FOUR_STEP

    private Long scenarioId;                       // 场景ID（场景模式时）

    @Enumerated(EnumType.STRING)
    private NvcAgentScene agentScene;              // 当前活跃 Agent

    @Enumerated(EnumType.STRING)
    private NvcDifficulty difficulty;              // EASY / MEDIUM / HARD

    @Enumerated(EnumType.STRING)
    private NvcVoiceSessionPhase currentPhase;     // INTRO / PRACTICE / WRAP_UP

    @Enumerated(EnumType.STRING)
    private NvcVoiceSessionStatus status;          // IN_PROGRESS / PAUSED / COMPLETED / FAILED

    private String llmProvider;                    // LLM 提供商（默认 dashscope）
    private Integer plannedDuration;               // 计划时长（分钟）
    private Integer actualDuration;                // 实际时长
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime pausedAt;
    private LocalDateTime resumedAt;

    @Enumerated(EnumType.STRING)
    private AsyncTaskStatus evaluateStatus;        // 评估状态
    private String evaluateError;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 5.2 NvcVoiceMessageEntity

```java
@Entity
@Table(name = "nvc_voice_messages")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NvcVoiceMessageEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private String messageType;                    // DIALOGUE / USER_SPEECH / AI_SPEECH / SYSTEM
    private String agentScene;                     // 生成此消息的 Agent

    @Column(columnDefinition = "TEXT")
    private String userRecognizedText;             // ASR 识别文本（可回填）

    @Column(columnDefinition = "TEXT")
    private String aiGeneratedText;                // AI 回复文本

    private Integer sequenceNum;                   // 序号
    private LocalDateTime timestamp;
    private LocalDateTime createdAt;
}
```

### 5.3 NvcVoiceEvaluationEntity

```java
@Entity
@Table(name = "nvc_voice_evaluations")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NvcVoiceEvaluationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long sessionId;

    // NVC 四维度评分（0-100）
    private Integer observationScore;
    private Integer feelingScore;
    private Integer needScore;
    private Integer requestScore;
    private Integer empathyScore;
    private Integer overallScore;

    // 语音特有评分
    private Integer fluencyScore;                  // 表达流畅度

    @Column(columnDefinition = "TEXT")
    private String observationDetail;
    @Column(columnDefinition = "TEXT")
    private String feelingDetail;
    @Column(columnDefinition = "TEXT")
    private String needDetail;
    @Column(columnDefinition = "TEXT")
    private String requestDetail;
    @Column(columnDefinition = "TEXT")
    private String overallFeedback;

    @Column(columnDefinition = "TEXT")
    private String strengthsJson;
    @Column(columnDefinition = "TEXT")
    private String improvementsJson;

    private LocalDateTime createdAt;
}
```

### 5.4 新增枚举

```java
public enum NvcVoiceSessionPhase {
    INTRO, PRACTICE, WRAP_UP
}

public enum NvcVoiceSessionStatus {
    IN_PROGRESS, PAUSED, COMPLETED, FAILED
}
```

---

## 6. Service 层设计

### 6.1 NvcVoiceService

```java
@Service
@Slf4j
public class NvcVoiceService {
    // 依赖注入
    private final NvcVoiceSessionRepository sessionRepo;
    private final NvcVoiceMessageRepository messageRepo;
    private final NvcVoiceEvaluationRepository evalRepo;
    private final RedissonClient redisson;
    private final NvcVoiceProperties properties;
    private final NvcVoiceEvaluateStreamProducer evalProducer;
    private final NvcAgentOrchestrator orchestrator;
    private final LlmProviderRegistry llmProviderRegistry;

    // === 会话生命周期 ===
    public VoiceSessionResponse createSession(CreateVoiceSessionRequest req);
    public VoiceSessionResponse endSession(Long sessionId);
    public VoiceSessionResponse pauseSession(Long sessionId, String reason);
    public VoiceSessionResponse resumeSession(Long sessionId);
    public VoiceSessionResponse getSession(Long sessionId);
    public void deleteSession(Long sessionId);

    // === 消息管理 ===
    public void saveMessage(Long sessionId, String userText, String aiText, String agentScene);
    public List<VoiceMessageDTO> getMessages(Long sessionId);

    // === Agent 调度（供 Pipeline 调用）===
    public PracticeContext buildVoiceContext(Long sessionId);
    public AgentDecision decideNextAgent(PracticeContext context);
    public String executeAgent(AgentDecision decision, PracticeContext context, String userText);

    // === 评估 ===
    public void triggerEvaluation(Long sessionId);

    // === 维护 ===
    @Scheduled(fixedRate = 300000)
    public void cleanupStaleSessions();
}
```

### 6.2 Agent 集成流程

```
VoicePipelineCoordinator.handleSubmit(sessionId, state)
  ↓
1. NvcVoiceService.buildVoiceContext(sessionId)
   → 加载会话、最近 20 条消息、最新评估、场景描述
   → 构建 PracticeContext（复用 nvcpractice 的 DTO）
  ↓
2. NvcVoiceService.decideNextAgent(context)
   → NvcAgentOrchestrator.decideNextAgent(context)
   → 返回 AgentDecision(scene, reason, action)
  ↓
3. NvcVoiceService.executeAgent(decision, context, userText)
   → NvcAgentOrchestrator.executeAgent(scene, context, userText)
   → 返回 AI 回复文本
  ↓
4. NvcVoiceLlmService.optimizeForVoice(aiText)
   → 截断到 120 字、去除 markdown
  ↓
5. TtsProvider.synthesize(optimizedText)
   → 返回 PCM 音频
  ↓
6. 保存消息、发送 WebSocket 响应
```

**关键设计决策：**
- `PracticeContext` 直接复用 `nvcpractice` 模块的 DTO，保持两个模块的 Agent 调度一致
- `NvcAgentOrchestrator` 不做任何修改，语音模块通过相同的接口调用
- Agent 配置热更新同样生效（通过 Redis 缓存）

---

## 7. Handler 与管线架构

### 7.1 NvcVoiceWebSocketHandler（~300行）

Handler 只负责：
- WebSocket 连接生命周期管理
- 消息类型路由（audio/control → 对应处理器）
- 并发安全（ConcurrentWebSocketSessionDecorator）
- 回声消除（AI 说话时丢弃麦克风输入）

业务逻辑全部委托给 `VoicePipelineCoordinator`。

```java
@Component
@Slf4j
public class NvcVoiceWebSocketHandler extends TextWebSocketHandler {
    private final VoicePipelineCoordinator pipeline;
    private final NvcVoiceService voiceService;

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 1. 解析 sessionId
        // 2. 创建 SessionState
        // 3. 发送欢迎消息 + 场景描述
        // 4. 启动 ASR 会话（通过 pipeline）
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        switch (msg.type()) {
            case "audio"  -> pipeline.handleAudioData(sessionId, decode(msg.data()), state);
            case "control"-> pipeline.handleControl(sessionId, msg.action(), state, session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 清理：停止 ASR、结束会话（如进行中）
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        // 错误处理
    }
}
```

### 7.2 VoicePipelineCoordinator

```java
@Service
@Slf4j
public class VoicePipelineCoordinator {
    private final AsrProvider asrProvider;
    private final TtsProvider ttsProvider;
    private final NvcVoiceLlmService llmService;
    private final NvcVoiceService voiceService;
    private final NvcVoiceProperties properties;

    private final ExecutorService pipelineExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService mergeScheduler = Executors.newScheduledThreadPool(2);

    // === ASR 阶段 ===
    public void startAsr(String sessionId, SessionState state) {
        asrProvider.startSession(sessionId, new AsrCallbacks(
            text -> sendSubtitle(session, text, true),           // onPartial
            text -> state.mergeBuffer.addSegment(text),          // onFinal
            () -> log.info("ASR ready: {}", sessionId),          // onReady
            t -> handleAsrError(sessionId, state, t)             // onError
        ));
    }

    public void handleAudioData(String sessionId, byte[] pcmData, SessionState state) {
        if (state.isAiSpeakingOrCooldown()) return;  // 回声消除
        asrProvider.sendAudio(sessionId, pcmData);
    }

    // === 提交阶段 ===
    public void handleSubmit(String sessionId, SessionState state, WebSocketSession wsSession) {
        String userText = state.mergeBuffer.flush();
        if (userText.isBlank()) return;

        pipelineExecutor.submit(() -> {
            if (!state.processing.compareAndSet(false, true)) return;  // CAS 防并发
            try {
                PracticeContext ctx = voiceService.buildVoiceContext(sessionId);
                AgentDecision decision = voiceService.decideNextAgent(ctx);
                String aiText = voiceService.executeAgent(decision, ctx, userText);
                String optimized = llmService.optimizeForVoice(aiText);

                byte[] audio = ttsProvider.synthesize(optimized);

                voiceService.saveMessage(sessionId, userText, optimized, decision.scene().name());
                sendTextResponse(wsSession, optimized, decision.scene());
                sendAudioResponse(wsSession, audio);
                voiceService.triggerEvaluation(sessionId);
            } finally {
                state.processing.set(false);
            }
        });
    }

    // === 控制消息 ===
    public void handleControl(String sessionId, String action, SessionState state, WebSocketSession wsSession) {
        switch (action) {
            case "submit" -> handleSubmit(sessionId, state, wsSession);
            case "pause"  -> voiceService.pauseSession(sessionId, "user");
            case "resume" -> voiceService.resumeSession(sessionId);
            case "end"    -> voiceService.endSession(sessionId);
        }
    }
}
```

### 7.3 SessionState

```java
public class SessionState {
    public final AtomicBoolean processing = new AtomicBoolean(false);
    public final AtomicBoolean aiSpeaking = new AtomicBoolean(false);
    public final AtomicLong aiSpeakEndAt = new AtomicLong(0);
    public final UtteranceMergeBuffer mergeBuffer = new UtteranceMergeBuffer();
}
```

### 7.4 UtteranceMergeBuffer

```java
public class UtteranceMergeBuffer {
    private final AtomicReference<String> buffer = new AtomicReference<>("");

    public void addSegment(String segment) {
        // 智能合并：去重、前后缀重叠检测、标点感知拼接
    }

    public String flush() {
        return buffer.getAndSet("");
    }
}
```

---

## 8. Provider 接口

### 8.1 ASR Provider

```java
public interface AsrProvider {
    void startSession(String sessionId, AsrCallbacks callbacks);
    void sendAudio(String sessionId, byte[] pcmData);
    void stopSession(String sessionId);
    boolean isReady(String sessionId);
    void restartSession(String sessionId);
}

public record AsrCallbacks(
    Consumer<String> onPartial,
    Consumer<String> onFinal,
    Runnable onReady,
    Consumer<Throwable> onError
) {}
```

### 8.2 TTS Provider

```java
public interface TtsProvider {
    byte[] synthesize(String text);
    byte[] synthesize(String text, int timeoutSeconds);
}
```

### 8.3 Qwen 实现（包装现有服务）

```java
@Service
@Primary
public class QwenAsrProvider implements AsrProvider {
    private final QwenAsrService asrService;

    @Override
    public void startSession(String sessionId, AsrCallbacks callbacks) {
        asrService.startTranscription(sessionId,
            callbacks.onFinal(), callbacks.onPartial(), callbacks.onReady(), callbacks.onError());
    }

    @Override
    public void sendAudio(String sessionId, byte[] pcmData) {
        asrService.sendAudio(sessionId, pcmData);
    }

    // ... 其他方法直接委托
}

@Service
@Primary
public class QwenTtsProvider implements TtsProvider {
    private final QwenTtsService ttsService;

    @Override
    public byte[] synthesize(String text) {
        return ttsService.synthesize(text);
    }

    @Override
    public byte[] synthesize(String text, int timeoutSeconds) {
        return ttsService.synthesize(text, timeoutSeconds);
    }
}
```

---

## 9. Prompt 服务与评估

### 9.1 NvcVoicePromptService

```java
@Service
public class NvcVoicePromptService {
    private final NvcVoiceProperties properties;

    public String buildSystemPrompt(NvcVoiceSessionEntity session,
                                     NvcAgentConfigEntity agentConfig,
                                     String scenarioDescription) {
        // 1. Agent 角色设定（来自 agentConfig.systemPrompt）
        // 2. NVC 场景描述
        // 3. 语音输出约束
        // 4. 反注入指令
    }

    private String getVoiceConstraints() {
        return """
            【语音输出要求】
            - 回复控制在 120 字以内
            - 不要使用 markdown 格式（如 **加粗**、- 列表）
            - 不要使用特殊符号（如 #、*、[]）
            - 使用口语化表达，适合语音朗读
            - 句子简短，避免长句
            - 适当使用语气词，让对话更自然
            """;
    }

    public String optimizeForVoice(String text) {
        // 1. 去除 markdown
        // 2. 去除特殊符号
        // 3. 截断到 aiQuestionMaxChars（默认 120）在句子边界
        // 4. 空回复返回默认提示
    }
}
```

### 9.2 NvcVoiceEvaluationService

```java
@Service
public class NvcVoiceEvaluationService {
    private final LlmProviderRegistry llmProviderRegistry;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final NvcVoiceEvaluationRepository evalRepo;

    public void evaluateRealtime(Long sessionId, String userText, String aiText) {
        // NVC 四维度单轮评估
    }

    public void generateEvaluation(Long sessionId) {
        // 综合评估（含语音流畅度）
    }

    private Integer evaluateFluency(List<NvcVoiceMessageEntity> messages) {
        // 基于 ASR 转录文本分析流畅度
    }
}
```

### 9.3 评估维度

| 维度 | 说明 | 评分范围 |
|------|------|---------|
| 观察（Observation） | 是否客观描述事实，不带评判 | 0-100 |
| 感受（Feeling） | 是否清晰表达情感 | 0-100 |
| 需求（Need） | 是否识别和表达内在需求 | 0-100 |
| 请求（Request） | 是否提出具体、可行的请求 | 0-100 |
| 共情（Empathy） | 是否理解对方的感受和需求 | 0-100 |
| 流畅度（Fluency） | 语音表达是否自然流畅 | 0-100 |

---

## 10. REST API

### 10.1 端点列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/nvc/voice/sessions` | 创建语音练习会话 |
| GET | `/api/nvc/voice/sessions/{sessionId}` | 获取会话详情 |
| POST | `/api/nvc/voice/sessions/{sessionId}/end` | 结束会话（触发评估） |
| PUT | `/api/nvc/voice/sessions/{sessionId}/pause` | 暂停会话 |
| PUT | `/api/nvc/voice/sessions/{sessionId}/resume` | 恢复会话 |
| GET | `/api/nvc/voice/sessions` | 列出会话（可按 userId/status 过滤） |
| DELETE | `/api/nvc/voice/sessions/{sessionId}` | 删除会话 |
| GET | `/api/nvc/voice/sessions/{sessionId}/messages` | 获取对话历史 |
| GET | `/api/nvc/voice/sessions/{sessionId}/evaluation` | 查询评估状态/结果 |
| POST | `/api/nvc/voice/sessions/{sessionId}/evaluation` | 手动触发评估 |

### 10.2 WebSocket 端点

```
ws://localhost:8080/ws/nvc-voice/{sessionId}
```

---

## 11. 配置

### 11.1 NvcVoiceProperties

```java
@ConfigurationProperties(prefix = "app.nvc.voice")
@Data
public class NvcVoiceProperties {
    // === 语音输出 ===
    private int aiQuestionMaxChars = 120;
    private boolean llmStreamingEnabled = true;
    private int aiStreamPushIntervalMs = 180;
    private int aiStreamMinCharsDelta = 12;

    // === TTS ===
    private int maxConcurrentTtsPerSession = 3;
    private boolean chunkedAudioEnabled = true;
    private int ttsTimeoutSeconds = 8;

    // === 阶段配置 ===
    private PhaseConfig intro = new PhaseConfig(1, 2, 3);
    private PhaseConfig practice = new PhaseConfig(10, 20, 30);
    private PhaseConfig wrapUp = new PhaseConfig(2, 3, 5);

    // === ASR/TTS 配置 ===
    private QwenAsrConfig qwenAsr = new QwenAsrConfig();
    private QwenTtsConfig qwenTts = new QwenTtsConfig();

    // === 暂停超时 ===
    private int pauseWarningSeconds = 270;
    private int pauseTimeoutSeconds = 300;

    // === 限流 ===
    private RateLimitConfig rateLimit = new RateLimitConfig();
}
```

### 11.2 WebSocket 配置

```java
@Configuration
@EnableWebSocket
public class NvcVoiceWebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/nvc-voice/{sessionId}")
            .addInterceptors(new HttpSessionHandshakeInterceptor())
            .setAllowedOrigins(corsProperties.getAllowedOrigins().toArray(new String[0]));
    }
}
```

---

## 12. 错误处理策略

| 层级 | 错误场景 | 处理方式 |
|------|---------|---------|
| ASR | 连接断开 | 自动重连（最多 3 次，间隔 2s） |
| ASR | ASR 未就绪 | 等待就绪（最多 1200ms），超时提示用户 |
| LLM | 超时/429/403 | 翻译为用户友好提示，发送到前端 |
| LLM | 空回复 | 返回"请继续"默认回复 |
| TTS | 超时 | 8s 超时保护，降级为纯文本回复 |
| TTS | 合成失败 | 发送文本回复，不发音频 |
| 评估 | 评估失败 | 标记 FAILED，不影响会话 |
| WebSocket | 连接断开 | 自动结束进行中的会话 |

---

## 13. 前端改动

### 13.1 路由

```tsx
{ path: 'nvc/voice/:sessionId', element: <NvcVoicePage /> }
```

### 13.2 WebSocket 连接

```typescript
const ws = new WebSocket(`ws://localhost:8080/ws/nvc-voice/${sessionId}`);
```

### 13.3 复用组件

- `AudioRecorder.tsx` — 录音（PCM 16kHz + VAD）
- `AudioPlayer.tsx` — 播放（Base64 WAV）
- `RealtimeSubtitle.tsx` — 实时字幕

---

## 14. 实施顺序

| 阶段 | 内容 | 依赖 |
|------|------|------|
| Phase 1 | Provider 接口 + Qwen 实现 | 无 |
| Phase 2 | Entity/枚举/Repository/DTO | 无 |
| Phase 3 | NvcVoiceProperties + 配置类 | 无 |
| Phase 4 | NvcVoicePromptService | Phase 1 |
| Phase 5 | NvcVoiceLlmService | Phase 1, 4 |
| Phase 6 | NvcVoiceService（会话管理） | Phase 2, 3 |
| Phase 7 | UtteranceMergeBuffer + OrderedTtsChunkEmitter | 无 |
| Phase 8 | VoicePipelineCoordinator | Phase 1, 5, 6, 7 |
| Phase 9 | NvcVoiceWebSocketHandler | Phase 8 |
| Phase 10 | NvcVoiceController | Phase 6 |
| Phase 11 | NvcVoiceEvaluationService | Phase 1 |
| Phase 12 | Redis Stream（评估异步） | Phase 11 |
| Phase 13 | NvcVoiceWebSocketConfig | Phase 9 |
| Phase 14 | 前端适配 | Phase 9, 10 |

---

## 15. 验证清单

```
=== 编译与启动 ===
□ 所有新文件编译通过
□ 项目启动成功，无 Bean 注入错误

=== WebSocket 连接 ===
□ ws://localhost:8080/ws/nvc-voice/{sessionId} 能连接
□ 连接后收到欢迎消息 + 场景描述
□ ASR 会话自动启动，状态就绪

=== 语音对话流程 ===
□ 录音 → ASR 识别 → 实时字幕显示
□ 用户松手 → 提交 → Agent 调度 → LLM 回复 → TTS 合成 → 音频播放
□ 多轮对话正常，对话历史正确保存
□ Agent 切换正常（对话引导官/困难搭档/步骤教练）

=== 三种练习模式 ===
□ 场景驱动模式：选择场景 → 创建会话 → 对话练习
□ 自由对话模式：直接创建会话 → 对话练习
□ 结构化四步模式：四步骤引导 → 自动推进

=== 评估 ===
□ 每轮对话后实时评估触发
□ 结束会话后综合评估生成
□ 评估结果包含 NVC 四维度 + 流畅度分数

=== 错误处理 ===
□ ASR 断连自动重连
□ LLM 超时/错误 → 用户友好提示
□ TTS 超时 → 降级为纯文本
□ WebSocket 断连 → 自动结束会话

=== 前端 ===
□ 录音 → 发送 → 收到 AI 回复 → 播放音频
□ 实时字幕显示正常
□ 结束后跳转到评估页面

=== Git 提交 ===
□ "Step 8: Add voice practice module"
```
