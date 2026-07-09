# Step 12：打磨与部署

> 目标：UI 打磨、场景库扩充、Prompt 调优、部署配置、README 编写
>
> 预计耗时：2-3 天
>
> 前置条件：Step 0-11 全部完成
>
> 这是最后一步，确保项目可以展示和部署

---

## 12.1 UI 打磨

### 12.1.1 动画效果

使用 Framer Motion 添加页面过渡和交互动画：

```tsx
// 页面过渡动画
import { motion } from 'framer-motion';

const pageVariants = {
  initial: { opacity: 0, y: 20 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: -20 },
};

const NvcPracticeHubPage: React.FC = () => (
  <motion.div
    variants={pageVariants}
    initial="initial"
    animate="animate"
    exit="exit"
    transition={{ duration: 0.3 }}
  >
    {/* 页面内容 */}
  </motion.div>
);
```

```tsx
// 练习模式卡片悬停动画
<motion.div
  whileHover={{ scale: 1.02, boxShadow: '0 10px 30px rgba(0,0,0,0.1)' }}
  whileTap={{ scale: 0.98 }}
  className="bg-white rounded-xl shadow-md p-6 cursor-pointer"
>
  {/* 卡片内容 */}
</motion.div>
```

```tsx
// 实时评估卡片出现动画
<motion.div
  initial={{ opacity: 0, x: 20 }}
  animate={{ opacity: 1, x: 0 }}
  transition={{ duration: 0.3 }}
>
  <NvcEvaluationCard data={evaluation} />
</motion.div>
```

### 12.1.2 响应式布局

确保所有页面在不同屏幕尺寸下正常显示：

```tsx
// 使用 TailwindCSS 响应式类
<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
  {/* 卡片 */}
</div>

// 移动端导航
<div className="lg:hidden">
  <MobileNav />
</div>
<div className="hidden lg:block">
  <DesktopNav />
</div>
```

### 12.1.3 加载状态

```tsx
// 骨架屏
const LoadingSkeleton: React.FC = () => (
  <div className="animate-pulse space-y-4">
    <div className="h-4 bg-gray-200 rounded w-3/4" />
    <div className="h-4 bg-gray-200 rounded w-1/2" />
    <div className="h-4 bg-gray-200 rounded w-5/6" />
  </div>
);

// 使用
{loading ? <LoadingSkeleton /> : <ActualContent />}
```

### 12.1.4 错误处理

```tsx
// 全局错误提示组件
const ErrorToast: React.FC<{ message: string; onClose: () => void }> = ({ message, onClose }) => (
  <motion.div
    initial={{ opacity: 0, y: -50 }}
    animate={{ opacity: 1, y: 0 }}
    exit={{ opacity: 0, y: -50 }}
    className="fixed top-4 right-4 bg-red-500 text-white px-6 py-3 rounded-lg shadow-lg"
  >
    {message}
    <button onClick={onClose} className="ml-4">×</button>
  </motion.div>
);
```

---

## 12.2 场景库扩充

目标：30+ 场景，覆盖所有类型和难度。

### 补充场景清单

**职场类（补充）**：
- 新人被老员工排挤
- 跨部门协作推诿
- 绩效评估不公
- 被要求加班但没有补偿
- 提出的方案被否决

**家庭类（补充）**：
- 兄弟姐妹之间的遗产分配
- 父母干涉你的育儿方式
- 过年去谁家的争论
- 父母健康问题的沟通

**亲密关系类（补充）**：
- 伴侣沉迷手机/游戏
- 关于要不要孩子的分歧
- 伴侣和前任的联系
- 经济观念不同

**社交类（补充）**：
- 朋友聚会你总被忽略
- 被朋友要求帮忙但你很忙
- 社交场合的尴尬话题

**自我对话类（补充）**：
- 面试失败后的自我对话
- 拖延症的自我觉察
- 身材焦虑的自我对话

将这些场景写入 `data-nvc-scenario.sql` 并重新执行。

---

## 12.3 Prompt 调优

### 调优清单

| Prompt 文件 | 调优重点 |
|------------|---------|
| `nvc-dialogue-guide-system.st` | 测试 5 轮对话，确保 AI 角色一致、引导自然 |
| `nvc-difficult-partner-system.st` | 测试对抗强度，确保不会太极端也不会太温和 |
| `nvc-evaluation-system.st` | 用已知质量的表达测试，确保评分合理 |
| `nvc-step-observe-system.st` | 测试观察/评论区分的教学效果 |
| `nvc-scenario-generate-system.st` | 测试生成场景的质量和多样性 |

### 调优方法

1. **收集测试对话**：每个 Agent 至少测试 5 轮对话
2. **评估评分校准**：用高质量和低质量表达测试评估分数
3. **检查一致性**：AI 角色是否在对话中保持一致
4. **检查引导效果**：用户是否能从 AI 的引导中学习

---

## 12.4 Docker Compose 部署配置

文件：`docker-compose.yml`

```yaml
version: '3.8'

services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: nvc-guide-postgres
    environment:
      POSTGRES_DB: nvc_guide
      POSTGRES_USER: nvc
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U nvc"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: nvc-guide-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: nvc-guide-app
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/nvc_guide
      SPRING_DATASOURCE_USERNAME: nvc
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      APP_AI_DEFAULT_PROVIDER: dashscope
      APP_AI_PROVIDERS_DASHSCOPE_API_KEY: ${DASHSCOPE_API_KEY}
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: nvc-guide-frontend
    ports:
      - "80:80"
    depends_on:
      - app

volumes:
  postgres_data:
  redis_data:
```

文件：`.env`

```env
DB_PASSWORD=your_secure_password
DASHSCOPE_API_KEY=your_dashscope_api_key
```

---

## 12.5 README 编写

文件：`README.md`

```markdown
# NVC 非暴力沟通练习助手

基于 AI 多 Agent 协同的非暴力沟通练习平台，帮助用户在安全的 AI 对话环境中练习 NVC 四要素（观察、感受、需求、请求）。

## ✨ 核心功能

- **三种练习模式**：场景驱动、自由对话、结构化四步练习
- **多 Agent 协同**：6 类 NVC 专职 Agent 自动调度
- **实时评估**：每轮对话后实时评估 NVC 表达质量
- **语音练习**：WebSocket 实时语音对话练习
- **个人档案**：NVC 能力画像、沟通背景、真实记录分析
- **数据可视化**：能力雷达图、趋势分析、薄弱环节识别
- **RAG 知识库**：NVC 理论文档检索增强

## 🛠️ 技术栈

### 后端
- Java 21 + Spring Boot 4.0 + Spring AI 2.0
- PostgreSQL + pgvector（向量搜索）
- Redis + Redisson（缓存 + 异步任务）
- DashScope Qwen3（ASR/TTS/LLM）

### 前端
- React 18 + TypeScript + Vite
- TailwindCSS 4 + Recharts + Framer Motion

## 🚀 快速开始

### 环境要求
- Java 21+
- Node.js 18+
- PostgreSQL 16+（with pgvector extension）
- Redis 6+

### 本地开发

1. 克隆项目
```bash
git clone https://github.com/your-username/nvc-guide.git
cd nvc-guide
```

2. 配置环境变量
```bash
cp .env.example .env
# 编辑 .env 填写数据库密码和 API Key
```

3. 启动后端
```bash
./gradlew bootRun
```

4. 启动前端
```bash
cd frontend
pnpm install
pnpm dev
```

5. 访问 http://localhost:5173

### Docker 部署

```bash
docker-compose up -d
```

## 📁 项目结构

```
nvc-guide/
├── app/                          # 后端
│   └── src/main/java/interview/guide/
│       ├── common/               # 通用基础能力
│       ├── infrastructure/       # 技术基础设施
│       └── modules/              # 业务模块
│           ├── nvcpractice/      # NVC 文字练习
│           ├── nvcvoice/         # NVC 语音练习
│           ├── nvcprofile/       # 用户档案
│           ├── nvcscenario/      # 场景库
│           ├── knowledgebase/    # 知识库（RAG）
│           └── llmprovider/      # LLM Provider 管理
├── frontend/                     # 前端
│   └── src/
│       ├── pages/                # 页面
│       ├── components/           # 组件
│       ├── api/                  # API 封装
│       └── types/                # TypeScript 类型
└── docker-compose.yml            # 部署配置
```

## 📝 License

MIT
```

---

## 12.6 最终检查清单

### 功能完整性

```
□ 三种练习模式全部可用
  □ 场景驱动：选择场景 → 多轮对话 → 评估报告
  □ 自由对话：描述困境 → AI 引导 → NVC 改写
  □ 结构化四步：观察 → 感受 → 需求 → 请求 → 综合
□ 语音练习可用
  □ WebSocket 连接 → 录音 → ASR → LLM → TTS → 播放
□ Agent 调度正常
  □ 对话引导官 / 困难搭档 / 步骤教练 自动切换
  □ Agent 配置热更新生效
□ 评估引擎正常
  □ 实时评估：每轮对话后评估
  □ 最终评估：练习结束后综合评估
  □ 四维度评分合理
□ 用户档案正常
  □ 能力分数自动更新
  □ NVC 等级自动计算
  □ 沟通记录分析可用
□ 场景库正常
  □ 30+ 预置场景
  □ AI 生成场景可用
□ RAG 知识库正常
  □ NVC 文档已上传并向量化
  □ 对话中能引用知识库内容
□ 数据可视化正常
  □ 雷达图渲染正确
  □ 趋势图渲染正确
  □ 统计面板数据正确
□ PDF 导出可用
  □ 练习报告 PDF 下载正常
```

### 代码质量

```
□ 无编译警告
□ 无 TypeScript 类型错误
□ 关键方法有日志记录
□ 异常处理完善（BusinessException + 全局异常处理）
□ 无硬编码的密钥或敏感信息
```

### 部署就绪

```
□ Docker Compose 能正常启动
□ .env 文件模板已提供
□ README 完整清晰
□ 项目能从零开始 clone 并运行
```

---

## 12.7 演示脚本

准备一个 5 分钟的演示流程：

```
1. 打开练习中心页面（展示三种模式入口 + 能力概览）

2. 选择「场景驱动练习」→ 选择「同事抢功」场景
   → 展示 AI 生成场景描述
   → 用户用 NVC 方式表达（1-2 轮）
   → 展示实时评估卡片（四维度评分）
   → 展示 Agent 自动切换（引导官 → 困难搭档）

3. 结束练习 → 展示练习报告
   → 四维度详细分析
   → 改进建议
   → PDF 导出

4. 切换到数据仪表盘
   → 展示雷达图
   → 展示趋势图
   → 展示薄弱环节分析

5. 切换到个人档案
   → 展示能力画像
   → 展示沟通记录分析

6. 切换到 Agent 配置
   → 展示配置热更新
   → 修改提示词 → 立即生效

7. 切换到语音练习
   → 展示实时语音对话
   → 展示实时字幕
```

---

## 12.8 简历项目描述

最终版（可直接用于简历）：

> **NVC 非暴力沟通练习助手** | Spring Boot 4 + Java 21 + Spring AI 2.0 + React 18 + PostgreSQL + pgvector
>
> 基于 AI 多 Agent 协同的非暴力沟通练习平台。设计 6 类 NVC 专职 Agent 协同调度体系，引入 Plan-Execute-Reflect 思维链实现 Agent 自主策略调整；构建用户档案向量化个性化系统，基于 pgvector + RAG 多路检索实现个性化知识推荐；实现结构化四步练习有限状态机 + 实时流式评估引擎；支持文字/语音双模态练习，评估结果实时反馈 + PDF 报告导出。

---

## 12.9 Git 提交

```
□ Git 提交："Step 12: Polish UI, expand scenarios, add deployment config"
□ 创建 release tag：v1.0.0
```
