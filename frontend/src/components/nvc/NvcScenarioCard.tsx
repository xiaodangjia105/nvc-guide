import { BookOpen, Star, Users } from 'lucide-react';
import type { NvcScenario } from '../../types/nvc';

interface NvcScenarioCardProps {
  scenario: NvcScenario;
  onClick: () => void;
}

const TYPE_LABELS: Record<string, string> = {
  WORKPLACE: '职场',
  FAMILY: '家庭',
  INTIMATE: '亲密关系',
  SOCIAL: '社交',
  SELF: '自我对话',
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

export default function NvcScenarioCard({
  scenario,
  onClick,
}: NvcScenarioCardProps) {
  return (
    <button
      onClick={onClick}
      className="group bg-white dark:bg-slate-800 rounded-xl p-5 border border-slate-200 dark:border-slate-700 text-left hover:shadow-md hover:border-primary-300 dark:hover:border-primary-600 transition-all"
    >
      <div className="flex items-start gap-3 mb-3">
        <div className="w-10 h-10 rounded-lg bg-primary-50 dark:bg-primary-900/30 flex items-center justify-center flex-shrink-0">
          <BookOpen className="w-5 h-5 text-primary-500" />
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="font-semibold text-slate-800 dark:text-white truncate">
            {scenario.title}
          </h3>
          <div className="flex items-center gap-2 mt-1">
            <span className="text-xs text-slate-500">
              {TYPE_LABELS[scenario.scenarioType] || scenario.scenarioType}
            </span>
            <span className={`px-2 py-0.5 rounded-full text-xs ${DIFFICULTY_COLORS[scenario.difficulty] || ''}`}>
              {DIFFICULTY_LABELS[scenario.difficulty] || scenario.difficulty}
            </span>
          </div>
        </div>
      </div>

      <p className="text-sm text-slate-500 dark:text-slate-400 line-clamp-3 mb-3">
        {scenario.description}
      </p>

      <div className="flex items-center gap-3 text-xs text-slate-400">
        <div className="flex items-center gap-1">
          <Users className="w-3.5 h-3.5" />
          <span>{scenario.usageCount} 次使用</span>
        </div>
        {scenario.isSystem && (
          <div className="flex items-center gap-1">
            <Star className="w-3.5 h-3.5 text-amber-400" />
            <span>系统推荐</span>
          </div>
        )}
      </div>

      {scenario.tags && (
        <div className="flex flex-wrap gap-1 mt-3">
          {scenario.tags.split(',').map((tag) => (
            <span
              key={tag}
              className="px-2 py-0.5 rounded-full text-xs bg-slate-100 dark:bg-slate-700 text-slate-500"
            >
              {tag.trim()}
            </span>
          ))}
        </div>
      )}
    </button>
  );
}
