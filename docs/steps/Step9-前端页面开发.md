# Step 9：前端页面开发

> 目标：实现 NVC 练习助手的所有前端页面
>
> 预计耗时：5-7 天（前端工作量较大）
>
> 前置条件：Step 3-8 的后端 API 已就绪
>
> 技术亮点：SSE 流式渲染、Recharts 雷达图、Framer Motion 动画

---

## 9.1 本步要创建/修改的文件清单

```
frontend/src/
├── pages/
│   ├── NvcPracticeHubPage.tsx             ← 练习中心（首页）
│   ├── NvcPracticePage.tsx                ← 文字练习对话页
│   ├── NvcVoicePage.tsx                   ← 语音练习页
│   ├── NvcHistoryPage.tsx                 ← 练习历史
│   ├── NvcReportPage.tsx                  ← 练习报告详情
│   ├── NvcProfilePage.tsx                 ← 个人档案
│   ├── NvcDashboardPage.tsx               ← 数据仪表盘
│   ├── NvcScenarioLibraryPage.tsx         ← 场景库浏览
│   └── NvcAgentConfigPage.tsx             ← Agent 配置管理
├── components/
│   ├── nvc/
│   │   ├── NvcChatPanel.tsx               ← NVC 对话面板
│   │   ├── NvcStepIndicator.tsx           ← 四步练习进度指示器
│   │   ├── NvcEvaluationCard.tsx          ← 实时评估卡片
│   │   ├── NvcRadarChart.tsx              ← NVC 四维雷达图
│   │   ├── NvcAbilityTrendChart.tsx       ← 能力趋势折线图
│   │   ├── NvcScenarioCard.tsx            ← 场景卡片
│   │   └── NvcProfileCard.tsx             ← 档案卡片
│   ├── AudioRecorder.tsx                  ← 复用
│   ├── AudioPlayer.tsx                    ← 复用
│   └── RealtimeSubtitle.tsx               ← 复用
├── api/
│   └── nvc.ts                             ← NVC API 封装
├── types/
│   └── nvc.ts                             ← TypeScript 类型定义
├── router.tsx                             ← 修改路由
└── App.tsx                                ← 修改导航
```

---

## 9.2 TypeScript 类型定义

文件：`frontend/src/types/nvc.ts`

```typescript
// ========== 枚举 ==========

export type NvcPracticeMode = 'SCENARIO' | 'FREE_DIALOG' | 'STRUCTURED_FOUR_STEP';
export type NvcSessionPhase = 'CREATED' | 'IN_PROGRESS' | 'PAUSED' | 'COMPLETED' | 'EVALUATED';
export type NvcPracticeStep = 'OBSERVE' | 'FEELING' | 'NEED' | 'REQUEST' | 'COMPLETED';
export type NvcDifficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type NvcScenarioType = 'WORKPLACE' | 'FAMILY' | 'INTIMATE' | 'SOCIAL' | 'SELF';
export type NvcMessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM';
export type NvcAgentScene =
  | 'SCENARIO_GENERATOR' | 'DIALOGUE_GUIDE' | 'DIFFICULT_PARTNER'
  | 'STEP_OBSERVE_COACH' | 'STEP_FEELING_COACH' | 'STEP_NEED_COACH' | 'STEP_REQUEST_COACH'
  | 'NVC_EXPRESSION_EVALUATOR' | 'EMPATHY_COACH' | 'NVC_KNOWLEDGE_ADVISOR';
export type NvcLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';

// ========== 会话 ==========

export interface PracticeSession {
  id: number;
  userId: number;
  practiceMode: NvcPracticeMode;
  currentPhase: NvcSessionPhase;
  currentStep: NvcPracticeStep | null;
  agentScene: string | null;
  difficulty: NvcDifficulty;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

export interface CreatePracticeSessionRequest {
  practiceMode: NvcPracticeMode;
  scenarioId?: number;
  difficulty?: NvcDifficulty;
}

// ========== 消息 ==========

export interface PracticeMessage {
  id: number;
  sessionId: number;
  role: NvcMessageRole;
  agentScene: string | null;
  content: string;
  step: NvcPracticeStep | null;
  sequenceNum: number;
  createdAt: string;
}

export interface DialogueResponse {
  sessionId: number;
  aiReply: string;
  agentScene: string;
  decisionReason: string;
  action: string | null;
  currentStep: string | null;
}

// ========== 评估 ==========

export interface NvcEvaluation {
  id: number;
  sessionId: number;
  observationScore: number;
  feelingScore: number;
  needScore: number;
  requestScore: number;
  overallScore: number;
  observationDetail: string;
  feelingDetail: string;
  needDetail: string;
  requestDetail: string;
  strengths: string;
  improvements: string;
  referenceExpressions: string;
  summary: string;
  evaluationType: 'REALTIME' | 'FINAL';
  createdAt: string;
}

// ========== 报告 ==========

export interface NvcPracticeReport {
  sessionId: number;
  practiceMode: NvcPracticeMode;
  difficulty: NvcDifficulty;
  totalRounds: number;
  startedAt: string;
  completedAt: string;
  observationScore: number;
  feelingScore: number;
  needScore: number;
  requestScore: number;
  overallScore: number;
  observationDetail: string;
  feelingDetail: string;
  needDetail: string;
  requestDetail: string;
  strengths: string;
  improvements: string;
  referenceExpressions: string;
  summary: string;
}

// ========== 用户档案 ==========

export interface AbilityRadar {
  observation: number;
  feeling: number;
  need: number;
  request: number;
  empathy: number;
  overall: number;
  level: NvcLevel;
}

export interface AbilityTrend {
  scoredAt: string;
  observation: number;
  feeling: number;
  need: number;
  request: number;
  empathy: number | null;
  practiceType: string | null;
}

export interface UserProfile {
  userId: number;
  communicationBackground: string | null;
  personalityTraits: string | null;
  communicationStyle: string | null;
  emotionalTriggers: string | null;
  commonScenarios: string | null;
  relationshipTypes: string | null;
  nvcLevel: NvcLevel;
  totalPracticeCount: number;
  totalPracticeMinutes: number;
  lastPracticeAt: string | null;
  abilityRadar: AbilityRadar;
}

// ========== 场景 ==========

export interface NvcScenario {
  id: number;
  title: string;
  description: string;
  scenarioType: NvcScenarioType;
  difficulty: NvcDifficulty;
  focusElements: string | null;
  context: string | null;
  sampleDialogue: string | null;
  tags: string | null;
  isSystem: boolean;
  usageCount: number;
}

// ========== Agent 配置 ==========

export interface AgentConfig {
  agentScene: NvcAgentScene;
  displayName: string;
  description: string | null;
  systemPrompt: string;
  modelProvider: string | null;
  modelName: string | null;
  temperature: number;
  maxTokens: number;
  topP: number;
  isEnabled: boolean;
}

export interface AgentConfigUpdateRequest {
  systemPrompt?: string;
  temperature?: number;
  maxTokens?: number;
  topP?: number;
  isEnabled?: boolean;
}

// ========== 步骤进度 ==========

export interface StepProgress {
  currentStep: NvcPracticeStep;
  stepIndex: number;
  totalSteps: number;
  currentScore: number | null;
  canAdvance: boolean;
  stepDescription: string;
}

// ========== 评估卡片 ==========

export interface EvaluationCardData {
  observation: { score: number; passed: boolean; detail: string };
  feeling: { score: number; passed: boolean; detail: string };
  need: { score: number; passed: boolean; detail: string };
  request: { score: number; passed: boolean; detail: string };
  overall: number;
}
```

---

## 9.3 API 封装

文件：`frontend/src/api/nvc.ts`

```typescript
import axios from 'axios';
import type {
  PracticeSession, CreatePracticeSessionRequest, DialogueResponse,
  PracticeMessage, NvcPracticeReport, UserProfile, UserProfileUpdateRequest,
  AbilityRadar, AbilityTrend, NvcScenario, ScenarioGenerateRequest,
  AgentConfig, AgentConfigUpdateRequest, StepProgress,
  CommunicationAnalysisRequest, CommunicationRecord
} from '@/types/nvc';

const api = axios.create({ baseURL: '/api/nvc' });

// ========== 练习会话 ==========

export const practiceApi = {
  createSession: (userId: number, request: CreatePracticeSessionRequest) =>
    api.post<PracticeSession>(`/practice/sessions?userId=${userId}`, request),

  getSessions: (userId: number, phase?: string) =>
    api.get<PracticeSession[]>(`/practice/sessions`, { params: { userId, phase } }),

  getSession: (sessionId: number) =>
    api.get<PracticeSession>(`/practice/sessions/${sessionId}`),

  sendMessage: (sessionId: number, content: string) =>
    api.post<DialogueResponse>(`/practice/sessions/${sessionId}/messages`, { content }),

  // SSE 流式
  sendMessageStream: (sessionId: number, content: string) =>
    fetch(`/api/nvc/practice/sessions/${sessionId}/messages/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content }),
    }),

  getMessages: (sessionId: number) =>
    api.get<PracticeMessage[]>(`/practice/sessions/${sessionId}/messages`),

  completeSession: (sessionId: number) =>
    api.post<PracticeSession>(`/practice/sessions/${sessionId}/complete`),

  getStepProgress: (sessionId: number) =>
    api.get<StepProgress>(`/practice/sessions/${sessionId}/step-progress`),

  advanceStep: (sessionId: number) =>
    api.post<StepProgress>(`/practice/sessions/${sessionId}/advance-step`),
};

// ========== 报告 ==========

export const reportApi = {
  getReport: (sessionId: number) =>
    api.get<NvcPracticeReport>(`/report/sessions/${sessionId}`),

  downloadPdf: (sessionId: number) =>
    `/api/nvc/report/sessions/${sessionId}/pdf`,
};

// ========== 用户档案 ==========

export const profileApi = {
  getProfile: (userId: number) =>
    api.get<UserProfile>(`/profile`, { params: { userId } }),

  updateProfile: (userId: number, request: UserProfileUpdateRequest) =>
    api.put<UserProfile>(`/profile?userId=${userId}`, request),

  getAbilityRadar: (userId: number) =>
    api.get<AbilityRadar>(`/profile/ability-radar`, { params: { userId } }),

  getAbilityTrends: (userId: number) =>
    api.get<AbilityTrend[]>(`/profile/ability-trends`, { params: { userId } }),

  analyzeCommunication: (userId: number, request: CommunicationAnalysisRequest) =>
    api.post<CommunicationRecord>(`/profile/communication-records/analyze?userId=${userId}`, request),

  getCommunicationRecords: (userId: number) =>
    api.get<CommunicationRecord[]>(`/profile/communication-records`, { params: { userId } }),
};

// ========== 场景 ==========

export const scenarioApi = {
  getScenarios: (scenarioType?: string, difficulty?: string) =>
    api.get<NvcScenario[]>(`/scenarios`, { params: { scenarioType, difficulty } }),

  getScenario: (id: number) =>
    api.get<NvcScenario>(`/scenarios/${id}`),

  generateScenario: (request: ScenarioGenerateRequest) =>
    api.post<NvcScenario>(`/scenarios/generate`, request),
};

// ========== Agent 配置 ==========

export const agentApi = {
  getAllConfigs: () =>
    api.get<AgentConfig[]>(`/agents/configs`),

  getConfig: (scene: string) =>
    api.get<AgentConfig>(`/agents/configs/${scene}`),

  updateConfig: (scene: string, request: AgentConfigUpdateRequest) =>
    api.put<AgentConfig>(`/agents/configs/${scene}`, request),
};
```

---

## 9.4 核心页面设计

### NvcPracticeHubPage（练习中心 / 首页）

这是用户进入应用的第一个页面，展示：
- 三种练习模式入口（大卡片）
- 语音练习入口
- 用户能力概览（雷达图缩略版）
- 快速开始按钮

```
┌─────────────────────────────────────────────────────────┐
│  NVC 非暴力沟通练习助手                    [档案] [仪表盘] │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐      │
│  │ 🎯 场景驱动  │ │ 💬 自由对话  │ │ 📝 结构化    │      │
│  │             │ │             │ │   四步练习   │      │
│  │ AI生成场景   │ │ 描述真实困境  │ │ 观察→感受→  │      │
│  │ 模拟真实对话  │ │ AI帮你用NVC │ │ 需求→请求   │      │
│  │             │ │ 重新组织表达  │ │             │      │
│  │ [开始练习]  │ │ [开始对话]  │ │ [开始练习]  │      │
│  └─────────────┘ └─────────────┘ └─────────────┘      │
│                                                         │
│  ┌─────────────────────────────────────────────┐       │
│  │ 🎤 语音练习  [开始语音练习]                    │       │
│  └─────────────────────────────────────────────┘       │
│                                                         │
│  ┌─────────────────────────────────────────────┐       │
│  │ 我的能力                                      │       │
│  │ 观察 ████░░ 72  感受 ███░░░ 58              │       │
│  │ 需求 ████░░ 65  请求 ██░░░░ 45              │       │
│  │ 已练习 23 次  累计 6.5 小时  [详细报告]       │       │
│  └─────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────┘
```

### NvcPracticePage（文字练习对话页）

核心页面，包含：
- 场景信息展示
- 对话区域（ChatPanel）
- 实时评估卡片
- 步骤进度指示器（结构化模式）

### NvcVoicePage（语音练习页）

复用 voiceinterview 的 UI，改动：
- 标题改为 NVC 练习
- 场景信息展示
- 评估结果展示

### NvcDashboardPage（数据仪表盘）

- NVC 能力雷达图（Recharts RadarChart）
- 能力趋势折线图（Recharts LineChart）
- 练习统计面板
- 薄弱环节分析

### NvcProfilePage（个人档案）

- 档案编辑表单
- 沟通记录上传和分析
- 历史记录列表

### NvcAgentConfigPage（Agent 配置管理）

- Agent 列表（卡片形式）
- 每个 Agent 可展开编辑
- 系统提示词编辑器
- 温度/模型参数调节
- 启用/禁用开关

---

## 9.5 路由配置

文件：`frontend/src/router.tsx`

```tsx
import { createBrowserRouter, Navigate } from 'react-router-dom';
import Layout from '@/components/Layout';
import NvcPracticeHubPage from '@/pages/NvcPracticeHubPage';
import NvcPracticePage from '@/pages/NvcPracticePage';
import NvcVoicePage from '@/pages/NvcVoicePage';
import NvcHistoryPage from '@/pages/NvcHistoryPage';
import NvcReportPage from '@/pages/NvcReportPage';
import NvcProfilePage from '@/pages/NvcProfilePage';
import NvcDashboardPage from '@/pages/NvcDashboardPage';
import NvcScenarioLibraryPage from '@/pages/NvcScenarioLibraryPage';
import NvcAgentConfigPage from '@/pages/NvcAgentConfigPage';
import KnowledgeBaseManagePage from '@/pages/KnowledgeBaseManagePage';
import KnowledgeBaseQueryPage from '@/pages/KnowledgeBaseQueryPage';
import SettingsPage from '@/pages/SettingsPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <Navigate to="/nvc" replace /> },

      // NVC 练习
      { path: 'nvc', element: <NvcPracticeHubPage /> },
      { path: 'nvc/practice/:sessionId', element: <NvcPracticePage /> },
      { path: 'nvc/voice/:sessionId', element: <NvcVoicePage /> },
      { path: 'nvc/history', element: <NvcHistoryPage /> },
      { path: 'nvc/history/:sessionId/report', element: <NvcReportPage /> },

      // 个人中心
      { path: 'nvc/profile', element: <NvcProfilePage /> },
      { path: 'nvc/dashboard', element: <NvcDashboardPage /> },

      // 场景库
      { path: 'nvc/scenarios', element: <NvcScenarioLibraryPage /> },

      // Agent 配置（管理功能）
      { path: 'nvc/agents', element: <NvcAgentConfigPage /> },

      // 知识库（复用）
      { path: 'knowledge-bases', element: <KnowledgeBaseManagePage /> },
      { path: 'knowledge-bases/:id/query', element: <KnowledgeBaseQueryPage /> },

      // 设置（复用）
      { path: 'settings', element: <SettingsPage /> },
    ],
  },
]);
```

---

## 9.6 NvcChatPanel 组件设计

这是最核心的组件，支持：
- 消息气泡（用户/AI 左右分布）
- Markdown 渲染（AI 回复）
- SSE 流式逐字显示
- 实时评估卡片展示

```tsx
// 核心结构
const NvcChatPanel: React.FC<Props> = ({ sessionId, practiceMode }) => {
  const [messages, setMessages] = useState<PracticeMessage[]>([]);
  const [input, setInput] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [evaluation, setEvaluation] = useState<EvaluationCardData | null>(null);

  const sendMessage = async () => {
    // SSE 流式发送
    const response = await practiceApi.sendMessageStream(sessionId, input);
    const reader = response.body?.getReader();
    // ... 逐字读取并显示
  };

  return (
    <div className="flex flex-col h-full">
      {/* 消息列表 */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {messages.map(msg => (
          <MessageBubble key={msg.id} message={msg} />
        ))}
      </div>

      {/* 实时评估卡片 */}
      {evaluation && <NvcEvaluationCard data={evaluation} />}

      {/* 输入区域 */}
      <div className="border-t p-4">
        <div className="flex gap-2">
          <input
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && sendMessage()}
            placeholder="输入你的 NVC 表达..."
            className="flex-1 border rounded-lg px-4 py-2"
          />
          <button onClick={sendMessage} className="bg-blue-500 text-white px-6 py-2 rounded-lg">
            发送
          </button>
        </div>
      </div>
    </div>
  );
};
```

---

## 9.7 NvcRadarChart 组件设计

使用 Recharts 的 RadarChart：

```tsx
import { Radar, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis } from 'recharts';

const NvcRadarChart: React.FC<{ data: AbilityRadar }> = ({ data }) => {
  const chartData = [
    { dimension: '观察', value: data.observation, fullMark: 100 },
    { dimension: '感受', value: data.feeling, fullMark: 100 },
    { dimension: '需求', value: data.need, fullMark: 100 },
    { dimension: '请求', value: data.request, fullMark: 100 },
    { dimension: '共情', value: data.empathy, fullMark: 100 },
  ];

  return (
    <RadarChart cx="50%" cy="50%" outerRadius="70%" data={chartData}>
      <PolarGrid />
      <PolarAngleAxis dataKey="dimension" />
      <PolarRadiusAxis angle={90} domain={[0, 100]} />
      <Radar name="NVC能力" dataKey="value" stroke="#3b82f6" fill="#3b82f6" fillOpacity={0.3} />
    </RadarChart>
  );
};
```

---

## 9.8 导航栏更新

在 Layout 组件中更新导航：

```tsx
const navItems = [
  { path: '/nvc', label: '练习中心', icon: '🎯' },
  { path: '/nvc/scenarios', label: '场景库', icon: '📚' },
  { path: '/nvc/history', label: '练习历史', icon: '📋' },
  { path: '/nvc/dashboard', label: '数据仪表盘', icon: '📊' },
  { path: '/nvc/profile', label: '个人档案', icon: '👤' },
  { path: '/knowledge-bases', label: '知识库', icon: '📖' },
  { path: '/nvc/agents', label: 'Agent配置', icon: '🤖' },
  { path: '/settings', label: '设置', icon: '⚙️' },
];
```

---

## 9.9 验证清单

```
□ 所有页面编译通过，无 TypeScript 错误
□ 页面路由正常切换
□ 练习中心页面：
  □ 三种练习模式入口可点击
  □ 点击后正确创建会话并跳转
  □ 用户能力概览正确显示
□ 文字练习页面：
  □ 对话面板正常显示
  □ 发送消息后收到 AI 回复
  □ SSE 流式输出逐字显示
  □ 实时评估卡片正确展示
  □ 结构化模式下步骤指示器正确显示
□ 语音练习页面：
  □ WebSocket 连接成功
  □ 录音 → 发送 → 收到回复 → 播放
□ 数据仪表盘：
  □ 雷达图正确渲染
  □ 趋势图正确渲染
□ 个人档案：
  □ 档案信息正确显示
  □ 编辑后保存成功
□ Agent 配置页面：
  □ 所有 Agent 列表显示
  □ 编辑提示词后保存成功
  □ 配置修改后立即生效
□ Git 提交："Step 9: Add frontend pages"
```
