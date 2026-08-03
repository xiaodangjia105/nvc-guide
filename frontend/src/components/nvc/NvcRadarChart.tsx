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

export default function NvcRadarChart({ data, size = 'md' }: NvcRadarChartProps) {
  const chartData = [
    { dimension: '观察', value: data.observation, fullMark: 100 },
    { dimension: '感受', value: data.feeling, fullMark: 100 },
    { dimension: '需求', value: data.need, fullMark: 100 },
    { dimension: '请求', value: data.request, fullMark: 100 },
    { dimension: '共情', value: data.empathy, fullMark: 100 },
  ];

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
