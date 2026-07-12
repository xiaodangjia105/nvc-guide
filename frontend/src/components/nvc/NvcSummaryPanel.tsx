import { useEffect, useState } from 'react';
import { Eye, Heart, Target, Handshake, Lightbulb, Loader2 } from 'lucide-react';
import { practiceApi } from '../../api/nvc';

interface NvcSummary {
  observation: string | null;
  feeling: string | null;
  need: string | null;
  request: string | null;
  hint: string | null;
}

interface NvcSummaryPanelProps {
  sessionId: number;
  refreshTrigger?: number;
}

const ELEMENTS = [
  { key: 'observation' as const, label: '观察', icon: Eye, color: 'text-blue-500', bg: 'bg-blue-50 dark:bg-blue-900/20' },
  { key: 'feeling' as const, label: '感受', icon: Heart, color: 'text-pink-500', bg: 'bg-pink-50 dark:bg-pink-900/20' },
  { key: 'need' as const, label: '需求', icon: Target, color: 'text-amber-500', bg: 'bg-amber-50 dark:bg-amber-900/20' },
  { key: 'request' as const, label: '请求', icon: Handshake, color: 'text-emerald-500', bg: 'bg-emerald-50 dark:bg-emerald-900/20' },
];

export default function NvcSummaryPanel({ sessionId, refreshTrigger }: NvcSummaryPanelProps) {
  const [summary, setSummary] = useState<NvcSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    practiceApi.getSummary(sessionId)
      .then((data) => {
        if (data) {
          setSummary({
            observation: data.observation,
            feeling: data.feeling,
            need: data.need,
            request: data.request,
            hint: data.hint,
          });
        }
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [sessionId, refreshTrigger]);

  if (loading) {
    return (
      <div className="bg-white dark:bg-slate-800 rounded-xl p-4 border border-slate-200 dark:border-slate-700 flex items-center justify-center">
        <Loader2 className="w-5 h-5 animate-spin text-slate-400" />
      </div>
    );
  }

  return (
    <div className="bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 overflow-hidden">
      {/* 标题 */}
      <div className="px-4 py-3 border-b border-slate-100 dark:border-slate-700">
        <h3 className="text-sm font-semibold text-slate-800 dark:text-white">
          你的表达梳理
        </h3>
      </div>

      {/* 四要素 */}
      <div className="p-4 space-y-3">
        {ELEMENTS.map(({ key, label, icon: Icon, color, bg }) => {
          const value = summary?.[key];
          const hasValue = value && value.trim();

          return (
            <div key={key} className={`rounded-lg p-3 ${hasValue ? bg : 'bg-slate-50 dark:bg-slate-700/50'}`}>
              <div className="flex items-center gap-2 mb-1">
                <Icon className={`w-4 h-4 ${hasValue ? color : 'text-slate-400'}`} />
                <span className={`text-xs font-medium ${hasValue ? 'text-slate-800 dark:text-white' : 'text-slate-400'}`}>
                  {label}
                </span>
              </div>
              <p className={`text-sm leading-relaxed ${hasValue ? 'text-slate-700 dark:text-slate-300' : 'text-slate-400 italic'}`}>
                {hasValue ? value : '尚未表达'}
              </p>
            </div>
          );
        })}
      </div>

      {/* 提示 */}
      {summary?.hint && (
        <div className="px-4 py-3 border-t border-slate-100 dark:border-slate-700 bg-amber-50/50 dark:bg-amber-900/10">
          <div className="flex items-start gap-2">
            <Lightbulb className="w-4 h-4 text-amber-500 flex-shrink-0 mt-0.5" />
            <p className="text-xs text-amber-700 dark:text-amber-400 leading-relaxed">
              {summary.hint}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
