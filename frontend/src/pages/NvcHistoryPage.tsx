import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  BookOpen, Clock, Layers, Loader2, MessageSquare,
  Search, Trophy,
} from 'lucide-react';
import { useUserId } from '../hooks/useUserId';
import { practiceApi } from '../api/nvc';
import type { PracticeSession } from '../types/nvc';

const MODE_ICONS: Record<string, React.ComponentType<{ className?: string }>> = {
  SCENARIO: BookOpen,
  FREE_DIALOG: MessageSquare,
  STRUCTURED_FOUR_STEP: Layers,
};

const MODE_LABELS: Record<string, string> = {
  SCENARIO: '场景驱动',
  FREE_DIALOG: '自由对话',
  STRUCTURED_FOUR_STEP: '结构化四步',
};

const PHASE_LABELS: Record<string, string> = {
  CREATED: '已创建',
  IN_PROGRESS: '进行中',
  PAUSED: '已暂停',
  COMPLETED: '已完成',
  EVALUATED: '已评估',
};

export default function NvcHistoryPage() {
  const [userId] = useUserId();
  const navigate = useNavigate();
  const [sessions, setSessions] = useState<PracticeSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<string>('');

  useEffect(() => {
    practiceApi.getSessions(userId)
      .then(setSessions)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [userId]);

  const filtered = filter
    ? sessions.filter((s) => s.practiceMode === filter)
    : sessions;

  if (loading) {
    return (
      <div className="flex items-center justify-center h-[60vh]">
        <Loader2 className="w-8 h-8 animate-spin text-primary-500" />
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800 dark:text-white">
            练习历史
          </h1>
          <p className="text-slate-500 dark:text-slate-400 mt-1">
            共 {sessions.length} 次练习
          </p>
        </div>
      </div>

      {/* 筛选 */}
      <div className="flex items-center gap-2">
        <Search className="w-4 h-4 text-slate-400" />
        {['', 'SCENARIO', 'FREE_DIALOG', 'STRUCTURED_FOUR_STEP'].map((mode) => (
          <button
            key={mode}
            onClick={() => setFilter(mode)}
            className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
              filter === mode
                ? 'bg-primary-500 text-white'
                : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-700'
            }`}
          >
            {mode ? MODE_LABELS[mode] : '全部'}
          </button>
        ))}
      </div>

      {/* 会话列表 */}
      {filtered.length === 0 ? (
        <div className="text-center py-20">
          <Trophy className="w-12 h-12 text-slate-300 mx-auto mb-3" />
          <p className="text-slate-500">还没有练习记录</p>
          <button
            onClick={() => navigate('/nvc')}
            className="mt-4 text-primary-500 hover:underline"
          >
            开始第一次练习
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          {filtered.map((session, idx) => {
            const Icon = MODE_ICONS[session.practiceMode] || MessageSquare;
            return (
              <motion.button
                key={session.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: idx * 0.03 }}
                onClick={() => navigate(`/nvc/history/${session.id}/report`)}
                className="w-full bg-white dark:bg-slate-800 rounded-xl p-4 border border-slate-200 dark:border-slate-700 flex items-center gap-4 hover:shadow-md hover:border-primary-300 dark:hover:border-primary-600 transition-all text-left"
              >
                <div className="w-10 h-10 rounded-lg bg-primary-50 dark:bg-primary-900/30 flex items-center justify-center">
                  <Icon className="w-5 h-5 text-primary-500" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-slate-800 dark:text-white">
                      {MODE_LABELS[session.practiceMode]}
                    </span>
                    <span className={`px-2 py-0.5 rounded-full text-xs ${
                      session.currentPhase === 'EVALUATED'
                        ? 'bg-emerald-100 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-400'
                        : session.currentPhase === 'COMPLETED'
                          ? 'bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400'
                          : 'bg-slate-100 text-slate-500 dark:bg-slate-800'
                    }`}>
                      {PHASE_LABELS[session.currentPhase] || session.currentPhase}
                    </span>
                  </div>
                  <div className="flex items-center gap-2 text-sm text-slate-500 mt-1">
                    <Clock className="w-3.5 h-3.5" />
                    <span>
                      {session.createdAt
                        ? new Date(session.createdAt).toLocaleString('zh-CN')
                        : '-'}
                    </span>
                  </div>
                </div>
                <div className="text-slate-400">
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                  </svg>
                </div>
              </motion.button>
            );
          })}
        </div>
      )}
    </div>
  );
}
