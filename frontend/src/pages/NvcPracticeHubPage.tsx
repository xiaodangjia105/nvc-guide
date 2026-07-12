import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  BookOpen, MessageSquare, Mic, Layers, ChevronRight, Loader2,
} from 'lucide-react';
import { useUserId } from '../hooks/useUserId';
import { practiceApi, voiceApi } from '../api/nvc';
import { ROUTES } from '../constants/routes';
import NvcProfileCard from '../components/nvc/NvcProfileCard';
import type { NvcPracticeMode, NvcDifficulty } from '../types/nvc';

interface ModeCard {
  mode: NvcPracticeMode;
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  subtitle: string;
  description: string;
  gradient: string;
}

const MODE_CARDS: ModeCard[] = [
  {
    mode: 'SCENARIO',
    icon: BookOpen,
    title: '场景驱动',
    subtitle: 'AI 生成场景',
    description: 'AI 生成真实场景，模拟对话练习 NVC 表达',
    gradient: 'from-blue-500 to-indigo-600',
  },
  {
    mode: 'FREE_DIALOG',
    icon: MessageSquare,
    title: '自由对话',
    subtitle: '描述真实困境',
    description: '描述你的真实沟通困境，AI 帮你用 NVC 重新组织表达',
    gradient: 'from-emerald-500 to-teal-600',
  },
  {
    mode: 'STRUCTURED_FOUR_STEP',
    icon: Layers,
    title: '结构化四步练习',
    subtitle: '观察→感受→需求→请求',
    description: '逐步练习 NVC 四要素，每步有专属教练指导',
    gradient: 'from-violet-500 to-purple-600',
  },
];

const DIFFICULTY_OPTIONS: { value: NvcDifficulty; label: string }[] = [
  { value: 'EASY', label: '简单' },
  { value: 'MEDIUM', label: '中等' },
  { value: 'HARD', label: '困难' },
];

export default function NvcPracticeHubPage() {
  const [userId] = useUserId();
  const navigate = useNavigate();
  const [loading, setLoading] = useState<string | null>(null);
  const [difficulty, setDifficulty] = useState<NvcDifficulty>('MEDIUM');

  const handleStartPractice = useCallback(async (mode: NvcPracticeMode) => {
    setLoading(mode);
    try {
      const session = await practiceApi.createSession(userId, {
        practiceMode: mode,
        difficulty,
      });
      navigate(ROUTES.nvcPractice(session.id));
    } catch (err) {
      console.error('Failed to create session:', err);
    } finally {
      setLoading(null);
    }
  }, [userId, difficulty, navigate]);

  const handleStartVoice = useCallback(async () => {
    setLoading('VOICE');
    try {
      const session = await voiceApi.createSession({
        userId,
        practiceMode: 'FREE_DIALOG',
        difficulty,
      });
      navigate(ROUTES.nvcVoice(session.id));
    } catch (err) {
      console.error('Failed to create voice session:', err);
    } finally {
      setLoading(null);
    }
  }, [userId, difficulty, navigate]);

  return (
    <div className="max-w-5xl mx-auto space-y-8">
      {/* 标题 */}
      <div>
        <h1 className="text-3xl font-bold text-slate-800 dark:text-white">
          NVC 练习中心
        </h1>
        <p className="text-slate-500 dark:text-slate-400 mt-2">
          选择练习模式，开始你的非暴力沟通之旅
        </p>
      </div>

      {/* 难度选择 */}
      <div className="flex items-center gap-3">
        <span className="text-sm text-slate-500 dark:text-slate-400">
          难度：
        </span>
        {DIFFICULTY_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            onClick={() => setDifficulty(opt.value)}
            className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors ${
              difficulty === opt.value
                ? 'bg-primary-500 text-white'
                : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-700'
            }`}
          >
            {opt.label}
          </button>
        ))}
      </div>

      {/* 三种模式卡片 */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {MODE_CARDS.map((card, idx) => {
          const Icon = card.icon;
          const isLoading = loading === card.mode;
          return (
            <motion.button
              key={card.mode}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: idx * 0.1 }}
              onClick={() => handleStartPractice(card.mode)}
              disabled={loading !== null}
              className="group relative bg-white dark:bg-slate-800 rounded-2xl p-6 border border-slate-200 dark:border-slate-700 text-left hover:shadow-lg hover:border-primary-300 dark:hover:border-primary-600 transition-all disabled:opacity-60"
            >
              <div className={`w-12 h-12 rounded-xl bg-gradient-to-br ${card.gradient} flex items-center justify-center mb-4`}>
                <Icon className="w-6 h-6 text-white" />
              </div>
              <h3 className="text-lg font-semibold text-slate-800 dark:text-white mb-1">
                {card.title}
              </h3>
              <p className="text-xs text-primary-500 dark:text-primary-400 mb-2">
                {card.subtitle}
              </p>
              <p className="text-sm text-slate-500 dark:text-slate-400 mb-4">
                {card.description}
              </p>
              <div className="flex items-center gap-1 text-primary-500 text-sm font-medium">
                {isLoading ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <>
                    开始练习
                    <ChevronRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
                  </>
                )}
              </div>
            </motion.button>
          );
        })}
      </div>

      {/* 语音练习入口 */}
      <motion.button
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.3 }}
        onClick={handleStartVoice}
        disabled={loading !== null}
        className="w-full bg-white dark:bg-slate-800 rounded-2xl p-5 border border-slate-200 dark:border-slate-700 flex items-center gap-4 hover:shadow-lg hover:border-primary-300 dark:hover:border-primary-600 transition-all disabled:opacity-60"
      >
        <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-rose-500 to-pink-600 flex items-center justify-center">
          <Mic className="w-6 h-6 text-white" />
        </div>
        <div className="flex-1 text-left">
          <h3 className="text-base font-semibold text-slate-800 dark:text-white">
            语音练习
          </h3>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            通过语音对话练习 NVC，支持实时语音识别和评估
          </p>
        </div>
        <div className="flex items-center gap-1 text-primary-500 text-sm font-medium">
          {loading === 'VOICE' ? (
            <Loader2 className="w-4 h-4 animate-spin" />
          ) : (
            <>
              开始语音练习
              <ChevronRight className="w-4 h-4" />
            </>
          )}
        </div>
      </motion.button>

      {/* 能力概览 */}
      <NvcProfileCard userId={userId} />
    </div>
  );
}
