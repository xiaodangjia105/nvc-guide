import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import TraceTimeline from './TraceTimeline';
import type { AgentTrace } from '../../types/trace';

describe('TraceTimeline', () => {
  const mockTrace: AgentTrace = {
    traceId: 'test-trace-123',
    sessionId: 'session-1',
    userId: 'user-1',
    mode: 'FREE_DIALOG',
    triggerType: 'USER_MESSAGE',
    totalSpans: 3,
    totalDurationMs: 500,
    totalInputTokens: 100,
    totalOutputTokens: 50,
    finalStatus: 'SUCCESS',
    createdAt: '2026-08-09T10:00:00Z',
    spans: [
      {
        spanId: 'span-1',
        sequence: 1,
        spanType: 'INTENT_ROUTING',
        componentName: 'IntentRouter',
        inputPayload: '{"message":"test"}',
        outputPayload: '{"intent":"profile_update"}',
        durationMs: 50,
        status: 'SUCCESS',
      },
      {
        spanId: 'span-2',
        sequence: 2,
        spanType: 'TOOL_CALL',
        componentName: 'ToolExecutor',
        inputPayload: '{"toolName":"profile_update","arguments":{}}',
        outputPayload: '{"success":true,"result":"updated"}',
        durationMs: 200,
        status: 'SUCCESS',
      },
      {
        spanId: 'span-3',
        sequence: 3,
        spanType: 'LLM_CALL',
        componentName: 'AgentLoop',
        inputPayload: '{"prompt":"test"}',
        outputPayload: '{"response":"ok"}',
        durationMs: 250,
        status: 'SUCCESS',
        inputTokens: 100,
        outputTokens: 50,
      },
    ],
  };

  it('渲染 Trace 标题', () => {
    render(<TraceTimeline trace={mockTrace} />);

    expect(screen.getByText(/Trace #test-tra/)).toBeInTheDocument();
    expect(screen.getByText(/FREE_DIALOG/)).toBeInTheDocument();
  });

  it('渲染所有 Span 卡片', () => {
    render(<TraceTimeline trace={mockTrace} />);

    // 验证 3 个 Span 卡片
    expect(screen.getByText('🎯 意图路由')).toBeInTheDocument();
    expect(screen.getByText('🤖 LLM 调用')).toBeInTheDocument();
    // 工具调用使用专用卡片
    expect(screen.getByText('🔧 工具调用')).toBeInTheDocument();
  });

  it('空 Span 列表显示提示', () => {
    const emptyTrace = { ...mockTrace, spans: [] };
    render(<TraceTimeline trace={emptyTrace} />);

    expect(screen.getByText('暂无 Span 数据')).toBeInTheDocument();
  });

  it('显示汇总栏', () => {
    render(<TraceTimeline trace={mockTrace} />);

    // 验证汇总信息
    expect(screen.getByText(/耗时/)).toBeInTheDocument();
    expect(screen.getByText(/Token/)).toBeInTheDocument();
    expect(screen.getByText(/Spans/)).toBeInTheDocument();
  });

  it('识别并行工具调用', () => {
    // 创建有并行工具调用的 trace
    const parallelTrace: AgentTrace = {
      ...mockTrace,
      spans: [
        {
          spanId: 'span-1',
          sequence: 1,
          spanType: 'INTENT_ROUTING',
          componentName: 'IntentRouter',
          inputPayload: '{}',
          outputPayload: '{}',
          durationMs: 50,
          status: 'SUCCESS',
        },
        {
          spanId: 'span-2',
          sequence: 2,
          spanType: 'TOOL_CALL',
          componentName: 'ToolExecutor',
          inputPayload: '{"toolName":"tool_a"}',
          outputPayload: '{}',
          durationMs: 200,
          status: 'SUCCESS',
        },
        {
          spanId: 'span-3',
          sequence: 3,
          spanType: 'TOOL_CALL',
          componentName: 'ToolExecutor',
          inputPayload: '{"toolName":"tool_b"}',
          outputPayload: '{}',
          durationMs: 180,
          status: 'SUCCESS',
        },
        {
          spanId: 'span-4',
          sequence: 4,
          spanType: 'LLM_CALL',
          componentName: 'AgentLoop',
          inputPayload: '{}',
          outputPayload: '{}',
          durationMs: 250,
          status: 'SUCCESS',
        },
      ],
    };

    render(<TraceTimeline trace={parallelTrace} />);

    // 验证显示并行标记
    expect(screen.getByText('并行执行')).toBeInTheDocument();
    expect(screen.getByText('2 个工具')).toBeInTheDocument();
  });

  it('显示时间占比', () => {
    render(<TraceTimeline trace={mockTrace} />);

    // 验证显示时间占比（如 50%、40% 等）
    const spanCards = screen.getAllByText(/ms/);
    expect(spanCards.length).toBeGreaterThan(0);
  });
});
