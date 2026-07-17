# NVC 非暴力沟通练习助手 — Claude Code 指令

> 人类掌舵，智能体执行。本文件是 Claude Code 的入口指令；详细规则在 harness 文档体系中。

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

基于 Spring Boot 4.0 + Java 21 + Spring AI 2.0 + React 18 的 NVC 非暴力沟通练习平台。核心功能：
- 三种练习模式：场景驱动、自由对话、结构化四步练习
- 多 Agent 协同调度（6+ 类 NVC 专职 Agent）
- 实时流式评估（四要素：观察/感受/需求/请求）
- 语音练习（WebSocket + ASR/TTS）
- 用户档案系统（能力画像 + 向量化个性化）
- RAG 知识库（NVC 理论文档检索增强）

---

## 🔀 分支开发规则（强制）

**AI 进行任何功能开发、Bug 修复、重构，都必须在独立分支上进行。审核通过后才能合并主分支。**

1. 从 `master` 创建特性分支：`feat/{简述}` / `fix/{简述}` / `refactor/{简述}`
2. 所有代码改动在分支上完成
3. 按 `services/<svc>/BUILD.md` 编译、启动、自测
4. 分支完成后由人类审核 diff
5. 审核通过后合并 `master`，删除特性分支

**禁止直接在 master 上修改业务代码。**

---

## 编码前必读 — harness 文档体系

详细规则不在本文件中，请按需读取：

| 文档 | 位置 | 内容 |
|------|------|------|
| **全局导航**（入口） | `AGENTS.md` | 服务清单、标准操作流程 |
| **三层架构原理** | `LAYERS.md` | L1/L2/L3 分层——harness 的设计尺子 |
| **改动护栏** | `docs/GUARDRAILS.md` | 红区/灰区/安全区——loop 的边界 |
| **完成标准** | `docs/DONE.md` | 收口判据——任一项不满足即未完成 |
| **领域术语** | `docs/GLOSSARY.md` | NVC 核心概念与系统术语 |
| **API 契约** | `docs/contracts/api.md` | 统一响应/错误码/分页 |
| **架构约束** | `docs/architecture/constraints.md` | 技术/业务/安全约束 |
| **后端服务事实层** | `services/nvc-backend/AGENTS.md` | 内部约定、分层、异常、事务 |
| **前端服务事实层** | `services/nvc-frontend/AGENTS.md` | 内部约定、状态管理、API 调用 |
| **决策记录** | `docs/decisions/` | 重大技术选型和架构取舍 |

---

## 速查：禁止清单

| 禁止项 | 原因 |
|--------|------|
| 直接在 master 上改业务代码 | 主分支安全——AI 改动必须在分支上审核 |
| `throw new RuntimeException(...)` | 绕过全局异常处理 |
| 直接返回 Entity 给前端 | 暴露内部结构 |
| `@Value` 散落在 Service 中 | 配置应集中管理 |
| 事务内调用外部 API（LLM、S3） | 占用 DB 连接 |
| 同类内部调用 `@Transactional` | AOP 代理不生效 |
| `catch (Exception e) {}` 静默忽略 | 隐藏错误 |
| 硬编码密钥 | 安全风险 |
| `Executors.newXxxThreadPool()` | OOM 风险 |

---

**新读者路径**：本文件 → `AGENTS.md`（入口）→ `LAYERS.md`（原理）→ 按需下钻各服务事实层。
