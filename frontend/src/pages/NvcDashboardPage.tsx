import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import {
  BarChart3, Clock, Loader2, Target, TrendingUp, Trophy,
} from 'lucide-react';
import { useUserId } from '../hooks/useUserId';
import { profileApi, dashboardApi } from '../api/nvc';
import NvcRadarChart from '../components/nvc/NvcRadarChart';
import NvcAbilityTrendChart from '../components/nvc/NvcAbilityTrendChart';
import type { AbilityRadar, AbilityTrend, DashboardStats } from '../types/nvc';

export default function NvcDashboardPage() {
  const [userId] = useUserId();
  const [radar, setRadar] = useState<AbilityRadar | null>(null);
  const [trends, setTrends] = useState<AbilityTrend[]>([]);
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      profileApi.getAbilityRadar(userId).catch(() => null),
      profileApi.getAbilityTrends(userId).catch(() => []),
      dashboardApi.getStats(userId).catch(() => null),
    ])
      .then(([r, t, s]) => {
        setRadar(r);
        setTrends(t);
        setStats(s);
      })
      .finally(() => setLoading(false));
  }, [userId]);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-[60vh]">
        <Loader2 className="w-8 h-8 animate-spin text-primary-500" />
      </div>
    );
  }

  // 找出薄弱维度
  const weakDimensions = radar
    ? [
        { label: '观察', value: radar.observation },
        { label: '感受', value: radar.feeling },
        { label: '需求', value: radar.need },
        { label: '请求', value: radar.request },
        { label: '共情', value: radar.empathy },
      ]
        .sort((a, b) => a.value - b.value)
        .slice(0, 2)
    : [];

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800 dark:text-white">
          数据仪表盘
        </h1>
        <p className="text-slate-500 dark:text-slate-400 mt-1">
          跟踪你的 NVC 能力成长轨迹
        </p>
      </div>

      {/* 统计卡片 */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          className="bg-white dark:bg-slate-800 rounded-xl p-5 border border-slate-200 dark:border-slate-700"
        >
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-blue-50 dark:bg-blue-900/30 flex items-center justify-center">
              <Trophy className="w-5 h-5 text-blue-500" />
            </div>
            <div>
              <p className="text-sm text-slate-500">总练习次数</p>
              <p className="text-2xl font-bold text-slate-800 dark:text-white">
                {(stats?.totalPracticeCount as number) ?? 0}
              </p>
            </div>
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="bg-white dark:bg-slate-800 rounded-xl p-5 border border-slate-200 dark:border-slate-700"
        >
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-emerald-50 dark:bg-emerald-900/30 flex items-center justify-center">
              <Clock className="w-5 h-5 text-emerald-500" />
            </div>
            <div>
              <p className="text-sm text-slate-500">累计时长</p>
              <p className="text-2xl font-bold text-slate-800 dark:text-white">
                {(((stats?.totalPracticeMinutes as number) ?? 0) / 60).toFixed(1)}h
              </p>
            </div>
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="bg-white dark:bg-slate-800 rounded-xl p-5 border border-slate-200 dark:border-slate-700"
        >
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-amber-50 dark:bg-amber-900/30 flex items-center justify-center">
              <BarChart3 className="w-5 h-5 text-amber-500" />
            </div>
            <div>
              <p className="text-sm text-slate-500">综合能力</p>
              <p className="text-2xl font-bold text-slate-800 dark:text-white">
                {radar?.overall ?? 0}
              </p>
            </div>
          </div>
        </motion.div>
      </div>

      {/* 雷达图 */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="bg-white dark:bg-slate-800 rounded-2xl p-6 border border-slate-200 dark:border-slate-700"
      >
        <h3 className="text-lg font-semibold text-slate-800 dark:text-white mb-4">
          NVC 能力雷达图
        </h3>
        {radar ? (
          <NvcRadarChart data={radar} size="md" />
        ) : (
          <div className="h-[300px] flex items-center justify-center text-slate-400">
            暂无数据
          </div>
        )}
      </motion.div>

      {/* 趋势图 */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
        className="bg-white dark:bg-slate-800 rounded-2xl p-6 border border-slate-200 dark:border-slate-700"
      >
        <div className="flex items-center gap-2 mb-4">
          <TrendingUp className="w-5 h-5 text-primary-500" />
          <h3 className="text-lg font-semibold text-slate-800 dark:text-white">
            能力趋势
          </h3>
        </div>
        <NvcAbilityTrendChart data={trends} />
      </motion.div>

      {/* 薄弱环节分析 */}
      {weakDimensions.length > 0 && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
          className="bg-gradient-to-r from-amber-50 to-orange-50 dark:from-amber-900/20 dark:to-orange-900/20 rounded-2xl p-6 border border-amber-100 dark:border-amber-900/30"
        >
          <div className="flex items-center gap-2 mb-3">
            <Target className="w-5 h-5 text-amber-500" />
            <h3 className="font-semibold text-slate-800 dark:text-white">
              薄弱环节
            </h3>
          </div>
          <p className="text-sm text-slate-600 dark:text-slate-400">
            你的 <span className="font-semibold text-amber-600">
              {weakDimensions.map((d) => d.label).join('、')}
            </span> 维度得分较低，建议针对性练习。
          </p>
        </motion.div>
      )}
    </div>
  );
}
