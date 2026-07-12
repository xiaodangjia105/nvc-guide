import {
  Radar, RadarChart, PolarGrid, PolarAngleAxis,
  PolarRadiusAxis, ResponsiveContainer,
} from 'recharts';

interface NvcRadarChartProps {
  data: {
    observation: number;
    feeling: number;
    need: number;
    request: number;
    empathy: number;
  };
  size?: 'sm' | 'md' | 'lg';
}

const DIMENSION_LABELS: Record<string, string> = {
  observation: '观察',
  feeling: '感受',
  need: '需求',
  request: '请求',
  empathy: '共情',
};

export default function NvcRadarChart({ data, size = 'md' }: NvcRadarChartProps) {
  const chartData = Object.entries(data).map(([key, value]) => ({
    dimension: DIMENSION_LABELS[key] || key,
    value,
    fullMark: 100,
  }));

  const height = size === 'sm' ? 200 : size === 'md' ? 300 : 400;

  return (
    <ResponsiveContainer width="100%" height={height}>
      <RadarChart cx="50%" cy="50%" outerRadius="70%" data={chartData}>
        <PolarGrid stroke="#94a3b8" strokeOpacity={0.3} />
        <PolarAngleAxis
          dataKey="dimension"
          tick={{ fill: '#64748b', fontSize: 12 }}
        />
        <PolarRadiusAxis
          angle={90}
          domain={[0, 100]}
          tick={{ fill: '#94a3b8', fontSize: 10 }}
        />
        <Radar
          name="NVC 能力"
          dataKey="value"
          stroke="#3b82f6"
          fill="#3b82f6"
          fillOpacity={0.25}
          strokeWidth={2}
        />
      </RadarChart>
    </ResponsiveContainer>
  );
}
