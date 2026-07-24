import { useState } from 'react';
import { Save, X, Tag, Folder } from 'lucide-react';
import type { WikiCategory, WikiCreateRequest, WikiUpdateRequest } from '../../api/wiki';
import { WIKI_CATEGORY_LABELS } from '../../api/wiki';

interface NvcWikiEditorProps {
  initialTitle?: string;
  initialContent?: string;
  initialCategory?: WikiCategory;
  initialTags?: string[];
  onSave: (data: WikiCreateRequest | WikiUpdateRequest) => void;
  onCancel: () => void;
  isEditing?: boolean;
}

const CATEGORIES: WikiCategory[] = [
  'CONVERSATION_CASE',
  'REAL_SCENARIO',
  'LEARNING_SUMMARY',
  'BOOK_KNOWLEDGE',
  'OTHER',
];

export default function NvcWikiEditor({
  initialTitle = '',
  initialContent = '',
  initialCategory = 'OTHER',
  initialTags = [],
  onSave,
  onCancel,
  isEditing = false,
}: NvcWikiEditorProps) {
  const [title, setTitle] = useState(initialTitle);
  const [content, setContent] = useState(initialContent);
  const [category, setCategory] = useState<WikiCategory>(initialCategory);
  const [tagInput, setTagInput] = useState('');
  const [tags, setTags] = useState<string[]>(initialTags);

  const handleAddTag = () => {
    const tag = tagInput.trim();
    if (tag && !tags.includes(tag)) {
      setTags([...tags, tag]);
      setTagInput('');
    }
  };

  const handleRemoveTag = (tagToRemove: string) => {
    setTags(tags.filter((t) => t !== tagToRemove));
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleAddTag();
    }
  };

  const handleSave = () => {
    if (!title.trim()) return;

    const data = {
      title: title.trim(),
      category,
      content,
      tags,
    };

    if (isEditing) {
      onSave(data as WikiUpdateRequest);
    } else {
      onSave({
        ...data,
        sourceType: 'MANUAL',
      } as WikiCreateRequest);
    }
  };

  return (
    <div className="bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 overflow-hidden">
      {/* 头部 */}
      <div className="flex items-center justify-between p-4 border-b border-slate-200 dark:border-slate-700">
        <h3 className="text-lg font-semibold text-slate-800 dark:text-white">
          {isEditing ? '编辑 Wiki' : '新建 Wiki'}
        </h3>
        <div className="flex items-center gap-2">
          <button
            onClick={onCancel}
            className="px-4 py-2 text-sm text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors"
          >
            <X className="w-4 h-4 inline mr-1" />
            取消
          </button>
          <button
            onClick={handleSave}
            disabled={!title.trim()}
            className="px-4 py-2 text-sm bg-primary-500 text-white rounded-lg hover:bg-primary-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Save className="w-4 h-4 inline mr-1" />
            保存
          </button>
        </div>
      </div>

      {/* 表单 */}
      <div className="p-4 space-y-4">
        {/* 标题 */}
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
            标题
          </label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="输入 Wiki 标题..."
            className="w-full px-3 py-2 border border-slate-200 dark:border-slate-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent bg-white dark:bg-slate-700 text-slate-900 dark:text-white"
          />
        </div>

        {/* 分类 */}
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
            <Folder className="w-4 h-4 inline mr-1" />
            分类
          </label>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value as WikiCategory)}
            className="w-full px-3 py-2 border border-slate-200 dark:border-slate-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent bg-white dark:bg-slate-700 text-slate-900 dark:text-white"
          >
            {CATEGORIES.map((cat) => (
              <option key={cat} value={cat}>
                {WIKI_CATEGORY_LABELS[cat]}
              </option>
            ))}
          </select>
        </div>

        {/* 标签 */}
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
            <Tag className="w-4 h-4 inline mr-1" />
            标签
          </label>
          <div className="flex gap-2">
            <input
              type="text"
              value={tagInput}
              onChange={(e) => setTagInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="输入标签后按回车..."
              className="flex-1 px-3 py-2 border border-slate-200 dark:border-slate-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent bg-white dark:bg-slate-700 text-slate-900 dark:text-white"
            />
            <button
              onClick={handleAddTag}
              disabled={!tagInput.trim()}
              className="px-3 py-2 text-sm bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-400 rounded-lg hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors disabled:opacity-50"
            >
              添加
            </button>
          </div>
          {tags.length > 0 && (
            <div className="flex flex-wrap gap-2 mt-2">
              {tags.map((tag) => (
                <span
                  key={tag}
                  className="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400"
                >
                  {tag}
                  <button
                    onClick={() => handleRemoveTag(tag)}
                    className="hover:text-primary-800 dark:hover:text-primary-200"
                  >
                    ×
                  </button>
                </span>
              ))}
            </div>
          )}
        </div>

        {/* 内容编辑器 */}
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
            内容（支持 Markdown）
          </label>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="输入 Wiki 内容...&#10;&#10;支持 Markdown 格式：&#10;- **粗体** *斜体*&#10;- # 标题&#10;- - 列表项&#10;- > 引用"
            rows={15}
            className="w-full px-3 py-2 border border-slate-200 dark:border-slate-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent bg-white dark:bg-slate-700 text-slate-900 dark:text-white font-mono text-sm leading-relaxed resize-y"
          />
        </div>
      </div>
    </div>
  );
}
