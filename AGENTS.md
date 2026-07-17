# AGENTS.md — 项目全局导航

> 人类掌舵，智能体执行。先文档后开发：必要约束未写入文档前不启动编码。
> **harness/loop ｜ 起点**：每轮任务的第一入口与总地图；从这里按需下钻，细节在此不提。

## 🗺️ 服务
> 本仓库是单体项目（Spring Boot 后端 + React 前端），经 HTTP 集成。每个服务以 `services/<service>/AGENTS.md` 为事实层入口。

- **nvc-backend** — Spring Boot API，承载全部业务规则与 AI Agent 调度
- **nvc-frontend** — React SPA，NVC 练习交互界面

## 📚 全局知识（仅跨服务才共享的内容）
- **架构**（系统边界 / 服务地图 / 部署形状 / 关键约束）：`docs/architecture/`
- **集成契约**（API 响应 / 错误码 / 认证）：`docs/contracts/`
- **领域术语**：`docs/GLOSSARY.md`

## 🚧 编码前必读
- **改动护栏**（哪里能动、哪里碰前要确认）：`docs/GUARDRAILS.md`
- **完成标准**（收口判据）：`docs/DONE.md`
- **决策记录**（为什么这样做）：`docs/decisions/`

## 🔀 分支开发规则
> AI 进行任何功能开发、Bug 修复、重构，都必须在独立分支上进行。审核通过后才能合并主分支。

1. **创建分支**：从 `master` 创建特性分支，命名：`feat/{简述}` / `fix/{简述}` / `refactor/{简述}`
2. **在分支上开发**：所有代码改动在分支上完成
3. **自测验证**：按 `services/<svc>/BUILD.md` 编译、启动、手测
4. **提交审核**：分支完成后由人类审核 diff
5. **合并主分支**：审核通过后合并 `master`，删除特性分支

**禁止直接在 master 上修改业务代码。**

## ⚙️ 标准操作流程
1. 识别任务涉及的服务。
2. 阅读该服务的 `AGENTS.md`，遵循其内部约定。
3. **创建特性分支**，在分支上进行开发。
4. 跨服务需求：先查 `docs/contracts/`。
5. 改前查 `docs/GUARDRAILS.md`，改后按 `BUILD.md` 自测。
6. 完成后按 `docs/DONE.md` 逐条收口。
7. 沉淀回写：新约定→对应 `AGENTS.md`；踩坑→`docs/decisions/`；术语→`GLOSSARY.md`。
