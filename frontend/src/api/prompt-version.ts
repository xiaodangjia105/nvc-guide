import { request } from './request';

// ==================== 类型定义 ====================

export interface PromptVersion {
  id: number;
  agentScene: string;
  version: number;
  systemPrompt: string;
  isActive: boolean;
  trafficPercentage: number;
  changeNote: string | null;
  totalCalls: number;
  avgEvaluationScore: number | null;
  avgTokenUsage: number | null;
  avgLatencyMs: number | null;
  createdAt: string;
  activatedAt: string | null;
}

export interface CreatePromptVersionRequest {
  systemPrompt: string;
  changeNote?: string;
  trafficPercentage?: number;
}

// ==================== API 方法 ====================

export const promptVersionApi = {
  /** 获取某个场景的所有版本 */
  getVersions: (scene: string) =>
    request.get<PromptVersion[]>(`/api/nvc/prompt-versions/${scene}`),

  /** 创建新版本 */
  createVersion: (scene: string, data: CreatePromptVersionRequest) =>
    request.post<PromptVersion>(`/api/nvc/prompt-versions/${scene}`, data),

  /** 激活版本（全量切换） */
  activateVersion: (scene: string, version: number) =>
    request.post<PromptVersion>(
      `/api/nvc/prompt-versions/${scene}/versions/${version}/activate`
    ),

  /** A/B 流量分配 */
  setTrafficSplit: (
    scene: string,
    version1: number,
    pct1: number,
    version2: number,
    pct2: number
  ) =>
    request.post<void>(
      `/api/nvc/prompt-versions/${scene}/ab-test`,
      null,
      { params: { version1, pct1, version2, pct2 } }
    ),
};
