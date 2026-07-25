import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronDown, Wrench, CheckCircle, XCircle, Clock } from 'lucide-react';
import type { ToolCallRecord } from '../../api/nvc-assistant';

interface NvcToolCallCardProps {
  toolCall: ToolCallRecord;
}

/** 工具名称中文映射 */
const TOOL_LABELS: Record<string, string> = {
  query_practice_data: '查询练习数据',
  search_knowledge: '搜索知识库',
  create_practice_session: '创建练习会话',
  get_user_profile: '获取用户档案',
  analyze_communication: '分析沟通内容',
  search_scenarios: '搜索练习场景',
  get_practice_report: '获取练习报告',
  recommend_practice: '推荐练习方案',
};

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

function formatJson(str: string): string {
  try {
    const obj = JSON.parse(str);
    return JSON.stringify(obj, null, 2);
  } catch {
    return str;
  }
}

export default function NvcToolCallCard({ toolCall }: NvcToolCallCardProps) {
  const [expanded, setExpanded] = useState(false);

  const label = TOOL_LABELS[toolCall.toolName] || toolCall.toolName;
  const isError = !toolCall.success;

  return (
    <div
      className={`rounded-xl border transition-colors ${
        isError
          ? 'border-red-200 dark:border-red-800 bg-red-50/50 dark:bg-red-900/10'
          : 'border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50'
      }`}
    >
      {/* 头部 — 始终可见 */}
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-slate-100/50 dark:hover:bg-slate-700/30 transition-colors rounded-xl"
      >
        <div className={`w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 ${
          isError
            ? 'bg-red-100 dark:bg-red-900/30 text-red-500'
            : 'bg-blue-100 dark:bg-blue-900/30 text-blue-500'
        }`}>
          <Wrench className="w-4 h-4" />
        </div>
        <div className="flex-1 min-w-0">
          <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
            {label}
          </span>
          <span className="text-xs text-slate-400 dark:text-slate-500 ml-2">
            {toolCall.toolName}
          </span>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          {isError ? (
            <XCircle className="w-4 h-4 text-red-400" />
          ) : (
            <CheckCircle className="w-4 h-4 text-emerald-400" />
          )}
          <span className="flex items-center gap-1 text-xs text-slate-400">
            <Clock className="w-3 h-3" />
            {formatDuration(toolCall.durationMs)}
          </span>
          <motion.div
            animate={{ rotate: expanded ? 180 : 0 }}
            transition={{ duration: 0.2 }}
          >
            <ChevronDown className="w-4 h-4 text-slate-400" />
          </motion.div>
        </div>
      </button>

      {/* 展开内容 */}
      <AnimatePresence initial={false}>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2, ease: 'easeInOut' }}
            className="overflow-hidden"
          >
            <div className="px-4 pb-3 space-y-3 border-t border-slate-200 dark:border-slate-700 pt-3">
              {/* 参数 */}
              <div>
                <div className="text-xs font-semibold text-slate-500 dark:text-slate-400 mb-1">
                  参数
                </div>
                <pre className="text-xs text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-900 rounded-lg p-3 overflow-x-auto max-h-40 overflow-y-auto">
                  {formatJson(toolCall.arguments)}
                </pre>
              </div>
              {/* 结果 */}
              <div>
                <div className="text-xs font-semibold text-slate-500 dark:text-slate-400 mb-1">
                  结果
                </div>
                <pre className={`text-xs rounded-lg p-3 overflow-x-auto max-h-48 overflow-y-auto ${
                  isError
                    ? 'text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20'
                    : 'text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-900'
                }`}>
                  {formatJson(toolCall.result)}
                </pre>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
