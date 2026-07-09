# NVC 非暴力沟通练习助手 — 完整改造方案

> 基于 interview-guide 项目二次改造，参考 AI-Meeting 的 Agent 设计模式
>
> 编写日期：2026-07-08

---

## 目录

1. [项目概述与定位](#一项目概述与定位)
2. [系统架构设计](#二系统架构设计)
3. [与 interview-guide 改造对照表](#三与-interview-guide-改造对照表)
4. [数据库表设计](#四数据库表设计)
5. [Agent 多角色体系设计](#五agent-多角色体系设计)
6. [三种练习模式流程设计](#六三种练习模式流程设计)
7. [用户档案系统设计](#七用户档案系统设计)
8. [RAG 知识库设计](#八rag-知识库设计)
9. [技术亮点清单与实现思路](#九技术亮点清单与实现思路)
10. [前端页面规划](#十前端页面规划)
11. [分阶段实施计划](#十一分阶段实施计划)
12. [简历写法参考](#十二简历写法参考)

---

## 一、项目概述与定位

### 1.1 项目名称

**NVC 非暴力沟通练习助手**（NVC Practice Assistant）

### 1.2 定位

- **主要用途**：作品集 / 简历项目 / 毕业设计
- **目标用户**：希望提升沟通能力的个人用户
- **核心价值**：通过 AI 多角色对话，帮助用户在安全环境中练习非暴力沟通的四要素（观察、感受、需求、请求）

### 1.3 核心功能

| 功能模块 | 说明 |
|---------|------|
| 🎯 多模式练习 | 场景驱动练习、自由对话练习、结构化四步练习 |
| 🤖 多 Agent 协同 | 场景生成官、对话引导官、困难搭档、NVC 评估官、共情教练、知识顾问 |
| 👤 个人档案系统 | NVC 能力画像、沟通背景、性格分析、真实沟通记录分析 |
| 📚 RAG 知识库 | NVC 理论文档向量化，个性化检索增强对话 |
| 🎤 语音练习 | WebSocket 实时语音对话，ASR/TTS 全链路 |
| 📊 数据可视化 | 四维度能力雷达图、练习趋势、薄弱环节分析 |
| 📄 练习报告 | 结构化评估 + PDF 导出 |
| 🔄 Agent 自动调度 | 练习流程自动推进，低分自动加练 |
| ⚙️ Agent 配置热更新 | 运行时修改 Agent 提示词/模型/参数 |

### 1.4 技术栈（继承 interview-guide）

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 4.0 + Java 21（虚拟线程） |
| AI 框架 | Spring AI 2.0（OpenAI 兼容协议，支持多 Provider） |
| 数据库 | PostgreSQL 16 + pgvector（向量搜索 1024 维 COSINE） |
| 缓存/消息 | Redisson（Redis）+ Redis Stream 异步任务 |
| 对象存储 | S3 兼容（MinIO / RustFS） |
| 文档解析 | Apache Tika |
| PDF 导出 | iText 8 |
| 语音 | DashScope Qwen3 ASR/TTS |
| 前端 | React 18 + TypeScript + Vite + TailwindCSS 4 |
| 构建 | Gradle 8.14 |

---

## 二、系统架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端（React 18）                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │ 文字练习  │ │ 语音练习  │ │ 档案管理  │ │ 数据仪表盘│           │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘           │
│       │  SSE/REST   │ WebSocket  │  REST      │  REST           │
└───────┼─────────────┼────────────┼────────────┼─────────────────┘
        │             │            │            │
┌───────┼─────────────┼────────────┼────────────┼─────────────────┐
│       ▼             ▼            ▼            ▼   后端            │
│  ┌─────────────────────────────────────────────────────┐        │
│  │                   Controller 层                      │        │
│  │  NvcPracticeController  NvcVoiceController           │        │
│  │  NvcProfileController   NvcReportController          │        │
│  │  KnowledgeBaseController（复用） LlmProviderController（复用）││
│  └─────────────────────┬───────────────────────────────┘        │
│                        ▼                                        │
│  ┌─────────────────────────────────────────────────────┐        │
│  │                   Service 层                         │        │
│  │  ┌──────────────────────────────────────────────┐   │        │
│  │  │           NvcAgentOrchestrator                │   │        │
│  │  │  （Agent 调度中心：场景生成 → 对话引导 → 评估）    │   │        │
│  │  └──────────┬───────────────────────────────────┘   │        │
│  │             │                                        │        │
│  │  ┌──────────▼──────────┐  ┌─────────────────────┐  │        │
│  │  │ NvcPracticeService  │  │ NvcEvaluationService │  │        │
│  │  │ （文字练习核心）      │  │ （四要素评估引擎）     │  │        │
│  │  └─────────────────────┘  └─────────────────────┘  │        │
│  │  ┌─────────────────────┐  ┌─────────────────────┐  │        │
│  │  │ NvcVoiceService     │  │ NvcProfileService   │  │        │
│  │  │ （语音练习核心）      │  │ （用户档案管理）      │  │        │
│  │  └─────────────────────┘  └─────────────────────┘  │        │
│  │  ┌─────────────────────┐  ┌─────────────────────┐  │        │
│  │  │ NvcScenarioService  │  │ NvcReportService    │  │        │
│  │  │ （场景库管理）        │  │ （练习报告生成）      │  │        │
│  │  └─────────────────────┘  └─────────────────────┘  │        │
│  └─────────────────────────────────────────────────────┘        │
│                        │                                        │
│  ┌─────────────────────▼───────────────────────────────┐        │
│  │                Infrastructure 层                      │        │
│  │  LlmProviderRegistry    StructuredOutputInvoker      │        │
│  │  RedisService           InterviewSessionCache → NvcSessionCache│
│  │  FileStorageService     PdfExportService             │        │
│  │  AbstractStreamProducer/Consumer（异步任务）           │        │
│  └─────────────────────────────────────────────────────┘        │
│                        │                                        │
│  ┌─────────────────────▼───────────────────────────────┐        │
│  │                   数据层                              │        │
│  │  PostgreSQL + pgvector    Redis（缓存 + Stream）      │        │
│  │  S3 / MinIO（文件存储）                               │        │
│  └─────────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 模块划分

```
nvc.guide/
├── App.java                              # @SpringBootApplication + @EnableScheduling
│
├── common/                               # 通用基础能力（直接复用，微调）
│   ├── ai/                               #   LlmProviderRegistry、StructuredOutputInvoker
│   ├── annotation/ + aspect/             #   @RateLimit 限流
│   ├── async/                            #   Redis Stream 异步任务模板
│   ├── config/                           #   CORS、S3、ObjectMapper、OpenAPI
│   ├── constant/                         #   常量
│   ├── exception/                        #   ErrorCode（重新划分域范围）
│   ├── evaluation/                       #   → 改造为 NvcUnifiedEvaluationEngine
│   └── result/                           #   Result<T> 统一响应
│
├── infrastructure/                       # 技术基础设施（直接复用）
│   ├── export/                           #   PdfExportService
│   ├── file/                             #   文件解析、存储
│   ├── mapper/                           #   MapStruct 映射器（重新生成）
│   └── redis/                            #   RedisService、NvcSessionCache
│
└── modules/                              # 业务模块
    ├── nvcpractice/                      # 🆕 NVC 文字练习（核心模块）
    │   ├── controller/
    │   ├── service/
    │   ├── model/
    │   ├── dto/
    │   ├── repository/
    │   └── agent/                        #   Agent 调度相关
    │
    ├── nvcvoice/                         # 🆕 NVC 语音练习（改造自 voiceinterview）
    │   ├── controller/
    │   ├── service/
    │   ├── handler/                      #   WebSocket Handler
    │   ├── model/
    │   ├── dto/
    │   └── config/
    │
    ├── nvcprofile/                       # 🆕 用户档案系统
    │   ├── controller/
    │   ├── service/
    │   ├── model/
    │   ├── dto/
    │   └── repository/
    │
    ├── nvcscenario/                      # 🆕 场景库管理
    │   ├── controller/
    │   ├── service/
    │   ├── model/
    │   └── dto/
    │
    ├── nvcreport/                        # 🆕 练习报告与数据可视化
    │   ├── controller/
    │   ├── service/
    │   └── dto/
    │
    ├── knowledgebase/                    # 复用（改 prompt + 新增 NVC 知识）
    │   └── ...
    │
    └── llmprovider/                      # 直接复用
        └── ...
```

---

## 三、与 interview-guide 改造对照表

### 3.1 模块级对照

| interview-guide 模块 | NVC 改造方式 | 改动程度 |
|---------------------|-------------|---------|
| `common/` | 直接复用，ErrorCode 域重新划分 | 🟢 小改 |
| `infrastructure/` | 直接复用 | 🟢 不改 |
| `modules/interview/` | → `modules/nvcpractice/`，业务逻辑全换 | 🔴 大改 |
| `modules/voiceinterview/` | → `modules/nvcvoice/`，改 prompt + 阶段管理 | 🟡 中改 |
| `modules/knowledgebase/` | 保留，新增 NVC 知识库内容 | 🟢 小改 |
| `modules/resume/` | 删除 | ⚫ 删除 |
| `modules/interviewschedule/` | 删除 | ⚫ 删除 |
| `modules/llmprovider/` | 直接复用 | 🟢 不改 |
| `frontend/` | 页面全换，基础设施保留 | 🔴 大改 |

### 3.2 Entity 级对照

| interview-guide Entity | NVC 改造方式 |
|------------------------|-------------|
| `InterviewSessionEntity` | → `NvcPracticeSessionEntity`，字段全换 |
| `InterviewAnswerEntity` | → `NvcPracticeMessageEntity`，改为对话消息 |
| `InterviewEvaluationEntity`` | → `NvcEvaluationEntity`，改为四要素评估 |
| `VoiceInterviewSessionEntity` | → `NvcVoiceSessionEntity`，改阶段管理 |
| `VoiceInterviewMessageEntity` | → `NvcVoiceMessageEntity`，改字段 |
| `VoiceInterviewEvaluationEntity` | → `NvcVoiceEvaluationEntity` |
| `KnowledgeBaseEntity` | 保留 |
| `KnowledgeBaseDocumentEntity` | 保留 |
| `RagChatSessionEntity` | 保留 |
| `RagChatMessageEntity` | 保留 |
| `ResumeEntity` | 删除 |
| `LlmProviderEntity` | 保留 |
| 🆕 `NvcUserProfileEntity` | 新增：用户个人档案 |
| 🆕 `NvcUserAbilityScoreEntity` | 新增：NVC 能力评分记录 |
| 🆕 `NvcScenarioEntity` | 新增：练习场景库 |
| 🆕 `NvcCommunicationRecordEntity` | 新增：真实沟通记录 |
| 🆕 `NvcAgentConfigEntity` | 新增：Agent 配置（热更新） |

### 3.3 Service 级对照

| interview-guide Service | NVC 改造方式 |
|------------------------|-------------|
| `InterviewSessionService` | → `NvcPracticeSessionService` |
| `InterviewQuestionService` | 删除（NVC 不需要预生成题目） |
| `UnifiedEvaluationService` | → `NvcUnifiedEvaluationEngine` |
| `VoiceInterviewService` | → `NvcVoiceService` |
| `DashscopeLlmService` | 直接复用（语音 LLM 流式调用） |
| `QwenAsrService` | 直接复用 |
| `QwenTtsService` | 直接复用 |
| `KnowledgeBaseQueryService` | 保留，新增 NVC 查询优化 |
| `KnowledgeBaseVectorService` | 保留 |
| `LlmProviderService` | 直接复用 |
| 🆕 `NvcAgentOrchestrator` | 新增：Agent 调度中心 |
| 🆕 `NvcProfileService` | 新增：用户档案管理 |
| 🆕 `NvcScenarioService` | 新增：场景库管理 |
| 🆕 `NvcReportService` | 新增：练习报告生成 |
| 🆕 `NvcDashboardService` | 新增：数据可视化 |

### 3.4 Controller / API 级对照

| interview-guide API | NVC 改造方式 |
|--------------------|-------------|
| `POST /api/interview/sessions` | → `POST /api/nvc/practice/sessions` |
| `GET /api/interview/sessions/{id}/question` | 删除 |
| `POST /api/interview/sessions/{id}/answers` | → `POST /api/nvc/practice/sessions/{id}/messages` |
| `POST /api/interview/sessions/{id}/complete` | → `POST /api/nvc/practice/sessions/{id}/complete` |
| `GET /api/interview/sessions/{id}/report` | → `GET /api/nvc/practice/sessions/{id}/report` |
| `POST /api/voice-interview/sessions` | → `POST /api/nvc/voice/sessions` |
| `GET /api/voice-interview/sessions/{id}` | → `GET /api/nvc/voice/sessions/{id}` |
| `POST /api/voice-interview/sessions/{id}/end` | → `POST /api/nvc/voice/sessions/{id}/end` |
| `/ws/voice-interview/{sessionId}` | → `/ws/nvc-voice/{sessionId}` |
| `/api/knowledge-bases/**` | 保留 |
| `/api/llm-providers/**` | 保留 |
| 🆕 `POST /api/nvc/profile/analyze` | 新增：分析沟通记录，更新档案 |
| 🆕 `GET /api/nvc/profile/me` | 新增：获取个人档案 |
| 🆕 `GET /api/nvc/profile/ability-radar` | 新增：NVC 能力雷达图数据 |
| 🆕 `GET /api/nvc/scenarios` | 新增：获取场景库 |
| 🆕 `POST /api/nvc/scenarios/generate` | 新增：AI 生成练习场景 |
| 🆕 `GET /api/nvc/report/summary` | 新增：练习总览报告 |
| 🆕 `GET /api/nvc/dashboard/trends` | 新增：练习趋势数据 |
| 🆕 `GET /api/nvc/agents/configs` | 新增：获取 Agent 配置 |
| 🆕 `PUT /api/nvc/agents/configs/{scene}` | 新增：更新 Agent 配置（热更新） |

### 3.5 Prompt 模板对照

| interview-guide Prompt | NVC 改造方式 |
|-----------------------|-------------|
| `interview-question-skill-system.st` | 删除 |
| `interview-question-skill-user.st` | 删除 |
| `interview-question-resume-system.st` | 删除 |
| `interview-question-resume-user.st` | 删除 |
| `interview-evaluation-system.st` | → `nvc-evaluation-system.st` |
| `interview-evaluation-user.st` | → `nvc-evaluation-user.st` |
| `interview-evaluation-summary-system.st` | → `nvc-evaluation-summary-system.st` |
| `interview-evaluation-summary-user.st` | → `nvc-evaluation-summary-user.st` |
| `knowledgebase-query-system.st` | 保留 |
| `knowledgebase-query-user.st` | 保留 |
| `knowledgebase-query-rewrite.st` | 保留 |
| `resume-analysis-system.st` | 删除 |
| `resume-analysis-user.st` | 删除 |
| `jd-parse-system.st` | 删除 |
| 🆕 `nvc-scenario-generate-system.st` | 新增：场景生成 |
| 🆕 `nvc-dialogue-guide-system.st` | 新增：对话引导（正常模式） |
| 🆕 `nvc-difficult-partner-system.st` | 新增：困难搭档（对抗模式） |
| 🆕 `nvc-empathy-coach-system.st` | 新增：共情教练 |
| 🆕 `nvc-profile-analyze-system.st` | 新增：用户档案分析 |
| 🆕 `nvc-report-generate-system.st` | 新增：练习报告生成 |
| 🆕 `nvc-step-observe-system.st` | 新增：结构化练习-观察步骤 |
| 🆕 `nvc-step-feeling-system.st` | 新增：结构化练习-感受步骤 |
| 🆕 `nvc-step-need-system.st` | 新增：结构化练习-需求步骤 |
| 🆕 `nvc-step-request-system.st` | 新增：结构化练习-请求步骤 |

### 3.6 前端页面对照

| interview-guide 页面 | NVC 改造方式 |
|---------------------|-------------|
| `InterviewHubPage` | → `NvcPracticeHubPage`（练习模式选择） |
| `InterviewPage` | → `NvcPracticePage`（文字对话练习） |
| `VoiceInterviewPage` | → `NvcVoicePage`（语音对话练习） |
| `VoiceInterviewEvaluationPage` | → `NvcVoiceEvaluationPage` |
| `InterviewHistoryPage` | → `NvcHistoryPage`（练习历史） |
| `ResumeDetailPage` | 删除 |
| `UploadPage` | 删除 |
| `HistoryPage`（简历） | 删除 |
| `KnowledgeBaseManagePage` | 保留 |
| `KnowledgeBaseQueryPage` | 保留 |
| `KnowledgeBaseUploadPage` | 保留 |
| `InterviewSchedulePage` | 删除 |
| `SettingsPage` | 保留（LLM Provider 配置） |
| 🆕 `NvcProfilePage` | 新增：个人档案管理 |
| 🆕 `NvcDashboardPage` | 新增：数据可视化仪表盘 |
| 🆕 `NvcScenarioLibraryPage` | 新增：场景库浏览 |
| 🆕 `NvcAgentConfigPage` | 新增：Agent 配置管理 |
| 🆕 `NvcReportPage` | 新增：练习报告详情 |

### 3.7 前端组件对照

| interview-guide 组件 | NVC 改造方式 |
|---------------------|-------------|
| `InterviewChatPanel` | → `NvcChatPanel`（NVC 对话面板） |
| `InterviewPanel` | → `NvcPracticePanel` |
| `UnifiedInterviewModal` | → `NvcPracticeConfigModal`（练习配置弹窗） |
| `AudioRecorder` | 直接复用 |
| `AudioPlayer` | 直接复用 |
| `RealtimeSubtitle` | 直接复用 |
| `AnalysisPanel` | → `NvcAnalysisPanel`（四要素分析面板） |
| `RadarChart` | → `NvcRadarChart`（NVC 四维雷达图） |
| 🆕 `NvcAbilityTrendChart` | 新增：能力趋势折线图 |
| 🆕 `NvcStepIndicator` | 新增：四步练习进度指示器 |
| 🆕 `NvcScenarioCard` | 新增：场景卡片组件 |
| 🆕 `NvcProfileCard` | 新增：个人档案卡片 |

---

## 四、数据库表设计

### 4.1 核心业务表

#### `nvc_practice_session` — NVC 文字练习会话

```sql
CREATE TABLE nvc_practice_session (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,                    -- 关联用户
    practice_mode   VARCHAR(20) NOT NULL,               -- SCENARIO / FREE_DIALOG / STRUCTURED_FOUR_STEP
    scenario_id     BIGINT,                             -- 关联场景（场景驱动模式）
    current_phase   VARCHAR(30) DEFAULT 'CREATED',      -- CREATED → IN_PROGRESS → COMPLETED → EVALUATED
    current_step    VARCHAR(20),                        -- 结构化模式：OBSERVE / FEELING / NEED / REQUEST / COMPLETED
    agent_scene     VARCHAR(50),                        -- 当前激活的 Agent 场景
    difficulty      VARCHAR(10) DEFAULT 'MEDIUM',       -- EASY / MEDIUM / HARD
    context_summary TEXT,                               -- 对话上下文摘要（供 Agent 调度参考）
    session_config  JSONB,                              -- 会话配置（温度、模型选择等）
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nvc_session_user ON nvc_practice_session(user_id);
CREATE INDEX idx_nvc_session_status ON nvc_practice_session(current_phase);
```

#### `nvc_practice_message` — NVC 练习对话消息

```sql
CREATE TABLE nvc_practice_message (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT NOT NULL REFERENCES nvc_practice_session(id),
    role            VARCHAR(20) NOT NULL,               -- USER / ASSISTANT / SYSTEM
    agent_scene     VARCHAR(50),                        -- 产生该消息的 Agent 场景
    content         TEXT NOT NULL,                       -- 消息内容
    step            VARCHAR(20),                        -- 结构化模式下所属步骤
    sequence_num    INT NOT NULL,                       -- 消息序号
    metadata        JSONB,                              -- 扩展元数据
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nvc_message_session ON nvc_practice_message(session_id, sequence_num);
```

#### `nvc_evaluation` — NVC 评估记录

```sql
CREATE TABLE nvc_evaluation (
    id                  BIGSERIAL PRIMARY KEY,
    session_id          BIGINT NOT NULL REFERENCES nvc_practice_session(id),
    user_id             BIGINT NOT NULL,
    observation_score   INT,                            -- 观察维度得分 0-100
    feeling_score       INT,                            -- 感受维度得分 0-100
    need_score          INT,                            -- 需求维度得分 0-100
    request_score       INT,                            -- 请求维度得分 0-100
    overall_score       INT,                            -- 综合得分
    empathy_score       INT,                            -- 共情能力得分
    observation_detail  TEXT,                           -- 观察维度详细反馈
    feeling_detail      TEXT,                           -- 感受维度详细反馈
    need_detail         TEXT,                           -- 需求维度详细反馈
    request_detail      TEXT,                           -- 请求维度详细反馈
    strengths           TEXT,                           -- 优势总结
    improvements        TEXT,                           -- 改进建议
    reference_expressions TEXT,                         -- 参考 NVC 表达
    summary             TEXT,                           -- AI 综合评价
    evaluation_type     VARCHAR(20) DEFAULT 'REALTIME', -- REALTIME / FINAL
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nvc_eval_session ON nvc_evaluation(session_id);
CREATE INDEX idx_nvc_eval_user ON nvc_evaluation(user_id, created_at);
```

### 4.2 语音练习表

#### `nvc_voice_session` — NVC 语音练习会话

```sql
CREATE TABLE nvc_voice_session (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    practice_mode   VARCHAR(20) NOT NULL,               -- SCENARIO / FREE_DIALOG
    scenario_id     BIGINT,
    current_phase   VARCHAR(30) DEFAULT 'IN_PROGRESS',  -- IN_PROGRESS → PAUSED → COMPLETED
    voice_config    JSONB,                              -- 音色、语速、音量配置
    agent_scene     VARCHAR(50),
    difficulty      VARCHAR(10) DEFAULT 'MEDIUM',
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### `nvc_voice_message` — 语音对话消息

```sql
CREATE TABLE nvc_voice_message (
    id                  BIGSERIAL PRIMARY KEY,
    session_id          BIGINT NOT NULL REFERENCES nvc_voice_session(id),
    role                VARCHAR(20) NOT NULL,           -- USER / ASSISTANT
    agent_scene         VARCHAR(50),
    user_recognized_text TEXT,                          -- ASR 识别文本
    ai_generated_text   TEXT,                           -- AI 生成文本
    audio_url           VARCHAR(500),                   -- TTS 音频文件 URL
    sequence_num        INT NOT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### `nvc_voice_evaluation` — 语音练习评估

```sql
CREATE TABLE nvc_voice_evaluation (
    id                  BIGSERIAL PRIMARY KEY,
    session_id          BIGINT NOT NULL REFERENCES nvc_voice_session(id),
    user_id             BIGINT NOT NULL,
    observation_score   INT,
    feeling_score       INT,
    need_score          INT,
    request_score       INT,
    overall_score       INT,
    empathy_score       INT,
    fluency_score       INT,                            -- 表达流畅度（语音特有）
    observation_detail  TEXT,
    feeling_detail      TEXT,
    need_detail         TEXT,
    request_detail      TEXT,
    summary             TEXT,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 4.3 用户档案表

#### `nvc_user_profile` — 用户个人档案

```sql
CREATE TABLE nvc_user_profile (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL UNIQUE,
    communication_background TEXT,                       -- 个人沟通背景描述
    personality_traits      JSONB,                       -- 性格特征标签 ["内向", "容易焦虑", ...]
    communication_style     VARCHAR(50),                 -- 沟通风格：ASSERTIVE / PASSIVE / AGGRESSIVE / NVC
    emotional_triggers      TEXT,                        -- 情绪触发点描述
    common_scenarios        JSONB,                       -- 常遇到的沟通场景 ["职场汇报", "家庭矛盾", ...]
    relationship_types      JSONB,                       -- 关系类型 ["职场", "家庭", "亲密关系", ...]
    nvc_level               VARCHAR(20) DEFAULT 'BEGINNER', -- BEGINNER / INTERMEDIATE / ADVANCED
    total_practice_count    INT DEFAULT 0,
    total_practice_minutes  INT DEFAULT 0,
    profile_embedding       VECTOR(1024),                -- 档案向量（用于 RAG 个性化检索）
    last_practice_at        TIMESTAMP,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### `nvc_user_ability_score` — NVC 能力评分历史

```sql
CREATE TABLE nvc_user_ability_score (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    session_id      BIGINT,                             -- 关联的练习会话
    observation     INT NOT NULL,                       -- 观察能力 0-100
    feeling         INT NOT NULL,                       -- 感受表达 0-100
    need            INT NOT NULL,                       -- 需求识别 0-100
    request         INT NOT NULL,                       -- 请求表达 0-100
    empathy         INT,                                -- 共情能力 0-100
    practice_type   VARCHAR(20),                        -- TEXT / VOICE
    scored_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nvc_ability_user ON nvc_user_ability_score(user_id, scored_at);
```

#### `nvc_communication_record` — 真实沟通记录

```sql
CREATE TABLE nvc_communication_record (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    title           VARCHAR(200),                       -- 记录标题
    scenario_type   VARCHAR(50),                        -- 场景类型：WORKPLACE / FAMILY / INTIMATE / SOCIAL
    raw_content     TEXT NOT NULL,                       -- 原始沟通内容（用户输入）
    analysis_result JSONB,                              -- AI 分析结果
    nvc_suggestion  TEXT,                               -- NVC 改写建议
    embedding       VECTOR(1024),                       -- 向量嵌入
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nvc_comm_user ON nvc_communication_record(user_id);
```

### 4.4 场景库表

#### `nvc_scenario` — NVC 练习场景

```sql
CREATE TABLE nvc_scenario (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,              -- 场景标题
    description     TEXT NOT NULL,                       -- 场景描述
    scenario_type   VARCHAR(50) NOT NULL,               -- WORKPLACE / FAMILY / INTIMATE / SOCIAL / SELF
    difficulty      VARCHAR(10) NOT NULL,               -- EASY / MEDIUM / HARD
    focus_elements  JSONB,                              -- 重点练习的 NVC 要素 ["observation", "feeling"]
    context         TEXT,                               -- 详细背景信息
    sample_dialogue TEXT,                               -- 示例对话（参考）
    tags            JSONB,                              -- 标签 ["职场", "冲突", "拒绝"]
    is_system       BOOLEAN DEFAULT TRUE,               -- 系统预置 vs 用户创建
    created_by      BIGINT,                             -- 创建者
    usage_count     INT DEFAULT 0,                      -- 使用次数
    embedding       VECTOR(1024),                       -- 向量嵌入（用于相似场景推荐）
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nvc_scenario_type ON nvc_scenario(scenario_type, difficulty);
```

### 4.5 Agent 配置表

#### `nvc_agent_config` — Agent 角色配置（支持热更新）

```sql
CREATE TABLE nvc_agent_config (
    id              BIGSERIAL PRIMARY KEY,
    agent_scene     VARCHAR(50) NOT NULL UNIQUE,        -- SCENARIO_GENERATOR / DIALOGUE_GUIDE / ...
    display_name    VARCHAR(100) NOT NULL,              -- 显示名称
    description     TEXT,                               -- Agent 描述
    system_prompt   TEXT NOT NULL,                       -- 系统提示词
    model_provider  VARCHAR(50),                        -- 使用的 LLM Provider（null 则用默认）
    model_name      VARCHAR(100),                       -- 模型名称
    temperature     DOUBLE PRECISION DEFAULT 0.7,
    max_tokens      INT DEFAULT 2000,
    top_p           DOUBLE PRECISION DEFAULT 0.9,
    is_enabled      BOOLEAN DEFAULT TRUE,
    config_metadata JSONB,                              -- 扩展配置
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 4.6 复用表（不改结构）

| 表名 | 说明 |
|------|------|
| `llm_provider` | LLM Provider 配置，直接复用 |
| `knowledge_base` | 知识库，直接复用 |
| `knowledge_base_document` | 知识库文档，直接复用 |
| `rag_chat_session` | RAG 对话会话，直接复用 |
| `rag_chat_message` | RAG 对话消息，直接复用 |

### 4.7 删除的表

| 表名 | 原因 |
|------|------|
| `resume` | NVC 不需要简历管理 |
| `interview_session` | 替换为 nvc_practice_session |
| `interview_answer` | 替换为 nvc_practice_message |
| `interview_schedule` | NVC 不需要面试日程 |

---

## 五、Agent 多角色体系设计

### 5.1 NvcAgentScene 枚举

```java
public enum NvcAgentScene {
    // ===== 练习引导类 =====
    SCENARIO_GENERATOR      // 场景生成官：根据用户档案和偏好生成 NVC 练习场景
    DIALOGUE_GUIDE           // 对话引导官：模拟真实对话对象，引导用户练习 NVC
    DIFFICULT_PARTNER        // 困难搭档：故意用评判/指责语气，训练用户在压力下保持 NVC

    // ===== 结构化练习类 =====
    STEP_OBSERVE_COACH       // 观察步骤教练：引导用户区分观察和评论
    STEP_FEELING_COACH       // 感受步骤教练：引导用户识别和表达感受
    STEP_NEED_COACH          // 需求步骤教练：引导用户发现深层需求
    STEP_REQUEST_COACH       // 请求步骤教练：引导用户提出具体可执行的请求

    // ===== 评估反馈类 =====
    NVC_EXPRESSION_EVALUATOR // NVC 表达评估官：评估用户表达中的四要素质量
    EMPATHY_COACH            // 共情教练：分析用户的倾听和共情能力

    // ===== 知识辅助类 =====
    NVC_KNOWLEDGE_ADVISOR    // NVC 知识顾问：回答 NVC 理论问题（结合 RAG）

    // ===== 档案分析类 =====
    PROFILE_ANALYZER         // 档案分析师：分析用户沟通记录，更新个人档案
}
```

### 5.2 Agent 调度流程

```
┌─────────────────────────────────────────────────────────────────┐
│                    NvcAgentOrchestrator                          │
│                    （Agent 调度中心）                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  用户选择练习模式                                                │
│       │                                                         │
│       ▼                                                         │
│  [SCENARIO_GENERATOR] ──→ 根据用户档案 + 场景库生成练习场景       │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────────────────────────────────────┐       │
│  │              练习循环（多轮对话）                      │       │
│  │                                                     │       │
│  │  [DIALOGUE_GUIDE] 或 [DIFFICULT_PARTNER]            │       │
│  │       │                                             │       │
│  │       ▼  用户回复后                                  │       │
│  │  [NVC_EXPRESSION_EVALUATOR] ──→ 实时评估             │       │
│  │       │                                             │       │
│  │       ├── 评分 < 60 → 切换 [DIALOGUE_GUIDE] 引导重试 │       │
│  │       ├── 要素缺失 → 切换对应 [STEP_*_COACH] 针对练习 │       │
│  │       ├── 评分 ≥ 60 且四要素完整 → 推进              │       │
│  │       └── 难度升级 → 切换 [DIFFICULT_PARTNER]        │       │
│  │                                                     │       │
│  │  （循环直到用户结束或达到目标）                        │       │
│  └─────────────────────────────────────────────────────┘       │
│       │                                                         │
│       ▼                                                         │
│  [NVC_EXPRESSION_EVALUATOR] ──→ 最终综合评估                    │
│       │                                                         │
│       ▼                                                         │
│  [EMPATHY_COACH] ──→ 共情能力分析（可选）                       │
│       │                                                         │
│       ▼                                                         │
│  生成练习报告 + 更新用户档案                                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.3 Agent 配置热更新机制

```
┌──────────────┐     PUT /api/nvc/agents/configs/{scene}     ┌──────────────┐
│   前端管理页面  │ ─────────────────────────────────────────→ │   Controller  │
└──────────────┘                                              └──────┬───────┘
                                                                     │
                                                                     ▼
                                                              ┌──────────────┐
                                                              │    Service    │
                                                              │  1. 校验参数   │
                                                              │  2. 更新 DB    │
                                                              │  3. 清除缓存   │
                                                              └──────┬───────┘
                                                                     │
                                              ┌──────────────────────┼──────────────────────┐
                                              ▼                      ▼                      ▼
                                     ┌──────────────┐      ┌──────────────┐      ┌──────────────┐
                                     │  Redis 缓存   │      │ ChatClient   │      │   日志记录    │
                                     │  更新/失效     │      │ 重建实例      │      │              │
                                     └──────────────┘      └──────────────┘      └──────────────┘
```

关键实现：
- `NvcAgentConfigEntity` 存储在 PostgreSQL
- 运行时缓存在 Redis（TTL 5 分钟，更新时主动失效）
- `NvcAgentOrchestrator` 每次调度时先查缓存，miss 则查 DB
- `ChatClient` 实例根据配置动态创建（复用 `LlmProviderRegistry` 的能力）
- 温度、提示词变更不需要重启服务

### 5.4 Agent 思维链（Plan-Execute-Reflect）

在 `NvcAgentOrchestrator` 中实现 Agent 的自主决策循环：

```java
// 伪代码 - Agent 调度核心逻辑
public AgentDecision decideNextAction(PracticeContext context) {
    // PLAN: 分析当前状态，规划下一步
    String plan = invokeLlm(planPrompt, context);

    // EXECUTE: 根据规划选择 Agent
    NvcAgentScene nextScene = parsePlanToScene(plan);

    // REFLECT: 执行后反思，决定是否调整策略
    // （在对话结束后触发）
    String reflection = invokeLlm(reflectPrompt, context);
    updateStrategy(reflection);  // 更新难度、切换 Agent 等

    return new AgentDecision(nextScene, plan);
}
```

具体流程：

```
┌──────────────────────────────────────────────────────────┐
│                  Agent 思维链循环                          │
│                                                          │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐             │
│  │  PLAN    │ →  │ EXECUTE │ →  │ REFLECT │             │
│  │ 规划阶段  │    │ 执行阶段  │    │ 反思阶段  │             │
│  └────┬────┘    └────┬────┘    └────┬────┘             │
│       │              │              │                    │
│       │              │              ▼                    │
│       │              │    ┌──────────────────┐          │
│       │              │    │ 策略调整决策：      │          │
│       │              │    │ - 提升/降低难度     │          │
│       │              │    │ - 切换 Agent 角色   │          │
│       │              │    │ - 聚焦薄弱要素      │          │
│       │              │    │ - 结束练习          │          │
│       │              │    └────────┬─────────┘          │
│       │              │             │                     │
│       └──────────────┴─────────────┘                    │
│              （循环直到练习结束）                           │
└──────────────────────────────────────────────────────────┘
```

---

## 六、三种练习模式流程设计

### 6.1 模式一：场景驱动练习

**流程**：
```
用户选择「场景驱动」模式
    │
    ▼
[SCENARIO_GENERATOR] 根据用户档案推荐场景
    │  - 用户常遇到的沟通类型
    │  - 用户薄弱的 NVC 要素
    │  - 用户选择的难度
    │
    ▼
展示场景描述，用户确认或换一个
    │
    ▼
[DIALOGUE_GUIDE] 或 [DIFFICULT_PARTNER] 开始对话
    │
    │  用户 ←→ AI 多轮对话
    │  每轮结束后 [NVC_EXPRESSION_EVALUATOR] 实时评估
    │
    ▼
用户主动结束 或 达到目标
    │
    ▼
生成练习报告 + 更新用户能力画像
```

**示例场景**：
```
标题：同事抢功
难度：MEDIUM
背景：你在团队项目中做了大部分工作，但同事在汇报时把功劳归于自己。
对方角色：你的同事，性格比较强势，不太善于自省。
重点练习：观察（区分事实和评判）、请求（如何提出你的诉求）
```

### 6.2 模式二：自由对话练习

**流程**：
```
用户选择「自由对话」模式
    │
    ▼
用户描述自己遇到的真实沟通困境
    │  例："我和我妈总是因为她催婚吵架"
    │
    ▼
[NVC_EXPRESSION_EVALUATOR] 分析用户描述中的 NVC 要素
    │  - 哪些是观察？哪些是评判？
    │  - 用户的感受是什么？
    │  - 用户的深层需求是什么？
    │
    ▼
[DIALOGUE_GUIDE] 引导用户用 NVC 重新组织表达
    │
    │  用户 ←→ AI 多轮对话
    │  AI 帮用户一步步把「她总是逼我」改写为 NVC 表达
    │
    ▼
[NVC_KNOWLEDGE_ADVISOR] 结合 RAG 检索相关 NVC 知识
    │  例：检索「非暴力沟通」中关于家庭沟通的章节
    │
    ▼
生成练习报告 + 保存沟通记录到用户档案
```

### 6.3 模式三：结构化四步练习

**流程**：
```
用户选择「结构化四步」模式
    │
    ▼
[SCENARIO_GENERATOR] 生成练习场景（或用户自选）
    │
    ▼
┌──────────────────────────────────────────────┐
│ 步骤 1：观察练习                              │
│ [STEP_OBSERVE_COACH]                         │
│ - 展示一段冲突描述                             │
│ - 引导用户区分「观察」和「评论」                 │
│ - 用户练习用客观语言描述事实                     │
│ - 评估 → 通过则进入下一步，否则继续练习          │
└──────────────────┬───────────────────────────┘
                   ▼
┌──────────────────────────────────────────────┐
│ 步骤 2：感受练习                              │
│ [STEP_FEELING_COACH]                         │
│ - 引导用户识别自己的感受                        │
│ - 区分「感受」和「想法」（"我觉得你不尊重我"是想法）│
│ - 用户练习用感受词汇表达                        │
│ - 评估 → 通过则进入下一步                      │
└──────────────────┬───────────────────────────┘
                   ▼
┌──────────────────────────────────────────────┐
│ 步骤 3：需求练习                              │
│ [STEP_NEED_COACH]                            │
│ - 引导用户从感受追溯到深层需求                   │
│ - 区分「策略」和「需求」（"你每天陪我是策略，陪伴是需求"）│
│ - 用户练习识别和表达需求                        │
│ - 评估 → 通过则进入下一步                      │
└──────────────────┬───────────────────────────┘
                   ▼
┌──────────────────────────────────────────────┐
│ 步骤 4：请求练习                              │
│ [STEP_REQUEST_COACH]                         │
│ - 引导用户将需求转化为具体、可执行的请求          │
│ - 区分「请求」和「命令」                        │
│ - 用户练习提出正向、具体的请求                   │
│ - 评估 → 通过则进入综合练习                    │
└──────────────────┬───────────────────────────┘
                   ▼
┌──────────────────────────────────────────────┐
│ 综合练习：四步串联                             │
│ [DIALOGUE_GUIDE]                             │
│ - 将四步串联成完整的 NVC 表达                   │
│ - 多轮对话练习                                │
│ - 最终评估                                    │
└──────────────────┬───────────────────────────┘
                   ▼
生成练习报告 + 更新用户能力画像
```

---

## 七、用户档案系统设计

### 7.1 档案维度

```
┌─────────────────────────────────────────────────────────┐
│                    用户个人档案                            │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │ NVC 能力画像  │  │ 沟通背景     │  │ 性格分析     │    │
│  │             │  │             │  │             │    │
│  │ 观察: 72/100│  │ 职场: 高频   │  │ 内向        │    │
│  │ 感受: 58/100│  │ 家庭: 中频   │  │ 容易焦虑     │    │
│  │ 需求: 65/100│  │ 亲密: 低频   │  │ 回避冲突     │    │
│  │ 请求: 45/100│  │             │  │             │    │
│  │ 共情: 70/100│  │ 常见场景：    │  │ 情绪触发：    │    │
│  │             │  │ - 催婚争吵   │  │ - 被误解     │    │
│  │             │  │ - 工作汇报   │  │ - 不被认可   │    │
│  └─────────────┘  └─────────────┘  └─────────────┘    │
│                                                         │
│  ┌─────────────────────────────────────────────┐       │
│  │ 真实沟通记录分析                               │       │
│  │                                             │       │
│  │ 记录1: "和妈妈的催婚对话"                      │       │
│  │   → 观察: ✅ 感受: ❌ 需求: ✅ 请求: ❌        │       │
│  │   → NVC 改写建议: ...                        │       │
│  │                                             │       │
│  │ 记录2: "和同事的项目分歧"                      │       │
│  │   → 观察: ❌ 感受: ✅ 需求: ❌ 请求: ✅        │       │
│  │   → NVC 改写建议: ...                        │       │
│  └─────────────────────────────────────────────┘       │
│                                                         │
│  ┌─────────────────────────────────────────────┐       │
│  │ 能力趋势（最近 30 天）                         │       │
│  │                                             │       │
│  │ 观察 ────────/\────/\───────                │       │
│  │ 感受 ───/\──────────/\─────                 │       │
│  │ 需求 ─────────/\──────/\───                 │       │
│  │ 请求 ──/\────────────────/\─                │       │
│  │      7/1  7/5  7/10  7/15                   │       │
│  └─────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────┘
```

### 7.2 档案数据来源

| 来源 | 更新时机 | 更新内容 |
|------|---------|---------|
| 练习评估 | 每次练习结束后 | 更新四维度能力分数、练习次数、练习时长 |
| 沟通记录分析 | 用户上传沟通记录后 | 更新沟通背景、场景类型、薄弱要素 |
| 用户自填 | 用户编辑档案时 | 沟通背景、关系类型、性格标签 |
| AI 自动提取 | 对话过程中 | 情绪触发点、沟通风格、常见问题模式 |
| RAG 检索 | 每次对话时 | 根据档案向量检索匹配的 NVC 知识和案例 |

### 7.3 档案在对话中的应用

```java
// 伪代码 - 对话前加载用户档案
public ChatContext prepareChatContext(Long userId, NvcAgentScene scene) {
    // 1. 加载用户档案
    NvcUserProfile profile = profileService.getProfile(userId);

    // 2. 根据档案向量检索相关 NVC 知识
    List<Document> relevantKnowledge = vectorStore.similaritySearch(
        SearchRequest.query(profile.getProfileEmbedding())
            .withTopK(5)
            .withFilterExpression("knowledgeType == 'nvc_theory'")
    );

    // 3. 加载最近的练习历史
    List<AbilityScore> recentScores = abilityScoreService.getRecent(userId, 10);

    // 4. 构建上下文注入到 Agent 提示词
    return ChatContext.builder()
        .userProfile(profile)
        .relevantKnowledge(relevantKnowledge)
        .recentScores(recentScores)
        .weakElements(calculateWeakElements(recentScores))
        .build();
}
```

---

## 八、RAG 知识库设计

### 8.1 NVC 知识库内容规划

| 知识库名称 | 内容 | 用途 |
|-----------|------|------|
| NVC 理论基础 | 《非暴力沟通》书籍核心内容、四要素详解 | 知识顾问回答理论问题 |
| NVC 场景案例 | 各类场景下的 NVC 对话示例 | 场景生成参考、对话引导参考 |
| NVC 话术模板 | 观察/感受/需求/请求的标准表达模板 | 评估时提供参考表达 |
| NVC 常见误区 | "我觉得..."（想法伪装成感受）、评判伪装成观察等 | 评估时识别错误模式 |
| 情绪词汇库 | 感受词汇分类表、需求词汇分类表 | 引导用户使用准确词汇 |
| 沟通心理学 | 共情倾听、情绪管理、冲突解决相关知识 | 共情教练参考 |

### 8.2 个性化 RAG 检索流程

```
用户发起对话
    │
    ▼
加载用户档案（profile_embedding）
    │
    ▼
根据当前练习模式 + 用户薄弱要素，构建检索查询
    │
    ▼
┌──────────────────────────────────────────────┐
│ 多路检索                                       │
│                                              │
│ 路径1: 用户档案向量 → 检索相似用户的练习案例     │
│ 路径2: 当前对话上下文 → 检索相关 NVC 知识       │
│ 路径3: 用户薄弱要素 → 检索针对性话术模板         │
└──────────────────┬───────────────────────────┘
                   ▼
合并 + 重排序 → 注入 Agent 上下文
```

---

## 九、技术亮点清单与实现思路

### 9.1 亮点一览（简历可写）

| # | 亮点 | 技术实现 | 难度 |
|---|------|---------|------|
| 1 | **多 Agent 角色协同调度** | NvcAgentScene 枚举 + NvcAgentOrchestrator 调度中心 + 动态 ChatClient 切换 | ⭐⭐⭐ |
| 2 | **Agent 思维链（Plan-Execute-Reflect）** | LLM 自主分析练习状态 → 选择策略 → 执行 → 反思调整 | ⭐⭐⭐⭐ |
| 3 | **Agent 配置热更新** | DB 持久化 + Redis 缓存 + 运行时 ChatClient 重建 | ⭐⭐ |
| 4 | **实时流式评估** | SSE 流式返回 + StructuredOutputInvoker 结构化输出 | ⭐⭐ |
| 5 | **用户档案 + 向量化个性化** | pgvector 存储档案向量 + RAG 个性化检索 | ⭐⭐⭐ |
| 6 | **多模态练习（文字 + 语音）** | WebSocket 实时语音 + SSE 文字对话，共用 Agent 体系 | ⭐⭐⭐ |
| 7 | **结构化四步练习流程** | 状态机驱动 + 步骤级评估 + 自动推进 | ⭐⭐ |
| 8 | **练习报告 + PDF 导出** | 结构化评估 + iText 8 PDF 生成 | ⭐ |
| 9 | **数据可视化仪表盘** | Recharts 雷达图 + 趋势图 + 统计面板 | ⭐ |
| 10 | **真实沟通记录分析** | 用户输入 → AI 分析四要素 → NVC 改写建议 → 档案更新 | ⭐⭐ |

### 9.2 各亮点实现思路

#### 亮点 1：多 Agent 角色协同调度

```
核心类：
- NvcAgentScene（枚举，定义所有 Agent 角色）
- NvcAgentOrchestrator（调度中心，决定下一步用哪个 Agent）
- NvcAgentConfigService（读取 Agent 配置，支持热更新）

关键设计：
- 每个 NvcAgentScene 对应一套 system prompt + 模型参数
- Orchestrator 根据练习状态（评分、要素完整性、轮次）决定切换
- 复用 LlmProviderRegistry 创建不同配置的 ChatClient
- Agent 切换对用户透明，对话连续
```

#### 亮点 2：Agent 思维链

```
核心实现：
- Plan 阶段：LLM 分析当前对话状态，输出 JSON 格式的决策
  { "action": "CONTINUE_GUIDE", "reason": "用户观察要素薄弱", "suggestion": "..." }
- Execute 阶段：根据决策调用对应 Agent
- Reflect 阶段：对话结束后 LLM 反思整体策略，更新难度和重点

技术要点：
- 使用 StructuredOutputInvoker 保证 Plan 输出可解析
- Reflect 结果写入用户档案，影响下次练习的场景推荐
```

#### 亮点 4：实时流式评估

```
核心实现：
- 用户每轮回复后，异步触发 NVC_EXPRESSION_EVALUATOR
- 评估结果通过 SSE 流式返回前端
- 评估内容：四要素逐项分析 + 实时评分 + 改进建议
- 使用 StructuredOutputInvoker 保证评估结果结构化

前端展示：
- 对话气泡旁实时显示 NVC 要素标签（✅观察 ✅感受 ❌需求 ❌请求）
- 评分变化实时更新
```

#### 亮点 5：用户档案向量化

```
核心实现：
- 用户档案的各个维度拼接成文本，通过 EmbeddingModel 生成 1024 维向量
- 存储在 nvc_user_profile.profile_embedding（pgvector）
- 对话前根据用户向量检索相似用户的练习案例和 NVC 知识
- 每次练习后重新计算档案向量（能力变化 → 向量变化）
```

#### 亮点 6：多模态练习

```
核心实现：
- 文字模式：SSE 流式对话（复用 knowledgebase 的 SSE 模式）
- 语音模式：WebSocket + ASR/TTS（复用 voiceinterview 的管道）
- 共用 NvcAgentOrchestrator 调度逻辑
- 语音消息和文字消息统一存储到 nvc_practice_message / nvc_voice_message
```

---

## 十、前端页面规划

### 10.1 页面结构

```
/                                    → 重定向到 /nvc
/nvc                                 → NvcPracticeHubPage（练习中心）
/nvc/practice/:sessionId             → NvcPracticePage（文字练习对话）
/nvc/voice/:sessionId                → NvcVoicePage（语音练习对话）
/nvc/voice/:sessionId/evaluation     → NvcVoiceEvaluationPage
/nvc/history                         → NvcHistoryPage（练习历史）
/nvc/history/:sessionId/report       → NvcReportPage（练习报告）
/nvc/profile                         → NvcProfilePage（个人档案）
/nvc/dashboard                       → NvcDashboardPage（数据仪表盘）
/nvc/scenarios                       → NvcScenarioLibraryPage（场景库）
/nvc/agents                          → NvcAgentConfigPage（Agent 配置管理）
/knowledge-bases                     → KnowledgeBaseManagePage（复用）
/knowledge-bases/:id/query           → KnowledgeBaseQueryPage（复用）
/settings                            → SettingsPage（复用，LLM Provider 配置）
```

### 10.2 核心页面设计

#### NvcPracticeHubPage（练习中心）

```
┌─────────────────────────────────────────────────────────┐
│  NVC 非暴力沟通练习助手                    [档案] [仪表盘] │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌─────────────────┐ ┌─────────────────┐ ┌───────────┐│
│  │  🎯 场景驱动练习  │ │  💬 自由对话练习  │ │ 📝 结构化  ││
│  │                 │ │                 │ │   四步练习  ││
│  │  AI 生成冲突场景  │ │  描述你的真实困境  │ │ 观察→感受→ ││
│  │  模拟真实对话    │ │  AI 帮你用 NVC   │ │ 需求→请求  ││
│  │  实时评估反馈    │ │  重新组织表达    │ │ 逐步练习   ││
│  │                 │ │                 │ │           ││
│  │  [开始练习]     │ │  [开始对话]     │ │ [开始练习] ││
│  └─────────────────┘ └─────────────────┘ └───────────┘│
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │  🎤 语音练习模式                                  │   │
│  │  用语音实时对话练习 NVC，像真实沟通一样             │   │
│  │  [开始语音练习]                                   │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │  📊 我的能力                                      │   │
│  │  观察 ████████░░ 72    感受 ██████░░░░ 58        │   │
│  │  需求 ███████░░░ 65    请求 █████░░░░░ 45        │   │
│  │  已练习 23 次  累计 6.5 小时                       │   │
│  │  [查看详细报告]                                   │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

#### NvcPracticePage（文字练习对话）

```
┌─────────────────────────────────────────────────────────┐
│  ← 返回  场景驱动练习  进行中         [结束练习] [暂停]   │
├─────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────────┐ │
│ │ 📋 场景：同事抢功                                    │ │
│ │ 你在团队项目中做了大部分工作，同事在汇报时把功劳归于   │ │
│ │ 自己...                              [查看完整场景]  │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                         │
│ ┌─────────────────────────────────────────────────────┐ │
│ │                                                     │ │
│ │  🤖 同事（AI）                            14:30     │ │
│ │  ┌─────────────────────────────────────────────┐   │ │
│ │  │ 嗨，上次项目汇报你有啥想说的？我觉得咱们配合   │   │ │
│ │  │ 得挺好的啊，领导也很认可我的方案。            │   │ │
│ │  └─────────────────────────────────────────────┘   │ │
│ │                                                     │ │
│ │                                    你（用户）14:31  │ │
│ │  ┌─────────────────────────────────────────────┐   │ │
│ │  │ 我注意到上次汇报中，你提到的三个技术方案都是   │   │ │
│ │  │ 我独立完成的，但你没有提及我的贡献。           │   │ │
│ │  │                                             │   │ │
│ │  │ 我感到有些失落和不公平，因为我需要被认可。     │   │ │
│ │  │                                             │   │ │
│ │  │ 我希望以后汇报时，我们能各自介绍自己负责的     │   │ │
│ │  │ 部分，你觉得可以吗？                          │   │ │
│ │  └─────────────────────────────────────────────┘   │ │
│ │                                                     │ │
│ │  ✅ 观察  ✅ 感受  ✅ 需求  ✅ 请求    综合: 85分    │ │
│ │                                                     │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                         │
│ ┌─────────────────────────────────────────────────────┐ │
│ │  [输入你的 NVC 表达...]                     [发送]   │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                         │
│ ┌─────────────────┐                                     │
│ │ 实时评估          │                                     │
│ │ 观察: ✅ 区分了事实 │                                     │
│ │ 感受: ✅ 表达了失落 │                                     │
│ │ 需求: ✅ 识别了认可 │                                     │
│ │ 请求: ⚠️ 可以更具体 │                                     │
│ └─────────────────┘                                     │
└─────────────────────────────────────────────────────────┘
```

### 10.3 前端技术方案

| 功能 | 技术实现 |
|------|---------|
| 对话界面 | 自定义 ChatPanel 组件，支持 Markdown 渲染 |
| 流式输出 | SSE（EventSource API）+ 逐字显示 |
| 语音交互 | WebSocket + AudioRecorder/AudioPlayer（复用） |
| 雷达图 | Recharts RadarChart |
| 趋势图 | Recharts LineChart |
| PDF 导出 | 后端生成，前端下载 |
| 实时评估 | SSE 流式 + 动画展示 |
| 页面路由 | React Router v7 |
| 状态管理 | React Context + useReducer |
| 样式 | TailwindCSS 4 |

---

## 十一、分阶段实施计划

### Phase 0：项目初始化（1-2 天）

```
□ 复制 interview-guide 项目
□ 重命名项目：interview-guide → nvc-guide
□ 重命名包名：interview.guide → nvc.guide
□ 删除不需要的模块（resume、interviewschedule）
□ 删除不需要的 Entity、Service、Controller
□ 删除不需要的前端页面
□ 保留并验证：common/、infrastructure/、knowledgebase/、llmprovider/
□ 确保项目能正常启动
```

### Phase 1：核心数据层（2-3 天）

```
□ 创建所有 NVC 相关 Entity
  □ NvcPracticeSessionEntity
  □ NvcPracticeMessageEntity
  □ NvcEvaluationEntity
  □ NvcUserProfileEntity
  □ NvcUserAbilityScoreEntity
  □ NvcScenarioEntity
  □ NvcAgentConfigEntity
  □ NvcCommunicationRecordEntity
□ 创建对应的 Repository
□ 创建 DTO / Request / Response
□ 创建 MapStruct Mapper
□ 初始化 Agent 配置数据（6 个 Agent 的默认 prompt）
□ 初始化场景库数据（10+ 预置场景）
□ 验证数据库表创建正常
```

### Phase 2：NVC 文字练习核心（3-5 天）

```
□ 实现 NvcPracticeSessionService（会话管理）
□ 实现 NvcAgentOrchestrator（Agent 调度中心）
□ 实现 NvcAgentConfigService（Agent 配置读取 + 缓存）
□ 编写 NVC Prompt 模板
  □ nvc-scenario-generate-system.st
  □ nvc-dialogue-guide-system.st
  □ nvc-difficult-partner-system.st
  □ nvc-evaluation-system.st
  □ nvc-evaluation-user.st
□ 实现 NvcPracticeController
□ 实现 SSE 流式对话接口
□ 前端：NvcPracticeHubPage + NvcPracticePage
□ 验证：场景驱动练习端到端跑通
```

### Phase 3：NVC 评估引擎（2-3 天）

```
□ 实现 NvcUnifiedEvaluationEngine
  □ 四要素逐项评估（结构化输出）
  □ 综合评分
  □ 改进建议生成
  □ 参考表达生成
□ 实现实时流式评估（每轮对话后触发）
□ 实现练习报告生成
□ 实现 PDF 导出（复用 PdfExportService）
□ 前端：NvcReportPage + 评估动画
□ 验证：评估结果准确、PDF 导出正常
```

### Phase 4：用户档案系统（2-3 天）

```
□ 实现 NvcProfileService
  □ 档案 CRUD
  □ 能力分数记录
  □ 档案向量化（EmbeddingModel）
  □ 沟通记录分析
□ 实现 NvcDashboardService
  □ 能力趋势数据
  □ 练习统计
□ 前端：NvcProfilePage + NvcDashboardPage
  □ 档案编辑表单
  □ 雷达图（Recharts）
  □ 趋势图
  □ 沟通记录上传和分析
□ 验证：档案更新、向量检索、仪表盘展示
```

### Phase 5：结构化四步练习（2-3 天）

```
□ 实现结构化练习状态机
  □ OBSERVE → FEELING → NEED → REQUEST → COMPLETED
  □ 每步独立评估 + 自动推进
□ 编写四步教练 Prompt 模板
  □ nvc-step-observe-system.st
  □ nvc-step-feeling-system.st
  □ nvc-step-need-system.st
  □ nvc-step-request-system.st
□ 前端：步骤进度指示器 + 步骤切换动画
□ 验证：四步练习端到端跑通
```

### Phase 6：语音练习模块（3-5 天）

```
□ 改造 voiceinterview → nvcvoice
  □ 修改 WebSocket Handler
  □ 修改阶段管理（INTRO/TECH/PROJECT/HR → NVC 阶段）
  □ 修改 prompt 模板
□ 实现 NvcVoiceService
□ 实现语音评估（复用评估引擎）
□ 前端：NvcVoicePage
  □ 语音录制 + 实时字幕
  □ AI 语音播放
  □ 语音练习报告
□ 验证：语音练习端到端跑通
```

### Phase 7：RAG 知识库 + 个性化（2-3 天）

```
□ 准备 NVC 知识库内容
  □ NVC 理论文档
  □ 场景案例文档
  □ 话术模板文档
  □ 情绪/需求词汇库
□ 上传到知识库，触发向量化
□ 实现个性化 RAG 检索
  □ 根据用户档案向量检索
  □ 根据薄弱要素检索
□ 实现 NvcKnowledgeAdvisor 对话
□ 验证：RAG 检索准确、个性化推荐合理
```

### Phase 8：Agent 进阶特性（2-3 天）

```
□ 实现 Agent 配置热更新 API
□ 实现 Agent 思维链（Plan-Execute-Reflect）
□ 实现 Agent 自动难度调节
□ 前端：NvcAgentConfigPage
  □ Agent 列表 + 配置编辑
  □ 实时预览 prompt 效果
□ 验证：热更新生效、思维链决策合理
```

### Phase 9：打磨与部署（2-3 天）

```
□ 前端 UI 打磨
  □ 动画效果（Framer Motion）
  □ 响应式布局
  □ 加载状态、错误处理
□ 场景库扩充（30+ 场景）
□ Prompt 调优
□ Docker Compose 部署配置
□ README 编写
□ 演示视频录制
```

### 时间线总览

```
Phase 0 ████░░░░░░░░░░░░░░░░░░░░░░░░░░  1-2 天
Phase 1 ████████░░░░░░░░░░░░░░░░░░░░░░  2-3 天
Phase 2 ████████████████░░░░░░░░░░░░░░  3-5 天
Phase 3 ████████░░░░░░░░░░░░░░░░░░░░░░  2-3 天
Phase 4 ████████░░░░░░░░░░░░░░░░░░░░░░  2-3 天
Phase 5 ████████░░░░░░░░░░░░░░░░░░░░░░  2-3 天
Phase 6 ████████████████░░░░░░░░░░░░░░  3-5 天
Phase 7 ████████░░░░░░░░░░░░░░░░░░░░░░  2-3 天
Phase 8 ████████░░░░░░░░░░░░░░░░░░░░░░  2-3 天
Phase 9 ████████░░░░░░░░░░░░░░░░░░░░░░  2-3 天
                                       ─────────
                                       约 20-33 天
```

---

## 十二、简历写法参考

### 12.1 项目概述（简历版）

一个基于 AI 多 Agent 协同的非暴力沟通（NVC）练习平台，帮助用户在安全的 AI 对话环境中练习 NVC 四要素（观察、感受、需求、请求）。用户可选择场景驱动、自由对话、结构化四步三种练习模式，通过文字或语音与多个 AI Agent 进行沉浸式对话练习，系统实时评估表达质量并生成个性化练习报告。

**技术架构**：
- 前端：React 18 + TypeScript + TailwindCSS 4 + Recharts
- 后端：Spring Boot 4 + Java 21 + Spring AI 2.0 + PostgreSQL + pgvector

---

### 12.2 功能特性清单（参照 interview-guide 风格）

#### NVC 练习模块

- **三种练习模式**：场景驱动练习（AI 生成冲突场景，模拟真实对话）、自由对话练习（用户描述真实困境，AI 帮助用 NVC 重新组织表达）、结构化四步练习（观察→感受→需求→请求逐步训练，每步独立评估 + 自动推进）。
- **多 Agent 角色协同**：内置 6 类 NVC 专职 Agent（场景生成官、对话引导官、困难搭档、四步教练、表达评估官、共情教练），由 `NvcAgentOrchestrator` 统一调度，根据练习状态动态切换 Agent 角色与难度策略。
- **实时流式评估**：用户每轮回复后异步触发结构化评估，基于 `StructuredOutputInvoker` 返回四要素逐项分析 + 评分 + 改进建议 + 参考 NVC 表达，通过 SSE 流式推送至前端实时展示。
- **Agent 思维链**：引入 Plan-Execute-Reflect 循环，Agent 自主分析练习状态（评分、要素完整性、轮次）并调整对话策略，支持自动难度调节和薄弱环节强化。
- **Agent 配置热更新**：Agent 提示词/模型参数/温度持久化至 PostgreSQL，运行时通过 Redis 缓存 + 主动失效策略实现零重启配置更新，前端提供可视化配置管理界面。
- **练习报告导出**：练习结束生成结构化报告（四维度评分、详细反馈、改进建议、参考表达），支持 PDF 一键导出（iText 8）。

#### 用户档案模块

- **四维度能力画像**：基于练习评估数据持续更新用户在观察/感受/需求/请求/共情五维度的能力分数，支持雷达图和趋势图可视化。
- **个人沟通背景**：记录用户常遇到的沟通场景（职场/家庭/亲密关系）、性格特征、沟通风格、情绪触发点，供 Agent 对话时参考。
- **真实沟通记录分析**：用户可上传真实沟通记录（聊天截图/文字），AI 自动分析四要素质量并生成 NVC 改写建议，分析结果反哺档案形成成长闭环。
- **档案向量化个性化**：基于 pgvector 存储用户档案的 1024 维向量嵌入，结合 RAG 多路检索（档案向量 + 对话上下文 + 薄弱要素）实现个性化知识推荐和案例匹配。

#### 语音练习模块

- **实时语音对话练习**：WebSocket + DashScope Qwen3 语音模型（ASR/TTS/LLM 统一 API Key），支持实时语音 NVC 对话练习。
- **实时流式对话**：句子级并发 TTS，边生成边合成边播放，服务端 VAD 自动断句，实时字幕（含中间结果）。
- **回声防护 + 手动提交**：避免 AI 语音被误录入，支持手动提交防误触。
- **多轮上下文记忆 + 暂停/恢复**：超时自动暂停，支持从历史记录恢复练习。

#### 知识库模块

- **NVC 知识库**：支持上传 NVC 理论文档（《非暴力沟通》核心内容、四要素详解、话术模板、情绪词汇库等），自动分块与异步向量化。
- **RAG 检索增强**：集成 pgvector 向量数据库，通过检索增强生成提升 AI 问答的准确性与专业度。
- **个性化检索**：根据用户档案向量和薄弱要素，智能推荐最匹配的 NVC 知识和案例。
- **流式响应交互**：基于 SSE 实现打字机式流式响应，支持基于知识库内容的智能问答。

#### 数据可视化模块

- **能力雷达图**：NVC 五维度能力可视化，支持与历史数据对比。
- **练习趋势图**：按时间线展示各维度能力变化趋势。
- **练习统计面板**：总练习次数、累计时长、各模式分布、薄弱环节分析。

---

### 12.3 项目亮点写法（参照 AI-Meeting 风格）

**1. 设计并实现多 Agent 角色协同调度体系**

定义 6 类 NVC 专职 Agent（场景生成官、对话引导官、困难搭档、四步教练、表达评估官、共情教练），基于 `NvcAgentScene` 枚举绑定 + `LlmProviderRegistry` 动态 ChatClient 切换实现运行时 Agent 路由；引入 Plan-Execute-Reflect 思维链循环，`NvcAgentOrchestrator` 自主分析练习状态（评分、要素完整性、轮次）并动态切换 Agent 角色与难度策略。Agent 配置（提示词/模型/温度）持久化至 PostgreSQL，运行时通过 Redis 缓存 + 主动失效实现零重启热更新。

**2. 构建用户档案向量化个性化系统**

基于 pgvector 存储用户 NVC 能力画像（观察/感受/需求/请求/共情五维度）的 1024 维向量嵌入，每次练习后自动更新能力分数并重新计算向量；结合 RAG 多路检索（档案向量相似用户 + 当前对话上下文 + 薄弱要素）实现个性化知识推荐和案例匹配。支持真实沟通记录上传，AI 自动分析四要素质量并生成 NVC 改写建议，分析结果反哺档案形成成长闭环。

**3. 实现结构化四步练习有限状态机**

将 NVC 的观察→感受→需求→请求四步骤建模为有限状态机（`OBSERVE → FEELING → NEED → REQUEST → COMPLETED`），每步由独立 Coach Agent 引导 + 结构化评估，评估通过自动推进，未通过则针对性加练；支持步骤级回退和薄弱环节强化。状态机与 Agent 调度层解耦，可灵活扩展新的练习步骤。

**4. 设计多模态练习管道**

文字模式基于 WebFlux Flux + SSE 实现流式对话 + 实时逐要素评估反馈；语音模式基于 WebSocket + DashScope Qwen3 ASR/TTS 实现实时语音对话（服务端 VAD 断句、句子级并发 TTS、回声防护、手动提交防误触）。两种模式共用 `NvcAgentOrchestrator` 调度层和 `NvcUnifiedEvaluationEngine` 评估引擎，练习记录统一存储，支持跨模态对比分析。

**5. 实现实时流式 NVC 表达评估引擎**

基于 `StructuredOutputInvoker` 封装结构化输出重试，用户每轮回复后异步触发评估 Agent，返回四要素逐项分析（是否区分观察和评论、是否表达感受而非想法、是否识别深层需求、是否提出可执行请求）+ 评分 + 改进建议 + 参考 NVC 表达。评估结果通过 SSE 流式推送至前端实时展示，支持评估过程中的 JSON 修复和注入防护。

**6. 基于 Redis Stream 实现异步评估 + 练习报告导出**

练习结束后通过 `AbstractStreamProducer` 将评估任务推入 Redis Stream，`Consumer` 异步执行全量评估（分批评估 + 二次汇总 + 降级兜底），评估完成后生成结构化练习报告并支持 PDF 导出（iText 8）。支持评估进度实时查询和失败自动重试（最大 3 次）。

---

### 12.4 与 AI-Meeting 对比（你的项目优势）

| 维度 | AI-Meeting（码上面试） | NVC 练习助手（你的项目） |
|------|---------------------|----------------------|
| Agent 设计 | 5 个 Agent 绑定星云工作流（外部依赖） | 6 个 Agent 纯 Spring AI 实现（无外部依赖） |
| Agent 调度 | LiteFlow 规则链（重型框架） | Orchestrator + 思维链（轻量、自主决策） |
| Agent 配置 | 数据库配置，重启生效 | 数据库 + Redis 缓存，运行时热更新 |
| 会话状态 | MongoDB 快照 + Redis 懒加载（重量级） | PostgreSQL + Redis 双层缓存（轻量级） |
| 个性化能力 | 无 | 用户档案向量化 + RAG 个性化检索 |
| 评估方式 | 单次评分 | 实时逐轮 + 四要素结构化 + 趋势追踪 |
| 状态机 | LiteFlow 规则链驱动追问裁决 | 原生有限状态机驱动四步推进（更轻量） |
| 外部依赖 | 星辰 Agent 平台 + LiteFlow + MongoDB | 纯 Spring AI 生态，无外部平台依赖 |

**你的项目在以下两点上比 AI-Meeting 更有亮点**：
1. **Agent 自主性**：Plan-Execute-Reflect 思维链 vs AI-Meeting 的硬编码 LiteFlow 规则链
2. **个性化深度**：用户档案向量化 + RAG vs AI-Meeting 的无个性化

---

### 12.5 简历一句话版本（可直接粘贴）

> **NVC 非暴力沟通练习助手** | Spring Boot 4 + Java 21 + Spring AI 2.0 + React 18 + PostgreSQL + pgvector
>
> 基于 AI 多 Agent 协同的非暴力沟通练习平台。设计 6 类 NVC 专职 Agent 协同调度体系，引入 Plan-Execute-Reflect 思维链实现 Agent 自主策略调整；构建用户档案向量化个性化系统，基于 pgvector + RAG 多路检索实现个性化知识推荐；实现结构化四步练习有限状态机 + 实时流式评估引擎；支持文字/语音双模态练习，评估结果实时反馈 + PDF 报告导出。

---

> **文档版本**：v1.1
> **编写日期**：2026-07-08
> **基于项目**：interview-guide（Spring Boot 4.0 + Java 21 + Spring AI 2.0 + React 18）
> **参考项目**：AI-Meeting（多 Agent 协同、长会话状态治理、Agent 配置管理）
