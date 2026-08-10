export const ROUTES = {
  // 认证
  login: '/login',
  register: '/register',

  // NVC 练习
  nvcHub: '/nvc',
  nvcPractice: (sessionId: number) => `/nvc/practice/${sessionId}`,
  nvcVoice: (sessionId: number) => `/nvc/voice/${sessionId}`,
  nvcHistory: '/nvc/history',
  nvcReport: (sessionId: number) =>
    `/nvc/history/${sessionId}/report`,
  nvcProfile: '/nvc/profile',
  nvcDashboard: '/nvc/dashboard',
  nvcScenarios: '/nvc/scenarios',
  nvcAgents: '/nvc/agents',

  // Wiki
  nvcWiki: '/nvc/wiki',

  // AI 助手
  nvcAssistant: '/nvc/assistant',

  // 知识库
  knowledgebase: '/knowledgebase',
  knowledgebaseUpload: '/knowledgebase/upload',
  knowledgebaseChat: '/knowledgebase/chat',

  // 设置
  settings: '/settings',

  // Trace 可观测
  nvcTraces: '/nvc/traces',
  nvcTraceDetail: (traceId: string) => `/nvc/traces/${traceId}`,
} as const;
