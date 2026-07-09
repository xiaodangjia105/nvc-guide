# NVC 非暴力沟通练习助手 — 分步骤实施索引

> 每个步骤文档都是独立的，换一个 AI 上下文也能直接执行。
> 建议按顺序执行，每完成一步 Git 提交一次。

---

## 步骤总览

| Step | 文件 | 内容 | 预计耗时 | 依赖 |
|------|------|------|---------|------|
| 0 | [Step0-项目初始化与清理.md](Step0-项目初始化与清理.md) | 复制项目、重命名、删除不需要的模块 | 1-2 天 | 无 |
| 1 | [Step1-数据库实体与Repository创建.md](Step1-数据库实体与Repository创建.md) | 8个Entity、11个枚举、8个Repository、初始数据 | 2-3 天 | Step 0 |
| 2 | [Step2-Agent配置体系与调度中心.md](Step2-Agent配置体系与调度中心.md) | Agent配置热更新、Agent调度中心、思维链 | 3-5 天 | Step 1 |
| 3 | [Step3-NVC文字练习核心流程.md](Step3-NVC文字练习核心流程.md) | 会话管理、多轮对话、SSE流式输出 | 3-5 天 | Step 2 |
| 4 | [Step4-NVC评估引擎.md](Step4-NVC评估引擎.md) | 实时评估、最终评估、练习报告、PDF导出 | 2-3 天 | Step 3 |
| 5 | [Step5-用户档案系统.md](Step5-用户档案系统.md) | 能力画像、沟通记录分析、能力趋势 | 2-3 天 | Step 4 |
| 6 | [Step6-场景库管理.md](Step6-场景库管理.md) | 场景CRUD、AI生成场景 | 1-2 天 | Step 1 |
| 7 | [Step7-结构化四步练习模式.md](Step7-结构化四步练习模式.md) | 四步状态机、步骤教练Prompt、自动推进 | 2-3 天 | Step 2,3,4 |
| 8 | [Step8-语音练习模块.md](Step8-语音练习模块.md) | 改造voiceinterview为nvcvoice | 3-5 天 | Step 2,4 |
| 9 | [Step9-前端页面开发.md](Step9-前端页面开发.md) | 所有前端页面、组件、路由、API封装 | 5-7 天 | Step 3-8 |
| 10 | [Step10-RAG知识库与个性化检索.md](Step10-RAG知识库与个性化检索.md) | NVC知识文档、个性化RAG检索 | 2-3 天 | Step 5 |
| 11 | [Step11-数据可视化仪表盘.md](Step11-数据可视化仪表盘.md) | 雷达图、趋势图、统计面板 | 1-2 天 | Step 5,9 |
| 12 | [Step12-打磨与部署.md](Step12-打磨与部署.md) | UI打磨、场景扩充、Prompt调优、部署 | 2-3 天 | Step 0-11 |

**总计：约 25-40 天**

---

## 推荐执行顺序

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
