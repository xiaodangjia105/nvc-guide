import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { traceApi } from '../api/trace';
import type { AgentTrace } from '../types/trace';
import TraceTimeline from '../components/nvc/TraceTimeline';

export default function TraceDetailPage() {
  const { traceId } = useParams<{ traceId: string }>();
  const navigate = useNavigate();
  const [trace, setTrace] = useState<AgentTrace | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!traceId) return;

    const fetchTrace = async () => {
      try {
        const res = await traceApi.getDetail(traceId);
        setTrace((res as unknown as { data: AgentTrace }).data);
      } catch (err) {
        console.error('Failed to fetch trace:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchTrace();
  }, [traceId]);

  if (loading) {
    return <p className="text-center text-gray-400 py-8">加载中...</p>;
  }

  if (!trace) {
    return (
      <div className="text-center py-8">
        <p className="text-gray-400">Trace 不存在</p>
        <button
          onClick={() => navigate('/nvc/traces')}
          className="mt-4 text-blue-500 hover:underline"
        >
          返回列表
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto p-6">
      <div className="flex items-center gap-4 mb-4">
        <button
          onClick={() => navigate('/nvc/traces')}
          className="text-gray-500 hover:text-gray-700"
        >
          ← 返回
        </button>
        <h1 className="text-2xl font-bold">
          Trace #{trace.traceId.slice(0, 8)}
        </h1>
      </div>

      <TraceTimeline trace={trace} />
    </div>
  );
}
