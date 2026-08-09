import { useState } from 'react';

interface Props {
  onSearch: (params: {
    sessionId?: string;
    from?: string;
    to?: string;
    status?: string;
    toolName?: string;
    spanType?: string;
  }) => void;
}

const SPAN_TYPES = [
  { value: '', label: '全部 Span' },
  { value: 'INTENT_ROUTING', label: '🎯 意图路由' },
  { value: 'LLM_CALL', label: '🤖 LLM 调用' },
  { value: 'TOOL_CALL', label: '🔧 工具调用' },
  { value: 'COMPRESSION', label: '📦 上下文压缩' },
  { value: 'EVALUATION', label: '📊 评估触发' },
  { value: 'FALLBACK', label: '⚠️ 降级处理' },
  { value: 'METRICS', label: '📈 指标采集' },
];

export default function TraceFilterBar({ onSearch }: Props) {
  const [sessionId, setSessionId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [status, setStatus] = useState('');
  const [toolName, setToolName] = useState('');
  const [spanType, setSpanType] = useState('');

  const handleSearch = () => {
    onSearch({
      sessionId: sessionId || undefined,
      from: from || undefined,
      to: to || undefined,
      status: status || undefined,
      toolName: toolName || undefined,
      spanType: spanType || undefined,
    });
  };

  const handleReset = () => {
    setSessionId('');
    setFrom('');
    setTo('');
    setStatus('');
    setToolName('');
    setSpanType('');
    onSearch({});
  };

  return (
    <div className="flex flex-wrap items-end gap-3 p-4 bg-gray-50 border rounded-lg mb-4">
      <div>
        <label className="block text-xs text-gray-500 mb-1">会话 ID（conversationId）</label>
        <input
          type="text"
          value={sessionId}
          onChange={e => setSessionId(e.target.value)}
          placeholder="留空查询所有"
          className="border rounded px-2 py-1 text-sm w-40"
        />
      </div>
      <div>
        <label className="block text-xs text-gray-500 mb-1">开始时间</label>
        <input
          type="datetime-local"
          value={from}
          onChange={e => setFrom(e.target.value)}
          className="border rounded px-2 py-1 text-sm"
        />
      </div>
      <div>
        <label className="block text-xs text-gray-500 mb-1">结束时间</label>
        <input
          type="datetime-local"
          value={to}
          onChange={e => setTo(e.target.value)}
          className="border rounded px-2 py-1 text-sm"
        />
      </div>
      <div>
        <label className="block text-xs text-gray-500 mb-1">状态</label>
        <select
          value={status}
          onChange={e => setStatus(e.target.value)}
          className="border rounded px-2 py-1 text-sm"
        >
          <option value="">全部</option>
          <option value="SUCCESS">成功</option>
          <option value="DEGRADED">降级</option>
          <option value="FAILED">失败</option>
        </select>
      </div>
      <div>
        <label className="block text-xs text-gray-500 mb-1">工具名</label>
        <input
          type="text"
          value={toolName}
          onChange={e => setToolName(e.target.value)}
          placeholder="如: profile_update"
          className="border rounded px-2 py-1 text-sm w-40"
        />
      </div>
      <div>
        <label className="block text-xs text-gray-500 mb-1">Span 类型</label>
        <select
          value={spanType}
          onChange={e => setSpanType(e.target.value)}
          className="border rounded px-2 py-1 text-sm"
        >
          {SPAN_TYPES.map(type => (
            <option key={type.value} value={type.value}>
              {type.label}
            </option>
          ))}
        </select>
      </div>
      <button
        onClick={handleSearch}
        className="bg-blue-500 text-white px-4 py-1 rounded text-sm hover:bg-blue-600"
      >
        查询
      </button>
      <button
        onClick={handleReset}
        className="bg-gray-500 text-white px-4 py-1 rounded text-sm hover:bg-gray-600"
      >
        重置
      </button>
    </div>
  );
}
