import { Plus, MessageSquare, Trash2, Loader2 } from 'lucide-react';
import type { ConversationResponse } from '../../api/nvc-assistant';

interface NvcAssistantSidebarProps {
  conversations: ConversationResponse[];
  activeId: number | null;
  loading: boolean;
  onSelect: (id: number) => void;
  onNew: () => void;
  onDelete: (id: number) => void;
}

export default function NvcAssistantSidebar({
  conversations,
  activeId,
  loading,
  onSelect,
  onNew,
  onDelete,
}: NvcAssistantSidebarProps) {
  const handleDelete = (e: React.MouseEvent, id: number) => {
    e.stopPropagation();
    onDelete(id);
  };

  return (
    <div className="w-64 flex-shrink-0 bg-white dark:bg-slate-800 border-r border-slate-200 dark:border-slate-700 flex flex-col">
      {/* 新建按钮 */}
      <div className="p-3 border-b border-slate-200 dark:border-slate-700">
        <button
          onClick={onNew}
          className="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-primary-500 text-white rounded-xl hover:bg-primary-600 transition-colors text-sm font-medium"
        >
          <Plus className="w-4 h-4" />
          新对话
        </button>
      </div>

      {/* 对话列表 */}
      <div className="flex-1 overflow-y-auto">
        {loading ? (
          <div className="flex items-center justify-center py-8">
            <Loader2 className="w-5 h-5 animate-spin text-primary-500" />
          </div>
        ) : conversations.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 px-4 text-center">
            <MessageSquare className="w-10 h-10 text-slate-300 dark:text-slate-600 mb-3" />
            <p className="text-sm text-slate-400 dark:text-slate-500">
              还没有对话
            </p>
            <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">
              点击「新对话」开始
            </p>
          </div>
        ) : (
          <div className="py-2">
            {conversations.map((conv) => {
              const isActive = conv.id === activeId;
              return (
                <button
                  key={conv.id}
                  onClick={() => onSelect(conv.id)}
                  className={`group w-full flex items-center gap-3 px-3 py-3 text-left transition-colors ${
                    isActive
                      ? 'bg-primary-50 dark:bg-primary-900/20 border-r-2 border-primary-500'
                      : 'hover:bg-slate-50 dark:hover:bg-slate-700/50'
                  }`}
                >
                  <MessageSquare className={`w-4 h-4 flex-shrink-0 ${
                    isActive
                      ? 'text-primary-500'
                      : 'text-slate-400 dark:text-slate-500'
                  }`} />
                  <div className="flex-1 min-w-0">
                    <span className={`text-sm truncate block ${
                      isActive
                        ? 'font-medium text-primary-700 dark:text-primary-300'
                        : 'text-slate-700 dark:text-slate-300'
                    }`}>
                      {conv.title || '新对话'}
                    </span>
                    <span className="text-xs text-slate-400 dark:text-slate-500">
                      {new Date(conv.updatedAt).toLocaleDateString('zh-CN')}
                    </span>
                  </div>
                  <button
                    onClick={(e) => handleDelete(e, conv.id)}
                    className="opacity-0 group-hover:opacity-100 p-1 rounded-md hover:bg-red-100 dark:hover:bg-red-900/30 text-slate-400 hover:text-red-500 transition-all flex-shrink-0"
                    title="删除对话"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
