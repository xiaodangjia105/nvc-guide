/**
 * Trace API 封装
 */

import { request } from './request';
import type { AgentTrace, TraceStats, EvaluationReport } from '../types/trace';

export const traceApi = {
  /** 按 sessionId 查询 Trace 列表 */
  listBySession: (sessionId: string, page = 0, size = 20) =>
    request.get<AgentTrace[]>('/api/nvc/traces', { params: { sessionId, page, size } }),

  /** 查询单个 Trace 详情（含 Spans） */
  getDetail: (traceId: string) =>
    request.get<AgentTrace>(`/api/nvc/traces/${traceId}`),

  /** 按时间范围查询 Trace */
  search: (params: { from: string; to: string; status?: string; mode?: string }) =>
    request.get<AgentTrace[]>('/api/nvc/traces/search', { params }),

  /** Trace 统计概览 */
  getStats: (from: string, to: string) =>
    request.get<TraceStats>('/api/nvc/traces/stats', { params: { from, to } }),

  /** 运行离线评估（手动触发） */
  runEvaluation: (from: string, to: string) =>
    request.post<EvaluationReport>('/api/nvc/traces/evaluate', null, { params: { from, to } }),
};
