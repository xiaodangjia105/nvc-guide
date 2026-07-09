# Step 8：语音练习模块

> 目标：改造 voiceinterview 模块为 nvcvoice，实现语音 NVC 对话练习
>
> 预计耗时：3-5 天
>
> 前置条件：Step 2（Agent 调度）、Step 4（评估引擎）已完成
>
> 技术亮点：WebSocket 实时语音、ASR/TTS 全链路、多模态共用 Agent 调度

---

## 8.1 改造思路

voiceinterview 模块的 WebSocket + ASR/TTS 管道**完全复用**，只改以下内容：

| 改什么 | 具体改动 |
|--------|---------|
| 包名/类名 | `voiceinterview` → `nvcvoice`，类名加 `Nvc` 前缀 |
| 阶段管理 | INTRO/TECH/PROJECT/HR → NVC 练习阶段 |
| Prompt 模板 | 面试提示词 → NVC 对话引导提示词 |
| 评估逻辑 | 面试评估 → NVC 四要素评估（复用 Step 4 的评估引擎） |
| 会话模型 | 面试字段 → NVC 字段 |
| Agent 调度 | 无 → 接入 NvcAgentOrchestrator |

---

## 8.2 本步要创建的文件清单

```
app/src/main/java/interview/guide/modules/nvcvoice/
├── model/
│   ├── NvcVoiceSessionEntity.java         ← 语音练习会话实体
│   ├── NvcVoiceMessageEntity.java         ← 语音对话消息实体
│   └── NvcVoiceSessionStatus.java         ← 会话状态枚举
├── repository/
│   ├── NvcVoiceSessionRepository.java
│   └── NvcVoiceMessageRepository.java
├── dto/
│   ├── CreateVoiceSessionRequest.java
│   ├── VoiceSessionResponse.java
│   └── VoiceEvaluationResult.java
├── service/
│   ├── NvcVoiceService.java               ← 语音练习核心服务
│   ├── NvcVoiceEvaluationService.java     ← 语音评估（复用评估引擎）
│   └── NvcVoiceSessionCache.java          ← Redis 缓存
├── handler/
│   └── NvcVoiceWebSocketHandler.java      ← WebSocket 处理器
├── config/
│   ├── NvcVoiceProperties.java            ← 语音配置
│   └── NvcVoiceWebSocketConfig.java       ← WebSocket 配置
├── controller/
│   └── NvcVoiceController.java            ← 语音练习 API
└── listener/
    ├── NvcVoiceEvaluateStreamProducer.java
    └── NvcVoiceEvaluateStreamConsumer.java
```

复用的文件（不改或微改）：
- `QwenAsrService.java` — 直接复用
- `QwenTtsService.java` — 直接复用
- `DashscopeLlmService.java` — 直接复用
- `AudioRecorder.tsx` / `AudioPlayer.tsx` / `RealtimeSubtitle.tsx` — 直接复用

---

## 8.3 改造步骤

### 第一步：复制 voiceinterview 目录

```
从：app/src/main/java/interview/guide/modules/voiceinterview/
到：  app/src/main/java/interview/guide/modules/nvcvoice/
```

### 第二步：重命名所有文件

| 原文件名 | 新文件名 |
|---------|---------|
| `VoiceInterviewSessionEntity.java` | `NvcVoiceSessionEntity.java` |
| `VoiceInterviewMessageEntity.java` | `NvcVoiceMessageEntity.java` |
| `VoiceInterviewSessionStatus.java` | `NvcVoiceSessionStatus.java` |
| `VoiceInterviewService.java` | `NvcVoiceService.java` |
| `VoiceInterviewWebSocketHandler.java` | `NvcVoiceWebSocketHandler.java` |
| `VoiceInterviewController.java` | `NvcVoiceController.java` |
| `VoiceInterviewProperties.java` | `NvcVoiceProperties.java` |
| `VoiceInterviewWebSocketConfig.java` | `NvcVoiceWebSocketConfig.java` |
| `VoiceEvaluateStreamProducer.java` | `NvcVoiceEvaluateStreamProducer.java` |
| `VoiceEvaluateStreamConsumer.java` | `NvcVoiceEvaluateStreamConsumer.java` |

### 第三步：修改 Entity 字段

**NvcVoiceSessionEntity** 改动：

```java
// 删除面试相关字段
// - interviewDirection
// - jobDescription
// - resumeText

// 新增 NVC 相关字段
@Column(name = "practice_mode", length = 20)
@Enumerated(EnumType.STRING)
private NvcPracticeMode practiceMode;  // SCENARIO / FREE_DIALOG

@Column(name = "scenario_id")
private Long scenarioId;

@Column(name = "agent_scene", length = 50)
private String agentScene;

@Enumerated(EnumType.STRING)
@Column(length = 10)
private NvcDifficulty difficulty;
```

**NvcVoiceMessageEntity** 改动：

```java
// 新增字段
@Column(name = "agent_scene", length = 50)
private String agentScene;
```

### 第四步：修改阶段管理

原来的语音面试有 4 个阶段：INTRO → TECH → PROJECT → HR

NVC 语音练习简化为：
- `INTRO` — 开场介绍练习场景
- `PRACTICE` — 对话练习（主阶段）
- `WRAP_UP` — 结束总结

在 `NvcVoiceService` 中修改阶段转换逻辑。

### 第五步：修改 WebSocket Handler

核心改动：

```java
// 1. 修改 WebSocket 路径
// 原：/ws/voice-interview/{sessionId}
// 新：/ws/nvc-voice/{sessionId}

// 2. 修改开场白逻辑
// 原：发送面试开场白 + 预合成音频
// 新：发送 NVC 练习场景描述 + 对方角色的第一句话

// 3. 修改 LLM 调用逻辑
// 原：直接调用 DashscopeLlmService
// 新：通过 NvcAgentOrchestrator 调度 Agent 后调用

// 4. 修改评估逻辑
// 原：面试评估（评分、追问）
// 新：NVC 四要素评估（复用 NvcEvaluationService）
```

### 第六步：修改 Service 层

**NvcVoiceService** 核心改动：

```java
// 注入 NvcAgentOrchestrator
private final NvcAgentOrchestrator orchestrator;

// 修改对话逻辑
public String chat(Long sessionId, String userText) {
    NvcVoiceSessionEntity session = getSession(sessionId);

    // 构建练习上下文
    PracticeContext context = buildVoiceContext(session, userText);

    // Agent 调度
    AgentDecision decision = orchestrator.decideNextAgent(context);

    // 执行 Agent 对话
    return orchestrator.executeAgent(decision.scene(), context, userText);
}
```

### 第七步：修改 Controller

```java
@RestController
@RequestMapping("/api/nvc/voice")
public class NvcVoiceController {

    @PostMapping("/sessions")
    public Result<VoiceSessionResponse> createSession(...)

    @GetMapping("/sessions/{sessionId}")
    public Result<VoiceSessionResponse> getSession(...)

    @PostMapping("/sessions/{sessionId}/end")
    public Result<VoiceSessionResponse> endSession(...)
}
```

### 第八步：修改 WebSocket 配置

```java
@Configuration
@EnableWebSocket
public class NvcVoiceWebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/nvc-voice/{sessionId}")
            .addInterceptors(new HttpSessionHandshakeInterceptor())
            .setAllowedOrigins("*");
    }
}
```

---

## 8.4 复用的文件（不改）

以下文件从 voiceinterview 直接复制，**内容不改**：

| 文件 | 说明 |
|------|------|
| `QwenAsrService.java` | ASR 语音识别，完全通用 |
| `QwenTtsService.java` | TTS 语音合成，完全通用 |
| `DashscopeLlmService.java` | LLM 流式调用，完全通用 |
| `WebSocketControlMessage.java` | WebSocket 控制消息 |
| `WebSocketSubtitleMessage.java` | WebSocket 字幕消息 |
| `OrderedTtsChunkEmitter.java`（如果存在） | TTS 分片排序 |

---

## 8.5 前端改动

### 修改 WebSocket 路径

```typescript
// 原
const ws = new WebSocket(`ws://localhost:8080/ws/voice-interview/${sessionId}`);
// 新
const ws = new WebSocket(`ws://localhost:8080/ws/nvc-voice/${sessionId}`);
```

### 修改页面路由

```tsx
// 原
{ path: 'voice-interview/:sessionId', element: <VoiceInterviewPage /> }
// 新
{ path: 'nvc/voice/:sessionId', element: <NvcVoicePage /> }
```

### 复用组件

以下组件直接复用：
- `AudioRecorder.tsx` — 录音
- `AudioPlayer.tsx` — 播放
- `RealtimeSubtitle.tsx` — 实时字幕

---

## 8.6 验证清单

```
□ NvcVoice 模块所有文件编译通过
□ 项目启动成功
□ WebSocket 连接测试：
  □ ws://localhost:8080/ws/nvc-voice/{sessionId} 能连接
  □ 连接后收到欢迎消息 + 场景描述
  □ 发送音频数据后收到 AI 回复文本 + TTS 音频
□ 对话流程测试：
  □ 语音对话多轮正常
  □ Agent 调度正常（对话引导官/困难搭档切换）
  □ 对话历史正确保存
□ 评估测试：
  □ 结束会话后有评估记录
  □ 评估结果包含四维度分数
□ 前端测试：
  □ 录音 → 发送 → 收到 AI 回复 → 播放音频
  □ 实时字幕显示正常
  □ 结束后跳转到评估页面
□ Git 提交："Step 8: Add voice practice module"
```
