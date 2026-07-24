import { request } from './request';

// ==================== 类型定义 ====================

export type WikiCategory =
  | 'CONVERSATION_CASE'
  | 'REAL_SCENARIO'
  | 'LEARNING_SUMMARY'
  | 'BOOK_KNOWLEDGE'
  | 'OTHER';

export type WikiSourceType = 'AUTO_GENERATED' | 'MANUAL' | 'AI_ASSISTED';

export interface WikiCreateRequest {
  title: string;
  category: WikiCategory;
  sourceType: WikiSourceType;
  content?: string;
  tags?: string[];
  sessionId?: number;
}

export interface WikiUpdateRequest {
  title?: string;
  category?: WikiCategory;
  content?: string;
  tags?: string[];
}

export interface WikiResponse {
  id: number;
  title: string;
  category: WikiCategory;
  sourceType: WikiSourceType;
  content?: string;
  tags: string[];
  sessionId?: number;
  createdAt: string;
  updatedAt: string;
}

export interface WikiSearchResult {
  id: number;
  title: string;
  category: WikiCategory;
  contentSnippet: string;
  score: number;
  createdAt: string;
}

export interface WikiPageResponse {
  content: WikiResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ==================== 分类标签映射 ====================

export const WIKI_CATEGORY_LABELS: Record<WikiCategory, string> = {
  CONVERSATION_CASE: '对话案例',
  REAL_SCENARIO: '真实场景',
  LEARNING_SUMMARY: '学习总结',
  BOOK_KNOWLEDGE: '书籍知识',
  OTHER: '其他',
};

export const WIKI_SOURCE_TYPE_LABELS: Record<WikiSourceType, string> = {
  AUTO_GENERATED: '自动生成',
  MANUAL: '手动记录',
  AI_ASSISTED: 'AI 辅助',
};

// ==================== API 方法 ====================

export const wikiApi = {
  /** 创建 Wiki 条目 */
  create: (userId: number, data: WikiCreateRequest) =>
    request.post<WikiResponse>(`/api/nvc/wiki?userId=${userId}`, data),

  /** 获取单个 Wiki */
  get: (userId: number, wikiId: number) =>
    request.get<WikiResponse>(`/api/nvc/wiki/${wikiId}?userId=${userId}`),

  /** 更新 Wiki 条目 */
  update: (userId: number, wikiId: number, data: WikiUpdateRequest) =>
    request.put<WikiResponse>(`/api/nvc/wiki/${wikiId}?userId=${userId}`, data),

  /** 删除 Wiki 条目 */
  delete: (userId: number, wikiId: number) =>
    request.delete(`/api/nvc/wiki/${wikiId}?userId=${userId}`),

  /** 列出用户所有 Wiki（分页 + 分类筛选） */
  list: (userId: number, category?: WikiCategory, page = 0, size = 20) =>
    request.get<WikiPageResponse>('/api/nvc/wiki', {
      params: { userId, ...(category ? { category } : {}), page, size },
    }),

  /** 语义搜索 */
  search: (userId: number, query: string, topK = 5) =>
    request.get<WikiSearchResult[]>('/api/nvc/wiki/search', {
      params: { userId, query, topK },
    }),

  /** 关键词搜索 */
  searchByKeyword: (userId: number, keyword: string) =>
    request.get<WikiResponse[]>('/api/nvc/wiki/search/keyword', {
      params: { userId, keyword },
    }),
};
