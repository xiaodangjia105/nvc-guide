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
    request.post<PracticeSession>(
      `/api/nvc/practice/sessions?userId=${userId}`, data
    ),

  getSessions: (userId: number, phase?: string) =>
    request.get<PracticeSession[]>('/api/nvc/practice/sessions', {
      params: { userId, ...(phase ? { phase } : {}) },
    }),

  getSession: (sessionId: number) =>
    request.get<PracticeSession>(
      `/api/nvc/practice/sessions/${sessionId}`
    ),

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
    request.get<NvcPracticeReport>(
      `/api/nvc/report/sessions/${sessionId}`
    ),

  downloadPdfUrl: (sessionId: number) =>
    (import.meta.env.PROD ? '' : 'http://localhost:8080')
    + `/api/nvc/report/sessions/${sessionId}/pdf`,
};

// ========== 用户档案 ==========

export const profileApi = {
  getProfile: (userId: number) =>
    request.get<UserProfile>('/api/nvc/profile', {
      params: { userId },
    }),

  updateProfile: (userId: number, data: UserProfileUpdateRequest) =>
    request.put<UserProfile>(
      `/api/nvc/profile?userId=${userId}`, data
    ),

  getAbilityRadar: (userId: number) =>
    request.get<AbilityRadar>('/api/nvc/profile/ability-radar', {
      params: { userId },
    }),

  getAbilityTrends: (userId: number) =>
    request.get<AbilityTrend[]>('/api/nvc/profile/ability-trends', {
      params: { userId },
    }),

  analyzeCommunication: (
    userId: number, data: CommunicationAnalysisRequest
  ) =>
    request.post<CommunicationRecord>(
      `/api/nvc/profile/communication-records/analyze?userId=${userId}`,
      data
    ),

  getCommunicationRecords: (userId: number) =>
    request.get<CommunicationRecord[]>(
      '/api/nvc/profile/communication-records',
      { params: { userId } }
    ),
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
    request.put<AgentConfig>(
      `/api/nvc/agents/configs/${scene}`, data
    ),
};

// ========== 语音会话 ==========

export const voiceApi = {
  createSession: (data: CreateVoiceSessionRequest) =>
    request.post<VoiceSession>('/api/nvc/voice/sessions', data),

  getSession: (sessionId: number) =>
    request.get<VoiceSession>(
      `/api/nvc/voice/sessions/${sessionId}`
    ),

  endSession: (sessionId: number) =>
    request.post<VoiceSession>(
      `/api/nvc/voice/sessions/${sessionId}/end`
    ),

  pauseSession: (sessionId: number) =>
    request.put<VoiceSession>(
      `/api/nvc/voice/sessions/${sessionId}/pause`
    ),

  resumeSession: (sessionId: number) =>
    request.put<VoiceSession>(
      `/api/nvc/voice/sessions/${sessionId}/resume`
    ),

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
