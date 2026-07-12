# Step 8: 语音练习模块 - 完成记录

## 完成时间

2026-07-12

## 设计方案

采用**模块化管线架构**，解决了两个参考项目（interview-guide、AI-Meeting）共同的问题：
- Handler 瘦身：从 1485 行缩减到 ~300 行
- ASR/TTS Provider 接口化：支持未来换 Provider
- 语音管线独立编排：VoicePipelineCoordinator 负责 ASR→合并→LLM→TTS 流程

## 实现内容

### 新增文件（35个）

| 文件 | 类型 | 职责 |
|------|------|------|
| **Provider 接口层** | | |
| AsrProvider.java | 接口 | ASR 语音识别抽象 |
| TtsProvider.java | 接口 | TTS 语音合成抽象 |
| AsrCallbacks.java | Record | ASR 回调函数集合 |
| QwenAsrProvider.java | 实现 | Qwen ASR 实现（包装 QwenAsrService） |
| QwenTtsProvider.java | 实现 | Qwen TTS 实现（包装 QwenTtsService） |
| **Entity 层** | | |
| NvcVoiceSessionEntity.java | Entity | 语音练习会话实体 |
| NvcVoiceMessageEntity.java | Entity | 语音对话消息实体 |
| NvcVoiceEvaluationEntity.java | Entity | 语音评估结果实体 |
| NvcVoiceSessionPhase.java | 枚举 | 会话阶段（INTRO/PRACTICE/WRAP_UP） |
| NvcVoiceSessionStatus.java | 枚举 | 会话状态（IN_PROGRESS/PAUSED/COMPLETED/FAILED） |
| **Repository 层** | | |
| NvcVoiceSessionRepository.java | Repository | 会话数据访问 |
| NvcVoiceMessageRepository.java | Repository | 消息数据访问 |
| NvcVoiceEvaluationRepository.java | Repository | 评估数据访问 |
| **DTO 层** | | |
| CreateVoiceSessionRequest.java | DTO | 创建会话请求 |
| VoiceSessionResponse.java | DTO | 会话响应 |
| VoiceMessageDTO.java | DTO | 消息 DTO |
| VoiceEvaluationDetailDTO.java | DTO | 评估详情 DTO |
| VoiceEvaluationStatusDTO.java | DTO | 评估状态 DTO |
| WebSocketControlMessage.java | DTO | WebSocket 控制消息 |
| WebSocketSubtitleMessage.java | DTO | WebSocket 字幕消息 |
| **管线层** | | |
| VoicePipelineCoordinator.java | Service | 语音管线编排器 |
| UtteranceMergeBuffer.java | 工具 | ASR 语句合并缓冲 |
| OrderedTtsChunkEmitter.java | 工具 | TTS 分片排序发射 |
| SessionState.java | 工具 | WebSocket 会话状态 |
| **Service 层** | | |
| NvcVoiceService.java | Service | 会话生命周期 + Agent 调度 |
| NvcVoiceLlmService.java | Service | LLM 流式调用（句子分割） |
| NvcVoicePromptService.java | Service | 语音 Prompt 构建 |
| NvcVoiceEvaluationService.java | Service | NVC 四维度语音评估 |
| **Handler/Controller** | | |
| NvcVoiceWebSocketHandler.java | Handler | WebSocket 协议层（瘦 Handler） |
| NvcVoiceController.java | Controller | REST API |
| **配置** | | |
| NvcVoiceProperties.java | 配置 | 语音配置属性 |
| NvcVoiceWebSocketConfig.java | 配置 | WebSocket 注册 |
| **异步任务** | | |
| NvcVoiceEvaluateStreamProducer.java | Listener | 评估任务生产者 |
| NvcVoiceEvaluateStreamConsumer.java | Listener | 评估任务消费者 |

### 修改文件（1个）

| 文件 | 修改内容 |
|------|----------|
| AsyncTaskStreamConstants.java | 添加 NVC 语音评估 Stream 常量 |

## 架构亮点

### 1. Handler 瘦身（~300行 vs 原 1485行）
- Handler 只做 WebSocket 协议层（连接管理、消息路由）
- 业务逻辑全部委托给 VoicePipelineCoordinator

### 2. ASR/TTS Provider 接口
```java
public interface AsrProvider {
    void startSession(String sessionId, AsrCallbacks callbacks);
    void sendAudio(String sessionId, byte[] pcmData);
    void stopSession(String sessionId);
    boolean isReady(String sessionId);
    void restartSession(String sessionId, AsrCallbacks callbacks);
}

public interface TtsProvider {
    byte[] synthesize(String text);
    byte[] synthesize(String text, int timeoutSeconds);
}
```

### 3. 语音管线编排
```java
VoicePipelineCoordinator.handleAudioData()  // ASR
VoicePipelineCoordinator.handleSubmit()     // 合并 → Agent → LLM → TTS
VoicePipelineCoordinator.handleControl()    // 暂停/恢复/结束
```

### 4. Agent 调度集成
- 复用 nvcpractice 模块的 NvcAgentOrchestrator
- PracticeContext 共用，保持两个模块的 Agent 调度一致

## 待完善项

1. **PracticeContext 构建**：当前使用简化构建，需要完善将 NvcVoiceSessionEntity 转换为 NvcPracticeSessionEntity
2. **AgentConfig 集成**：需要从 NvcAgentConfigEntity 获取 systemPrompt
3. **场景描述注入**：需要从场景库获取场景描述
4. **前端适配**：需要创建 NvcVoicePage 组件
5. **集成测试**：需要添加 WebSocket 集成测试

## 提交记录

```
ebe377c feat(step8): add NVC voice practice module
```
