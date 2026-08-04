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
