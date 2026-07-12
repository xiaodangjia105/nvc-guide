import {
  LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import type { AbilityTrend } from '../../types/nvc';

interface NvcAbilityTrendChartProps {
  data: AbilityTrend[];
  height?: number;
}

const LINES = [
  { key: 'observation', label: '观察', color: '#3b82f6' },
  { key: 'feeling', label: '感受', color: '#10b981' },
  { key: 'need', label: '需求', color: '#f59e0b' },
  { key: 'request', label: '请求', color: '#ef4444' },
  { key: 'empathy', label: '共情', color: '#8b5cf6' },
];

function formatDate(dateStr: string): string {
  const d = new Date(dateStr);
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

export default function NvcAbilityTrendChart({
  data,
  height = 300,
}: NvcAbilityTrendChartProps) {
  if (!data || data.length === 0) {
    return (
      <div
        className="flex items-center justify-center text-slate-400"
        style={{ height }}
      >
        暂无趋势数据
      </div>
    );
  }

  const chartData = data.map((item) => ({
    date: formatDate(item.scoredAt),
    observation: item.observation,
    feeling: item.feeling,
    need: item.need,
    request: item.request,
    empathy: item.empathy ?? 0,
  }));

  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
        <XAxis
          dataKey="date"
          tick={{ fill: '#64748b', fontSize: 12 }}
        />
        <YAxis
          domain={[0, 100]}
          tick={{ fill: '#64748b', fontSize: 12 }}
        />
        <Tooltip
          contentStyle={{
            backgroundColor: '#1e293b',
            border: 'none',
            borderRadius: '8px',
            color: '#f8fafc',
          }}
        />
        <Legend />
        {LINES.map((line) => (
          <Line
            key={line.key}
            type="monotone"
            dataKey={line.key}
            name={line.label}
            stroke={line.color}
            strokeWidth={2}
            dot={{ r: 3 }}
            activeDot={{ r: 5 }}
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
}
