import type { AgentTrace } from '../../types/trace';

interface Props {
  trace: AgentTrace;
}

const STATUS_BADGES: Record<string, { label: string; className: string }> = {
  SUCCESS: { label: '✅ 成功', className: 'bg-green-100 text-green-800' },
  DEGRADED: { label: '⚠️ 降级', className: 'bg-yellow-100 text-yellow-800' },
  FAILED: { label: '❌ 失败', className: 'bg-red-100 text-red-800' },
};

export default function TraceSummaryBar({ trace }: Props) {
  const badge = STATUS_BADGES[trace.finalStatus] || STATUS_BADGES.SUCCESS;
  const totalTokens = trace.totalInputTokens + trace.totalOutputTokens;

  return (
    <div className="flex items-center gap-6 px-4 py-3 bg-gray-50 border-t text-sm">
      <span className={`px-2 py-1 rounded-full text-xs font-medium ${badge.className}`}>
        {badge.label}
      </span>
      <span>
        耗时: <strong>{(trace.totalDurationMs / 1000).toFixed(1)}s</strong>
      </span>
      <span>
        Token: <strong>{totalTokens.toLocaleString()}</strong>
        <span className="text-gray-400 ml-1">
          (in:{trace.totalInputTokens.toLocaleString()} out:{trace.totalOutputTokens.toLocaleString()})
        </span>
      </span>
      <span>
        Spans: <strong>{trace.totalSpans}</strong>
      </span>
      <span className="text-gray-400">
        {new Date(trace.createdAt).toLocaleString()}
      </span>
    </div>
  );
}
