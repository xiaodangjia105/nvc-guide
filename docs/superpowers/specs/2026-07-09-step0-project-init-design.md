# Step 0 设计文档：项目初始化与清理

> 将 interview-guide 改造为 NVC 非暴力沟通练习助手底座
>
> 设计日期：2026-07-09

---

## 一、目标

将 `D:\code\interview-guide`（AI 面试平台）完整复制并改造为 `D:\code\nvc-guide`（NVC 练习助手底座），确保项目能正常编译和启动。

## 二、关键决策

| 决策项 | 选择 | 原因 |
|--------|------|------|
| Java 包名 | `interview.guide` → `nvc.guide` | 方案 A，干净彻底 |
| 数据库连接 | 直连远程 VM (10.237.255.9) | 用户选择，PG 库改为 `nvc_practice` |
| Redis | 直连远程 VM，使用 db 2 | 避免与其他项目冲突 |
| LLM Provider | 小米 MiMo | 用户提供的 API Key |
| MinIO | 直连远程 VM (10.237.255.9:9000) | 桶名改为 `nvc-guide` |

## 三、实施步骤

### 3.1 复制项目 + Git 初始化

- 从 `D:\code\interview-guide` 复制到 `D:\code\nvc-guide`（覆盖已有文件）
- **保留已有的** `docs/` 和 `.claude/` 目录（用户已有的文档和配置）
- 删除 `.git` 目录
- 执行 `git init` + 首次提交

### 3.2 全局包名重命名

将所有 Java 文件中的 `interview.guide` 替换为 `nvc.guide`：

- `package interview.guide.xxx` → `package nvc.guide.xxx`
- `import interview.guide.xxx` → `import nvc.guide.xxx`
- 目录结构：`interview/guide/` → `nvc/guide/`
- 涉及约 150+ 个 Java 文件

### 3.3 删除不需要的模块

**Java 模块删除：**
- `modules/interview/` — 面试模块
- `modules/resume/` — 简历模块
- `modules/interviewschedule/` — 面试日程模块
- `modules/test/` — 测试模块

**Java 基础设施清理：**
- `infrastructure/mapper/InterviewMapper.java`
- `infrastructure/mapper/ResumeMapper.java`
- `infrastructure/redis/InterviewSessionCache.java` → 后续改造为 NvcSessionCache

**Java evaluation 清理：**
- `common/evaluation/InterviewEvaluationProperties.java` — 删除
- `common/evaluation/UnifiedEvaluationService.java` — 保留，后续改造

**Prompt 模板清理（resources/prompts/）：**
- 删除：interview-question-*.st, interview-evaluation-*.st, resume-analysis-*.st, jd-parse-*.st
- 保留：knowledgebase-query-*.st

**Skill 目录清理（resources/skills/）：**
- 删除：java-backend/, ali-backend/, bytedance-backend/, java-backend-tencent/, frontend/, python-backend/, algorithm/, system-design/, test-development/, ai-agent-dev/
- 保留 skills/ 目录本身

### 3.4 前端清理

**删除的页面：**
- InterviewPage.tsx, InterviewHubPage.tsx, InterviewHistoryPage.tsx
- VoiceInterviewPage.tsx, VoiceInterviewEvaluationPage.tsx
- ResumeDetailPage.tsx, UploadPage.tsx, HistoryPage.tsx
- InterviewSchedulePage.tsx

**删除的组件：**
- InterviewChatPanel.tsx, InterviewPanel.tsx, InterviewDetailPanel.tsx
- InterviewMessageBubble.tsx, InterviewPageHeader.tsx
- UnifiedInterviewModal.tsx, AnalysisPanel.tsx, RadarChart.tsx
- interviewschedule/ 目录（7 个文件）
- HistoryList.tsx, ScoreProgressBar.tsx

**删除的 API/类型/Hook：**
- api/interview.ts, api/interviewSchedule.ts, api/resume.ts, api/history.ts, api/skill.ts
- types/interview.ts, types/interviewSchedule.ts, types/resume.ts
- hooks/useInterviewConfig.ts, hooks/useInterviewSchedule.ts

**保留的页面：**
- KnowledgeBaseManagePage.tsx, KnowledgeBaseQueryPage.tsx, KnowledgeBaseUploadPage.tsx
- SettingsPage.tsx

**保留的组件：**
- AudioRecorder.tsx, AudioPlayer.tsx, RealtimeSubtitle.tsx
- Layout.tsx, CodeBlock.tsx, ConfirmDialog.tsx, DeleteConfirmDialog.tsx
- FileUploadCard.tsx

### 3.5 配置文件修改

**settings.gradle：**
```
rootProject.name = 'nvc-guide'
```

**app/build.gradle：**
- archivesBaseName 或相关配置改为 `nvc-guide`

**application.yml：**
- `spring.application.name: nvc-guide`
- `springdoc.packages-to-scan: nvc.guide.modules`
- 数据库：`POSTGRES_DB:nvc_practice`
- 配置路径：`~/.nvc-guide/llm-providers.yml`
- 存储桶：`nvc-guide`
- 添加 MiMo Provider：
  ```yaml
  mimo:
    base-url: https://token-plan-cn.xiaomimimo.com/v1
    api-key: ${PROVIDER_MIMO_API_KEY:}
    model: ${PROVIDER_MIMO_MODEL:mimo-2.5}
    supports-embedding: false
  ```

**docker-compose.yml：**
- 服务名/容器名改为 nvc-guide 相关

**frontend/package.json：**
- `name: "nvc-guide-frontend"`

**frontend/index.html：**
- `<title>NVC 非暴力沟通练习助手</title>`

### 3.6 ErrorCode 清理

删除面试/简历相关错误码，新增 NVC 预留错误码。

### 3.7 前端路由修改

临时路由：重定向 `/` → `/knowledge-bases`，保留知识库和设置路由。

### 3.8 .env 文件

```env
# PostgreSQL — NVC 项目专用库
POSTGRES_HOST=10.237.255.9
POSTGRES_PORT=5432
POSTGRES_DB=nvc_practice
POSTGRES_USER=nvc
POSTGRES_PASSWORD=nvc123

# Redis — 使用 db 2 避免冲突
REDIS_HOST=10.237.255.9
REDIS_PORT=6379
REDIS_DATABASE=2

# MinIO
APP_STORAGE_ENDPOINT=http://10.237.255.9:9000
APP_STORAGE_ACCESS_KEY=minioadmin
APP_STORAGE_SECRET_KEY=minioadmin
APP_STORAGE_BUCKET=nvc-guide

# LLM — 小米 MiMo
PROVIDER_MIMO_API_KEY=sk-cgmf0ae7ifxn4kdqyi2qr94ony1womyri1qtom1ba2qhhkh2
PROVIDER_MIMO_MODEL=mimo-2.5
APP_AI_DEFAULT_PROVIDER=mimo
AI_MODEL=mimo-2.5
```

## 四、验证清单

- [ ] 后端 `./gradlew bootRun` 编译通过，启动成功
- [ ] 前端 `pnpm dev` 编译通过，页面可访问
- [ ] Swagger UI 显示的 API 只包含 knowledge-bases/* 和 llm-providers/*
- [ ] 数据库 nvc_practice 中表自动创建（knowledge_base, llm_provider 等）
- [ ] 知识库页面和设置页面可正常显示
- [ ] Git 已初始化，首次提交

## 五、风险与注意事项

1. **包名重命名可能遗漏**：全局替换后需编译验证，逐个修复残留引用
2. **voiceinterview 模块保留**：虽然删除了面试相关模块，但 voiceinterview 保留供后续改造
3. **JPA ddl-auto: update**：首次连接 nvc_practice 库会自动创建保留模块的表
4. **MiMo API 地址**：`https://token-plan-cn.xiaomimimo.com/v1`，模型名 `mimo-2.5`
