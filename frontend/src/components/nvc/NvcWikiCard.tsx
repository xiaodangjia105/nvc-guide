import { FileText, Calendar, Tag } from 'lucide-react';
import type { WikiResponse, WikiCategory, WikiSourceType } from '../../api/wiki';
import { WIKI_CATEGORY_LABELS, WIKI_SOURCE_TYPE_LABELS } from '../../api/wiki';

interface NvcWikiCardProps {
  wiki: WikiResponse;
  onClick: () => void;
}

const CATEGORY_COLORS: Record<WikiCategory, string> = {
  CONVERSATION_CASE: 'bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400',
  REAL_SCENARIO: 'bg-emerald-100 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-400',
  LEARNING_SUMMARY: 'bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400',
  BOOK_KNOWLEDGE: 'bg-amber-100 text-amber-600 dark:bg-amber-900/30 dark:text-amber-400',
  OTHER: 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-400',
};

const SOURCE_TYPE_COLORS: Record<WikiSourceType, string> = {
  AUTO_GENERATED: 'bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400',
  MANUAL: 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-400',
  AI_ASSISTED: 'bg-indigo-100 text-indigo-600 dark:bg-indigo-900/30 dark:text-indigo-400',
};

export default function NvcWikiCard({ wiki, onClick }: NvcWikiCardProps) {
  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  };

  return (
    <button
      onClick={onClick}
      className="group bg-white dark:bg-slate-800 rounded-xl p-5 border border-slate-200 dark:border-slate-700 text-left hover:shadow-md hover:border-primary-300 dark:hover:border-primary-600 transition-all w-full"
    >
      {/* 头部：图标 + 标题 */}
      <div className="flex items-start gap-3 mb-3">
        <div className="w-10 h-10 rounded-lg bg-primary-50 dark:bg-primary-900/30 flex items-center justify-center flex-shrink-0">
          <FileText className="w-5 h-5 text-primary-500" />
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="font-semibold text-slate-800 dark:text-white truncate">
            {wiki.title}
          </h3>
          <div className="flex items-center gap-2 mt-1 flex-wrap">
            <span className={`px-2 py-0.5 rounded-full text-xs ${CATEGORY_COLORS[wiki.category] || ''}`}>
              {WIKI_CATEGORY_LABELS[wiki.category] || wiki.category}
            </span>
            <span className={`px-2 py-0.5 rounded-full text-xs ${SOURCE_TYPE_COLORS[wiki.sourceType] || ''}`}>
              {WIKI_SOURCE_TYPE_LABELS[wiki.sourceType] || wiki.sourceType}
            </span>
          </div>
        </div>
      </div>

      {/* 内容摘要 */}
      {wiki.content && (
        <p className="text-sm text-slate-500 dark:text-slate-400 line-clamp-3 mb-3">
          {wiki.content}
        </p>
      )}

      {/* 底部信息 */}
      <div className="flex items-center gap-3 text-xs text-slate-400">
        <div className="flex items-center gap-1">
          <Calendar className="w-3.5 h-3.5" />
          <span>{formatDate(wiki.createdAt)}</span>
        </div>
        {wiki.tags && wiki.tags.length > 0 && (
          <div className="flex items-center gap-1">
            <Tag className="w-3.5 h-3.5" />
            <span>{wiki.tags.slice(0, 3).join(', ')}</span>
          </div>
        )}
      </div>

      {/* 标签 */}
      {wiki.tags && wiki.tags.length > 0 && (
        <div className="flex flex-wrap gap-1 mt-3">
          {wiki.tags.map((tag) => (
            <span
              key={tag}
              className="px-2 py-0.5 rounded-full text-xs bg-slate-100 dark:bg-slate-700 text-slate-500"
            >
              {tag}
            </span>
          ))}
        </div>
      )}
    </button>
  );
}
