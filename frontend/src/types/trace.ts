/**
 * Agent Trace 类型定义
 */

export interface AgentTrace {
  traceId: string;
  sessionId: string;
  userId: string;
  mode: string;
  triggerType: string;
  totalSpans: number;
  totalDurationMs: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  finalStatus: 'SUCCESS' | 'DEGRADED' | 'FAILED';
  createdAt: string;
  spans: AgentSpan[];
}

export interface AgentSpan {
  spanId: string;
  sequence: number;
  spanType: string;
  componentName: string;
  inputPayload: string;
  outputPayload: string;
  durationMs: number;
  status: string;
  inputTokens?: number;
  outputTokens?: number;
  failureReason?: string;
  metadata?: Record<string, unknown>;
}

/**
 * Hook 执行记录
 */
export interface HookRecord {
  hook: string;
  phase: 'before' | 'after';
  decision: 'SKIP' | 'CONTINUE' | 'MODIFIED' | 'PASSTHROUGH' | 'ERROR';
  durationMs: number;
  error?: string;
}

/**
 * 工具调用输入 payload
 */
export interface ToolCallInput {
  toolName: string;
  arguments?: string | Record<string, unknown>;
  hookChain?: HookRecord[];
}

/**
 * 工具调用输出 payload
 */
export interface ToolCallOutput {
  success: boolean;
  result: string;
  skipReason?: string;
}

/**
 * 工具调用 metadata
 */
export interface ToolCallMetadata {
  hookCount: number;
  skipped: boolean;
  userId: number;
  sessionId: number;
}

export interface TraceStats {
  totalTraces: number;
  avgDurationMs: number;
  avgTokensPerTrace: number;
  successRate: number;
  statusCounts: Record<string, number>;
  modeCounts: Record<string, number>;
  topFailureReasons: { reason: string; count: number }[];
}

export interface EvaluationReport {
  reportId: string;
  evaluatedAt: string;
  totalTraces: number;
  intentRoutingAccuracy: number;
  toolSuccessRates: Record<string, number>;
  overallToolSuccessRate: number;
  latencyP50: number;
  latencyP90: number;
  latencyP99: number;
  avgLatencyMs: number;
  avgTokensPerSession: number;
  totalTokens: number;
  summary: string;
}
