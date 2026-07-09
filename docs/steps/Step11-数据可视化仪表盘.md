# Step 11：数据可视化仪表盘

> 目标：实现 NVC 练习数据的可视化展示 — 雷达图、趋势图、统计面板
>
> 预计耗时：1-2 天（主要是前端工作）
>
> 前置条件：Step 5（用户档案 + 能力数据）、Step 9（前端基础页面）已完成
>
> 技术亮点：Recharts 雷达图、趋势折线图、数据统计面板

---

## 11.1 本步要创建/修改的文件

```
frontend/src/
├── pages/
│   └── NvcDashboardPage.tsx               ← 仪表盘页面（主要工作）
├── components/
│   └── nvc/
│       ├── NvcRadarChart.tsx              ← 能力雷达图（Step 9 已创建，本步完善）
│       ├── NvcAbilityTrendChart.tsx       ← 能力趋势折线图
│       ├── NvcStatsPanel.tsx              ← 练习统计面板
│       └── NvcWeaknessAnalysis.tsx        ← 薄弱环节分析
```

---

## 11.2 NvcDashboardPage 页面实现

```tsx
import React, { useEffect, useState } from 'react';
import { profileApi } from '@/api/nvc';
import type { AbilityRadar, AbilityTrend, UserProfile } from '@/types/nvc';
import NvcRadarChart from '@/components/nvc/NvcRadarChart';
import NvcAbilityTrendChart from '@/components/nvc/NvcAbilityTrendChart';
import NvcStatsPanel from '@/components/nvc/NvcStatsPanel';
import NvcWeaknessAnalysis from '@/components/nvc/NvcWeaknessAnalysis';

const NvcDashboardPage: React.FC = () => {
  const userId = 1; // 临时硬编码，后续接入用户系统
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [radar, setRadar] = useState<AbilityRadar | null>(null);
  const [trends, setTrends] = useState<AbilityTrend[]>([]);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    const [profileRes, radarRes, trendsRes] = await Promise.all([
      profileApi.getProfile(userId),
      profileApi.getAbilityRadar(userId),
      profileApi.getAbilityTrends(userId),
    ]);
    setProfile(profileRes.data.data);
    setRadar(radarRes.data.data);
    setTrends(trendsRes.data.data);
  };

  return (
    <div className="max-w-6xl mx-auto p-6 space-y-6">
      <h1 className="text-2xl font-bold">📊 数据仪表盘</h1>

      {/* 统计面板 */}
      <NvcStatsPanel profile={profile} />

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 能力雷达图 */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold mb-4">🎯 NVC 能力雷达</h2>
          {radar && <NvcRadarChart data={radar} />}
        </div>

        {/* 薄弱环节分析 */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold mb-4">🔍 薄弱环节分析</h2>
          {radar && <NvcWeaknessAnalysis data={radar} />}
        </div>
      </div>

      {/* 能力趋势图 */}
      <div className="bg-white rounded-lg shadow p-6">
        <h2 className="text-lg font-semibold mb-4">📈 能力趋势</h2>
        {trends.length > 0 && <NvcAbilityTrendChart data={trends} />}
        {trends.length === 0 && (
          <p className="text-gray-500 text-center py-8">暂无数据，完成练习后查看趋势</p>
        )}
      </div>
    </div>
  );
};

export default NvcDashboardPage;
```

---

## 11.3 NvcStatsPanel 统计面板

```tsx
import React from 'react';
import type { UserProfile } from '@/types/nvc';

interface Props {
  profile: UserProfile | null;
}

const NvcStatsPanel: React.FC<Props> = ({ profile }) => {
  if (!profile) return null;

  const stats = [
    { label: 'NVC 等级', value: levelLabel(profile.nvcLevel), icon: '🏆' },
    { label: '总练习次数', value: `${profile.totalPracticeCount} 次`, icon: '📝' },
    { label: '累计练习时长', value: `${profile.totalPracticeMinutes} 分钟`, icon: '⏱️' },
    { label: '最近练习', value: profile.lastPracticeAt
        ? new Date(profile.lastPracticeAt).toLocaleDateString()
        : '暂无', icon: '📅' },
  ];

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
      {stats.map((stat, i) => (
        <div key={i} className="bg-white rounded-lg shadow p-4 text-center">
          <div className="text-2xl mb-2">{stat.icon}</div>
          <div className="text-sm text-gray-500">{stat.label}</div>
          <div className="text-xl font-bold mt-1">{stat.value}</div>
        </div>
      ))}
    </div>
  );
};

function levelLabel(level: string): string {
  switch (level) {
    case 'BEGINNER': return '🌱 初学者';
    case 'INTERMEDIATE': return '🌿 中级';
    case 'ADVANCED': return '🌳 高级';
    default: return level;
  }
}

export default NvcStatsPanel;
```

---

## 11.4 NvcRadarChart 雷达图

```tsx
import React from 'react';
import {
  Radar, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, ResponsiveContainer, Tooltip
} from 'recharts';
import type { AbilityRadar } from '@/types/nvc';

interface Props {
  data: AbilityRadar;
  compareData?: AbilityRadar; // 可选：对比数据（如上次练习）
}

const NvcRadarChart: React.FC<Props> = ({ data, compareData }) => {
  const chartData = [
    { dimension: '观察', current: data.observation, previous: compareData?.observation ?? 0 },
    { dimension: '感受', current: data.feeling, previous: compareData?.feeling ?? 0 },
    { dimension: '需求', current: data.need, previous: compareData?.need ?? 0 },
    { dimension: '请求', current: data.request, previous: compareData?.request ?? 0 },
    { dimension: '共情', current: data.empathy, previous: compareData?.empathy ?? 0 },
  ];

  return (
    <ResponsiveContainer width="100%" height={300}>
      <RadarChart cx="50%" cy="50%" outerRadius="70%" data={chartData}>
        <PolarGrid />
        <PolarAngleAxis dataKey="dimension" />
        <PolarRadiusAxis angle={90} domain={[0, 100]} />
        <Radar
          name="当前能力"
          dataKey="current"
          stroke="#3b82f6"
          fill="#3b82f6"
          fillOpacity={0.3}
        />
        {compareData && (
          <Radar
            name="上次练习"
            dataKey="previous"
            stroke="#94a3b8"
            fill="#94a3b8"
            fillOpacity={0.1}
            strokeDasharray="5 5"
          />
        )}
        <Tooltip />
      </RadarChart>
    </ResponsiveContainer>
  );
};

export default NvcRadarChart;
```

---

## 11.5 NvcAbilityTrendChart 趋势图

```tsx
import React from 'react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer
} from 'recharts';
import type { AbilityTrend } from '@/types/nvc';

interface Props {
  data: AbilityTrend[];
}

const NvcAbilityTrendChart: React.FC<Props> = ({ data }) => {
  const chartData = data
    .slice()
    .reverse()  // 按时间正序
    .map(item => ({
      date: new Date(item.scoredAt).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' }),
      观察: item.observation,
      感受: item.feeling,
      需求: item.need,
      请求: item.request,
      共情: item.empathy ?? 0,
    }));

  return (
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="date" />
        <YAxis domain={[0, 100]} />
        <Tooltip />
        <Legend />
        <Line type="monotone" dataKey="观察" stroke="#3b82f6" strokeWidth={2} dot={{ r: 4 }} />
        <Line type="monotone" dataKey="感受" stroke="#10b981" strokeWidth={2} dot={{ r: 4 }} />
        <Line type="monotone" dataKey="需求" stroke="#f59e0b" strokeWidth={2} dot={{ r: 4 }} />
        <Line type="monotone" dataKey="请求" stroke="#ef4444" strokeWidth={2} dot={{ r: 4 }} />
        <Line type="monotone" dataKey="共情" stroke="#8b5cf6" strokeWidth={2} dot={{ r: 4 }} strokeDasharray="5 5" />
      </LineChart>
    </ResponsiveContainer>
  );
};

export default NvcAbilityTrendChart;
```

---

## 11.6 NvcWeaknessAnalysis 薄弱环节分析

```tsx
import React from 'react';
import type { AbilityRadar } from '@/types/nvc';

interface Props {
  data: AbilityRadar;
}

const NvcWeaknessAnalysis: React.FC<Props> = ({ data }) => {
  const dimensions = [
    { name: '观察', score: data.observation, description: '区分观察和评论的能力' },
    { name: '感受', score: data.feeling, description: '识别和表达真实感受的能力' },
    { name: '需求', score: data.need, description: '发现深层需求的能力' },
    { name: '请求', score: data.request, description: '提出具体请求的能力' },
    { name: '共情', score: data.empathy, description: '倾听和理解他人的能力' },
  ];

  // 按分数排序，最弱的排前面
  const sorted = [...dimensions].sort((a, b) => a.score - b.score);
  const weakest = sorted[0];
  const strongest = sorted[sorted.length - 1];

  return (
    <div className="space-y-4">
      {/* 薄弱环节 */}
      <div className="p-4 bg-red-50 rounded-lg border border-red-200">
        <div className="text-sm text-red-600 font-medium">最需提升</div>
        <div className="text-lg font-bold text-red-700">{weakest.name}（{weakest.score}分）</div>
        <div className="text-sm text-red-600 mt-1">{weakest.description}</div>
        <div className="mt-2">
          <div className="w-full bg-red-200 rounded-full h-2">
            <div className="bg-red-500 h-2 rounded-full" style={{ width: `${weakest.score}%` }} />
          </div>
        </div>
      </div>

      {/* 优势项 */}
      <div className="p-4 bg-green-50 rounded-lg border border-green-200">
        <div className="text-sm text-green-600 font-medium">你的优势</div>
        <div className="text-lg font-bold text-green-700">{strongest.name}（{strongest.score}分）</div>
        <div className="text-sm text-green-600 mt-1">{strongest.description}</div>
      </div>

      {/* 各维度进度条 */}
      <div className="space-y-2">
        {sorted.map(dim => (
          <div key={dim.name} className="flex items-center gap-2">
            <div className="w-12 text-sm font-medium">{dim.name}</div>
            <div className="flex-1 bg-gray-200 rounded-full h-2">
              <div
                className={`h-2 rounded-full ${getScoreColor(dim.score)}`}
                style={{ width: `${dim.score}%` }}
              />
            </div>
            <div className="w-10 text-sm text-right">{dim.score}</div>
          </div>
        ))}
      </div>
    </div>
  );
};

function getScoreColor(score: number): string {
  if (score >= 80) return 'bg-green-500';
  if (score >= 60) return 'bg-blue-500';
  if (score >= 40) return 'bg-yellow-500';
  return 'bg-red-500';
}

export default NvcWeaknessAnalysis;
```

---

## 11.7 验证清单

```
□ 仪表盘页面编译通过
□ 统计面板正确显示 NVC 等级、练习次数、时长
□ 雷达图正确渲染五维度数据
□ 趋势图正确渲染历史数据折线
□ 薄弱环节分析正确识别最强/最弱维度
□ 数据为空时显示友好提示
□ 响应式布局在不同屏幕尺寸下正常
□ Git 提交："Step 11: Add data visualization dashboard"
```
