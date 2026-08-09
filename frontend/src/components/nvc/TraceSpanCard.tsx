import { useState } from 'react';
import type { AgentSpan } from '../../types/trace';
import ToolCallSpanCard from './ToolCallSpanCard';

interface Props {
  span: AgentSpan;
  maxDuration: number;
}

const SPAN_TYPE_LABELS: Record<string, string> = {
  INTENT_ROUTING: '🎯 意图路由',
  LLM_CALL: '🤖 LLM 调用',
  TOOL_CALL: '🔧 工具调用',
  COMPRESSION: '📦 上下文压缩',
  EVALUATION: '📊 评估触发',
  FALLBACK: '⚠️ 降级处理',
  METRICS: '📈 指标采集',
};

const STATUS_COLORS: Record<string, string> = {
  SUCCESS: 'bg-green-100 border-green-300 text-green-800',
  DEGRADED: 'bg-yellow-100 border-yellow-300 text-yellow-800',
  FAILED: 'bg-red-100 border-red-300 text-red-800',
};

export default function TraceSpanCard({ span, maxDuration }: Props) {
  // 工具调用使用专用卡片
  if (span.spanType === 'TOOL_CALL') {
    return <ToolCallSpanCard span={span} maxDuration={maxDuration} />;
  }

  const [expanded, setExpanded] = useState(false);

  const widthPercent = maxDuration > 0 ? Math.max((span.durationMs / maxDuration) * 100, 2) : 2;
  const statusColor = STATUS_COLORS[span.status] || STATUS_COLORS.SUCCESS;
  const typeLabel = SPAN_TYPE_LABELS[span.spanType] || span.spanType;

  return (
    <div className="border rounded-lg overflow-hidden mb-2">
      {/* 头部 */}
      <div
        className={`flex items-center gap-3 px-4 py-2 cursor-pointer hover:bg-gray-50 ${statusColor}`}
        onClick={() => setExpanded(!expanded)}
      >
        <span className="text-sm font-medium w-32">{typeLabel}</span>
        <span className="text-xs text-gray-500 w-24">{span.componentName}</span>

        {/* 时间条 */}
        <div className="flex-1 h-4 bg-gray-200 rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full ${
              span.status === 'FAILED' ? 'bg-red-400' :
              span.status === 'DEGRADED' ? 'bg-yellow-400' : 'bg-blue-400'
            }`}
            style={{ width: `${widthPercent}%` }}
          />
        </div>

        <span className="text-xs font-mono w-16 text-right">{span.durationMs}ms</span>
        <span className="text-xs">{expanded ? '▼' : '▶'}</span>
      </div>

      {/* 展开详情 */}
      {expanded && (
        <div className="px-4 py-3 bg-gray-50 border-t text-xs space-y-2">
          {span.inputTokens != null && (
            <div className="flex gap-4">
              <span>Input Tokens: <strong>{span.inputTokens}</strong></span>
              <span>Output Tokens: <strong>{span.outputTokens}</strong></span>
            </div>
          )}

          {span.inputPayload && (
            <div>
              <div className="font-medium text-gray-600 mb-1">Input:</div>
              <pre className="bg-white p-2 rounded border overflow-x-auto max-h-32">
                {formatPayload(span.inputPayload)}
              </pre>
            </div>
          )}

          {span.outputPayload && (
            <div>
              <div className="font-medium text-gray-600 mb-1">Output:</div>
              <pre className="bg-white p-2 rounded border overflow-x-auto max-h-32">
                {formatPayload(span.outputPayload)}
              </pre>
            </div>
          )}

          {span.failureReason && (
            <div className="text-red-600">
              <strong>失败原因:</strong> {span.failureReason}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function formatPayload(payload: string): string {
  try {
    return JSON.stringify(JSON.parse(payload), null, 2);
  } catch {
    return payload;
  }
}
