
import { useEffect, useState } from 'react';
import { BarChart3, Clock, Trophy } from 'lucide-react';
import { profileApi } from '../../api/nvc';
import type { UserProfile } from '../../types/nvc';

interface NvcProfileCardProps {
  userId: number;
}

const LEVEL_LABELS: Record<string, string> = {
  BEGINNER: '初学者',
  INTERMEDIATE: '进阶者',
  ADVANCED: '高级者',
};

export default function NvcProfileCard({ userId }: NvcProfileCardProps) {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    profileApi.getProfile(userId)
      .then(setProfile)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [userId]);

  if (loading) {
    return (
      <div className="bg-white dark:bg-slate-800 rounded-2xl p-6 border border-slate-200 dark:border-slate-700 animate-pulse">
        <div className="h-4 bg-slate-200 dark:bg-slate-700 rounded w-1/3 mb-4" />
        <div className="space-y-3">
          <div className="h-3 bg-slate-200 dark:bg-slate-700 rounded" />
          <div className="h-3 bg-slate-200 dark:bg-slate-700 rounded w-2/3" />
        </div>
      </div>
    );
  }

  if (!profile) return null;

  const radar = profile.abilityRadar;
  const dimensions = [
    { label: '观察', value: radar.observation },
    { label: '感受', value: radar.feeling },
    { label: '需求', value: radar.need },
    { label: '请求', value: radar.request },
    { label: '共情', value: radar.empathy },
  ];

  return (
    <div className="bg-white dark:bg-slate-800 rounded-2xl p-6 border border-slate-200 dark:border-slate-700">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold text-slate-800 dark:text-white">
          我的能力
        </h3>
        <span className="px-3 py-1 rounded-full text-xs font-medium bg-primary-100 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400">
          {LEVEL_LABELS[radar.level] || radar.level}
        </span>
      </div>

      {/* 能力条 */}
      <div className="space-y-3 mb-5">
        {dimensions.map((dim) => (
          <div key={dim.label} className="flex items-center gap-3">
            <span className="text-sm text-slate-500 dark:text-slate-400 w-10">
              {dim.label}
            </span>
            <div className="flex-1 h-2 bg-slate-100 dark:bg-slate-700 rounded-full overflow-hidden">
              <div
                className="h-full bg-primary-500 rounded-full transition-all duration-500"
                style={{ width: `${dim.value}%` }}
              />
            </div>
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300 w-8 text-right">
              {dim.value}
            </span>
          </div>
        ))}
      </div>

      {/* 统计 */}
      <div className="flex items-center gap-4 pt-4 border-t border-slate-100 dark:border-slate-700">
        <div className="flex items-center gap-1.5 text-slate-500 dark:text-slate-400">
          <Trophy className="w-4 h-4" />
          <span className="text-sm">
            已练习 {profile.totalPracticeCount} 次
          </span>
        </div>
        <div className="flex items-center gap-1.5 text-slate-500 dark:text-slate-400">
          <Clock className="w-4 h-4" />
          <span className="text-sm">
            累计 {(profile.totalPracticeMinutes / 60).toFixed(1)} 小时
          </span>
        </div>
        <div className="flex items-center gap-1.5 text-slate-500 dark:text-slate-400">
          <BarChart3 className="w-4 h-4" />
          <span className="text-sm">
            综合 {radar.overall}
          </span>
        </div>
      </div>
    </div>
  );
}
