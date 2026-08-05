# NVC 非暴力沟通练习助手 — 分步骤实施索引

> 每个步骤文档都是独立的，换一个 AI 上下文也能直接执行。
> 建议按顺序执行，每完成一步 Git 提交一次。

---

## 当前项目状态

**Phase 2 面试亮点补充已完成**（2026-08-05）。项目核心功能已全部落地，具备面试展示能力。

---

## 已完成的步骤

| Step | 文件 | 内容 | 状态 |
|------|------|------|------|
| 0 | [Step0-项目初始化与清理.md](Step0-项目初始化与清理.md) | 包名重命名、删除面试模块 | ✅ 已完成 |
| 1 | [Step1-数据库实体与Repository创建.md](Step1-数据库实体与Repository创建.md) | 8 Entity + 11 枚举 + 8 Repository + 种子数据 | ✅ 已完成 |
| 5 | [Step5-用户档案系统.md](Step5-用户档案系统.md) | 3 Service + 2 Controller + 7 DTO + Prompt | ✅ 已完成 |
| 6 | [Step6-场景库管理.md](Step6-场景库管理.md) | Service + Controller + 3 DTO + AI 生成 | ✅ 已完成 |
| 7 | [Step7-结构化四步练习模式.md](Step7-结构化四步练习模式.md) | 4 Prompt + Service + 3 API + 自动推进 | ✅ 已完成 |
| 8 | [Step8-语音练习模块.md](Step8-语音练习模块.md) | 35 文件，模块化管线架构，Provider 接口化 | ✅ 已完成 |
| 9 | [Step9-前端页面开发.md](Step9-前端页面开发.md) | 20 新文件 + 3 修改，9 页面 + 7 组件 + 3 基础层 | ✅ 已完成 |

## 已完成的 Phase

| Phase | 内容 | 规模 |
|-------|------|------|
| Phase 0.5 + RAG 基建 | 基建补全 + RAG 前端基建 + 命名修复 | — |
| Phase 1.1 RAG | RAG 知识库集成：15 份文档 + 种子服务 + 全模式 RAG + 个性化检索 | — |
| Phase 1.3 Skill | Skill 标准化：NvcScenarioRecommendService + Service 即 Skill 理念 | — |
| Wiki 模块 | 12 后端新文件 + 5 前端新文件 + 8 修改，复用 knowledge_base 表 | 25 文件 |
| 主 Agent | 13 后端 + 6 前端新文件，流式 SSE + 工具调用 | 19 文件 |
| Agent 深度改进 | 35 文件 +2039 行 | 35 文件 |
| Agent 工具路由 | IntentRouter 集成 + 面试残留清理 | — |
| 质量加固 | 15 后端测试 + 前端基建 + Error Boundary + SSE 抽取 | — |
| Phase 2 面试亮点补充 | P0-1 指标 + P0-2 Trace + P0-3 Fallback | 74 文件 +5844 行 |

---

## 步骤总览（含未完成步骤）

| Step | 文件 | 内容 | 状态 |
|------|------|------|------|
| 0 | [Step0-项目初始化与清理.md](Step0-项目初始化与清理.md) | 复制项目、重命名、删除不需要的模块 | ✅ 已完成 |
| 1 | [Step1-数据库实体与Repository创建.md](Step1-数据库实体与Repository创建.md) | 8个Entity、11个枚举、8个Repository、初始数据 | ✅ 已完成 |
| 2 | [Step2-Agent配置体系与调度中心.md](Step2-Agent配置体系与调度中心.md) | Agent配置热更新、Agent调度中心、思维链 | ⏳ 待规划 |
| 3 | [Step3-NVC文字练习核心流程.md](Step3-NVC文字练习核心流程.md) | 会话管理、多轮对话、SSE流式输出 | ⏳ 待规划 |
| 4 | [Step4-NVC评估引擎.md](Step4-NVC评估引擎.md) | 实时评估、最终评估、练习报告、PDF导出 | ⏳ 待规划 |
| 5 | [Step5-用户档案系统.md](Step5-用户档案系统.md) | 能力画像、沟通记录分析、能力趋势 | ✅ 已完成 |
| 6 | [Step6-场景库管理.md](Step6-场景库管理.md) | 场景CRUD、AI生成场景 | ✅ 已完成 |
| 7 | [Step7-结构化四步练习模式.md](Step7-结构化四步练习模式.md) | 四步状态机、步骤教练Prompt、自动推进 | ✅ 已完成 |
| 8 | [Step8-语音练习模块.md](Step8-语音练习模块.md) | 模块化管线架构，Provider 接口化 | ✅ 已完成 |
| 9 | [Step9-前端页面开发.md](Step9-前端页面开发.md) | 所有前端页面、组件、路由、API封装 | ✅ 已完成 |
| 10 | [Step10-RAG知识库与个性化检索.md](Step10-RAG知识库与个性化检索.md) | NVC知识文档、个性化RAG检索 | ✅ 已完成（Phase 1.1） |
| 11 | [Step11-数据可视化仪表盘.md](Step11-数据可视化仪表盘.md) | 雷达图、趋势图、统计面板 | ⏳ 待规划 |
| 12 | [Step12-打磨与部署.md](Step12-打磨与部署.md) | UI打磨、场景扩充、Prompt调优、部署 | ⏳ 待规划 |

---

## 原始执行顺序（供参考）

```
Phase 1：基础搭建（3-5 天）
  Step 0 → Step 1 → Step 6

Phase 2：核心功能（10-15 天）
  Step 2 → Step 3 → Step 4 → Step 5

Phase 3：扩展功能（5-8 天）
  Step 7 → Step 8 → Step 10

Phase 4：前端开发（5-7 天）
  Step 9 → Step 11

Phase 5：打磨部署（2-3 天）
  Step 12
```

---

## 使用方法

1. 将整个 `steps` 文件夹复制到你的项目目录
2. 每次开始一个新步骤时，将对应的 `.md` 文件喂给 AI
3. 按照文档中的指示执行
4. 每完成一步，Git 提交一次
5. 遇到问题时，回到上一步检查
