import { request } from './request';

// ==================== 类型定义 ====================

export type FeedbackSource = 'PRACTICE' | 'ASSISTANT';

export interface SubmitFeedbackRequest {
  sessionId: number;
  messageId: number;
  messageSource: FeedbackSource;
  agentScene?: string;
  rating: number; // 1=踩, 5=赞
  comment?: string;
}

export interface FeedbackEntity {
  id: number;
  userId: number;
  sessionId: number;
  messageId: number;
  messageSource: FeedbackSource;
  agentScene: string | null;
  rating: number;
  comment: string | null;
  createdAt: string;
}

export interface SceneFeedbackStats {
  agentScene: string;
  count: number;
  thumbsUpCount: number;
  thumbsUpRate: number;
}

export interface FeedbackStatsResponse {
  totalFeedbackCount: number;
  overallThumbsUpRate: number;
  perSceneStats: SceneFeedbackStats[];
}

// ==================== API 方法 ====================

export const feedbackApi = {
  /** 提交反馈 */
  submit: (userId: number, data: SubmitFeedbackRequest) =>
    request.post<FeedbackEntity>('/api/nvc/feedback', data, {
      params: { userId },
    }),

  /** 获取反馈统计 */
  getStats: (from: string, to: string) =>
    request.get<FeedbackStatsResponse>('/api/nvc/feedback/stats', {
      params: { from, to },
    }),

  /** 获取最近差评 */
  getNegative: (limit = 20) =>
    request.get<FeedbackEntity[]>('/api/nvc/feedback/negative', {
      params: { limit },
    }),
};
