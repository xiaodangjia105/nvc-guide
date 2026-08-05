import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { traceApi } from '../api/trace';
import type { AgentTrace, TraceStats } from '../types/trace';
import TraceFilterBar from '../components/nvc/TraceFilterBar';

const STATUS_BADGES: Record<string, { label: string; className: string }> = {
  SUCCESS: { label: '✅', className: 'text-green-600' },
  DEGRADED: { label: '⚠️', className: 'text-yellow-600' },
  FAILED: { label: '❌', className: 'text-red-600' },
};

export default function TraceListPage() {
  const navigate = useNavigate();
  const [traces, setTraces] = useState<AgentTrace[]>([]);
  const [stats, setStats] = useState<TraceStats | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSearch = async (params: { sessionId?: string; from?: string; to?: string; status?: string }) => {
    setLoading(true);
    try {
      if (params.sessionId) {
        const res = await traceApi.listBySession(params.sessionId);
        setTraces((res as unknown as { data: AgentTrace[] }).data || []);
      } else if (params.from && params.to) {
        const res = await traceApi.search({
          from: params.from,
          to: params.to,
          status: params.status,
        });
        setTraces((res as unknown as { data: AgentTrace[] }).data || []);

        // 同时获取统计
        const statsRes = await traceApi.getStats(params.from, params.to);
        setStats((statsRes as unknown as { data: TraceStats }).data);
      }
    } catch (err) {
      console.error('Failed to fetch traces:', err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-6xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-4">🔍 Trace 查询</h1>

      <TraceFilterBar onSearch={handleSearch} />

      {/* 统计概览 */}
      {stats && (
        <div className="grid grid-cols-4 gap-4 mb-4">
          <div className="bg-white p-3 rounded border">
            <div className="text-xs text-gray-500">总 Trace 数</div>
            <div className="text-xl font-bold">{stats.totalTraces}</div>
          </div>
          <div className="bg-white p-3 rounded border">
            <div className="text-xs text-gray-500">平均耗时</div>
            <div className="text-xl font-bold">{(stats.avgDurationMs / 1000).toFixed(1)}s</div>
          </div>
          <div className="bg-white p-3 rounded border">
            <div className="text-xs text-gray-500">平均 Token</div>
            <div className="text-xl font-bold">{Math.round(stats.avgTokensPerTrace)}</div>
          </div>
          <div className="bg-white p-3 rounded border">
            <div className="text-xs text-gray-500">成功率</div>
            <div className="text-xl font-bold">{stats.successRate}%</div>
          </div>
        </div>
      )}

      {/* Trace 列表 */}
      {loading ? (
        <p className="text-center text-gray-400 py-8">加载中...</p>
      ) : traces.length === 0 ? (
        <p className="text-center text-gray-400 py-8">暂无数据，请输入查询条件</p>
      ) : (
        <div className="bg-white border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-2 text-left">Trace ID</th>
                <th className="px-4 py-2 text-left">时间</th>
                <th className="px-4 py-2 text-left">模式</th>
                <th className="px-4 py-2 text-right">Spans</th>
                <th className="px-4 py-2 text-right">耗时</th>
                <th className="px-4 py-2 text-right">Token</th>
                <th className="px-4 py-2 text-center">状态</th>
              </tr>
            </thead>
            <tbody>
              {traces.map(trace => {
                const badge = STATUS_BADGES[trace.finalStatus] || STATUS_BADGES.SUCCESS;
                return (
                  <tr
                    key={trace.traceId}
                    className="border-t hover:bg-gray-50 cursor-pointer"
                    onClick={() => navigate(`/nvc/traces/${trace.traceId}`)}
                  >
                    <td className="px-4 py-2 font-mono text-xs">{trace.traceId.slice(0, 12)}...</td>
                    <td className="px-4 py-2">{new Date(trace.createdAt).toLocaleString()}</td>
                    <td className="px-4 py-2">{trace.mode}</td>
                    <td className="px-4 py-2 text-right">{trace.totalSpans}</td>
                    <td className="px-4 py-2 text-right">{(trace.totalDurationMs / 1000).toFixed(1)}s</td>
                    <td className="px-4 py-2 text-right">
                      {(trace.totalInputTokens + trace.totalOutputTokens).toLocaleString()}
                    </td>
                    <td className={`px-4 py-2 text-center ${badge.className}`}>{badge.label}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
