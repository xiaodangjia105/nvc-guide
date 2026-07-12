import { useState } from 'react';
import { ChevronDown, ChevronUp, Star } from 'lucide-react';
import type { EvaluationCardData } from '../../types/nvc';

interface NvcEvaluationCardProps {
  data: EvaluationCardData;
}

const DIMENSION_LABELS: Record<string, string> = {
  observation: '观察',
  feeling: '感受',
  need: '需求',
  request: '请求',
  empathy: '共情',
};

function ScoreBar({
  label,
  score,
  passed,
  detail,
}: {
  label: string;
  score: number;
  passed: boolean;
  detail: string;
}) {
  const [expanded, setExpanded] = useState(false);

  const barColor = score >= 80
    ? 'bg-emerald-500'
    : score >= 60
      ? 'bg-amber-500'
      : 'bg-red-500';

  return (
    <div className="py-2">
      <div className="flex items-center gap-2 mb-1">
        <span className="text-sm font-medium text-slate-700 dark:text-slate-300 w-10">
          {label}
        </span>
        <div className="flex-1 h-2 bg-slate-100 dark:bg-slate-700 rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full transition-all duration-500 ${barColor}`}
            style={{ width: `${score}%` }}
          />
        </div>
        <span className="text-sm font-semibold text-slate-800 dark:text-white w-8 text-right">
          {score}
        </span>
        {passed && (
          <Star className="w-3.5 h-3.5 text-amber-400 fill-amber-400" />
        )}
      </div>
      {detail && (
        <button
          onClick={() => setExpanded(!expanded)}
          className="flex items-center gap-1 text-xs text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 mt-1"
        >
          {expanded ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
          详情
        </button>
      )}
      {expanded && detail && (
        <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 pl-12 leading-relaxed">
          {detail}
        </p>
      )}
    </div>
  );
}

export default function NvcEvaluationCard({ data }: NvcEvaluationCardProps) {
  const dimensions = [
    { key: 'observation', ...data.observation },
    { key: 'feeling', ...data.feeling },
    { key: 'need', ...data.need },
    { key: 'request', ...data.request },
    { key: 'empathy', ...data.empathy },
  ];

  return (
    <div className="bg-white dark:bg-slate-800 rounded-xl p-4 border border-slate-200 dark:border-slate-700">
      <div className="flex items-center justify-between mb-3">
        <h4 className="text-sm font-semibold text-slate-700 dark:text-slate-300">
          实时评估
        </h4>
        <div className="flex items-center gap-1.5">
          <span className="text-xs text-slate-500">综合</span>
          <span className="text-lg font-bold text-primary-500">
            {data.overall}
          </span>
        </div>
      </div>

      <div className="divide-y divide-slate-100 dark:divide-slate-700">
        {dimensions.map((dim) => (
          <ScoreBar
            key={dim.key}
            label={DIMENSION_LABELS[dim.key] || dim.key}
            score={dim.score}
            passed={dim.passed}
            detail={dim.detail}
          />
        ))}
      </div>
    </div>
  );
}
