import { useMemo } from 'react';
import type { AgentSpan, AgentTrace } from '../../types/trace';
import TraceSpanCard from './TraceSpanCard';
import TraceSummaryBar from './TraceSummaryBar';

interface Props {
  trace: AgentTrace;
}

interface SpanGroup {
  type: 'single' | 'parallel';
  spans: AgentSpan[];
  label?: string;
}

export default function TraceTimeline({ trace }: Props) {
  const spans = trace.spans || [];
  const maxDuration = Math.max(...spans.map(s => s.durationMs), 1);

  // 识别并行工具调用
  const spanGroups = useMemo(() => {
    if (spans.length === 0) return [];

    const groups: SpanGroup[] = [];
    let i = 0;

    while (i < spans.length) {
      // 检测连续的 TOOL_CALL span（并行执行）
      if (spans[i].spanType === 'TOOL_CALL') {
        const parallelSpans: AgentSpan[] = [spans[i]];
        let j = i + 1;

        // 收集连续的 TOOL_CALL span
        while (j < spans.length && spans[j].spanType === 'TOOL_CALL') {
          parallelSpans.push(spans[j]);
          j++;
        }

        if (parallelSpans.length > 1) {
          // 多个工具调用 - 并行执行
          groups.push({
            type: 'parallel',
            spans: parallelSpans,
            label: `并行执行 - ${parallelSpans.length} 个工具`,
          });
        } else {
          // 单个工具调用
          groups.push({
            type: 'single',
            spans: parallelSpans,
          });
        }

        i = j;
      } else {
        // 非工具调用 span
        groups.push({
          type: 'single',
          spans: [spans[i]],
        });
        i++;
      }
    }

    return groups;
  }, [spans]);

  return (
    <div className="border rounded-lg overflow-hidden">
      {/* 标题栏 */}
      <div className="px-4 py-3 bg-white border-b">
        <h3 className="font-medium">
          Trace #{trace.traceId.slice(0, 8)}
          <span className="ml-2 text-sm text-gray-500">
            {trace.mode} | {trace.triggerType}
          </span>
        </h3>
      </div>

      {/* Span 时间线 */}
      <div className="p-4">
        {spanGroups.length === 0 ? (
          <p className="text-gray-400 text-center py-8">暂无 Span 数据</p>
        ) : (
          spanGroups.map((group, groupIndex) => (
            <div key={groupIndex} className="mb-4">
              {/* 并行执行标记 */}
              {group.type === 'parallel' && group.label && (
                <div className="flex items-center gap-2 mb-2">
                  <div className="flex items-center gap-1 px-2 py-1 bg-blue-50 border border-blue-200 rounded text-xs text-blue-700">
                    <span>⚡</span>
                    <span className="font-medium">并行执行</span>
                    <span className="text-blue-500">{group.spans.length} 个工具</span>
                  </div>
                  <div className="flex-1 h-px bg-blue-200" />
                </div>
              )}

              {/* Span 卡片 */}
              <div className={group.type === 'parallel' ? 'ml-4 border-l-2 border-blue-200 pl-2' : ''}>
                {group.spans.map(span => (
                  <TraceSpanCard
                    key={span.spanId}
                    span={span}
                    maxDuration={maxDuration}
                  />
                ))}
              </div>
            </div>
          ))
        )}
      </div>

      {/* 汇总栏 */}
      <TraceSummaryBar trace={trace} />
    </div>
  );
}
