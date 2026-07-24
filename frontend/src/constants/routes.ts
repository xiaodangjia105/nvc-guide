export const ROUTES = {
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

  // 知识库
  knowledgebase: '/knowledgebase',
  knowledgebaseUpload: '/knowledgebase/upload',
  knowledgebaseChat: '/knowledgebase/chat',

  // 设置
  settings: '/settings',
} as const;
