import type { AgentTrace } from '../../types/trace';
import TraceSpanCard from './TraceSpanCard';
import TraceSummaryBar from './TraceSummaryBar';

interface Props {
  trace: AgentTrace;
}

export default function TraceTimeline({ trace }: Props) {
  const spans = trace.spans || [];
  const maxDuration = Math.max(...spans.map(s => s.durationMs), 1);

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
        {spans.length === 0 ? (
          <p className="text-gray-400 text-center py-8">暂无 Span 数据</p>
        ) : (
          spans.map(span => (
            <TraceSpanCard
              key={span.spanId}
              span={span}
              maxDuration={maxDuration}
            />
          ))
        )}
      </div>

      {/* 汇总栏 */}
      <TraceSummaryBar trace={trace} />
    </div>
  );
}
