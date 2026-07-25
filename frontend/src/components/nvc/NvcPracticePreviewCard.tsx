import { useNavigate } from 'react-router-dom';
import { Play, BookOpen, Layers } from 'lucide-react';

interface PracticePreviewData {
  sessionId: number;
  scenarioTitle: string;
  scenarioDescription?: string;
  practiceMode: string;
  difficulty?: string;
}

interface NvcPracticePreviewCardProps {
  data: PracticePreviewData;
}

const MODE_LABELS: Record<string, string> = {
  SCENARIO: '场景驱动',
  FREE_DIALOG: '自由对话',
  STRUCTURED_FOUR_STEP: '结构化四步',
};

const DIFFICULTY_LABELS: Record<string, string> = {
  EASY: '简单',
  MEDIUM: '中等',
  HARD: '困难',
};

const DIFFICULTY_COLORS: Record<string, string> = {
  EASY: 'bg-emerald-100 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-400',
  MEDIUM: 'bg-amber-100 text-amber-600 dark:bg-amber-900/30 dark:text-amber-400',
  HARD: 'bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400',
};

export default function NvcPracticePreviewCard({ data }: NvcPracticePreviewCardProps) {
  const navigate = useNavigate();

  const handleStart = () => {
    navigate(`/nvc/practice/${data.sessionId}`);
  };

  return (
    <div className="rounded-xl border border-indigo-200 dark:border-indigo-800 bg-gradient-to-br from-indigo-50 to-blue-50 dark:from-indigo-900/20 dark:to-blue-900/20 p-4 max-w-sm">
      {/* 标题 */}
      <div className="flex items-center gap-2 mb-3">
        <div className="w-8 h-8 rounded-lg bg-indigo-500 flex items-center justify-center">
          <BookOpen className="w-4 h-4 text-white" />
        </div>
        <span className="text-sm font-semibold text-indigo-700 dark:text-indigo-300">
          练习场景预览
        </span>
      </div>

      {/* 场景信息 */}
      <div className="space-y-2 mb-4">
        <div className="flex items-start gap-2">
          <BookOpen className="w-4 h-4 text-slate-400 mt-0.5 flex-shrink-0" />
          <div>
            <span className="text-xs text-slate-500 dark:text-slate-400">场景</span>
            <p className="text-sm font-medium text-slate-800 dark:text-slate-200">
              {data.scenarioTitle}
            </p>
          </div>
        </div>

        {data.scenarioDescription && (
          <p className="text-xs text-slate-500 dark:text-slate-400 leading-relaxed pl-6">
            {data.scenarioDescription}
          </p>
        )}

        <div className="flex items-center gap-3 pl-6">
          <div className="flex items-center gap-1.5">
            <Layers className="w-3.5 h-3.5 text-slate-400" />
            <span className="text-xs text-slate-600 dark:text-slate-400">
              {MODE_LABELS[data.practiceMode] || data.practiceMode}
            </span>
          </div>
          {data.difficulty && (
            <span className={`px-2 py-0.5 rounded-full text-xs ${DIFFICULTY_COLORS[data.difficulty] || 'bg-slate-100 text-slate-600'}`}>
              {DIFFICULTY_LABELS[data.difficulty] || data.difficulty}
            </span>
          )}
        </div>
      </div>

      {/* 开始按钮 */}
      <button
        onClick={handleStart}
        className="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-indigo-500 text-white rounded-xl hover:bg-indigo-600 transition-colors text-sm font-medium"
      >
        <Play className="w-4 h-4" />
        开始练习
      </button>
    </div>
  );
}
