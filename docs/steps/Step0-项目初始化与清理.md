# Step 0：项目初始化与清理

> 目标：复制 interview-guide 基座项目，清理不需要的模块，确保项目能正常启动
>
> 预计耗时：1-2 天
>
> 前置条件：已从 https://github.com/Snailclimb/interview-guide.git 克隆项目到本地

---

## 0.1 复制项目

将克隆的 interview-guide 项目完整复制一份，作为 NVC 项目的起点：

```
源目录：D:\code\interview-guide
目标目录：D:\code\nvc-guide（或你希望的路径）
```

复制后删除 `.git` 目录，重新 `git init` 作为新项目。

## 0.2 重命名项目标识

### 0.2.1 修改 settings.gradle

文件：`settings.gradle`

```gradle
// 修改前
rootProject.name = 'interview-guide'
// 修改后
rootProject.name = 'nvc-guide'
```

### 0.2.2 修改 build.gradle 中的 artifactId

文件：`app/build.gradle`

找到 `archivesBaseName` 或 `group` 相关配置，改为 `nvc-guide`。

### 0.2.3 修改 application.yml 中的应用名

文件：`app/src/main/resources/application.yml`

```yaml
spring:
  application:
    name: nvc-guide  # 原为 interview-guide
```

### 0.2.4 修改 docker-compose.yml

文件：`docker-compose.yml`（如果有的话）

将服务名、容器名中的 `interview-guide` 改为 `nvc-guide`。

### 0.2.5 修改前端项目名

文件：`frontend/package.json`

```json
{
  "name": "nvc-guide-frontend"  // 原为 ai-interview-frontend
}
```

文件：`frontend/index.html`

```html
<title>NVC 非暴力沟通练习助手</title>  <!-- 原为 AI Interview Guide -->
```

## 0.3 重命名 Java 包名（可选，推荐做）

### 方案 A：全局替换包名（干净但工作量大）

将 `interview.guide` 全局替换为 `nvc.guide`，涉及：
- 所有 Java 文件的 `package` 声明
- 所有 Java 文件的 `import` 语句
- `application.yml` 中的包扫描路径
- `App.java` 的 `@SpringBootApplication` 扫描路径

### 方案 B：保留原包名（省事但不干净）

保留 `interview.guide` 包名不动，只改业务逻辑。推荐初期用这个方案，后续有时间再清理。

**如果选方案 B，以下步骤中的路径仍然用 `interview.guide`，但业务模块名改为 NVC 相关。**

## 0.4 删除不需要的模块

### 0.4.1 删除简历模块

删除以下目录（整个目录删除）：

```
app/src/main/java/interview/guide/modules/resume/
```

包含的文件：
- `controller/ResumeController.java`
- `dto/` 目录下所有文件
- `model/ResumeEntity.java`
- `repository/ResumeRepository.java`
- `service/ResumeUploadService.java`
- `service/ResumeParseService.java`
- `service/ResumeGradingService.java`
- `service/ResumeService.java`（或其他名称）

### 0.4.2 删除面试日程模块

删除以下目录：

```
app/src/main/java/interview/guide/modules/interviewschedule/
```

### 0.4.3 删除面试模块（后面会重建为 nvcpractice）

删除以下目录：

```
app/src/main/java/interview/guide/modules/interview/
```

**注意：先不要删除 voiceinterview 和 knowledgebase，后面会改造复用。**

### 0.4.4 删除不需要的 Prompt 模板

删除以下文件（在 `app/src/main/resources/prompts/` 下）：

```
interview-question-skill-system.st
interview-question-skill-user.st
interview-question-resume-system.st
interview-question-resume-user.st
interview-evaluation-system.st
interview-evaluation-user.st
interview-evaluation-summary-system.st
interview-evaluation-summary-user.st
resume-analysis-system.st
resume-analysis-user.st
jd-parse-system.st
```

保留：
```
knowledgebase-query-system.st
knowledgebase-query-user.st
knowledgebase-query-rewrite.st
```

### 0.4.5 删除不需要的 Skill 目录

删除以下目录（在 `app/src/main/resources/skills/` 下）：

```
java-backend/
ali-backend/
bytedance-backend/
java-backend-tencent/
frontend/
python-backend/
algorithm/
system-design/
test-development/
ai-agent-dev/
_shared/（如果有的话）
```

**保留 skills/ 目录本身**，后面会添加 NVC 相关的 Skill。

### 0.4.6 删除不需要的前端页面

删除以下文件（在 `frontend/src/pages/` 下）：

```
InterviewPage.tsx
InterviewHubPage.tsx
InterviewHistoryPage.tsx
VoiceInterviewPage.tsx
VoiceInterviewEvaluationPage.tsx
ResumeDetailPage.tsx
UploadPage.tsx
HistoryPage.tsx
InterviewSchedulePage.tsx
```

保留：
```
KnowledgeBaseManagePage.tsx
KnowledgeBaseQueryPage.tsx
KnowledgeBaseUploadPage.tsx（如果存在）
SettingsPage.tsx
```

### 0.4.7 删除不需要的前端组件

删除以下文件（在 `frontend/src/components/` 下）：

```
InterviewChatPanel.tsx（后面会重建为 NvcChatPanel）
InterviewPanel.tsx（后面会重建）
UnifiedInterviewModal.tsx（后面会重建）
AnalysisPanel.tsx（后面会重建）
RadarChart.tsx（后面会改造）
```

保留：
```
AudioRecorder.tsx
AudioPlayer.tsx
RealtimeSubtitle.tsx
```

### 0.4.8 清理前端 API 文件

如果 `frontend/src/api/` 目录下有面试相关的 API 文件，删除或清空内容，后面重建。

## 0.5 清理 common/ 中面试相关的评估逻辑

### 0.5.1 检查 UnifiedEvaluationService

文件：`app/src/main/java/interview/guide/common/evaluation/UnifiedEvaluationService.java`

这个文件需要改造但**不要删除**。先保留原内容，后续 Step 中会改造为 NVC 评估引擎。

### 0.5.2 检查 Infrastructure 中的 Mapper

文件：`app/src/main/java/interview/guide/infrastructure/mapper/`

删除面试相关的 MapStruct Mapper：
- `InterviewMapper.java`（如果存在）
- `ResumeMapper.java`（如果存在）

保留：
- `KnowledgeBaseMapper.java`
- `RagChatMapper.java`（如果存在）

### 0.5.3 检查 Redis 缓存

文件：`app/src/main/java/interview/guide/infrastructure/redis/InterviewSessionCache.java`

**不要删除**，后续改造为 `NvcSessionCache`。

## 0.6 清理 Redis Stream 相关

### 0.6.1 删除简历分析的 Stream

删除以下文件（如果存在）：
- `ResumeAnalyzeStreamProducer.java`
- `ResumeAnalyzeStreamConsumer.java`

### 0.6.2 删除面试评估的 Stream

删除以下文件（如果存在）：
- `EvaluateStreamProducer.java`
- `EvaluateStreamConsumer.java`

保留：
- `VectorizeStreamProducer.java`（知识库向量化）
- `VectorizeStreamConsumer.java`（知识库向量化）
- `VoiceEvaluateStreamProducer.java`（语音评估，后面改造）
- `VoiceEvaluateStreamConsumer.java`（语音评估，后面改造）

## 0.7 修改 ErrorCode

文件：`app/src/main/java/interview/guide/common/exception/ErrorCode.java`

删除面试和简历相关的错误码，保留通用错误码和知识库相关错误码。

**需要删除的**（示例，根据实际内容调整）：
```java
// 简历相关 (2xxx)
RESUME_NOT_FOUND(2001),
RESUME_PARSE_FAILED(2002),
// ...

// 面试相关 (3xxx)
INTERVIEW_SESSION_NOT_FOUND(3001),
// ...
```

**需要新增的**（暂时预留，后续填充）：
```java
// NVC 练习相关 (3xxx，复用面试的域范围)
NVC_SESSION_NOT_FOUND(3001),
NVC_EVALUATION_FAILED(3002),
NVC_SCENARIO_NOT_FOUND(3003),
NVC_PROFILE_NOT_FOUND(3004),
NVC_AGENT_CONFIG_NOT_FOUND(3005),

// 语音练习相关 (10xxx，复用语音面试的域范围)
NVC_VOICE_SESSION_NOT_FOUND(10001),
```

## 0.8 修改前端路由

文件：`frontend/src/router.tsx`（或 `App.tsx`，根据实际文件名）

删除面试和简历相关的路由，保留知识库和设置路由，添加临时的首页路由：

```tsx
// 临时首页 - 后续会替换为 NvcPracticeHubPage
const routes = [
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <Navigate to="/knowledge-bases" replace /> },
      { path: 'knowledge-bases', element: <KnowledgeBaseManagePage /> },
      { path: 'knowledge-bases/:id/query', element: <KnowledgeBaseQueryPage /> },
      { path: 'settings', element: <SettingsPage /> },
      // 后续会添加 NVC 相关路由
    ],
  },
];
```

## 0.9 验证项目能正常启动

### 0.9.1 后端启动验证

```bash
cd D:\code\nvc-guide
./gradlew bootRun
```

预期结果：
- 编译通过（可能有少量 import 报错，需要手动清理残留引用）
- Spring Boot 启动成功
- 数据库表自动创建（除了已删除模块的表）
- Swagger UI 可访问：http://localhost:8080/swagger-ui.html

### 0.9.2 前端启动验证

```bash
cd D:\code\nvc-guide\frontend
pnpm install
pnpm dev
```

预期结果：
- 编译通过（可能有少量 import 报错，需要手动清理残留引用）
- 页面可访问：http://localhost:5173
- 知识库页面和设置页面可正常显示

### 0.9.3 常见问题处理

**问题 1：编译报错 - 找不到已删除的类**

原因：其他文件 import 了已删除的类。

解决：全局搜索已删除类名，清理所有引用。

**问题 2：启动报错 - Bean 创建失败**

原因：某个 Service 依赖了已删除的 Repository 或 Service。

解决：找到报错的 Bean，清理依赖或删除该 Bean。

**问题 3：前端报错 - 找不到已删除的组件**

原因：路由或页面 import 了已删除的组件。

解决：清理 import 和引用，暂时用占位符替代。

## 0.10 清理检查清单

完成以上步骤后，确认以下状态：

```
□ 后端能正常启动，无编译错误
□ 前端能正常启动，无编译错误
□ 知识库页面可正常访问
□ 设置页面可正常访问
□ Swagger UI 显示的 API 只包含：knowledge-bases/*, llm-providers/*
□ 数据库中没有 resume、interview_session、interview_answer 等表
□ Git 已初始化，首次提交标记为 "Project initialization: clean up interview-guide"
```

---

## 附录：需要保留的完整文件清单

### 后端 - 完整保留的包

```
app/src/main/java/interview/guide/
├── App.java                                    ← 改 application name
├── common/
│   ├── ai/
│   │   ├── LlmProviderRegistry.java           ← 直接保留
│   │   ├── StructuredOutputInvoker.java       ← 直接保留
│   │   ├── PromptSanitizer.java               ← 直接保留
│   │   └── PromptSecurityConstants.java       ← 直接保留
│   ├── annotation/                            ← 直接保留
│   ├── aspect/                                ← 直接保留
│   ├── async/
│   │   ├── AbstractStreamConsumer.java        ← 直接保留
│   │   └── AbstractStreamProducer.java        ← 直接保留
│   ├── config/                                ← 直接保留
│   ├── constant/                              ← 直接保留
│   ├── exception/                             ← 清理 ErrorCode
│   ├── model/                                 ← 直接保留
│   └── result/                                ← 直接保留
├── infrastructure/
│   ├── export/                                ← 直接保留
│   ├── file/                                  ← 直接保留
│   ├── mapper/                                ← 清理面试相关 Mapper
│   └── redis/                                 ← 保留，后续改造
└── modules/
    ├── knowledgebase/                         ← 直接保留
    ├── llmprovider/                           ← 直接保留
    ├── voiceinterview/                        ← 保留，后续改造
    ├── interview/                             ← 删除
    ├── resume/                                ← 删除
    └── interviewschedule/                     ← 删除
```

### 前端 - 完整保留的文件

```
frontend/src/
├── components/
│   ├── AudioRecorder.tsx                      ← 直接保留
│   ├── AudioPlayer.tsx                        ← 直接保留
│   └── RealtimeSubtitle.tsx                   ← 直接保留
├── pages/
│   ├── KnowledgeBaseManagePage.tsx            ← 直接保留
│   ├── KnowledgeBaseQueryPage.tsx             ← 直接保留
│   └── SettingsPage.tsx                       ← 直接保留
└──（其他文件需要清理后重建）
```
