# Phase 2：主 Agent + 个人 Wiki（7 天）

> 前置条件：Phase 1 完成（Agent 工具调用框架 + RAG + Skill）
>
> 简历亮点：主 Agent 全能对话入口、个人知识 Wiki 系统、语音出题练习

---

## 一、主 Agent 全能对话入口（Day 9-10）

### 1.1 功能描述

用户通过自然语言与主 Agent 对话，完成所有平台操作：
- 查看练习数据和进度
- 生成练习场景
- 评估 NVC 表达
- 查询/写入个人 Wiki
- 搜索 RAG 知识库
- 查询/更新用户档案
- 启动练习会话

### 1.2 架构

```
NvcMainAgentController
    POST /api/nvc/assistant/chat          # 非流式
    POST /api/nvc/assistant/chat/stream   # 流式 SSE
    GET  /api/nvc/assistant/history       # 对话历史

NvcMainAgentService
    ├── 思考链引擎（CoT）
    │   ├── 理解用户意图
    │   ├── 选择工具（Function Calling）
    │   ├── 执行工具
    │   └── 生成回复
    │
    ├── 对话历史管理（Redis + DB）
    │
    └── 工具调用链记录（用于前端展示）
```

### 1.3 系统 Prompt

```
你是 NVC 非暴力沟通练习平台的 AI 助手。你可以帮助用户：

1. 查看练习数据和进度（调用 dashboard_query）
2. 生成练习场景（调用 scenario_generate）
3. 评估 NVC 表达（调用 evaluate_nvc）
4. 查询/写入个人知识 Wiki（调用 wiki_search / wiki_write）
5. 搜索 RAG 知识库（调用 rag_search）
6. 查询/更新用户档案（调用 profile_query / profile_update）
7. 启动练习会话（调用 practice_start）

请根据用户的需求，思考是否需要调用工具，然后给出回复。
如果需要调用工具，请使用工具获取数据后再回复。
回复要简洁、友好、专业。
```

### 1.4 对话示例

```
用户: 帮我看看我最近的练习情况
主Agent: [调用 dashboard_query]
→ "你最近一周练习了3次，观察维度提升了15%，整体水平在稳步进步..."

用户: 帮我生成一个和同事沟通的场景
主Agent: [调用 scenario_generate]
→ "已为你生成一个职场场景：同事在会议上公开质疑你的方案..."

用户: 我昨天和领导的沟通不太顺利，帮我记录一下
主Agent: [调用 wiki_write]
→ "已记录到你的个人 Wiki '真实场景' 分类中。你可以随时回顾和分析..."

用户: 帮我评估一下这段表达："我注意到你这周三次会议都迟到了，我感到焦虑..."
主Agent: [调用 evaluate_nvc]
→ "评估结果：观察 95 分（描述了具体事实）... 你的观察维度非常好！"
```

### 1.5 数据模型

```java
@Entity
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NvcAssistantMessageEntity {
    @Id @GeneratedValue
    private Long id;
    private Long userId;
    private String role;  // USER / ASSISTANT
    private String content;

    @Column(columnDefinition = "TEXT")
    private String toolCallsJson;  // 工具调用链 JSON

    private LocalDateTime createdAt;
}
```

### 1.6 前端页面

```tsx
// NvcAssistantPage.tsx — 新页面
// 功能：
// - 对话界面（类似 ChatGPT）
// - 工具调用可视化（显示调用了什么工具、参数、结果）
// - 快捷入口（练习、Wiki、仪表盘等）
// - 流式 SSE 对话

// 组件：
// - NvcAssistantChat.tsx — 对话组件
// - NvcToolCallCard.tsx — 工具调用展示卡片
```

### 1.7 新建文件

```
modules/nvcmainagent/
├── controller/NvcMainAgentController.java
├── service/NvcMainAgentService.java
├── dto/AssistantRequest.java
├── dto/AssistantResponse.java
├── dto/ToolCallRecord.java
├── model/NvcAssistantMessageEntity.java
└── repository/NvcAssistantMessageRepository.java

resources/prompts/
└── nvc-main-agent-system.st

frontend/src/
├── pages/NvcAssistantPage.tsx
├── components/nvc/NvcAssistantChat.tsx
├── components/nvc/NvcToolCallCard.tsx
└── api/nvc-assistant.ts
```

---

## 二、个人知识 Wiki（Day 11-13）

### 2.1 功能描述

用户的个人 NVC 知识体系，支持三种来源：
1. **自动沉淀** — 练习结束后 AI 自动生成学习笔记
2. **手动记录** — 用户在 Wiki 页面手动写笔记
3. **AI 辅助** — 用户在主 Agent 对话中说"帮我记录"

### 2.2 数据模型

```java
@Entity
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NvcWikiEntity {
    @Id @GeneratedValue
    private Long id;
    private Long userId;
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;  // Markdown 内容

    @Enumerated(EnumType.STRING)
    private NvcWikiCategory category;  // 分类

    private String tags;  // 逗号分隔的标签

    @Enumerated(EnumType.STRING)
    private NvcWikiSourceType sourceType;  // 来源类型

    private Long sourceSessionId;  // 关联的练习会话
    private String filePath;  // Markdown 文件路径
    private String vectorId;  // 向量化后的 ID

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

public enum NvcWikiCategory {
    PRACTICE_NOTE,     // 练习笔记
    REAL_SCENARIO,     // 真实场景
    LEARNING_SUMMARY   // 学习总结
}

public enum NvcWikiSourceType {
    AUTO_GENERATED,  // 练习后自动生成
    MANUAL,          // 用户手动记录
    AI_ASSISTED      // 主 Agent 辅助生成
}
```

### 2.3 三层存储架构

```
文件系统（直观可编辑）：
wiki/
├── user-123/
│   ├── practice-notes/
│   │   └── 2026-07-16-职场沟通练习.md
│   ├── real-scenarios/
│   │   └── 和领导的沟通困境.md
│   └── learning-summaries/
│       └── NVC四要素理解.md
└── user-456/
    └── ...

数据库（元数据管理）：
NvcWikiEntity（id, userId, title, category, tags, filePath, ...）

向量库（语义检索）：
PgVectorStore（Wiki 内容向量化，按 userId 隔离）
```

### 2.4 自动沉淀流程

```
练习结束
    ↓
NvcPracticeController.completeSession()
    ↓
NvcWikiService.autoGeneratePracticeNote(sessionId)
    ↓
调用 AI 生成学习笔记（Markdown 格式）
    ↓
保存到文件系统 + 数据库
    ↓
向量化（PgVectorStore）
```

**AI 生成 Prompt：**
```
基于以下练习记录，生成一份 NVC 学习笔记（Markdown 格式）：

练习模式：{practiceMode}
练习轮次：{roundCount}
评估分数：观察{obs} 感受{feel} 需求{need} 请求{req}
对话摘要：{summary}

请包含：
1. 本次练习要点（2-3 个）
2. 做得好的地方
3. 需要改进的地方
4. 下次练习建议
```

### 2.5 API 设计

| 方法 | URL | 功能 |
|------|-----|------|
| POST | /api/nvc/wiki | 创建 Wiki |
| GET | /api/nvc/wiki | 列表（按类别/标签筛选） |
| GET | /api/nvc/wiki/{id} | 详情 |
| PUT | /api/nvc/wiki/{id} | 更新 |
| DELETE | /api/nvc/wiki/{id} | 删除 |
| GET | /api/nvc/wiki/search?q=xxx | 语义搜索 |

### 2.6 前端页面

```tsx
// NvcWikiPage.tsx — 新页面
// 功能：
// - 知识列表（按类别/标签筛选）
// - Markdown 编辑器（react-markdown + 编辑模式）
// - AI 总结入口（一键生成学习总结）
// - 语义搜索（基于 RAG）

// 组件：
// - NvcWikiEditor.tsx — Markdown 编辑器
// - NvcWikiCard.tsx — Wiki 条目卡片
// - NvcWikiSearch.tsx — 语义搜索
```

### 2.7 新建文件

```
modules/nvcwiki/
├── controller/NvcWikiController.java
├── service/NvcWikiService.java
├── service/NvcWikiAutoGenerateService.java
├── model/NvcWikiEntity.java
├── model/NvcWikiCategory.java
├── model/NvcWikiSourceType.java
├── dto/WikiRequest.java
├── dto/WikiResponse.java
└── repository/NvcWikiRepository.java

resources/prompts/
└── nvc-wiki-auto-generate.st

frontend/src/
├── pages/NvcWikiPage.tsx
├── components/nvc/NvcWikiEditor.tsx
├── components/nvc/NvcWikiCard.tsx
└── components/nvc/NvcWikiSearch.tsx
```

---

## 三、语音出题练习（Day 14-15）

### 3.1 功能描述

基于用户档案的针对性语音问答练习：
1. 解析用户档案 → 找出薄弱要素
2. 生成针对性题目
3. 语音出题（TTS）
4. 用户语音回答（ASR）
5. 实时评估（NvcEvaluationSkill）
6. 反馈 + 记录到 Wiki

### 3.2 题目生成逻辑

```
用户档案分析：
  观察分数 90 → 跳过
  感受分数 45 → 感受薄弱，出 3 道题
  需求分数 60 → 需求一般，出 2 道题
  请求分数 30 → 请求薄弱，出 3 道题

题目示例：
  感受题："你的伴侣忘记了你们的纪念日，请表达你的真实感受"
  需求题："你感到工作压力很大，识别你背后的深层需求"
  请求题："你希望室友保持公共区域整洁，提出一个具体的请求"
```

### 3.3 API 设计

| 方法 | URL | 功能 |
|------|-----|------|
| POST | /api/nvc/voice-quiz/sessions | 创建出题会话 |
| GET | /api/nvc/voice-quiz/sessions/{id}/question | 获取当前题目 |
| POST | /api/nvc/voice-quiz/sessions/{id}/answer | 提交语音答案 |
| GET | /api/nvc/voice-quiz/sessions/{id}/result | 获取结果 |

### 3.4 技术实现

```
NvcVoiceQuizService
    ├── 解析用户档案 → 找薄弱要素
    ├── 生成题目列表（AI 生成）
    ├── getQuestion(sessionId) → 返回当前题目
    ├── submitAnswer(sessionId, audioData)
    │   ├── ASR 识别（复用 QwenAsrService）
    │   ├── 评估（调用 NvcEvaluationSkill）
    │   └── 反馈 + 保存
    └── getResult(sessionId) → 汇总结果
```

### 3.5 前端页面

```tsx
// NvcVoiceQuizPage.tsx — 新页面
// 功能：
// - 题目展示（文字 + 语音播放）
// - 录音按钮（复用 AudioRecorder 组件）
// - 实时字幕（ASR 结果）
// - 评估结果（分数 + 反馈）
// - 进度条（已答/总题数）
// - 最终总结

// 组件：
// - NvcVoiceQuizCard.tsx — 语音题目卡片
```

### 3.6 新建文件

```
modules/nvcvoicequiz/
├── controller/NvcVoiceQuizController.java
├── service/NvcVoiceQuizService.java
├── model/NvcVoiceQuizSessionEntity.java
├── dto/VoiceQuizRequest.java
└── dto/VoiceQuizResponse.java

resources/prompts/
├── nvc-voice-quiz-question.st
└── nvc-voice-quiz-evaluate.st

frontend/src/
├── pages/NvcVoiceQuizPage.tsx
└── components/nvc/NvcVoiceQuizCard.tsx
```

---

## 四、验收标准

```
□ 主 Agent
  □ 用户能通过对话完成所有操作
  □ 工具调用正常（至少 7 种工具）
  □ 流式 SSE 对话正常
  □ 工具调用链正确记录和展示

□ 个人 Wiki
  □ 自动沉淀：练习结束自动生成学习笔记
  □ 手动记录：用户能手动创建和编辑
  □ AI 辅助：主 Agent 能帮用户记录
  □ 语义搜索正常

□ 语音出题
  □ 基于用户档案生成针对性题目
  □ 语音出题（TTS）正常
  □ 语音回答识别（ASR）正常
  □ 实时评估正常
  □ 结果汇总 + Wiki 记录正常
```
