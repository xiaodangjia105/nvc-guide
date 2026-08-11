import { request, getAuthHeaders } from './request';
import { consumeSSEEventsWithAbort } from '../utils/sse';

// ==================== 类型定义 ====================

export interface AssistantRequest {
  conversationId?: number | null;
  message: string;
}

export interface ToolCallRecord {
  toolName: string;
  arguments: string;
  result: string;
  success: boolean;
  durationMs: number;
}

export interface AssistantResponse {
  conversationId: number;
  messageId: number;
  content: string;
  toolCalls: ToolCallRecord[];
  done: boolean;
}

export interface ConversationResponse {
  id: number;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface AssistantMessageResponse {
  id: number;
  role: 'USER' | 'ASSISTANT' | 'TOOL' | 'SYSTEM';
  content: string;
  toolCalls: ToolCallRecord[];
  createdAt: string;
}

export interface StreamEvent {
  type: 'thinking' | 'tool_call' | 'tool_result' | 'content' | 'done' | 'error';
  data: string | Record<string, unknown>;
}

// ==================== API 方法 ====================

export const assistantApi = {
  /** 获取对话列表 */
  getConversations: (userId: number) =>
    request.get<ConversationResponse[]>('/api/nvc/assistant/conversations', {
      params: { userId },
    }),

  /** 获取对话消息 */
  getMessages: (userId: number, conversationId: number) =>
    request.get<AssistantMessageResponse[]>(
      `/api/nvc/assistant/conversations/${conversationId}/messages`,
      { params: { userId } }
    ),

  /** 删除对话 */
  deleteConversation: (userId: number, conversationId: number) =>
    request.delete<void>(
      `/api/nvc/assistant/conversations/${conversationId}`,
      { params: { userId } }
    ),

  /** 非流式对话 */
  sendChat: (userId: number, data: AssistantRequest) =>
    request.post<AssistantResponse>('/api/nvc/assistant/chat', data, {
      params: { userId },
    }),
};

// ==================== 流式 SSE ====================

/**
 * 发送流式对话请求，通过 SSE 逐步接收事件
 * 返回 AbortController 用于取消请求
 */
export function sendChatStream(
  userId: number,
  data: AssistantRequest,
  onEvent: (event: StreamEvent) => void,
  onError?: (error: Error) => void
): AbortController {
  const baseURL = import.meta.env.VITE_API_BASE_URL || '';

  return consumeSSEEventsWithAbort<string | Record<string, unknown>>(
    `${baseURL}/api/nvc/assistant/chat/stream?userId=${userId}`,
    {
      method: 'POST',
      headers: {
        ...getAuthHeaders(),
        'Accept': 'text/event-stream',
      },
      body: JSON.stringify(data),
    },
    {
      onEvent: (type, parsedData) => {
        onEvent({
          type: (type || 'content') as StreamEvent['type'],
          data: parsedData,
        });
      },
      onComplete: () => {},
      onError: (err) => onError?.(err),
    }
  );
}
