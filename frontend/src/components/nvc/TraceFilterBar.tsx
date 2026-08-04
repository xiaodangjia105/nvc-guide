import { useState } from 'react';

interface Props {
  onSearch: (params: { sessionId?: string; from?: string; to?: string; status?: string }) => void;
}

export default function TraceFilterBar({ onSearch }: Props) {
  const [sessionId, setSessionId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [status, setStatus] = useState('');

  const handleSearch = () => {
    onSearch({
      sessionId: sessionId || undefined,
      from: from || undefined,
      to: to || undefined,
      status: status || undefined,
    });
  };

  return (
    <div className="flex flex-wrap items-end gap-3 p-4 bg-gray-50 border rounded-lg mb-4">
      <div>
        <label className="block text-xs text-gray-500 mb-1">Session ID</label>
        <input
          type="text"
          value={sessionId}
          onChange={e => setSessionId(e.target.value)}
          placeholder="sess-xxx"
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
      <button
        onClick={handleSearch}
        className="bg-blue-500 text-white px-4 py-1 rounded text-sm hover:bg-blue-600"
      >
        查询
      </button>
    </div>
  );
}
