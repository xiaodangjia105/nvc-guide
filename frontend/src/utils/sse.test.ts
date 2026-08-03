import { describe, it, expect } from 'vitest';
import { consumeSSEStream, consumeSSEEvents } from './sse';

/**
 * 创建模拟 SSE Response
 */
function createMockSSEResponse(lines: string[]): Response {
  const encoder = new TextEncoder();
  const data = lines.join('\n') + '\n';
  const stream = new ReadableStream({
    start(controller) {
      controller.enqueue(encoder.encode(data));
      controller.close();
    },
  });
  return new Response(stream);
}

describe('SSE 工具函数', () => {
  describe('consumeSSEStream', () => {
    it('解析简单的 data: 行', async () => {
      const response = createMockSSEResponse([
        'data: hello',
        'data: world',
      ]);
      const messages: string[] = [];
      await consumeSSEStream(response, {
        onMessage: (msg) => messages.push(msg),
        onComplete: () => {},
        onError: () => {},
      });
      expect(messages).toEqual(['hello', 'world']);
    });

    it('空 data: 视为换行符', async () => {
      const response = createMockSSEResponse([
        'data: hello',
        'data:',
        'data: world',
      ]);
      const messages: string[] = [];
      await consumeSSEStream(response, {
        onMessage: (msg) => messages.push(msg),
        onComplete: () => {},
        onError: () => {},
      });
      expect(messages).toEqual(['hello', '\n', 'world']);
    });

    it('忽略非 data: 行', async () => {
      const response = createMockSSEResponse([
        'event: message',
        'data: hello',
        'retry: 3000',
        'data: world',
      ]);
      const messages: string[] = [];
      await consumeSSEStream(response, {
        onMessage: (msg) => messages.push(msg),
        onComplete: () => {},
        onError: () => {},
      });
      expect(messages).toEqual(['hello', 'world']);
    });
  });

  describe('consumeSSEEvents', () => {
    it('解析 event: + data: 格式', async () => {
      const response = createMockSSEResponse([
        'event: thinking',
        'data: 思考中...',
        'event: content',
        'data: 你好',
      ]);
      const events: Array<{ type: string; data: unknown }> = [];
      await consumeSSEEvents(response, {
        onEvent: (type, data) => events.push({ type, data }),
        onComplete: () => {},
        onError: () => {},
      });
      expect(events).toEqual([
        { type: 'thinking', data: '思考中...' },
        { type: 'content', data: '你好' },
      ]);
    });

    it('跳过 [DONE] 标记', async () => {
      const response = createMockSSEResponse([
        'data: hello',
        'data: [DONE]',
      ]);
      const events: Array<{ type: string; data: unknown }> = [];
      await consumeSSEEvents(response, {
        onEvent: (type, data) => events.push({ type, data }),
        onComplete: () => {},
        onError: () => {},
      });
      expect(events).toHaveLength(1);
      expect(events[0].data).toBe('hello');
    });

    it('尝试 JSON 解析', async () => {
      const response = createMockSSEResponse([
        'data: {"key":"value"}',
        'data: plain text',
      ]);
      const events: Array<{ type: string; data: unknown }> = [];
      await consumeSSEEvents(response, {
        onEvent: (type, data) => events.push({ type, data }),
        onComplete: () => {},
        onError: () => {},
      });
      expect(events[0].data).toEqual({ key: 'value' });
      expect(events[1].data).toBe('plain text');
    });

    it('无 event: 时默认 content 类型', async () => {
      const response = createMockSSEResponse(['data: hello']);
      const events: Array<{ type: string; data: unknown }> = [];
      await consumeSSEEvents(response, {
        onEvent: (type, data) => events.push({ type, data }),
        onComplete: () => {},
        onError: () => {},
      });
      expect(events[0].type).toBe('content');
    });

    it('parseJSON=false 时不解析 JSON', async () => {
      const response = createMockSSEResponse(['data: {"key":"value"}']);
      const events: Array<{ type: string; data: unknown }> = [];
      await consumeSSEEvents(response, {
        onEvent: (type, data) => events.push({ type, data }),
        onComplete: () => {},
        onError: () => {},
      }, { parseJSON: false });
      expect(events[0].data).toBe('{"key":"value"}');
    });
  });
});
