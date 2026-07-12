import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowLeft, Download, Lightbulb, Loader2,
  Sparkles, Star, Target,
} from 'lucide-react';
import { reportApi } from '../api/nvc';
import NvcRadarChart from '../components/nvc/NvcRadarChart';
import type { NvcPracticeReport } from '../types/nvc';

const MODE_LABELS: Record<string, string> = {
  SCENARIO: '场景驱动',
  FREE_DIALOG: '自由对话',
  STRUCTURED_FOUR_STEP: '结构化四步',
};

function ScoreCard({
  label,
  score,
  detail,
  color,
}: {
  label: string;
  score: number;
  detail: string;
  color: string;
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="bg-white dark:bg-slate-800 rounded-xl p-4 border border-slate-200 dark:border-slate-700">
      <div className="flex items-center justify-between mb-2">
        <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
          {label}
        </span>
        <span className={`text-2xl font-bold ${color}`}>{score}</span>
      </div>
      <div className="h-2 bg-slate-100 dark:bg-slate-700 rounded-full overflow-hidden mb-2">
        <div
          className={`h-full rounded-full ${
            score >= 80 ? 'bg-emerald-500'
              : score >= 60 ? 'bg-amber-500'
              : 'bg-red-500'
          }`}
          style={{ width: `${score}%` }}
        />
      </div>
      {detail && (
        <>
          <button
            onClick={() => setExpanded(!expanded)}
            className="text-xs text-primary-500 hover:underline"
          >
            {expanded ? '收起详情' : '查看详情'}
          </button>
          {expanded && (
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-2 leading-relaxed">
              {detail}
            </p>
          )}
        </>
      )}
    </div>
  );
}

export default function NvcReportPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const sid = Number(sessionId);

  const [report, setReport] = useState<NvcPracticeReport | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!sid) return;
    reportApi.getReport(sid)
      .then(setReport)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [sid]);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-[60vh]">
        <Loader2 className="w-8 h-8 animate-spin text-primary-500" />
      </div>
    );
  }

  if (!report) {
    return (
      <div className="text-center py-20">
        <p className="text-slate-500">报告不存在</p>
        <button
          onClick={() => navigate('/nvc/history')}
          className="mt-4 text-primary-500 hover:underline"
        >
          返回练习历史
        </button>
      </div>
    );
  }

  const radarData = {
    observation: report.observationScore,
    feeling: report.feelingScore,
    need: report.needScore,
    request: report.requestScore,
    empathy: report.empathyScore,
  };

  const dimensions = [
    { label: '观察', score: report.observationScore, detail: report.observationDetail },
    { label: '感受', score: report.feelingScore, detail: report.feelingDetail },
    { label: '需求', score: report.needScore, detail: report.needDetail },
    { label: '请求', score: report.requestScore, detail: report.requestDetail },
    { label: '共情', score: report.empathyScore, detail: report.empathyDetail },
  ];

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* 顶部 */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate('/nvc/history')}
            className="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          >
            <ArrowLeft className="w-5 h-5 text-slate-500" />
          </button>
          <div>
            <h1 className="text-2xl font-bold text-slate-800 dark:text-white">
              练习报告
            </h1>
            <p className="text-sm text-slate-500">
              {MODE_LABELS[report.practiceMode]} · 共 {report.totalRounds} 轮对话
            </p>
          </div>
        </div>
        <a
          href={reportApi.downloadPdfUrl(sid)}
          className="flex items-center gap-2 px-4 py-2 bg-primary-500 text-white rounded-xl hover:bg-primary-600 transition-colors"
        >
          <Download className="w-4 h-4" />
          下载 PDF
        </a>
      </div>

      {/* 综合分数 + 雷达图 */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="bg-white dark:bg-slate-800 rounded-2xl p-6 border border-slate-200 dark:border-slate-700"
      >
        <div className="flex items-center gap-8">
          <div className="text-center">
            <div className="text-5xl font-bold text-primary-500">
              {report.overallScore}
            </div>
            <p className="text-sm text-slate-500 mt-1">综合得分</p>
          </div>
          <div className="flex-1">
            <NvcRadarChart data={radarData} size="sm" />
          </div>
        </div>
      </motion.div>

      {/* 各维度详情 */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {dimensions.map((dim) => (
          <ScoreCard
            key={dim.label}
            label={dim.label}
            score={dim.score}
            detail={dim.detail}
            color={dim.score >= 80 ? 'text-emerald-500'
              : dim.score >= 60 ? 'text-amber-500'
              : 'text-red-500'}
          />
        ))}
      </div>

      {/* 综合分析 */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-white dark:bg-slate-800 rounded-xl p-5 border border-slate-200 dark:border-slate-700">
          <div className="flex items-center gap-2 mb-3">
            <Star className="w-5 h-5 text-amber-500" />
            <h3 className="font-semibold text-slate-800 dark:text-white">优势</h3>
          </div>
          <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
            {report.strengths}
          </p>
        </div>

        <div className="bg-white dark:bg-slate-800 rounded-xl p-5 border border-slate-200 dark:border-slate-700">
          <div className="flex items-center gap-2 mb-3">
            <Target className="w-5 h-5 text-blue-500" />
            <h3 className="font-semibold text-slate-800 dark:text-white">改进方向</h3>
          </div>
          <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
            {report.improvements}
          </p>
        </div>

        <div className="bg-white dark:bg-slate-800 rounded-xl p-5 border border-slate-200 dark:border-slate-700">
          <div className="flex items-center gap-2 mb-3">
            <Lightbulb className="w-5 h-5 text-emerald-500" />
            <h3 className="font-semibold text-slate-800 dark:text-white">参考表达</h3>
          </div>
          <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed whitespace-pre-line">
            {report.referenceExpressions}
          </p>
        </div>
      </div>

      {/* 总结 */}
      <div className="bg-gradient-to-r from-primary-50 to-indigo-50 dark:from-primary-900/20 dark:to-indigo-900/20 rounded-2xl p-6 border border-primary-100 dark:border-primary-900/30">
        <div className="flex items-center gap-2 mb-3">
          <Sparkles className="w-5 h-5 text-primary-500" />
          <h3 className="font-semibold text-slate-800 dark:text-white">总结</h3>
        </div>
        <p className="text-sm text-slate-700 dark:text-slate-300 leading-relaxed">
          {report.summary}
        </p>
      </div>
    </div>
  );
}
