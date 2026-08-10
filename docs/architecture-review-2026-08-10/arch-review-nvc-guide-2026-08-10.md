# Architecture Review: nvc-guide

**Date:** 2026-08-10
**Reviewed by:** arch-review (Claude Code)
**Project path:** D:\code\agent\nvc-guide
**Language / Framework:** Java 21 / Spring Boot 4.0 + TypeScript / React 18

---

## Executive Summary

NVC Guide 是一个功能丰富的 AI 驱动 NVC 练习平台，架构清晰、文档出色，但工程基础存在明显短板。后端模块化良好但存在 2 个 god-file 和跨模块耦合问题；测试覆盖率极低（后端 16%、前端 7%），且完全没有 CI/CD 流水线。错误处理和可观测性是最大亮点（零空 catch 块、42+ 错误码、自研 Trace 系统）。性能方面，@Transactional 内调外部 API 和多个无界查询是生产隐患。总体而言，项目功能完成度高但工程质量需要一个集中修复周期。

**Overall Health: 5.1/10**

| Dimension | Score | Signal |
|-----------|-------|--------|
| Code Organization | 6/10 | 结构清晰但 2 个 god-file + 跨模块耦合 |
| Dependency Health | 5/10 | 双锁文件 + Spring AI 预发布版 + 4 个未使用依赖 |
| Test Coverage | 3/10 | 后端 16%、前端 7%、无 CI/CD、无 E2E |
| Error Handling & Observability | 7/10 | 全局异常处理完善、自研 Trace/Metrics 系统 |
| Performance & Scalability | 5/10 | @Transactional 内调 API + 多个无界查询 |
| Developer Experience | 5/10 | 文档优秀但无 CI/CD、无代码风格强制 |

---

## Project Fingerprint

| Property | Value |
|----------|-------|
| Framework | Spring Boot 4.0 + React 18 |
| Language | Java 21 + TypeScript 5.6 |
| Source files | ~463 |
| Last commit | 2026-08-09 |
| Contributors | 1 |
| Tests present | partial (52 backend, 5 frontend) |
| CI/CD | no |
| Monitoring | custom Trace/Metrics (no external) |
| TypeScript | strict |
| Lock file | dual (pnpm + npm — problem) |

---

## Current Architecture Map

**Organizational pattern:** 混合式（feature-based 模块 + layer-based 内部分层） — 模块边界清晰但 common 层反向依赖模块

**Data flow:**
```
Frontend → HTTP/SSE → Controller → Service → Repository → PostgreSQL
                         ↓
                    AgentLoop → LLM (DashScope) → ToolExecutor → Hook Chain
                         ↓
                    Redis Stream → Async Consumers (Metrics/Eval/Wiki/Trace)
```

**External dependencies:**

| Service | Purpose | How integrated |
|---------|---------|----------------|
| PostgreSQL + pgvector | 业务数据 + 向量搜索 | Spring Data JPA + Spring AI VectorStore |
| Redis 7 | 缓存 + 异步队列 + 限流 | Redisson + Redis Stream + Lua |
| MinIO (S3) | 文件存储 | AWS S3 SDK |
| DashScope (Qwen3) | LLM / Embedding / ASR / TTS | Spring AI OpenAI-compatible + DashScope SDK |

---

## Findings

### Critical (3 items)

#### CRITICAL — 测试覆盖率极低，无 CI/CD 兜底

**Problem:** 后端 52 个测试文件覆盖 332 个源文件（16%），前端 5 个测试覆盖 70 个源文件（7%）。80% 的 Controller 无测试。Testcontainers 基础设施已搭建但仅 1 个测试使用。无 CI/CD 流水线，测试不会在 PR 时自动运行。
**Evidence:** `app/src/test/` — 52 test files; `frontend/src/test/` — 5 test files; `.github/workflows/` — 目录不存在
**Impact:** 回归 bug 静默引入，重构无安全网，代码质量依赖人工审查
**Fix:** 1) 为核心 Controller 和 Service 补充集成测试（优先 NvcAssistantController、KnowledgeBaseController）；2) 添加 GitHub Actions CI（build + test + lint）；3) 充分利用已有的 Testcontainers 基础设施
**Effort:** 2-3 周

#### CRITICAL — SettingsPage.tsx 和 LlmProviderConfigService.java 是 god-file

**Problem:** SettingsPage.tsx（1500 行）处理 LLM Provider CRUD、预设、ASR/TTS 配置、表单状态、模态框、Toast。LlmProviderConfigService.java（1217 行）混合 Provider CRUD、YAML 文件编辑、.env 文件操作、ASR/TTS 配置管理。
**Evidence:** `frontend/src/pages/SettingsPage.tsx` — 1500 lines; `app/.../LlmProviderConfigService.java` — 1217 lines (YamlTextEditor inner class at line 1058)
**Impact:** 修改任一功能都需要理解整个文件，测试困难，合并冲突概率高
**Fix:** SettingsPage 拆分为 ProviderList、ProviderForm、AsrTtsConfig 等子组件。LlmProviderConfigService 提取 ConfigFilePersistenceService（YAML/.env 操作）和 AsrTtsConfigService
**Effort:** 1 周

#### CRITICAL — @Transactional 内调 DashScope Embedding API

**Problem:** KnowledgeBaseVectorService.vectorizeAndStore() 在 @Transactional 内调用 DashScope Embedding API。事务持有 DB 连接贯穿多次外部 API 调用，可能持续数分钟，耗尽连接池。
**Evidence:** `app/.../KnowledgeBaseVectorService.java:45-79` — @Transactional 包裹 vectorStore.add()
**Impact:** 高并发时连接池耗尽，服务不可用；DashScope 超时导致数据不一致（旧向量已删、新向量未写入）
**Fix:** 拆分为两步：事务内只做 DB 操作（删除旧向量），事务外调 DashScope + 写入新向量。失败时标记为 REBUILDING 状态
**Effort:** 2-3 天

---

### High Impact (5 items)

#### HIGH — 跨模块耦合：common 层反向依赖 modules

**Problem:** common/ai/LlmProviderRegistry.java 导入 modules/llmprovider 的实体和仓库。common/trace/HttpTraceInterceptor.java 导入 modules/nvcassistant。Common 层不应依赖模块实现。
**Evidence:** `common/ai/LlmProviderRegistry.java` imports `modules/llmprovider/model/LlmProviderEntity`; `common/trace/HttpTraceInterceptor.java` imports `modules/nvcassistant`
**Impact:** 模块无法独立测试或复用，common 层变更影响所有模块
**Fix:** 在 common 层定义接口（如 LlmProviderResolver），模块层实现。使用 Spring @Qualifier 注入
**Effort:** 1 周

#### HIGH — 多个无界查询，数据增长后 OOM 风险

**Problem:** KnowledgeBaseRepository 有 6 个无分页查询（findAllByOrderByUploadedAtDesc 等）。AgentTraceRepository 的时间范围查询返回无界 List。场景库 fallback 查询加载全部数据。
**Evidence:** `KnowledgeBaseRepository.java:34-66` — 6 个 List 返回方法无分页; `AgentTraceRepository.java:22-32` — findByCreatedAtBetween 返回 List
**Impact:** 数据量增长后内存溢出，响应时间线性增长
**Fix:** 所有列表查询改为返回 Page<>，使用 Spring Data 分页。已有 PageResult 工具类可直接使用
**Effort:** 3-5 天

#### HIGH — 前端双锁文件导致构建不可复现

**Problem:** frontend/ 同时存在 pnpm-lock.yaml 和 package-lock.json。package.json 声明 packageManager 为 pnpm，但 npm lock 文件更近期被修改。
**Evidence:** `frontend/pnpm-lock.yaml` (Aug 5) vs `frontend/package-lock.json` (Aug 9)
**Impact:** 不同开发者使用不同包管理器，依赖版本不一致
**Fix:** 删除 package-lock.json，gitignore 防止再生成，CI 中使用 pnpm install --frozen-lockfile
**Effort:** 10 分钟

#### HIGH — Controller 绕过 Service 层直接调用 Repository

**Problem:** TraceController 直接调用 traceRepository 的多个方法，绕过 Service 层。NvcPracticeController 也有同样问题。
**Evidence:** `TraceController.java:51-91` — 直接调用 traceRepository.findBySessionIdOrderByCreatedAtDesc 等; `NvcPracticeController.java:227` — 直接调用 scenarioRepository.findById
**Impact:** 业务逻辑散落在 Controller 中，无法复用和测试
**Fix:** 将 Repository 调用移入 TraceService / NvcScenarioService，Controller 只调 Service
**Effort:** 2-3 天

#### HIGH — 无代码风格强制工具

**Problem:** 后端无 Checkstyle/Spotless/PMD。前端 ESLint 配置存在但 package.json 无 lint 脚本。代码风格完全依赖开发者 IDE 设置。
**Evidence:** `app/build.gradle` — 无代码风格插件; `frontend/package.json` — 无 lint script
**Impact:** 代码风格随贡献者不同而漂移，PR 审查浪费时间在风格讨论上
**Fix:** 后端添加 Spotless（google-java-format）；前端添加 lint + lint:fix 脚本；CI 中强制执行
**Effort:** 半天

---

### Quick Wins (5 items)

#### QUICK WIN — 删除前端未使用依赖

**Problem:** lucide-react、react-window、react-big-calendar、onnxruntime-web 均未被任何源文件导入。
**Evidence:** 0 imports across all source files for each package
**Fix:** `pnpm remove lucide-react react-window react-big-calendar onnxruntime-web`，减少 ~3.3MB node_modules
**Effort:** 10 分钟

#### QUICK WIN — 添加健康检查端点

**Problem:** 无 /health 或 /actuator/health 端点，无法监控服务状态。
**Evidence:** 无 Spring Boot Actuator 依赖
**Fix:** 添加 spring-boot-starter-actuator 依赖，配置 management.endpoints.web.exposure.include=health,info
**Effort:** 30 分钟

#### QUICK WIN — 修复 docker-compose.yml 与 .env.example 的 DB 名称不一致

**Problem:** docker-compose.yml 使用 `nvc_practice`，.env.example 使用 `nvc_guide`。新开发者复制 .env.example 会连接失败。
**Evidence:** `docker-compose.yml:20` vs `.env.example:38`
**Fix:** 统一为 `nvc_practice`（与 docker-compose 一致）
**Effort:** 5 分钟

#### QUICK WIN — 修复 log.error 丢失堆栈

**Problem:** 多处 log.error 只传 e.getMessage() 不传异常对象，丢失完整堆栈。
**Evidence:** `NvcAssistantController.java:126`, `NvcVoiceWebSocketHandler.java:195`, `VoicePipelineCoordinator.java:332`
**Fix:** 改为 `log.error("msg", e)` 格式
**Effort:** 15 分钟

#### QUICK WIN — SSE 端点不泄露原始异常

**Problem:** SSE 错误事件直接发送 e.getMessage()，可能泄露内部路径、SQL 错误等。
**Evidence:** `NvcAssistantController.java:126`, `RagChatController.java:147,155`
**Fix:** SSE 错误事件使用 ErrorCode 映射的用户友好消息，不发送原始异常
**Effort:** 1 小时

---

## Architecture Verdict

**核心优势：** 错误处理和可观测性设计出色 — 全局异常处理器覆盖 42+ 错误码、LLM 降级重试机制完善、自研 Trace/Metrics 系统通过 Redis Stream 异步持久化，零空 catch 块和零 RuntimeException 直接抛出。文档体系（AGENTS.md、GUARDRAILS.md、DONE.md、决策记录）在同规模项目中罕见。

**最大结构弱点：** 测试覆盖率和 CI/CD 的缺失。16% 的后端测试覆盖率意味着 84% 的代码变更没有自动安全网，任何重构都可能引入静默回归。没有 CI/CD 流水线意味着即使已有的测试也不会在 PR 时自动运行。

**规模适配性：** 当前的模块化架构（7 个业务模块 + common + infrastructure）适合项目的功能规模。但跨模块耦合（common→modules、llmprovider→nvcvoice）和 god-file 问题会随功能增长恶化。

**最有价值的结构性改变：** 建立 CI/CD 流水线 + 补充核心路径的集成测试。这一个改变就能解锁所有其他改进的安全实施 — 没有测试保障的重构只是在积累更多技术债。

**Overall Health: 5.1/10** — 功能完成度高但工程质量基础设施薄弱，技术债正在减缓新功能开发速度。

---

## Recommended Roadmap

### This Week (Quick Wins + Critical fixes)
- [ ] 删除前端 4 个未使用依赖（10 min）
- [ ] 统一 docker-compose.yml 与 .env.example 的 DB 名称（5 min）
- [ ] 修复 log.error 丢失堆栈的 3 处代码（15 min）
- [ ] 添加 spring-boot-starter-actuator 健康检查端点（30 min）
- [ ] 添加 GitHub Actions CI（build + test + lint）（2 小时）

### Next Sprint (High Impact)
- [ ] 拆分 SettingsPage.tsx 和 LlmProviderConfigService.java 两个 god-file
- [ ] 修复 KnowledgeBaseVectorService @Transactional 边界
- [ ] 所有无界查询改为分页查询
- [ ] 删除前端双锁文件，统一 pnpm
- [ ] 后端添加 Spotless 代码格式化 + 前端添加 lint 脚本
- [ ] Controller 层 Repository 调用移入 Service 层

### Next Quarter (Strategic)
- [ ] 核心 Controller/Service 补充集成测试（目标覆盖率 50%）
- [ ] 解耦 common 层对 modules 的反向依赖
- [ ] LLM Provider 故障转移机制
- [ ] 添加 Redis 缓存层（热查询）
- [ ] 前端补充组件测试（目标覆盖率 30%）

---

*Generated by arch-review — Claude Code skill*
