<div align="center">

# NVC 非暴力沟通练习助手

**基于 AI 的智能沟通技能练习平台**

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0-blue?logo=spring)](https://spring.io/projects/spring-ai)
[![React](https://img.shields.io/badge/React-18-blue?logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.6-blue?logo=typescript)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?logo=postgresql)](https://www.postgresql.org/)
[![OpenTelemetry](https://img.shields.io/badge/OpenTelemetry-Trace-425CC7?logo=opentelemetry)](https://opentelemetry.io/)

</div>

---

## 📖 项目介绍

NVC 非暴力沟通练习助手是一个基于大语言模型的智能沟通练习平台，帮助用户通过结构化练习掌握非暴力沟通（Nonviolent Communication）的四要素：**观察、感受、需求、请求**。

系统利用 Spring AI、多 Agent 协同调度、实时流式评估和语音识别技术，为用户提供文字练习、语音练习、场景库、用户档案和数据可视化等完整的 NVC 学习体验。

---

## ✨ 核心功能

### 🎯 三种练习模式

| 模式 | 说明 |
|------|------|
| **场景驱动模式** | 基于预设场景进行 NVC 对话练习，AI 扮演困难搭档 |
| **自由对话模式** | 开放式 NVC 表达练习，实时评估四要素 |
| **结构化四步练习** | 分步骤学习观察、感受、需求、请求 |

### 🤖 多 Agent 协同调度

系统内置 10+ 种 NVC 专职 Agent，通过智能调度中心自动选择最合适的 Agent 响应用户：

| Agent | 职责 |
|-------|------|
| 场景生成官 | 生成 NVC 练习场景 |
| 对话引导官 | 引导用户进行 NVC 对话 |
| 困难搭档 | 扮演难以沟通的对象 |
| 观察/感受/需求/请求教练 | 分步骤指导 NVC 四要素 |
| NVC 表达评估官 | 评估用户表达的 NVC 质量 |
| 共情教练 | 提供共情支持和反馈 |
| NVC 知识顾问 | 回答 NVC 相关问题 |

### 📊 实时流式评估

- **四要素识别**：实时识别用户表达中的观察、感受、需求、请求
- **结构化输出**：基于 Spring AI 的 StructuredOutputInvoker 实现结构化评估
- **NVC 评分**：多维度评分（共情度、清晰度、完整性等）
- **改进建议**：提供具体的 NVC 表达改进建议

### 🎙️ 语音练习

- **实时语音对话**：WebSocket + 语音模型（ASR/TTS）
- **服务端 VAD**：自动断句，实时字幕
- **回声防护**：避免 AI 语音被误录入
- **多轮上下文**：支持暂停/恢复

### 👤 用户档案系统

- **能力画像**：记录用户的 NVC 能力发展轨迹
- **沟通分析**：分析用户的沟通模式和改进空间
- **个性化推荐**：基于用户能力推荐合适的练习场景

### 📚 知识库与场景库

- **RAG 知识检索**：基于 NVC 理论文档的检索增强生成
- **AI 生成场景**：基于 NVC 理论自动生成练习场景
- **难度分级**：初级、中级、高级场景分类
- **场景标签**：家庭、职场、社交等多维度标签

### 📈 数据可视化仪表盘

- **雷达图**：展示用户在四要素上的能力分布
- **趋势图**：跟踪用户 NVC 能力的发展趋势
- **练习统计**：练习次数、时长、得分等统计

### 🔍 全链路追踪系统

基于 OpenTelemetry 思想的自研 Trace 系统，记录 Agent 对话的完整链路：

| Span 类型 | 说明 | 记录内容 |
|-----------|------|----------|
| `INTENT_ROUTING` | 意图路由 | 识别结果、耗时 |
| `LLM_CALL` | LLM 调用 | Token 消耗、耗时、Fallback 降级 |
| `TOOL_CALL` | 工具调用 | 工具名、参数、结果、Hook 链 |
| `COMPRESSION` | 上下文压缩 | 压缩前后字符数、摘要 |
| `HTTP_REQUEST` | HTTP 请求 | 请求方法、URI、响应状态 |
| `RAG_RETRIEVAL` | RAG 检索 | 查询内容、检索结果 |

**核心特性**：
- 🎯 **智能截断**：重要字段不截断，JSON 格式化后截断
- ⚡ **采样率控制**：生产环境可配置采样率，调试用户总是采样
- 🧹 **自动清理**：定期清理旧 trace 数据，默认保留 30 天
- 🔧 **Hook 链追踪**：记录每个 Hook 的执行顺序、决策、耗时
- 📊 **并行可视化**：自动识别并行工具调用，时间线展示

**前端展示**：
- 工具调用专用卡片（参数表格化、结果摘要、Hook 链时间线）
- 多维度筛选（会话 ID、状态、工具名、Span 类型）
- 并行工具调用可视化

### 📖 NVC Wiki

- **知识百科**：NVC 核心概念、理论框架、实践技巧
- **快速检索**：支持关键词搜索和分类浏览

---

## 🛠️ 技术栈

### 后端技术

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 4.0 | 应用框架 |
| Java | 21 | 开发语言（虚拟线程） |
| Spring AI | 2.0 | AI 集成框架、OpenAI 兼容模型接入 |
| PostgreSQL + pgvector | 14+ | 关系数据库 + 向量存储 |
| Redis + Redisson | 6+ | 缓存 + 消息队列（Stream） |
| MapStruct | 1.6 | 对象映射 |
| DashScope SDK | 2.22 | 语音识别/合成 |
| Gradle | 8.14 | 构建工具 |
| **自研 Trace 系统** | - | 全链路追踪（Trace/Span/采样/清理） |

### 前端技术

| 技术 | 版本 | 说明 |
|------|------|------|
| React | 18 | UI 框架 |
| TypeScript | 5.6 | 开发语言 |
| Vite | 5.4 | 构建工具 |
| Tailwind CSS | 4.1 | 样式框架 |
| React Router | 7.11 | 路由管理 |
| Recharts | 3.6 | 图表库 |
| Lucide React | 0.468 | 图标库 |
| pnpm | 10.26 | 前端包管理器 |

---

## 📁 项目结构

```
nvc-guide/
├── app/                              # 后端应用
│   ├── src/main/java/nvc/guide/
│   │   ├── App.java                  # 主启动类
│   │   ├── common/                   # 通用基础能力
│   │   │   ├── ai/                   # LLM Provider、结构化输出
│   │   │   ├── annotation/           # @RateLimit 限流注解
│   │   │   ├── aspect/               # RateLimitAspect + Redis Lua 限流
│   │   │   ├── async/                # Redis Stream 生产者/消费者模板
│   │   │   ├── config/               # CORS、S3、OpenAPI、Jackson 等配置
│   │   │   ├── exception/            # 业务异常与全局异常处理
│   │   │   ├── result/               # 统一响应 Result<T>
│   │   │   └── trace/                # HTTP 请求追踪拦截器
│   │   ├── infrastructure/           # 基础设施
│   │   │   ├── export/               # PDF 导出
│   │   │   ├── file/                 # 文件解析、存储
│   │   │   ├── mapper/               # MapStruct 映射器
│   │   │   └── redis/                # RedisService
│   │   └── modules/                  # 业务模块（9个）
│   │       ├── nvcpractice/          # NVC 文字练习（核心模块）
│   │       ├── nvcvoice/             # NVC 语音练习
│   │       ├── nvcprofile/           # 用户档案系统
│   │       ├── nvcscenario/          # 场景库管理
│   │       ├── nvcassistant/         # 主 Agent 对话入口
│   │       │   ├── controller/       # Trace API 控制器
│   │       │   ├── service/          # AgentLoop、ToolExecutor
│   │       │   └── trace/            # Trace 系统核心（TraceManager、Span、采样、清理）
│   │       ├── nvcwiki/              # NVC Wiki 知识库
│   │       ├── knowledgebase/        # RAG 知识库
│   │       └── llmprovider/          # LLM Provider 管理
│   └── src/main/resources/
│       ├── application.yml           # 应用配置
│       ├── prompts/                  # AI 提示词模板
│       └── scripts/                  # Redis Lua 脚本
│
├── frontend/                         # 前端应用（15个页面）
│   ├── src/
│   │   ├── api/                      # API 接口
│   │   ├── components/               # 公共组件
│   │   │   └── nvc/                  # NVC 业务组件
│   │   │       ├── TraceTimeline.tsx     # Trace 时间线（并行可视化）
│   │   │       ├── TraceSpanCard.tsx     # Span 卡片
│   │   │       ├── ToolCallSpanCard.tsx  # 工具调用专用卡片（Hook 链）
│   │   │       ├── TraceFilterBar.tsx    # 多维度筛选
│   │   │       └── TraceSummaryBar.tsx   # 汇总统计
│   │   ├── hooks/                    # 业务 Hooks
│   │   ├── pages/                    # 页面组件
│   │   │   ├── TraceListPage.tsx     # Trace 列表页
│   │   │   └── TraceDetailPage.tsx   # Trace 详情页
│   │   │   ├── NvcPracticePage.tsx   # 练习页面
│   │   │   ├── NvcVoicePage.tsx      # 语音练习
│   │   │   ├── NvcAssistantPage.tsx  # AI 助手
│   │   │   ├── NvcDashboardPage.tsx  # 数据仪表盘
│   │   │   ├── NvcProfilePage.tsx    # 用户档案
│   │   │   ├── NvcWikiPage.tsx       # NVC Wiki
│   │   │   └── ...                   # 其他页面
│   │   ├── types/                    # 类型定义
│   │   └── utils/                    # 工具函数
│   ├── package.json
│   └── vite.config.ts
│
├── docker-compose.yml                # 完整部署：前端 + 后端 + PostgreSQL + Redis + MinIO
├── docker-compose.dev.yml            # 本地开发依赖：PostgreSQL + Redis + RustFS
├── docs/                             # 设计文档与决策记录
├── .env.example                      # 环境变量示例
└── README.md
```

---

## 🚀 快速开始

### 环境要求

| 依赖 | 版本 | 必需 | 说明 |
|------|------|------|------|
| JDK | 21+ | 是 | 开发语言 |
| Node.js | 18+ | 是 | 前端构建 |
| pnpm | 10+ | 推荐 | 前端包管理器 |
| Docker | - | 推荐 | 一键启动依赖服务 |

### 1. 克隆项目

```bash
git clone https://github.com/your-username/nvc-guide.git
cd nvc-guide
```

### 2. 配置环境变量

```bash
cp .env.example .env

# 编辑 .env，填写 AI 配置
# AI_BAILIAN_API_KEY=your_dashscope_api_key
# AI_MODEL=qwen3.5-flash
```

### 3. 启动依赖服务

```bash
# 启动 PostgreSQL + Redis + RustFS
docker compose -f docker-compose.dev.yml up -d
```

### 4. 启动应用

**后端：**

```bash
./gradlew :app:bootRun
```

后端服务启动于 `http://localhost:8080`

**前端：**

```bash
cd frontend
corepack enable
pnpm install
pnpm dev
```

前端服务启动于 `http://localhost:5173`

---

## 🐳 Docker 快速部署

```bash
# 1. 复制环境变量配置文件
cp .env.example .env

# 2. 编辑 .env 文件，填入 AI 配置
# AI_BAILIAN_API_KEY=your_key_here

# 3. 构建并启动所有服务
docker-compose up -d --build
```

启动完成后访问：
- **前端应用**：http://localhost
- **后端 API**：http://localhost:8080
- **接口文档**：http://localhost:8080/swagger-ui.html

---

## 📊 项目规模

| 指标 | 数量 |
|------|------|
| 后端模块 | 9 个 |
| Java 文件 | 230+ 个 |
| TypeScript 文件 | 60+ 个 |
| 前端页面 | 17 个 |
| AI Agent | 10+ 种 |
| Trace Span 类型 | 7 种 |
| 测试用例 | 80+ 个 |

---

## 📚 功能特性详解

### NVC 四要素练习

系统基于马歇尔·卢森堡的非暴力沟通理论，将沟通分为四个核心要素：

1. **观察（Observation）**：客观描述事实，不带评判
2. **感受（Feeling）**：表达真实情感，不指责他人
3. **需求（Need）**：识别内在需求，不依赖他人
4. **请求（Request）**：提出具体可行的请求

### Agent 调度流程

```
用户消息 → NvcAgentOrchestrator.decideNextAgent()
         → NvcAgentChatService.chat(config, context, message)
         → 保存消息 → 实时评估 → 返回结果
```

### 评估维度

- **共情度**：是否理解对方感受和需求
- **清晰度**：表达是否清晰具体
- **完整性**：是否包含四要素
- **NVC 质量**：整体 NVC 表达水平

---

## 🗺️ 开发路线

### ✅ 已完成（Phase 1-5）

- [x] 项目初始化与基础架构搭建
- [x] 数据库实体与 Repository 创建
- [x] Agent 配置体系与调度中心
- [x] NVC 文字练习核心流程
- [x] NVC 评估引擎
- [x] 用户档案系统
- [x] 场景库管理
- [x] 结构化四步练习模式
- [x] 语音练习模块
- [x] 前端页面开发
- [x] RAG 知识库与个性化检索
- [x] 数据可视化仪表盘
- [x] NVC 主 Agent 对话入口
- [x] NVC Wiki 知识库
- [x] **全链路追踪系统**（Trace/Span/采样/清理/前端可视化）

### 🚧 进行中

- [ ] UI 细节打磨与交互优化
- [ ] 场景库扩充（更多真实场景）
- [ ] Prompt 持续调优
- [ ] Trace 系统集成 EVALUATION/FALLBACK/RAG/语音埋点

### 📋 计划中

- [ ] 用户认证与多用户支持
- [ ] 练习社区与分享功能
- [ ] 移动端适配
- [ ] 更多语言支持

---

## ❓ 常见问题

### Q: AI 模型配置

系统默认使用阿里云 DashScope 的 qwen3.5-flash 模型。如需使用其他模型：

1. 在设置页配置新的 LLM Provider
2. 设置默认聊天模型和向量模型
3. 重启后端服务或调用 `/api/llm-provider/reload`

### Q: 语音功能无法使用

语音功能需要 DashScope API Key 支持 ASR/TTS。请检查：

1. `.env` 中的 `AI_BAILIAN_API_KEY` 是否正确
2. 浏览器麦克风权限是否已授权
3. 设置页的 ASR/TTS 测试是否通过

### Q: 数据库表创建失败

确保 PostgreSQL 已安装 pgvector 扩展：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

开发环境建议设置 `spring.jpa.hibernate.ddl-auto: update`

### Q: Trace 链路看不到工具调用

1. 检查 `application.yml` 中的 trace 配置是否正确
2. 确认 `nvc.trace.spans.TOOL_CALL.level` 设置为 `DETAILED` 或 `FULL`
3. 查看后端日志是否有 `[Trace]` 相关的 WARN/ERROR 信息
4. 检查数据库 `agent_trace` 和 `agent_span` 表是否有数据

### Q: 评估评分异常

评估系统使用 0-100 分制。如果看到评分验证错误：

1. 检查 `NvcEvaluationEntity` 的验证注解是否为 `@Max(100)`
2. 确认评估 Prompt 中指定的分数范围是 0-100
3. 查看后端日志是否有 JSON 解析错误（换行符问题已修复）

---

## 🛠️ 开发指南

### 添加新的 NVC Agent

1. 在 `NvcAgentScene` 枚举中添加新场景
2. 创建对应的 Prompt 模板（`resources/prompts/nvc-*.st`）
3. 在 `NvcAgentConfigRepository` 中添加配置
4. 在 `NvcAgentOrchestrator` 中添加调度逻辑

### 添加新的练习场景

1. 在 `NvcScenarioEntity` 中定义场景结构
2. 通过场景库管理页面添加场景
3. 或使用 AI 生成功能自动创建场景

### 代码规范

- **分层架构**：Controller → Service → Repository
- **命名规范**：Entity（`XxxEntity`）、DTO（`XxxDTO`）、Request（`XxxRequest`）、Response（`XxxResponse`）
- **异常处理**：统一使用 `BusinessException(ErrorCode.XXX, message)`
- **配置管理**：敏感信息放 `.env`，业务配置用 `@ConfigurationProperties`

### Trace 系统配置

在 `application.yml` 中配置 Trace 系统：

```yaml
nvc:
  trace:
    # 默认级别：BASIC / DETAILED / FULL
    default-level: BASIC

    # 按 Span 类型配置
    spans:
      TOOL_CALL:
        level: DETAILED              # 工具调用详细记录
        hook-detail-enabled: true    # 记录 Hook 链
        payload-max-length: 4096     # payload 最大长度
      LLM_CALL:
        level: BASIC                 # LLM 调用基础记录
      EVALUATION:
        level: FULL                  # 评估完整记录

    # 采样率配置（生产环境）
    sampling:
      enabled: false                 # 是否启用采样
      rate: 1.0                      # 采样率（0.0-1.0）

    # 自动清理配置
    cleanup:
      enabled: true                  # 是否启用自动清理
      retention-days: 30             # 保留天数
      batch-size: 1000               # 批量删除大小
      cron: "0 0 3 * * ?"            # cron 表达式（每天凌晨 3 点）

    # 运行时动态配置
    runtime:
      enabled: true
      debug-users: [1001, 1002]      # 调试用户（总是采样）
      debug-sessions: []             # 调试会话（总是采样）
```

### 添加新的 Trace Span 类型

1. 在 `TraceSpanCard.tsx` 的 `SPAN_TYPE_LABELS` 中添加标签
2. 在需要追踪的代码中调用 `traceManager.startSpan("NEW_TYPE", "Component")`
3. 设置 `inputPayload`、`outputPayload`、`metadata` 等字段
4. 调用 `traceManager.endSpan(span, status, failureReason)` 完成 Span

---

## 📄 许可证

AGPL-3.0 License

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

## 🙏 相关资源

- [非暴力沟通 - 马歇尔·卢森堡](https://www.nonviolentcommunication.com/)
- [NVC 中文网](https://www.nvc-cn.com/)
- [Spring AI 文档](https://docs.spring.io/spring-ai/reference/)
- [Spring Boot 文档](https://spring.io/projects/spring-boot)

---

<div align="center">

**开始你的 NVC 练习之旅吧！** 🌟

</div>