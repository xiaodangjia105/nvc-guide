# Step 9: 前端页面开发 - 完成记录

## 完成时间

2026-07-12

## 设计方案

基于 Step9 设计文档，结合现有代码实际情况做了以下调整：

| 决策点 | 设计文档 | 实际实现 | 原因 |
|--------|----------|----------|------|
| 维度数量 | 4 维 | 5+1 维（含共情） | 后端 DTO 已有 empathyScore |
| AgentConfig 字段 | 基础字段 | 全部字段 + 折叠高级配置 | 后端有 stepAdvanceThreshold 等 |
| UserId 管理 | 未说明 | useUserId Hook + localStorage | 无登录系统 |
| 路由架构 | createBrowserRouter | BrowserRouter + Routes + lazy() | 沿用现有模式 |
| API 封装 | 直接 axios | 使用现有 request 封装 | 统一错误处理 |
| 导航图标 | emoji | lucide-react | 沿用现有风格 |
| 语音页面 | 复用 voiceinterview | 新建 NvcVoicePage | nvcvoice 独立模块 |

## 实现内容

### 新增文件（20个）

| 文件 | 类型 | 职责 |
|------|------|------|
| **基础层** | | |
| types/nvc.ts | 类型 | TypeScript 类型定义（559 行） |
| api/nvc.ts | API | NVC API 封装（practice/report/profile/scenario/agent/voice/dashboard） |
| hooks/useUserId.ts | Hook | userId 管理（localStorage 存储） |
| **页面** | | |
| NvcPracticeHubPage.tsx | 页面 | 练习中心首页（三种模式卡片 + 语音入口 + 能力概览） |
| NvcPracticePage.tsx | 页面 | 文字练习对话页（SSE 流式 + 评估 + 步骤指示器） |
| NvcVoicePage.tsx | 页面 | 语音练习页（WebSocket + 录音/播放/字幕） |
| NvcHistoryPage.tsx | 页面 | 练习历史（筛选 + 卡片列表） |
| NvcReportPage.tsx | 页面 | 练习报告详情（雷达图 + 五维分析 + PDF 下载） |
| NvcProfilePage.tsx | 页面 | 个人档案（编辑表单 + 沟通记录分析） |
| NvcDashboardPage.tsx | 页面 | 数据仪表盘（统计 + 雷达图 + 趋势图 + 薄弱环节） |
| NvcScenarioLibraryPage.tsx | 页面 | 场景库浏览（筛选 + AI 生成） |
| NvcAgentConfigPage.tsx | 页面 | Agent 配置管理（提示词编辑 + 参数调节） |
| **组件** | | |
| NvcChatPanel.tsx | 组件 | 对话面板（SSE 流式 + Markdown + 消息气泡） |
| NvcStepIndicator.tsx | 组件 | 四步进度指示器（观察→感受→需求→请求） |
| NvcEvaluationCard.tsx | 组件 | 实时评估卡片（五维度分数条） |
| NvcRadarChart.tsx | 组件 | 五维雷达图（Recharts RadarChart） |
| NvcAbilityTrendChart.tsx | 组件 | 能力趋势折线图（Recharts LineChart） |
| NvcScenarioCard.tsx | 组件 | 场景卡片（标题/描述/标签） |
| NvcProfileCard.tsx | 组件 | 档案卡片（能力条 + 统计） |

### 修改文件（3个）

| 文件 | 修改内容 |
|------|----------|
| App.tsx | 添加 9 个 NVC 页面的 lazy import 和路由，默认重定向改为 /nvc |
| Layout.tsx | 添加 NVC 练习导航组（6 个菜单项），导入新图标 |
| constants/routes.ts | 添加 NVC 路由常量 |

## 技术亮点

### 1. SSE 流式对话
```typescript
const response = await practiceApi.sendMessageStream(sessionId, text);
const reader = response.body?.getReader();
// 逐字读取并显示
while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  // 解析 data: 前缀的 SSE 数据
}
```

### 2. WebSocket 语音集成
- 复用 AudioRecorder/AudioPlayer/RealtimeSubtitle 组件
- 连接 `/ws/nvc-voice/{sessionId}` 端点
- 支持音频流发送、字幕接收、TTS 播放

### 3. 五维雷达图
- 使用 Recharts RadarChart
- 5 个维度：观察/感受/需求/请求/共情
- 可配置尺寸（sm/md/lg）

### 4. Agent 配置热更新
- 系统提示词编辑器
- 温度/Token/TopP 参数调节
- 步骤教练专属高级配置（折叠区域）
- 保存后立即生效

## 构建验证

- ✅ `npx tsc --noEmit` TypeScript 编译通过
- ✅ `npm run build` 生产构建成功（14.63s）
- ✅ 所有页面懒加载正常
- ✅ 路由配置正确

## Git 提交记录

```
960339d feat(step9): add NVC types, API layer, and useUserId hook
2e11e12 feat(step9): update routing, navigation, and add page placeholders
aa9f9c9 feat(step9): add practice hub page with mode cards and profile overview
9fe0f75 feat(step9): add chat panel, evaluation card, and step indicator components
ec0880d feat(step9): add text practice page with chat and evaluation
d5a2c32 feat(step9): add voice practice page with WebSocket integration
46e00fa feat(step9): add practice history and report pages with radar chart
4e7b267 feat(step9): add dashboard page with radar and trend charts
c58e785 feat(step9): add profile page with editing and communication analysis
dd10320 feat(step9): add scenario library page with filtering and AI generation
da8be28 feat(step9): add agent config page with prompt editing and parameter tuning
```

## 待完善项

1. **SSE 评估解析**：当前从 AI 回复文本中解析 `<evaluation>` 标签，需要后端配合在 SSE 流中插入评估元数据
2. **语音评估展示**：语音练习结束后的评估结果展示页面
3. **PDF 导出样式**：PDF 下载功能依赖后端 PdfExportService
4. **响应式适配**：移动端布局需要进一步优化
5. **错误处理增强**：网络错误、会话过期等边界情况的用户提示
6. **国际化**：当前所有文本为中文硬编码
