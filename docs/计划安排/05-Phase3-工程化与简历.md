# Phase 3：工程化 + 简历包装（3 天）

> 前置条件：Phase 2 完成

---

## 一、测试覆盖（Day 16-17）

### 1.1 测试框架配置

```groovy
// build.gradle 新增
testImplementation 'org.springframework.boot:spring-boot-starter-test'
testImplementation 'org.testcontainers:postgresql'
testImplementation 'org.testcontainers:junit-jupiter'
```

### 1.2 测试基类

```java
@SpringBootTest
@Testcontainers
public abstract class TestBase {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("nvc_test");

    @Container
    static GenericContainer<?> redis =
        new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

### 1.3 核心测试（5 个）

| 测试类 | 测试目标 | 关键测试用例 |
|--------|---------|-------------|
| NvcPracticeSessionServiceTest | 会话管理 | 创建/获取/更新/缓存/状态转换校验 |
| NvcAgentOrchestratorTest | Agent 调度 | 三种模式路由/上下文组装/工具选择 |
| NvcEvaluationServiceTest | 评估服务 | 实时评估/最终评估/结构化输出 |
| NvcMainAgentServiceTest | 主 Agent | CoT 引擎/工具调用/对话历史 |
| NvcWikiServiceTest | Wiki 服务 | CRUD/自动沉淀/语义搜索/文件存储 |

### 1.4 覆盖率

- JaCoCo 覆盖率报告
- 目标：核心 Service ≥ 60%

---

## 二、文档（Day 18）

### 2.1 架构文档

`docs/architecture.md` 包含：
- 系统架构图
- 模块职责说明
- 数据流图
- 技术选型说明
- 部署架构

### 2.2 API 文档

完善 SpringDoc OpenAPI 注解：
- 所有 Controller 添加 `@Tag`
- 所有 API 添加 `@Operation`
- 所有 DTO 添加 `@Schema`

### 2.3 开发指南

`docs/development-guide.md` 包含：
- 环境搭建步骤
- 代码规范
- 提交规范
- 部署流程

---

## 三、CI/CD（Day 18）

```yaml
# .github/workflows/ci.yml
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Build
        run: ./gradlew build -x test
      - name: Test
        run: ./gradlew test
      - name: Coverage Report
        run: ./gradlew jacocoTestReport
      - name: Upload Coverage
        uses: actions/upload-artifact@v4
        with:
          name: coverage-report
          path: app/build/reports/jacoco/
```

---

## 四、简历包装

### 4.1 项目描述（简历用）

**简短版（2-3 行）：**

> **NVC 非暴力沟通智能练习平台** | Spring Boot 4.0 + Spring AI 2.0 + React 18
>
> AI 驱动的非暴力沟通学习平台，支持多 Agent 编排练习、RAG 知识检索、个人知识沉淀。基于 Spring AI 实现 Agent 工具调用框架，支持 10+ 工具动态注册和 Function Calling。

**详细版（5-6 行）：**

> **NVC 非暴力沟通智能练习平台** | Spring Boot 4.0 + Spring AI 2.0 + React 18 + PostgreSQL + pgvector
>
> - 设计并实现多 Agent 编排架构，支持 10 种专职 Agent 场景的动态路由和策略调度（ModeRouter 策略模式）
> - 基于 Spring AI Function Calling 实现 Agent 工具调用框架，支持 RAG 检索、用户档案查询、NVC 评估等 10+ 工具的动态注册
> - 构建个人知识 Wiki 系统，支持 AI 自动沉淀、手动记录、真实场景记录三种来源，基于 pgvector 实现语义检索
> - 实现 RAG 增强的练习对话，支持多知识库管理、混合检索（向量 + 关键词）、个性化权重
> - 设计可扩展的 Skill 体系，评估、场景生成、对话引导等能力标准化为可复用 Skill，支持 Agent 和主助手调用

### 4.2 六大技术亮点

#### 亮点 1：多 Agent 编排架构

**技术要点：**
- ModeRouter 策略模式（FreeDialogRouter / ScenarioRouter / StructuredRouter）
- EnumMap<NvcPracticeMode, ModeRouter> 动态路由
- Agent 配置热更新（DB + Redis 缓存）
- 10 种 Agent 场景枚举（NvcAgentScene）

**面试话术：**
> "我设计了一个基于策略模式的多 Agent 编排架构。核心是 NvcAgentOrchestrator，它使用 ModeRouter 接口为不同的练习模式提供独立的路由逻辑。每种模式下有多个专职 Agent，比如场景驱动模式下有 SCENARIO_GENERATOR（生成场景）、DIALOGUE_GUIDE（对话引导）、DIFFICULT_PARTNER（困难搭档）、NVC_EXPRESSION_EVALUATOR（评估官）等。路由决策基于对话状态（轮次、当前阶段、用户表现），而不是简单的关键词匹配。"

#### 亮点 2：Agent 工具调用框架

**技术要点：**
- NvcTool 接口设计（name / description / inputSchema / execute）
- NvcToolRegistry 自动注册（Spring List<NvcTool> 注入）
- Spring AI FunctionCallback 适配
- 10 种工具实现

**面试话术：**
> "我基于 Spring AI 的 Function Calling 机制实现了一个 Agent 工具调用框架。核心是 NvcTool 接口，每个工具定义名称、描述、输入 Schema 和执行方法。NvcToolRegistry 使用 Spring 的自动注入收集所有 NvcTool 实现，然后转换为 Spring AI 的 FunctionCallback 列表。Agent 在对话时可以动态调用这些工具。"

#### 亮点 3：RAG 知识检索系统

**技术要点：**
- pgvector COSINE 相似度检索
- PostgreSQL full-text search 关键词检索
- 混合排序算法（向量 × 0.7 + 关键词 × 0.3）
- 个性化权重（薄弱要素 × 1.5）
- 多知识库隔离（type 字段 + 逻辑隔离）

**面试话术：**
> "LLM 懂 NVC 理论，但不懂用户的个人知识。我的 RAG 系统主要检索两类内容：100+ 条话术模板和个人 Wiki。检索策略是混合检索——向量相似度和关键词检索结合，权重 7:3。个性化方面，系统会根据用户的薄弱环节加权检索相关内容。RAG 的价值不在于补充通用知识，而在于个性化和专业化。"

#### 亮点 4：个人知识 Wiki 系统

**技术要点：**
- 三层存储：文件系统 + DB + pgvector
- AI 自动生成学习笔记（Prompt Engineering）
- Markdown 编辑器（前端）
- 语义检索（基于 pgvector）
- 按用户隔离（userId 过滤）

**面试话术：**
> "我设计了一个三层存储的个人知识 Wiki：文件系统存储 Markdown 原文，数据库存储元数据，pgvector 存储向量化内容。Wiki 有三种来源：练习结束后 AI 自动生成、用户手动记录、以及通过主 Agent 对话时 AI 辅助生成。自动沉淀的流程是：练习结束 → AI 分析对话 → 生成 Markdown 笔记 → 写入文件 → 向量化。"

#### 亮点 5：Spring AI @Tool 注解 + Skill 组合模式

**技术要点：**
- Spring AI 原生 `@Tool` 注解定义工具
- Skill = 普通 `@Service`，封装业务逻辑
- Tool = `@Tool` 方法，内部调用 Service
- `ToolCallbacks.from()` 自动扫描注册
- 结构化输出（BeanOutputConverter）

**面试话术：**
> "我使用 Spring AI 的 `@Tool` 注解来定义 Agent 可调用的工具。Skill 就是普通的 Spring Service，封装业务逻辑；Tool 通过 `@Tool` 注解暴露给 LLM。两者是组合关系——`@Tool` 方法内部调用 Service 方法。这样 Skill 既可以直接调用，也可以通过 Agent 的 Function Calling 间接调用。新工具只需要加一个 `@Tool` 注解方法就行，Spring AI 自动处理 JSON Schema 生成和 Function Calling 协议。"

#### 亮点 6：主 Agent 全能对话入口

**技术要点：**
- CoT 思考链引擎（Prompt Engineering）
- Function Calling 动态工具选择
- 流式 SSE 对话
- 工具调用可视化（前端）

**面试话术：**
> "我实现了一个主 Agent 全能对话入口，用户可以通过自然语言完成所有操作。它基于思考链引擎工作：理解意图 → 选择工具 → 执行工具 → 生成回复。比如用户说'帮我看看最近的练习情况'，主 Agent 会调用 DashboardQueryTool 获取数据后再回复。前端会展示工具调用过程，让用户知道 AI 在做什么。"

### 4.3 面试常见问题

**Q: 为什么用多 Agent 而不是单个 Agent？**
> 不同练习模式需要不同的对话策略。场景驱动需要 Agent 扮演"困难搭档"，自由对话需要 Agent 做"共情教练"，结构化四步需要每步有专属"步骤教练"。单个 Agent 很难同时做好这些角色。多 Agent 编排让每个 Agent 专注于一件事，通过 Orchestator 协调，既保证了专业性，又保持了灵活性。

**Q: RAG 在这个场景的价值是什么？LLM 不是已经懂 NVC 了吗？**
> LLM 懂 NVC 理论，但不懂用户的个人知识。RAG 主要检索两类内容：100+ 条话术模板和个人 Wiki。RAG 的价值在于个性化和专业化。

**Q: 工具调用的错误处理怎么做？**
> 每个工具执行都有 try-catch，返回 NvcToolResult 包含成功/失败状态和错误信息。Agent 收到错误后会用自然语言告诉用户。工具调用还有超时控制（默认 10 秒）。

**Q: Skill 和 Tool 的区别是什么？**
> Tool 是 Agent 可以调用的外部能力，面向 LLM 的 Function Calling。Skill 是业务逻辑的封装，面向代码复用。一个 Skill 可以被包装为 Tool，也可以直接在 Service 中调用。

**Q: 向量检索的性能怎么保证？**
> pgvector 支持 IVFFlat 和 HNSW 索引。数据量小的时候用 IVFFlat，数据量大了切 HNSW。检索时先用向量相似度粗筛 Top-20，再用关键词精排 Top-5。

---

## 五、验收标准

```
□ 测试覆盖
  □ 5 个核心 Service 有单元测试
  □ 测试覆盖率 ≥ 60%
  □ 所有测试通过

□ 文档
  □ 架构文档完整
  □ API 文档完善
  □ 开发指南实用

□ CI/CD
  □ GitHub Actions 配置完成
  □ push/PR 自动触发构建和测试
  □ 测试失败时阻止合并
```
