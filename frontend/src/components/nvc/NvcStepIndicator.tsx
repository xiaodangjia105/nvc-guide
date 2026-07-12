import { CheckCircle2 } from 'lucide-react';
import type { StepProgress, NvcPracticeStep } from '../../types/nvc';

interface NvcStepIndicatorProps {
  progress: StepProgress | null;
  onAdvance?: () => void;
  onReset?: () => void;
}

const STEP_ORDER: NvcPracticeStep[] = [
  'OBSERVE', 'FEELING', 'NEED', 'REQUEST',
];

const STEP_LABELS: Record<NvcPracticeStep, string> = {
  OBSERVE: '观察',
  FEELING: '感受',
  NEED: '需求',
  REQUEST: '请求',
  COMPLETED: '完成',
};

export default function NvcStepIndicator({
  progress,
  onAdvance,
  onReset,
}: NvcStepIndicatorProps) {
  if (!progress) return null;

  const currentIndex = progress.stepIndex;
  const isCompleted = progress.currentStep === 'COMPLETED';

  return (
    <div className="bg-white dark:bg-slate-800 rounded-xl p-4 border border-slate-200 dark:border-slate-700">
      <div className="flex items-center justify-between mb-3">
        <h4 className="text-sm font-semibold text-slate-700 dark:text-slate-300">
          四步练习进度
        </h4>
        {isCompleted && (
          <span className="text-xs font-medium text-emerald-500 bg-emerald-50 dark:bg-emerald-900/20 px-2 py-0.5 rounded-full">
            已完成
          </span>
        )}
      </div>

      {/* 步骤条 */}
      <div className="flex items-center gap-2 mb-3">
        {STEP_ORDER.map((step, idx) => {
          const isDone = isCompleted || idx < currentIndex;
          const isCurrent = !isCompleted && idx === currentIndex;

          return (
            <div key={step} className="flex items-center gap-2 flex-1">
              <div className="flex flex-col items-center flex-1">
                <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold ${
                  isDone
                    ? 'bg-emerald-500 text-white'
                    : isCurrent
                      ? 'bg-primary-500 text-white'
                      : 'bg-slate-200 dark:bg-slate-700 text-slate-500'
                }`}>
                  {isDone ? (
                    <CheckCircle2 className="w-5 h-5" />
                  ) : (
                    idx + 1
                  )}
                </div>
                <span className={`text-xs mt-1 ${
                  isCurrent
                    ? 'text-primary-500 font-semibold'
                    : isDone
                      ? 'text-emerald-500'
                      : 'text-slate-400'
                }`}>
                  {STEP_LABELS[step]}
                </span>
              </div>
              {idx < STEP_ORDER.length - 1 && (
                <div className={`h-0.5 flex-1 rounded ${
                  isDone ? 'bg-emerald-500' : 'bg-slate-200 dark:bg-slate-700'
                }`} />
              )}
            </div>
          );
        })}
      </div>

      {/* 当前步骤信息 */}
      {!isCompleted && (
        <div className="bg-slate-50 dark:bg-slate-900 rounded-lg p-3">
          <p className="text-sm text-slate-600 dark:text-slate-400">
            {progress.stepDescription}
          </p>
          {progress.currentScore !== null && (
            <div className="flex items-center gap-2 mt-2">
              <span className="text-xs text-slate-500">当前分数：</span>
              <span className={`text-sm font-semibold ${
                progress.canAdvance ? 'text-emerald-500' : 'text-amber-500'
              }`}>
                {progress.currentScore}
              </span>
              {progress.canAdvance && onAdvance && (
                <button
                  onClick={onAdvance}
                  className="ml-auto text-xs px-3 py-1 bg-primary-500 text-white rounded-full hover:bg-primary-600 transition-colors"
                >
                  进入下一步
                </button>
              )}
            </div>
          )}
        </div>
      )}

      {/* 重置按钮 */}
      {onReset && (
        <button
          onClick={onReset}
          className="mt-3 text-xs text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
        >
          重新开始
        </button>
      )}
    </div>
  );
}
