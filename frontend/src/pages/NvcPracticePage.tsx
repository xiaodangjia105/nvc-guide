import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowLeft, CheckCircle, Clock, Loader2, StopCircle, BookOpen,
} from 'lucide-react';
import { practiceApi } from '../api/nvc';
import NvcChatPanel from '../components/nvc/NvcChatPanel';
import NvcSummaryPanel from '../components/nvc/NvcSummaryPanel';
import NvcStepIndicator from '../components/nvc/NvcStepIndicator';
import type {
  PracticeSession, StepProgress,
} from '../types/nvc';

const MODE_LABELS: Record<string, string> = {
  SCENARIO: '场景驱动',
  FREE_DIALOG: '自由对话',
  STRUCTURED_FOUR_STEP: '结构化四步',
};

export default function NvcPracticePage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const sid = Number(sessionId);

  const [session, setSession] = useState<PracticeSession | null>(null);
  const [stepProgress, setStepProgress] = useState<StepProgress | null>(null);
  const [loading, setLoading] = useState(true);
  const [completing, setCompleting] = useState(false);
  const [completingMessage, setCompletingMessage] = useState('');
  const [summaryRefreshTrigger, setSummaryRefreshTrigger] = useState(0);

  const isStructured = session?.practiceMode === 'STRUCTURED_FOUR_STEP';

  // 加载会话信息
  useEffect(() => {
    if (!sid) return;
    Promise.all([
      practiceApi.getSession(sid),
      isStructured ? practiceApi.getStepProgress(sid).catch(() => null) : Promise.resolve(null),
    ])
      .then(([s, sp]) => {
        setSession(s);
        setStepProgress(sp);
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [sid, isStructured]);

  const handleMessageSent = useCallback(() => {
    // 延迟刷新摘要，等待后台 LLM 分析完成
    setTimeout(() => {
      setSummaryRefreshTrigger((prev) => prev + 1);
    }, 2000);
  }, []);

  const handleAdvanceStep = useCallback(async () => {
    if (!sid) return;
    try {
      const progress = await practiceApi.advanceStep(sid);
      setStepProgress(progress);
    } catch (err) {
      console.error('Failed to advance step:', err);
    }
  }, [sid]);

  const handleResetStep = useCallback(async () => {
    if (!sid) return;
    try {
      const progress = await practiceApi.resetStep(sid);
      setStepProgress(progress);
    } catch (err) {
      console.error('Failed to reset step:', err);
    }
  }, [sid]);

  const handleComplete = useCallback(async () => {
    if (!sid) return;
    setCompleting(true);
    setCompletingMessage('正在生成评估报告...');
    try {
      await practiceApi.completeSession(sid);
      navigate(`/nvc/history/${sid}/report`);
    } catch (err) {
      console.error('Failed to complete session:', err);
      setCompletingMessage('');
    } finally {
      setCompleting(false);
    }
  }, [sid, navigate]);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-[60vh]">
        <Loader2 className="w-8 h-8 animate-spin text-primary-500" />
      </div>
    );
  }

  if (!session) {
    return (
      <div className="text-center py-20">
        <p className="text-slate-500">会话不存在</p>
        <button
          onClick={() => navigate('/nvc')}
          className="mt-4 text-primary-500 hover:underline"
        >
          返回练习中心
        </button>
      </div>
    );
  }

  const isCompleted = session.currentPhase === 'COMPLETED'
    || session.currentPhase === 'EVALUATED';

  return (
    <div className="h-[calc(100vh-8rem)] flex flex-col">
      {/* 顶部栏 */}
      <motion.div
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex items-center justify-between mb-4 flex-shrink-0"
      >
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate('/nvc')}
            className="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          >
            <ArrowLeft className="w-5 h-5 text-slate-500" />
          </button>
          <div>
            <h2 className="text-lg font-semibold text-slate-800 dark:text-white">
              {MODE_LABELS[session.practiceMode] || session.practiceMode}
            </h2>
            <div className="flex items-center gap-2 text-sm text-slate-500">
              <Clock className="w-3.5 h-3.5" />
              <span>
                {session.createdAt
                  ? new Date(session.createdAt).toLocaleString('zh-CN')
                  : '-'}
              </span>
              {session.difficulty && (
                <span className="px-2 py-0.5 rounded-full text-xs bg-slate-100 dark:bg-slate-800">
                  {session.difficulty === 'EASY' ? '简单'
                    : session.difficulty === 'MEDIUM' ? '中等'
                    : '困难'}
                </span>
              )}
            </div>
          </div>
        </div>

        {!isCompleted && (
          <button
            onClick={handleComplete}
            disabled={completing}
            className="flex items-center gap-2 px-4 py-2 bg-slate-800 dark:bg-slate-700 text-white rounded-xl hover:bg-slate-700 dark:hover:bg-slate-600 transition-colors disabled:opacity-50"
          >
            {completing ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <StopCircle className="w-4 h-4" />
            )}
            {completing ? completingMessage || '结束练习' : '结束练习'}
          </button>
        )}

        {isCompleted && (
          <div className="flex items-center gap-2 text-emerald-500">
            <CheckCircle className="w-5 h-5" />
            <span className="font-medium">已完成</span>
          </div>
        )}
      </motion.div>

      {/* 场景信息卡片（场景驱动模式） */}
      {session.practiceMode === 'SCENARIO' && session.scenarioTitle && (
        <motion.div
          initial={{ opacity: 0, y: -5 }}
          animate={{ opacity: 1, y: 0 }}
          className="flex-shrink-0 bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-900/20 dark:to-indigo-900/20 rounded-xl p-4 border border-blue-100 dark:border-blue-900/30"
        >
          <div className="flex items-start gap-3">
            <div className="w-8 h-8 rounded-lg bg-blue-500 flex items-center justify-center flex-shrink-0">
              <BookOpen className="w-4 h-4 text-white" />
            </div>
            <div className="flex-1 min-w-0">
              <h3 className="text-sm font-semibold text-slate-800 dark:text-white">
                {session.scenarioTitle}
              </h3>
              <p className="text-xs text-slate-600 dark:text-slate-400 mt-1 leading-relaxed">
                {session.scenarioDescription}
              </p>
            </div>
          </div>
        </motion.div>
      )}

      {/* 主体区域 */}
      <div className="flex-1 flex gap-4 min-h-0">
        {/* 对话面板 */}
        <div className="flex-1 bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 overflow-hidden">
          <NvcChatPanel
            sessionId={sid}
            practiceMode={session.practiceMode}
            onMessageSent={handleMessageSent}
          />
        </div>

        {/* 右侧面板 */}
        <div className="w-80 flex flex-col gap-4 flex-shrink-0">
          {/* 步骤指示器（结构化模式） */}
          {isStructured && (
            <NvcStepIndicator
              progress={stepProgress}
              onAdvance={handleAdvanceStep}
              onReset={handleResetStep}
            />
          )}

          {/* NVC 四要素摘要 */}
          <NvcSummaryPanel
            sessionId={sid}
            refreshTrigger={summaryRefreshTrigger}
          />
        </div>
      </div>
    </div>
  );
}
