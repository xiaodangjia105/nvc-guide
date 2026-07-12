# Step 9: 前端页面开发 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 NVC 练习助手的所有前端页面，包括练习中心、文字练习、语音练习、历史报告、数据仪表盘、个人档案、场景库、Agent 配置共 9 个页面。

**Architecture:** 沿用现有 BrowserRouter + Routes + lazy() 路由模式，使用现有 request.ts 封装调用后端 API。新增 types/nvc.ts 类型定义、api/nvc.ts API 封装、useUserId.ts Hook，以及 9 个页面和 7 个组件。

**Tech Stack:** React 18, TypeScript, Tailwind CSS 4, Framer Motion, Recharts, Lucide React, React Markdown, react-virtuoso

## Global Constraints

- 沿用现有 `BrowserRouter` + `Routes` + `lazy()` 模式，不使用 `createBrowserRouter`
- 使用现有 `request.ts` 封装（`request.get/post/put/delete`），不直接用 axios
- 沿用 `lucide-react` 图标，不用 emoji
- 5+1 维度：观察/感受/需求/请求/共情 + 综合分
- userId 通过 `useUserId` Hook 管理（localStorage 存储）
- 2 空格缩进，列限制 100 字符
- 语音 WebSocket 端点：`/ws/nvc-voice/{sessionId}`

---

## Task 1: 类型定义 + API 封装 + useUserId Hook

**Files:**
- Create: `frontend/src/types/nvc.ts`
- Create: `frontend/src/api/nvc.ts`
- Create: `frontend/src/hooks/useUserId.ts`

**Interfaces:**
- Produces: 所有 TypeScript 类型、API 函数、useUserId Hook，供后续所有 Task 使用

- [ ] **Step 1: 创建 types/nvc.ts**

```typescript
// frontend/src/types/nvc.ts

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
export type NvcVoiceSessionPhase = 'INTRO' | 'PRACTICE' | 'WRAP_UP';
export type NvcVoiceSessionStatus = 'IN_PROGRESS' | 'PAUSED' | 'COMPLETED' | 'FAILED';

// ========== 练习会话 ==========

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
  empathyScore: number;
  overallScore: number;
  observationDetail: string;
  feelingDetail: string;
  needDetail: string;
  requestDetail: string;
  empathyDetail: string;
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
  empathyScore: number;
  overallScore: number;
  observationDetail: string;
  feelingDetail: string;
  needDetail: string;
  requestDetail: string;
  empathyDetail: string;
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

export interface UserProfileUpdateRequest {
  communicationBackground?: string;
  personalityTraits?: string;
  communicationStyle?: string;
  emotionalTriggers?: string;
  commonScenarios?: string;
  relationshipTypes?: string;
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

export interface ScenarioGenerateRequest {
  scenarioType: NvcScenarioType;
  difficulty?: NvcDifficulty;
  description?: string;
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
  stepAdvanceThreshold: number | null;
  maxStepAttempts: number | null;
  stepTimeoutMinutes: number | null;
}

export interface AgentConfigUpdateRequest {
  systemPrompt?: string;
  temperature?: number;
  maxTokens?: number;
  topP?: number;
  isEnabled?: boolean;
  stepAdvanceThreshold?: number;
  maxStepAttempts?: number;
  stepTimeoutMinutes?: number;
}

// ========== 步骤进度 ==========

export interface StepProgress {
  currentStep: NvcPracticeStep;
  stepIndex: number;
  totalSteps: number;
  currentScore: number | null;
  canAdvance: boolean;
  stepDescription: string;
  maxAttempts: number | null;
  attemptsUsed: number | null;
  stepStartedAt: string | null;
  timeoutMinutes: number | null;
}

// ========== 评估卡片 ==========

export interface EvaluationCardData {
  observation: { score: number; passed: boolean; detail: string };
  feeling: { score: number; passed: boolean; detail: string };
  need: { score: number; passed: boolean; detail: string };
  request: { score: number; passed: boolean; detail: string };
  empathy: { score: number; passed: boolean; detail: string };
  overall: number;
}

// ========== 语音会话 ==========

export interface VoiceSession {
  id: number;
  userId: number;
  practiceMode: NvcPracticeMode;
  scenarioId: number | null;
  agentScene: string | null;
  difficulty: NvcDifficulty;
  currentPhase: NvcVoiceSessionPhase;
  status: NvcVoiceSessionStatus;
  llmProvider: string | null;
  plannedDuration: number | null;
  actualDuration: number | null;
  startTime: string | null;
  endTime: string | null;
  evaluateStatus: string | null;
  webSocketUrl: string;
}

export interface CreateVoiceSessionRequest {
  userId: number;
  practiceMode: NvcPracticeMode;
  scenarioId?: number;
  difficulty?: NvcDifficulty;
  llmProvider?: string;
}

export interface VoiceMessage {
  id: number;
  sessionId: number;
  messageType: string;
  userRecognizedText: string;
  aiGeneratedText: string;
  timestamp: string;
  sequenceNum: number;
}

export interface VoiceEvaluationStatus {
  evaluateStatus: string | null;
  evaluateError: string | null;
  evaluation: VoiceEvaluationDetail | null;
}

export interface VoiceEvaluationDetail {
  sessionId: number;
  totalQuestions: number;
  overallScore: number;
  overallFeedback: string;
  strengths: string[];
  improvements: string[];
  answers: Array<{
    questionIndex: number;
    question: string;
    category: string;
    userAnswer: string;
    score: number;
    feedback: string;
    referenceAnswer?: string | null;
    keyPoints?: string[] | null;
  }>;
}

// ========== 沟通记录 ==========

export interface CommunicationRecord {
  id: number;
  title: string;
  scenarioType: NvcScenarioType | null;
  rawContent: string;
  analysisResult: string;
  nvcSuggestion: string;
  createdAt: string;
}

export interface CommunicationAnalysisRequest {
  title: string;
  rawContent: string;
  scenarioType?: NvcScenarioType;
}

// ========== 仪表盘 ==========

export interface DashboardStats {
  [key: string]: unknown;
}
```

- [ ] **Step 2: 创建 api/nvc.ts**

```typescript
// frontend/src/api/nvc.ts
import { request } from './request';
import type {
  PracticeSession, CreatePracticeSessionRequest, DialogueResponse,
  PracticeMessage, NvcPracticeReport, UserProfile, UserProfileUpdateRequest,
  AbilityRadar, AbilityTrend, NvcScenario, ScenarioGenerateRequest,
  AgentConfig, AgentConfigUpdateRequest, StepProgress,
  CommunicationAnalysisRequest, CommunicationRecord, DashboardStats,
  VoiceSession, CreateVoiceSessionRequest, VoiceMessage,
  VoiceEvaluationStatus,
} from '../types/nvc';

// ========== 练习会话 ==========

export const practiceApi = {
  createSession: (userId: number, data: CreatePracticeSessionRequest) =>
    request.post<PracticeSession>(`/api/nvc/practice/sessions?userId=${userId}`, data),

  getSessions: (userId: number, phase?: string) =>
    request.get<PracticeSession[]>('/api/nvc/practice/sessions', {
      params: { userId, ...(phase ? { phase } : {}) },
    }),

  getSession: (sessionId: number) =>
    request.get<PracticeSession>(`/api/nvc/practice/sessions/${sessionId}`),

  sendMessage: (sessionId: number, content: string) =>
    request.post<DialogueResponse>(
      `/api/nvc/practice/sessions/${sessionId}/messages`,
      { content }
    ),

  sendMessageStream: (sessionId: number, content: string) =>
    fetch(
      (import.meta.env.PROD ? '' : 'http://localhost:8080')
      + `/api/nvc/practice/sessions/${sessionId}/messages/stream`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content }),
      }
    ),

  getMessages: (sessionId: number) =>
    request.get<PracticeMessage[]>(
      `/api/nvc/practice/sessions/${sessionId}/messages`
    ),

  completeSession: (sessionId: number) =>
    request.post<PracticeSession>(
      `/api/nvc/practice/sessions/${sessionId}/complete`
    ),

  getStepProgress: (sessionId: number) =>
    request.get<StepProgress>(
      `/api/nvc/practice/sessions/${sessionId}/step-progress`
    ),

  advanceStep: (sessionId: number) =>
    request.post<StepProgress>(
      `/api/nvc/practice/sessions/${sessionId}/advance-step`
    ),

  resetStep: (sessionId: number) =>
    request.post<StepProgress>(
      `/api/nvc/practice/sessions/${sessionId}/reset-step`
    ),
};

// ========== 报告 ==========

export const reportApi = {
  getReport: (sessionId: number) =>
    request.get<NvcPracticeReport>(`/api/nvc/report/sessions/${sessionId}`),

  downloadPdfUrl: (sessionId: number) =>
    (import.meta.env.PROD ? '' : 'http://localhost:8080')
    + `/api/nvc/report/sessions/${sessionId}/pdf`,
};

// ========== 用户档案 ==========

export const profileApi = {
  getProfile: (userId: number) =>
    request.get<UserProfile>('/api/nvc/profile', { params: { userId } }),

  updateProfile: (userId: number, data: UserProfileUpdateRequest) =>
    request.put<UserProfile>(`/api/nvc/profile?userId=${userId}`, data),

  getAbilityRadar: (userId: number) =>
    request.get<AbilityRadar>('/api/nvc/profile/ability-radar', {
      params: { userId },
    }),

  getAbilityTrends: (userId: number) =>
    request.get<AbilityTrend[]>('/api/nvc/profile/ability-trends', {
      params: { userId },
    }),

  analyzeCommunication: (userId: number, data: CommunicationAnalysisRequest) =>
    request.post<CommunicationRecord>(
      `/api/nvc/profile/communication-records/analyze?userId=${userId}`,
      data
    ),

  getCommunicationRecords: (userId: number) =>
    request.get<CommunicationRecord[]>('/api/nvc/profile/communication-records', {
      params: { userId },
    }),
};

// ========== 场景 ==========

export const scenarioApi = {
  getScenarios: (scenarioType?: string, difficulty?: string) =>
    request.get<NvcScenario[]>('/api/nvc/scenarios', {
      params: {
        ...(scenarioType ? { scenarioType } : {}),
        ...(difficulty ? { difficulty } : {}),
      },
    }),

  getScenario: (id: number) =>
    request.get<NvcScenario>(`/api/nvc/scenarios/${id}`),

  generateScenario: (data: ScenarioGenerateRequest) =>
    request.post<NvcScenario>('/api/nvc/scenarios/generate', data),
};

// ========== Agent 配置 ==========

export const agentApi = {
  getAllConfigs: () =>
    request.get<AgentConfig[]>('/api/nvc/agents/configs'),

  getConfig: (scene: string) =>
    request.get<AgentConfig>(`/api/nvc/agents/configs/${scene}`),

  updateConfig: (scene: string, data: AgentConfigUpdateRequest) =>
    request.put<AgentConfig>(`/api/nvc/agents/configs/${scene}`, data),
};

// ========== 语音会话 ==========

export const voiceApi = {
  createSession: (data: CreateVoiceSessionRequest) =>
    request.post<VoiceSession>('/api/nvc/voice/sessions', data),

  getSession: (sessionId: number) =>
    request.get<VoiceSession>(`/api/nvc/voice/sessions/${sessionId}`),

  endSession: (sessionId: number) =>
    request.post<VoiceSession>(`/api/nvc/voice/sessions/${sessionId}/end`),

  pauseSession: (sessionId: number) =>
    request.put<VoiceSession>(`/api/nvc/voice/sessions/${sessionId}/pause`),

  resumeSession: (sessionId: number) =>
    request.put<VoiceSession>(`/api/nvc/voice/sessions/${sessionId}/resume`),

  listSessions: (userId?: number, status?: string) =>
    request.get<VoiceSession[]>('/api/nvc/voice/sessions', {
      params: {
        ...(userId ? { userId } : {}),
        ...(status ? { status } : {}),
      },
    }),

  getMessages: (sessionId: number) =>
    request.get<VoiceMessage[]>(
      `/api/nvc/voice/sessions/${sessionId}/messages`
    ),

  getEvaluation: (sessionId: number) =>
    request.get<VoiceEvaluationStatus>(
      `/api/nvc/voice/sessions/${sessionId}/evaluation`
    ),

  triggerEvaluation: (sessionId: number) =>
    request.post<void>(
      `/api/nvc/voice/sessions/${sessionId}/evaluation`
    ),
};

// ========== 仪表盘 ==========

export const dashboardApi = {
  getStats: (userId: number) =>
    request.get<DashboardStats>('/api/nvc/dashboard/stats', {
      params: { userId },
    }),
};
```

- [ ] **Step 3: 创建 hooks/useUserId.ts**

```typescript
// frontend/src/hooks/useUserId.ts
import { useState, useCallback } from 'react';

const USER_ID_KEY = 'nvc_user_id';

function generateUserId(): number {
  return Math.floor(Math.random() * 900000) + 100000;
}

export function useUserId(): [number, (id: number) => void] {
  const [userId, setUserIdState] = useState<number>(() => {
    const stored = localStorage.getItem(USER_ID_KEY);
    if (stored) {
      const parsed = parseInt(stored, 10);
      if (!isNaN(parsed)) return parsed;
    }
    const newId = generateUserId();
    localStorage.setItem(USER_ID_KEY, String(newId));
    return newId;
  });

  const setUserId = useCallback((id: number) => {
    localStorage.setItem(USER_ID_KEY, String(id));
    setUserIdState(id);
  }, []);

  return [userId, setUserId];
}
```

- [ ] **Step 4: 验证 TypeScript 编译**

Run: `cd frontend && npx tsc --noEmit`
Expected: 无错误

- [ ] **Step 5: Commit**

```bash
git add frontend/src/types/nvc.ts frontend/src/api/nvc.ts frontend/src/hooks/useUserId.ts
git commit -m "feat(step9): add NVC types, API layer, and useUserId hook"
```

---

## Task 2: 路由与导航更新

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/components/Layout.tsx`
- Modify: `frontend/src/constants/routes.ts`

**Interfaces:**
- Consumes: 所有页面组件（后续 Task 创建，先用 lazy import 占位）
- Produces: 路由配置和导航菜单，所有页面通过路由可访问

- [ ] **Step 1: 更新 constants/routes.ts**

```typescript
// frontend/src/constants/routes.ts
export const ROUTES = {
  // NVC 练习
  nvcHub: '/nvc',
  nvcPractice: (sessionId: number) => `/nvc/practice/${sessionId}`,
  nvcVoice: (sessionId: number) => `/nvc/voice/${sessionId}`,
  nvcHistory: '/nvc/history',
  nvcReport: (sessionId: number) => `/nvc/history/${sessionId}/report`,
  nvcProfile: '/nvc/profile',
  nvcDashboard: '/nvc/dashboard',
  nvcScenarios: '/nvc/scenarios',
  nvcAgents: '/nvc/agents',

  // 知识库（保留）
  knowledgebase: '/knowledgebase',
  knowledgebaseUpload: '/knowledgebase/upload',
  knowledgebaseChat: '/knowledgebase/chat',

  // 设置
  settings: '/settings',
} as const;
```

- [ ] **Step 2: 更新 App.tsx**

在现有 lazy imports 后添加 NVC 页面的 lazy import，在 Routes 中添加 NVC 路由。默认重定向改为 `/nvc`。

- [ ] **Step 3: 更新 Layout.tsx 导航**

在 `navGroups` 数组开头添加 NVC 练习导航组：

```typescript
{
  id: 'nvc',
  title: 'NVC 练习',
  items: [
    { id: 'nvc-hub', path: '/nvc', label: '练习中心', icon: Target, description: '三种练习模式' },
    { id: 'nvc-scenarios', path: '/nvc/scenarios', label: '场景库', icon: BookOpen, description: '浏览练习场景' },
    { id: 'nvc-history', path: '/nvc/history', label: '练习历史', icon: ClipboardList, description: '查看历史记录' },
    { id: 'nvc-dashboard', path: '/nvc/dashboard', label: '数据仪表盘', icon: BarChart3, description: '能力数据分析' },
    { id: 'nvc-profile', path: '/nvc/profile', label: '个人档案', icon: User, description: '编辑个人信息' },
    { id: 'nvc-agents', path: '/nvc/agents', label: 'Agent配置', icon: Bot, description: '管理AI角色' },
  ],
},
```

需要从 lucide-react 导入 `Target, BookOpen, ClipboardList, BarChart3, User, Bot`。

- [ ] **Step 4: 验证编译**

Run: `cd frontend && npx tsc --noEmit`
Expected: 可能有未实现的页面组件导入错误，暂时注释掉或创建空页面占位

- [ ] **Step 5: Commit**

```bash
git add frontend/src/App.tsx frontend/src/components/Layout.tsx frontend/src/constants/routes.ts
git commit -m "feat(step9): update routing and navigation for NVC pages"
```

---

## Task 3: 练习中心首页 NvcPracticeHubPage

**Files:**
- Create: `frontend/src/pages/NvcPracticeHubPage.tsx`
- Create: `frontend/src/components/nvc/NvcProfileCard.tsx`

**Interfaces:**
- Consumes: `practiceApi`, `profileApi`, `useUserId`, `AbilityRadar` 类型
- Produces: 练习中心首页，三种模式入口 + 能力概览

- [ ] **Step 1: 创建 NvcProfileCard 组件**

显示用户能力概览（简版雷达图 + 统计数据）。

- [ ] **Step 2: 创建 NvcPracticeHubPage**

包含：
- 三种练习模式大卡片（场景驱动/自由对话/结构化四步）
- 语音练习入口
- 用户能力概览（NvcProfileCard）

点击模式卡片 → `practiceApi.createSession()` → 跳转到 `/nvc/practice/:sessionId`

- [ ] **Step 3: 验证页面渲染**

Run: `cd frontend && npm run dev`，访问 `/nvc` 确认页面正常渲染

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/NvcPracticeHubPage.tsx frontend/src/components/nvc/NvcProfileCard.tsx
git commit -m "feat(step9): add practice hub page with mode cards and profile overview"
```

---

## Task 4: 对话组件 NvcChatPanel + NvcEvaluationCard + NvcStepIndicator

**Files:**
- Create: `frontend/src/components/nvc/NvcChatPanel.tsx`
- Create: `frontend/src/components/nvc/NvcEvaluationCard.tsx`
- Create: `frontend/src/components/nvc/NvcStepIndicator.tsx`

**Interfaces:**
- Consumes: `practiceApi.sendMessageStream()`, `PracticeMessage`, `EvaluationCardData`, `StepProgress`
- Produces: 可复用的对话面板、评估卡片、步骤指示器组件

- [ ] **Step 1: 创建 NvcStepIndicator**

显示四步练习进度（观察→感受→需求→请求），当前步骤高亮，已完成步骤打勾。

- [ ] **Step 2: 创建 NvcEvaluationCard**

显示实时评估结果：五维度分数条 + 详情折叠。

- [ ] **Step 3: 创建 NvcChatPanel**

核心对话组件：
- 消息气泡（用户右侧/AI左侧）
- Markdown 渲染（AI 回复）
- SSE 流式逐字显示（`fetch` + `ReadableStream`）
- 自动滚动到底部
- 输入框 + 发送按钮

- [ ] **Step 4: 验证组件编译**

Run: `cd frontend && npx tsc --noEmit`
Expected: 无错误

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/nvc/NvcChatPanel.tsx frontend/src/components/nvc/NvcEvaluationCard.tsx frontend/src/components/nvc/NvcStepIndicator.tsx
git commit -m "feat(step9): add chat panel, evaluation card, and step indicator components"
```

---

## Task 5: 文字练习页面 NvcPracticePage

**Files:**
- Create: `frontend/src/pages/NvcPracticePage.tsx`

**Interfaces:**
- Consumes: `practiceApi`, `NvcChatPanel`, `NvcEvaluationCard`, `NvcStepIndicator`
- Produces: 完整的文字练习对话页面

- [ ] **Step 1: 创建 NvcPracticePage**

从 URL 获取 sessionId，加载会话信息和消息历史。包含：
- 顶部：场景信息 + 会话状态
- 中间：NvcChatPanel（对话区域）
- 侧边/底部：NvcEvaluationCard（实时评估）
- 结构化模式下显示 NvcStepIndicator
- 结束练习按钮

- [ ] **Step 2: 验证页面功能**

Run: `cd frontend && npm run dev`，手动创建会话并测试对话流程

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/NvcPracticePage.tsx
git commit -m "feat(step9): add text practice page with chat and evaluation"
```

---

## Task 6: 语音练习页面 NvcVoicePage

**Files:**
- Create: `frontend/src/pages/NvcVoicePage.tsx`

**Interfaces:**
- Consumes: `voiceApi`, `AudioRecorder`, `AudioPlayer`, `RealtimeSubtitle`（复用现有组件）
- Produces: 语音练习页面，WebSocket 连接 `/ws/nvc-voice/{sessionId}`

- [ ] **Step 1: 创建 NvcVoicePage**

参考现有 voiceinterview 页面结构，但使用 nvcvoice 端点：
- 创建语音会话 → 获取 webSocketUrl
- WebSocket 连接管理（发送音频、接收字幕和 TTS）
- 复用 `AudioRecorder`（录音）、`AudioPlayer`（播放）、`RealtimeSubtitle`（对话实录）
- 会话控制（暂停/恢复/结束）

- [ ] **Step 2: 验证 WebSocket 连接**

Run: `cd frontend && npm run dev`，测试语音会话创建和 WebSocket 连接

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/NvcVoicePage.tsx
git commit -m "feat(step9): add voice practice page with WebSocket integration"
```

---

## Task 7: 练习历史 NvcHistoryPage + 报告详情 NvcReportPage

**Files:**
- Create: `frontend/src/pages/NvcHistoryPage.tsx`
- Create: `frontend/src/pages/NvcReportPage.tsx`

**Interfaces:**
- Consumes: `practiceApi.getSessions()`, `reportApi.getReport()`, `reportApi.downloadPdfUrl()`
- Produces: 练习历史列表页面和报告详情页面

- [ ] **Step 1: 创建 NvcHistoryPage**

显示用户的练习会话列表：
- 卡片列表，显示模式、时间、状态、分数
- 筛选（按模式、按状态）
- 点击跳转到报告详情

- [ ] **Step 2: 创建 NvcReportPage**

显示单次练习的详细报告：
- 五维雷达图（NvcRadarChart，先用简单版本）
- 各维度详细分析
- 优势和改进建议
- 参考表达
- PDF 下载按钮

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/NvcHistoryPage.tsx frontend/src/pages/NvcReportPage.tsx
git commit -m "feat(step9): add practice history and report pages"
```

---

## Task 8: 数据仪表盘 NvcDashboardPage + 图表组件

**Files:**
- Create: `frontend/src/pages/NvcDashboardPage.tsx`
- Create: `frontend/src/components/nvc/NvcRadarChart.tsx`
- Create: `frontend/src/components/nvc/NvcAbilityTrendChart.tsx`

**Interfaces:**
- Consumes: `profileApi.getAbilityRadar()`, `profileApi.getAbilityTrends()`, `dashboardApi.getStats()`
- Produces: 数据仪表盘页面和两个 Recharts 图表组件

- [ ] **Step 1: 创建 NvcRadarChart**

使用 Recharts RadarChart，5 个维度（观察/感受/需求/请求/共情）。

- [ ] **Step 2: 创建 NvcAbilityTrendChart**

使用 Recharts LineChart，X 轴时间，Y 轴分数，5 条线。

- [ ] **Step 3: 创建 NvcDashboardPage**

包含：
- NvcRadarChart（能力雷达图）
- NvcAbilityTrendChart（趋势折线图）
- 练习统计面板（总次数、总时长、平均分）
- 薄弱环节分析

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/NvcDashboardPage.tsx frontend/src/components/nvc/NvcRadarChart.tsx frontend/src/components/nvc/NvcAbilityTrendChart.tsx
git commit -m "feat(step9): add dashboard page with radar and trend charts"
```

---

## Task 9: 个人档案 NvcProfilePage

**Files:**
- Create: `frontend/src/pages/NvcProfilePage.tsx`

**Interfaces:**
- Consumes: `profileApi.getProfile()`, `profileApi.updateProfile()`, `profileApi.analyzeCommunication()`, `profileApi.getCommunicationRecords()`
- Produces: 个人档案编辑页面

- [ ] **Step 1: 创建 NvcProfilePage**

包含：
- 档案编辑表单（沟通背景、性格特征、沟通风格等）
- 沟通记录上传和 AI 分析
- 历史沟通记录列表
- 保存按钮

- [ ] **Step 2: Commit**

```bash
git add frontend/src/pages/NvcProfilePage.tsx
git commit -m "feat(step9): add profile page with editing and communication analysis"
```

---

## Task 10: 场景库 NvcScenarioLibraryPage + NvcScenarioCard

**Files:**
- Create: `frontend/src/pages/NvcScenarioLibraryPage.tsx`
- Create: `frontend/src/components/nvc/NvcScenarioCard.tsx`

**Interfaces:**
- Consumes: `scenarioApi.getScenarios()`, `scenarioApi.generateScenario()`
- Produces: 场景库浏览页面

- [ ] **Step 1: 创建 NvcScenarioCard**

场景卡片组件：标题、描述、类型标签、难度标签、使用次数。

- [ ] **Step 2: 创建 NvcScenarioLibraryPage**

包含：
- 场景列表（网格布局）
- 筛选（按类型、按难度）
- AI 生成新场景按钮
- 点击场景 → 创建练习会话并跳转

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/NvcScenarioLibraryPage.tsx frontend/src/components/nvc/NvcScenarioCard.tsx
git commit -m "feat(step9): add scenario library page with filtering and AI generation"
```

---

## Task 11: Agent 配置页面 NvcAgentConfigPage

**Files:**
- Create: `frontend/src/pages/NvcAgentConfigPage.tsx`

**Interfaces:**
- Consumes: `agentApi.getAllConfigs()`, `agentApi.updateConfig()`
- Produces: Agent 配置管理页面

- [ ] **Step 1: 创建 NvcAgentConfigPage**

包含：
- Agent 列表（卡片形式，每个 Agent 一个卡片）
- 展开编辑：系统提示词、温度、模型参数
- 步骤配置用可折叠"高级配置"区域
- 启用/禁用开关
- 保存按钮（热更新）

- [ ] **Step 2: Commit**

```bash
git add frontend/src/pages/NvcAgentConfigPage.tsx
git commit -m "feat(step9): add agent config page with prompt editing and parameter tuning"
```

---

## Task 12: 最终集成验证 + 进度记录

**Files:**
- Modify: `frontend/src/App.tsx`（确保所有路由正确）
- Create: `docs/进度记录/Step9-前端页面开发-完成记录.md`

- [ ] **Step 1: 完整编译验证**

Run: `cd frontend && npx tsc --noEmit && npm run build`
Expected: 构建成功，无错误

- [ ] **Step 2: 功能验证清单**

- [ ] 练习中心页面正常渲染
- [ ] 三种模式点击可创建会话并跳转
- [ ] 文字练习对话正常（SSE 流式）
- [ ] 语音练习 WebSocket 连接正常
- [ ] 练习历史列表显示
- [ ] 报告详情页面显示
- [ ] 仪表盘雷达图和趋势图渲染
- [ ] 个人档案编辑保存
- [ ] 场景库浏览和筛选
- [ ] Agent 配置编辑保存

- [ ] **Step 3: 编写进度记录**

- [ ] **Step 4: 最终 Commit**

```bash
git add -A
git commit -m "docs(step9): add completion record for frontend pages"
```
