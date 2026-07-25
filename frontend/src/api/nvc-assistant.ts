import { request, getAuthHeaders } from './request';

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
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  toolCalls: ToolCallRecord[];
  createdAt: string;
}

export interface StreamEvent {
  type: 'thinking' | 'tool_call' | 'tool_result' | 'content' | 'done' | 'error';
  data: Record<string, unknown>;
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
  const controller = new AbortController();

  const baseURL = import.meta.env.PROD ? '' : 'http://localhost:8080';

  fetch(`${baseURL}/api/nvc/assistant/chat/stream?userId=${userId}`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify(data),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const reader = response.body?.getReader();
      if (!reader) return;

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const raw = line.slice(6);
            if (raw === '[DONE]') continue;
            try {
              const event = JSON.parse(raw) as StreamEvent;
              onEvent(event);
            } catch {
              // ignore parse errors for malformed SSE lines
            }
          }
        }
      }
    })
    .catch((err: Error) => {
      if (err.name !== 'AbortError') {
        onError?.(err);
      }
    });

  return controller;
}
