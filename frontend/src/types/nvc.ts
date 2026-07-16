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
  scenarioId: number | null;
  scenarioTitle: string | null;
  scenarioDescription: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  evaluationFailed: boolean;
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
