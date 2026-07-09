# Step 0：项目初始化与清理 — 完成记录

> 完成日期：2026-07-09
>
> 操作人：Claude Code + 用户协作
>
> 耗时：约 1 小时

---

## 一、做了什么

### 1.1 复制项目

从 `D:\code\interview-guide`（AI 面试平台）完整复制到 `D:\code\nvc-guide`，共复制 **364 个文件**。

复制时排除了：
- `.git/` — 重新初始化 Git
- `.claude/` — 保留用户已有的 Claude 配置
- `docs/` — 保留用户已有的 NVC 实施文档

### 1.2 全局包名重命名

将所有 Java 文件中的包名从 `interview.guide` 替换为 `nvc.guide`：

- **涉及文件**：196 个 Java 源文件 + 20 个测试文件
- **替换内容**：
  - `package interview.guide.xxx` → `package nvc.guide.xxx`
  - `import interview.guide.xxx` → `import nvc.guide.xxx`
- **目录重命名**：
  - `app/src/main/java/interview/guide/` → `app/src/main/java/nvc/guide/`
  - `app/src/test/java/interview/guide/` → `app/src/test/java/nvc/guide/`

### 1.3 删除不需要的模块

**Java 后端删除了 4 个业务模块：**

| 模块 | 说明 | 删除原因 |
|------|------|---------|
| `modules/interview/` | 面试模块（Controller、Service、Model、Repository、Listener） | NVC 不需要面试功能 |
| `modules/resume/` | 简历模块（上传、解析、评分） | NVC 不需要简历管理 |
| `modules/interviewschedule/` | 面试日程模块 | NVC 不需要日程管理 |
| `modules/test/` | AI 测试模块 | 测试用途，不需要 |

**基础设施清理：**

| 文件 | 操作 |
|------|------|
| `infrastructure/mapper/InterviewMapper.java` | 删除 |
| `infrastructure/mapper/ResumeMapper.java` | 删除 |
| `common/evaluation/InterviewEvaluationProperties.java` | 删除后重建（改为 NVC 配置） |

**Prompt 模板清理（resources/prompts/）：**

删除了 11 个面试/简历相关模板：
- `interview-question-skill-system.st` / `interview-question-skill-user.st`
- `interview-question-resume-system.st` / `interview-question-resume-user.st`
- `interview-evaluation-system.st` / `interview-evaluation-user.st`
- `interview-evaluation-summary-system.st` / `interview-evaluation-summary-user.st`
- `resume-analysis-system.st` / `resume-analysis-user.st`
- `jd-parse-system.st`

**Skill 目录清理（resources/skills/）：**

删除了 10 个面试技能目录：
- `java-backend/`, `ali-backend/`, `bytedance-backend/`, `java-backend-tencent/`
- `frontend/`, `python-backend/`, `algorithm/`
- `system-design/`, `test-development/`, `ai-agent-dev/`

保留了 `skills/` 目录本身和 `_shared/` 子目录。

### 1.4 前端清理

**删除了 9 个页面：**

| 页面 | 原功能 |
|------|--------|
| `InterviewPage.tsx` | 文字面试对话页 |
| `InterviewHubPage.tsx` | 面试中心（模式选择） |
| `InterviewHistoryPage.tsx` | 面试历史记录 |
| `VoiceInterviewPage.tsx` | 语音面试页 |
| `VoiceInterviewEvaluationPage.tsx` | 语音面试评估报告 |
| `ResumeDetailPage.tsx` | 简历详情页 |
| `UploadPage.tsx` | 简历上传页 |
| `HistoryPage.tsx` | 简历历史列表 |
| `InterviewSchedulePage.tsx` | 面试日程管理 |

**删除了 13 个组件：**

| 组件 | 原功能 |
|------|--------|
| `InterviewChatPanel.tsx` | 面试对话面板 |
| `InterviewPanel.tsx` | 面试面板 |
| `InterviewDetailPanel.tsx` | 面试详情面板 |
| `InterviewMessageBubble.tsx` | 面试消息气泡 |
| `InterviewPageHeader.tsx` | 面试页头 |
| `UnifiedInterviewModal.tsx` | 统一面试配置弹窗 |
| `AnalysisPanel.tsx` | 分析面板 |
| `RadarChart.tsx` | 雷达图 |
| `HistoryList.tsx` | 历史列表 |
| `ScoreProgressBar.tsx` | 分数进度条 |
| `interviewschedule/` 目录（7 个文件） | 面试日程相关组件 |

**删除了 10 个 API/类型/Hook 文件：**

| 文件 | 类型 |
|------|------|
| `api/interview.ts` | API 客户端 |
| `api/interviewSchedule.ts` | API 客户端 |
| `api/resume.ts` | API 客户端 |
| `api/history.ts` | API 客户端 |
| `api/skill.ts` | API 客户端 |
| `types/interview.ts` | TypeScript 类型 |
| `types/interviewSchedule.ts` | TypeScript 类型 |
| `types/resume.ts` | TypeScript 类型 |
| `hooks/useInterviewConfig.ts` | React Hook |
| `hooks/useInterviewSchedule.ts` | React Hook |
| `utils/voiceInterview.ts` | 工具函数 |

### 1.5 配置文件修改

**settings.gradle：**
```diff
- rootProject.name = 'interview-guide'
+ rootProject.name = 'nvc-guide'
```

**application.yml（主要改动）：**
```diff
- # AI Interview Platform Configuration
+ # NVC 非暴力沟通练习助手配置

- name: ai-interview-platform
+ name: nvc-guide

- url: jdbc:postgresql://.../${POSTGRES_DB:interview_guide}
+ url: jdbc:postgresql://.../${POSTGRES_DB:nvc_practice}

- default-provider: ${APP_AI_DEFAULT_PROVIDER:dashscope}
+ default-provider: ${APP_AI_DEFAULT_PROVIDER:mimo}

- config-yaml-path: .../.interview-guide/...
+ config-yaml-path: .../.nvc-guide/...

- bucket: ${APP_STORAGE_BUCKET:interview-guide}
+ bucket: ${APP_STORAGE_BUCKET:nvc-guide}
```

新增了小米 MiMo Provider 配置：
```yaml
mimo:
  base-url: https://token-plan-cn.xiaomimimo.com/v1
  api-key: ${PROVIDER_MIMO_API_KEY:}
  model: ${PROVIDER_MIMO_MODEL:mimo-2.5}
  supports-embedding: false
```

删除了 `app.interview` 和 `app.resume` 配置段。

**docker-compose.yml：**
- 所有容器名：`interview-*` → `nvc-*`
- 数据库名：`interview_guide` → `nvc_practice`
- 存储桶名：`interview-guide` → `nvc-guide`
- 删除了面试参数环境变量

**.env（完整重写）：**
```env
# LLM — 小米 MiMo
PROVIDER_MIMO_API_KEY=sk-cgmf0ae7ifxn4kdqyi2qr94ony1womyri1qtom1ba2qhhkh2
PROVIDER_MIMO_MODEL=mimo-2.5
AI_MODEL=mimo-2.5
APP_AI_DEFAULT_PROVIDER=mimo

# PostgreSQL — NVC 项目专用库
POSTGRES_HOST=10.237.255.9
POSTGRES_DB=nvc_practice
POSTGRES_USER=nvc
POSTGRES_PASSWORD=nvc123

# Redis — 使用 db 2
REDIS_HOST=10.237.255.9
REDIS_DATABASE=2

# MinIO
APP_STORAGE_BUCKET=nvc-guide
```

**frontend/package.json：**
```diff
- "name": "ai-interview-frontend"
+ "name": "nvc-guide-frontend"
```

**frontend/index.html：**
```diff
- <title>AI智能面试官 - 简历分析</title>
+ <title>NVC 非暴力沟通练习助手</title>
```

### 1.6 ErrorCode 清理

**删除的错误码：**
- 简历模块 2xxx：`RESUME_NOT_FOUND`, `RESUME_PARSE_FAILED`, `RESUME_UPLOAD_FAILED` 等 7 个
- 面试模块 3xxx：`INTERVIEW_SESSION_NOT_FOUND`, `INTERVIEW_EVALUATION_FAILED` 等 7 个
- 面试日程 9xxx：`INTERVIEW_SCHEDULE_NOT_FOUND` 1 个
- 语音面试 10xxx：`VOICE_SESSION_NOT_FOUND`, `VOICE_EVALUATION_FAILED` 等 3 个（改为 NVC 前缀）

**新增的错误码：**
```java
// NVC 练习模块错误 3xxx
NVC_SESSION_NOT_FOUND(3001, "NVC 练习会话不存在"),
NVC_SESSION_EXPIRED(3002, "NVC 练习会话已过期"),
NVC_EVALUATION_FAILED(3003, "NVC 评估失败"),
NVC_SCENARIO_NOT_FOUND(3004, "NVC 场景不存在"),
NVC_PROFILE_NOT_FOUND(3005, "用户档案不存在"),
NVC_AGENT_CONFIG_NOT_FOUND(3006, "Agent 配置不存在"),

// NVC 语音练习模块错误 10xxx
NVC_VOICE_SESSION_NOT_FOUND(10001, "NVC 语音练习会话不存在"),
NVC_VOICE_EVALUATION_FAILED(10004, "NVC 语音评估失败"),
NVC_VOICE_EVALUATION_NOT_FOUND(10006, "NVC 语音评估结果不存在"),
```

### 1.7 前端路由重写

**App.tsx 完全重写**，从 374 行简化到 115 行：

原路由（13 个）：
```
/ → /history
/upload, /history, /history/:resumeId
/interview-hub, /interviews, /interviews/:sessionId
/interview, /interview/:resumeId
/voice-interview, /voice-interview/:sessionId/evaluation
/knowledgebase, /knowledgebase/upload, /knowledgebase/chat
/interview-schedule, /settings
```

新路由（4 个）：
```
/ → /knowledgebase（临时重定向）
/knowledgebase（知识库管理）
/knowledgebase/upload（知识库上传）
/knowledgebase/chat（问答助手）
/settings（设置）
```

**Layout.tsx 简化**，从 257 行简化到 145 行：
- 删除了面试弹窗相关逻辑
- 删除了简历管理、面试中心、面试记录、面试日程导航项
- 保留了知识库和系统设置导航
- Logo 从 "AI Interview" 改为 "NVC 练习"

### 1.8 代码修复

**PdfExportService.java（重写）：**
- 删除了 `exportResumeAnalysis()` 方法（简历分析导出）
- 删除了 `exportInterviewReport()` 方法（面试报告导出）
- 保留了通用 PDF 工具方法（字体、标题、表格等）
- 从 309 行简化到 130 行

**InterviewSessionCache.java（重写）：**
- 删除了对 `InterviewQuestionDTO`, `SessionStatus` 的依赖
- 简化为通用会话缓存骨架
- 从 243 行简化到 107 行
- 缓存键前缀从 `interview:session:` 改为 `nvc:session:`

**DashscopeLlmService.java：**
- 删除了 `ResumeRepository` 依赖
- `buildPromptContext()` 方法中简历文本获取改为 TODO 占位

**VoiceInterviewEvaluationService.java：**
- 删除了 `InterviewSkillService` 依赖
- 评估参考上下文改为 null（后续从 NVC 知识库获取）

**UnifiedEvaluationService.java：**
- 重建了 `InterviewEvaluationProperties` 配置类
- Prompt 路径指向新的 NVC 评估模板

**RealtimeSubtitle.tsx（重写）：**
- 删除了对 `InterviewMessageBubble` 组件的依赖
- 内联实现了简单的 `MessageBubble` 组件

### 1.9 新增文件

| 文件 | 说明 |
|------|------|
| `app/src/main/resources/prompts/nvc-evaluation-system.st` | NVC 评估系统提示词 |
| `app/src/main/resources/prompts/nvc-evaluation-user.st` | NVC 评估用户提示词 |
| `app/src/main/resources/prompts/nvc-evaluation-summary-system.st` | NVC 评估汇总系统提示词 |
| `app/src/main/resources/prompts/nvc-evaluation-summary-user.st` | NVC 评估汇总用户提示词 |
| `app/src/main/java/nvc/guide/common/evaluation/InterviewEvaluationProperties.java` | 评估配置类（重建） |
| `docs/superpowers/specs/2026-07-09-step0-project-init-design.md` | Step 0 设计文档 |

---

## 二、当前项目状态

### 2.1 编译状态

| 部分 | 状态 | 说明 |
|------|------|------|
| 后端 Java | ✅ 编译通过 | `./gradlew compileJava` 成功 |
| 前端 TypeScript | ✅ 编译通过 | `pnpm run build` 成功 |

### 2.2 保留的后端模块

```
nvc.guide/
├── common/                           # 通用基础能力（117 个 Java 文件）
│   ├── ai/                           #   LlmProviderRegistry, StructuredOutputInvoker, PromptSanitizer
│   ├── annotation/ + aspect/         #   @RateLimit 限流
│   ├── async/                        #   Redis Stream 异步任务模板
│   ├── config/                       #   CORS, S3, Jackson, OpenAPI
│   ├── constant/                     #   常量
│   ├── evaluation/                   #   UnifiedEvaluationService（后续改造为 NVC 评估引擎）
│   ├── exception/                    #   ErrorCode（已清理为 NVC 版本）
│   ├── model/                        #   AsyncTaskStatus
│   └── result/                       #   Result<T> 统一响应
│
├── infrastructure/                   # 技术基础设施
│   ├── export/                       #   PdfExportService（骨架，后续扩展）
│   ├── file/                         #   文件解析、存储
│   ├── mapper/                       #   KnowledgeBaseMapper, RagChatMapper
│   └── redis/                        #   RedisService, InterviewSessionCache（骨架）
│
└── modules/
    ├── knowledgebase/                # 知识库模块（完整保留）
    │   ├── controller/               #   KnowledgeBaseController, RagChatController
    │   ├── listener/                 #   VectorizeStreamConsumer/Producer
    │   ├── model/                    #   KnowledgeBaseEntity, RagChatSessionEntity 等
    │   ├── repository/               #   4 个 Repository
    │   └── service/                  #   10 个 Service
    │
    ├── llmprovider/                  # LLM Provider 管理（完整保留）
    │   ├── controller/               #   LlmProviderController
    │   ├── dto/                      #   9 个 DTO
    │   ├── model/                    #   LlmProviderEntity, LlmGlobalSettingEntity
    │   ├── repository/               #   2 个 Repository
    │   └── service/                  #   ApiKeyEncryptionService, LlmProviderConfigService
    │
    └── voiceinterview/               # 语音面试模块（保留，后续改造为 NVC 语音练习）
        ├── config/                   #   VoiceInterviewProperties, WebSocketConfig
        ├── controller/               #   VoiceInterviewController
        ├── dto/                      #   7 个 DTO
        ├── handler/                  #   VoiceInterviewWebSocketHandler
        ├── listener/                 #   VoiceEvaluateStreamConsumer/Producer
        ├── model/                    #   4 个 Entity
        ├── repository/               #   3 个 Repository
        └── service/                  #   6 个 Service（DashscopeLlmService, QwenAsrService, QwenTtsService 等）
```

### 2.3 保留的前端文件

```
frontend/src/
├── pages/
│   ├── KnowledgeBaseManagePage.tsx    # 知识库管理
│   ├── KnowledgeBaseQueryPage.tsx     # 知识库问答
│   ├── KnowledgeBaseUploadPage.tsx    # 知识库上传
│   └── SettingsPage.tsx              # 设置（LLM Provider 配置）
│
├── components/
│   ├── AudioPlayer.tsx               # 音频播放器（语音模块用）
│   ├── AudioRecorder.tsx             # 音频录制器（语音模块用）
│   ├── RealtimeSubtitle.tsx          # 实时字幕（语音模块用）
│   ├── Layout.tsx                    # 布局组件（已简化）
│   ├── CodeBlock.tsx                 # 代码块渲染
│   ├── ConfirmDialog.tsx             # 确认对话框
│   ├── DeleteConfirmDialog.tsx       # 删除确认对话框
│   └── FileUploadCard.tsx            # 文件上传卡片
│
├── api/
│   ├── knowledgebase.ts              # 知识库 API
│   ├── llmProvider.ts                # LLM Provider API
│   ├── ragChat.ts                    # RAG 聊天 API
│   └── request.ts                    # HTTP 请求封装
│
├── hooks/
│   └── useTheme.ts                   # 主题切换 Hook
│
├── types/
│   └── llmProvider.ts                # LLM Provider 类型
│
└── utils/
    ├── date.ts                       # 日期工具
    ├── score.ts                      # 分数工具
    └── skillIcons.tsx                # 技能图标
```

### 2.4 基础设施配置

| 服务 | 地址 | 说明 |
|------|------|------|
| PostgreSQL | `10.237.255.9:5432` | 数据库 `nvc_practice`，用户 `nvc/nvc123` |
| Redis | `10.237.255.9:6379` | 使用 db 2 |
| MinIO | `10.237.255.9:9000` | 存储桶 `nvc-guide` |
| LLM | `https://token-plan-cn.xiaomimimo.com/v1` | 小米 MiMo 2.5 |

### 2.5 当前可用功能

由于保留了 knowledgebase 和 llmprovider 模块，以下功能目前可用：

1. **LLM Provider 管理**：添加/编辑/删除/测试 LLM Provider 配置
2. **知识库管理**：创建知识库、上传文档、自动向量化
3. **知识库问答**：基于 RAG 的智能问答（流式响应）
4. **语音基础设施**：AudioRecorder、AudioPlayer、RealtimeSubtitle 组件可用
5. **文件存储**：MinIO 文件上传/下载
6. **PDF 导出**：通用 PDF 生成框架（需扩展具体报告模板）

---

## 三、与原项目的对比

### 3.1 删除的内容

| 类别 | 删除数量 | 说明 |
|------|---------|------|
| Java 业务模块 | 4 个 | interview, resume, interviewschedule, test |
| Java 文件 | ~80 个 | Controller, Service, Model, Repository, DTO 等 |
| 前端页面 | 9 个 | 面试/简历相关页面 |
| 前端组件 | 13 个 | 面试/简历相关组件 |
| 前端 API/类型/Hook | 10 个 | 面试/简历相关 |
| Prompt 模板 | 11 个 | 面试/简历相关 |
| Skill 目录 | 10 个 | 面试技能目录 |
| ErrorCode | ~20 个 | 面试/简历相关错误码 |

### 3.2 保留的内容

| 类别 | 保留数量 | 说明 |
|------|---------|------|
| Java 通用模块 | 完整 | common/, infrastructure/ |
| Java 业务模块 | 3 个 | knowledgebase, llmprovider, voiceinterview |
| Java 文件 | 117 个 | |
| 前端页面 | 4 个 | 知识库 + 设置 |
| 前端组件 | 8 个 | 通用组件 + 音频组件 |
| 前端 API | 4 个 | 知识库 + LLM Provider |

### 3.3 新增的内容

| 类别 | 数量 | 说明 |
|------|------|------|
| NVC 评估 Prompt | 4 个 | 评估系统/用户/汇总模板 |
| NVC ErrorCode | 9 个 | 练习 + 语音练习错误码 |
| 配置文件 | 1 个 | InterviewEvaluationProperties（重建） |
| 设计文档 | 1 个 | Step 0 设计文档 |

---

## 四、Git 提交记录

```
ed117ed feat: Step 0 完成 - 项目初始化与清理
c3d5394 add CLAUDE.md with project conventions and permissions
2538620 init: 复制 interview-guide 项目作为 NVC 底座
```

---

## 五、后续步骤

按照实施计划，下一步是 **Step 1：数据库实体与 Repository 创建**，需要创建 8 个 NVC 核心实体：

1. `NvcPracticeSessionEntity` — NVC 文字练习会话
2. `NvcPracticeMessageEntity` — NVC 练习对话消息
3. `NvcEvaluationEntity` — NVC 评估记录
4. `NvcUserProfileEntity` — 用户个人档案
5. `NvcUserAbilityScoreEntity` — NVC 能力评分历史
6. `NvcScenarioEntity` — NVC 练习场景
7. `NvcAgentConfigEntity` — Agent 角色配置
8. `NvcCommunicationRecordEntity` — 真实沟通记录

以及对应的 Repository、DTO、MapStruct Mapper。
