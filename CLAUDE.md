# NVC 非暴力沟通练习助手 — Claude 编码规范

Spring Boot 4.0 + Java 21 + Spring AI 2.0 + React 18 非暴力沟通练习平台。写代码时必须遵守以下规则。

---

## 权限规则（仅限本项目）

- 查询文件、搜索代码、读取内容：**不需要询问**
- 执行命令（git、gradle、npm、pnpm、docker 等）：**不需要询问**
- 创建新文件、写入新内容：**不需要询问**
- 编辑已有文件（Edit 工具）：**不需要询问**
- **删除文件或目录：需要询问确认**
- **覆盖重要配置文件（application.yml、build.gradle、package.json）：需要询问确认**
- **执行破坏性 git 操作（reset --hard、force push）：需要询问确认**

---

## 项目概述

基于 interview-guide 基座改造的 NVC 非暴力沟通练习助手。核心功能：
- 三种练习模式：场景驱动、自由对话、结构化四步练习
- 多 Agent 协同调度（6 类 NVC 专职 Agent）
- 实时流式评估（四要素：观察/感受/需求/请求）
- 语音练习（WebSocket + ASR/TTS）
- 用户档案系统（能力画像 + 向量化个性化）
- RAG 知识库（NVC 理论文档检索增强）
- 数据可视化仪表盘（雷达图 + 趋势图）

---

## 项目结构

单模块 Gradle 项目，按功能分包：

```
interview.guide/                    # 包名暂保留原项目（后续可重构为 nvc.guide）
├── App.java                        # @SpringBootApplication + @EnableScheduling
│
├── common/                         # 通用基础能力（直接复用）
│   ├── annotation/                 #   @RateLimit
│   ├── aspect/                     #   RateLimitAspect
│   ├── ai/                         #   LlmProviderRegistry、StructuredOutputInvoker
│   ├── async/                      #   AbstractStreamConsumer/Producer
│   ├── config/                     #   配置类
│   ├── constant/                   #   常量
│   ├── exception/                  #   ErrorCode、BusinessException
│   └── result/                     #   Result<T>
│
├── infrastructure/                 # 技术基础设施（直接复用）
│   ├── export/                     #   PdfExportService
│   ├── file/                       #   文件解析、存储
│   ├── mapper/                     #   MapStruct
│   └── redis/                      #   RedisService
│
└── modules/                        # 业务模块
    ├── nvcpractice/                # NVC 文字练习（核心模块）
    │   ├── controller/             #   NvcPracticeController、NvcReportController、NvcAgentConfigController
    │   ├── service/                #   NvcPracticeSessionService、NvcPracticeDialogueService、NvcAgentOrchestrator、NvcEvaluationService
    │   ├── model/                  #   NvcPracticeSessionEntity、NvcPracticeMessageEntity、NvcEvaluationEntity、NvcAgentConfigEntity + 枚举
    │   ├── dto/                    #   请求/响应 DTO
    │   ├── repository/             #   Spring Data JPA
    │   └── listener/               #   Redis Stream 异步评估
    │
    ├── nvcvoice/                   # NVC 语音练习（改造自 voiceinterview）
    │   ├── controller/
    │   ├── service/                #   NvcVoiceService、复用 QwenAsrService/QwenTtsService
    │   ├── handler/                #   NvcVoiceWebSocketHandler
    │   ├── model/
    │   ├── dto/
    │   └── config/                 #   WebSocket 配置
    │
    ├── nvcprofile/                 # 用户档案系统
    │   ├── controller/             #   NvcProfileController、NvcDashboardController
    │   ├── service/                #   NvcProfileService、NvcCommunicationAnalysisService、NvcDashboardService
    │   ├── model/                  #   NvcUserProfileEntity、NvcUserAbilityScoreEntity、NvcCommunicationRecordEntity
    │   ├── dto/
    │   └── repository/
    │
    ├── nvcscenario/                # 场景库管理
    │   ├── controller/             #   NvcScenarioController
    │   ├── service/                #   NvcScenarioService
    │   ├── model/                  #   NvcScenarioEntity
    │   ├── dto/
    │   └── repository/
    │
    ├── knowledgebase/              # 知识库（复用，新增 NVC 内容）
    └── llmprovider/                # LLM Provider 管理（直接复用）
```

---

## 分层架构

```
Controller → Service → Repository
                ↕
          Infrastructure（RedisService、FileStorageService、PdfExportService）
```

### Controller 层
- 仅路由和委托，禁止业务逻辑
- RESTful 风格：`/api/nvc/{module}/{action}`
- 使用 `@RateLimit` 注解做限流
- 通过 `@Valid` + `@RequestBody` 校验请求

### Service 层
- 业务逻辑编排
- 使用 `LlmProviderRegistry.getChatClientOrDefault(provider)` 获取 ChatClient
- 异步任务通过 Redis Stream
- 所有业务异常使用 `BusinessException(ErrorCode.XXX, message)`

---

## JavaBean 后缀规则

| 后缀 | 用途 | 示例 |
|------|------|------|
| `XxxEntity` | JPA 持久化 | `NvcPracticeSessionEntity` |
| `XxxDTO` | 跨层数据传输 | `PracticeSessionResponse` |
| `XxxRequest` | 前端请求体 | `CreatePracticeSessionRequest` |
| `XxxResponse` | 前端响应体 | `DialogueResponse` |

- 不可变数据载体优先用 `record`
- Entity 映射用 MapStruct
- **禁止直接返回 Entity 给前端**

---

## 异常与错误码

### ErrorCode 分域

| 域 | 范围 | 示例 |
|----|------|------|
| 通用 | 1xxx | BAD_REQUEST(400)、NOT_FOUND(404) |
| 存储 | 4xxx | STORAGE_UPLOAD_FAILED(4001) |
| 导出 | 5xxx | EXPORT_PDF_FAILED(5001) |
| 知识库 | 6xxx | KNOWLEDGE_BASE_NOT_FOUND(6001) |
| AI 服务 | 7xxx | AI_SERVICE_TIMEOUT(7002) |
| 限流 | 8xxx | RATE_LIMIT_EXCEEDED(8001) |
| NVC 练习 | 3xxx | NVC_SESSION_NOT_FOUND(3001)、NVC_EVALUATION_FAILED(3002)、NVC_SCENARIO_NOT_FOUND(3003)、NVC_PROFILE_NOT_FOUND(3004)、NVC_AGENT_CONFIG_NOT_FOUND(3005) |
| NVC 语音 | 10xxx | NVC_VOICE_SESSION_NOT_FOUND(10001) |

### 异常处理规则
- 抛出：`throw new BusinessException(ErrorCode.XXX, "描述信息")`
- **禁止** `throw new RuntimeException(...)`
- 全局异常处理器统一返回 HTTP 200 + `Result.error(code, message)`

---

## AI 服务调用

### LLM Provider

```java
ChatClient chatClient = llmProviderRegistry.getChatClientOrDefault(provider);
```

### 结构化输出

```java
String result = structuredOutputInvoker.invokeStructuredOutput(prompt, chatClient, outputConverter);
```

### Prompt 模板
- 存放在 `resources/prompts/`，使用 StringTemplate（`.st`）格式
- NVC 相关：`nvc-*.st`

---

## 格式与命名

- **2 空格缩进**，列限制 100 字符
- 类名 UpperCamelCase，方法名 lowerCamelCase，常量 UPPER_SNAKE_CASE
- **禁止通配符导入**
- 优先 `record` 作为不可变数据载体
- 使用现代 Java 特性：`switch` 表达式、pattern matching `instanceof`、text blocks

---

## 事务规则

- `@Transactional` 放 Service 层
- **禁止**在事务方法内调用外部 API（LLM、S3）
- **禁止**同类内部调用 `@Transactional` 方法
- 保持事务范围最小

---

## 日志规范

- 使用 SLF4J（`@Slf4j`）
- 结构化日志：`log.info("Session created: sessionId={}", id)`
- 异常作为最后一个参数：`log.error("Evaluation failed: sessionId={}", id, e)`

---

## 数据库

- PostgreSQL + pgvector（向量搜索，1024 维 COSINE）
- JPA 实体使用 `@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
- `ddl-auto` 开发环境 `update`，生产环境 `false`

---

## 配置管理

- 配置文件：`application.yml` + `.env`
- 敏感信息放 `.env`
- 业务配置用 `@ConfigurationProperties`
- **禁止** `@Value` 散落在 Service 中

---

## NVC Agent 体系

### NvcAgentScene 枚举

```
SCENARIO_GENERATOR       // 场景生成官
DIALOGUE_GUIDE           // 对话引导官
DIFFICULT_PARTNER        // 困难搭档
STEP_OBSERVE_COACH       // 观察步骤教练
STEP_FEELING_COACH       // 感受步骤教练
STEP_NEED_COACH          // 需求步骤教练
STEP_REQUEST_COACH       // 请求步骤教练
NVC_EXPRESSION_EVALUATOR // NVC 表达评估官
EMPATHY_COACH            // 共情教练
NVC_KNOWLEDGE_ADVISOR    // NVC 知识顾问
```

### Agent 调度流程

```
用户消息 → NvcAgentOrchestrator.decideNextAgent()
         → NvcAgentChatService.chat(config, context, message)
         → 保存消息 → 实时评估 → 返回结果
```

### Agent 配置热更新

```
PUT /api/nvc/agents/configs/{scene}
→ 更新 DB → 清除 Redis 缓存 → 下次调用自动生效
```

---

## 速查：禁止清单

| 禁止项 | 原因 |
|--------|------|
| `throw new RuntimeException(...)` | 绕过全局异常处理 |
| 直接返回 Entity 给前端 | 暴露内部结构 |
| `@Value` 散落在 Service 中 | 配置应集中管理 |
| 事务内调用外部 API | 占用 DB 连接 |
| 同类内部调用 `@Transactional` | AOP 代理不生效 |
| `catch (Exception e) {}` 静默忽略 | 隐藏错误 |
| 循环调用 DB | 改用批量操作 |
| 硬编码密钥 | 安全风险 |
| `Executors.newXxxThreadPool()` | OOM 风险 |
