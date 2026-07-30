/**
 * SSE (Server-Sent Events) 工具函数
 *
 * 统一前端 4 处重复的 SSE 解析逻辑。
 * 后端格式: event: {type}\ndata: {content}
 */

/** SSE 基础回调 */
export interface SSECallbacks {
  onComplete: () => void;
  onError: (error: Error) => void;
}

/** 简单流回调（适用于 knowledgebase / ragChat） */
export interface SSEStreamCallbacks extends SSECallbacks {
  onMessage: (data: string) => void;
}

/** 事件流回调（适用于 nvc-assistant / NvcChatPanel） */
export interface SSEEventCallbacks<T = string> extends SSECallbacks {
  onEvent: (type: string, data: T) => void;
}

/** SSE 事件解析选项 */
export interface SSEOptions {
  /** 是否尝试 JSON.parse（默认 true） */
  parseJSON?: boolean;
  /** 是否跳过 [DONE] 标记（默认 true） */
  skipDone?: boolean;
}

/**
 * 消费简单 SSE 流（适用于 knowledgebase / ragChat 的纯文本流）
 *
 * 后端只发送 data: {content}，无 event: 类型。
 * 空 data: 行视为换行符。
 */
export async function consumeSSEStream(
  response: Response,
  callbacks: SSEStreamCallbacks
): Promise<void> {
  const reader = response.body?.getReader();
  if (!reader) {
    callbacks.onError(new Error('No response body'));
    return;
  }

  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        // 处理缓冲区剩余内容
        if (buffer.trim()) {
          const content = extractSimpleContent(buffer);
          if (content !== null) callbacks.onMessage(content);
        }
        callbacks.onComplete();
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        const content = extractSimpleContent(line);
        if (content !== null) callbacks.onMessage(content);
      }
    }
  } catch (err) {
    callbacks.onError(err instanceof Error ? err : new Error(String(err)));
  }
}

/**
 * 消费事件版 SSE 流（适用于 nvc-assistant / NvcChatPanel 的 typed event 流）
 *
 * 后端发送 event: {type}\ndata: {content} 格式。
 * 支持 JSON 解析和 [DONE] 跳过。
 */
export async function consumeSSEEvents<T = string>(
  response: Response,
  callbacks: SSEEventCallbacks<T>,
  options?: SSEOptions
): Promise<void> {
  const { parseJSON = true, skipDone = true } = options ?? {};

  const reader = response.body?.getReader();
  if (!reader) {
    callbacks.onError(new Error('No response body'));
    return;
  }

  const decoder = new TextDecoder();
  let buffer = '';
  let currentEventType = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEventType = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          const raw = line.slice(5).trimStart();
          if (skipDone && raw === '[DONE]') continue;

          const eventType = currentEventType || 'content';
          currentEventType = '';

          if (parseJSON) {
            let parsed: T;
            try {
              parsed = JSON.parse(raw) as T;
            } catch {
              parsed = raw as T;
            }
            callbacks.onEvent(eventType, parsed);
          } else {
            callbacks.onEvent(eventType, raw as T);
          }
        }
      }
    }
    callbacks.onComplete();
  } catch (err) {
    if (err instanceof Error && err.name === 'AbortError') return;
    callbacks.onError(err instanceof Error ? err : new Error(String(err)));
  }
}

/**
 * 消费带 AbortController 的事件流（适用于需要取消的场景）
 *
 * 返回 AbortController 用于取消请求。
 */
export function consumeSSEEventsWithAbort<T = string>(
  url: string,
  init: RequestInit,
  callbacks: SSEEventCallbacks<T>,
  options?: SSEOptions
): AbortController {
  const controller = new AbortController();

  fetch(url, { ...init, signal: controller.signal })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      await consumeSSEEvents<T>(response, callbacks, options);
    })
    .catch((err: Error) => {
      if (err.name !== 'AbortError') {
        callbacks.onError(err);
      }
    });

  return controller;
}

// ==================== 内部工具 ====================

/**
 * 从简单 data: 行提取内容
 * 空 data: 视为换行符
 */
function extractSimpleContent(line: string): string | null {
  if (!line.startsWith('data:')) return null;
  let content = line.substring(5);
  if (content.startsWith(' ')) content = content.substring(1);
  if (content.length === 0) return '\n';
  return content;
}
