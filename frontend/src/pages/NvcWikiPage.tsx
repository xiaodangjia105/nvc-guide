import { useState, useEffect, useCallback } from 'react';
import { Plus, Loader2, BookOpen, Search } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { wikiApi } from '../api/wiki';
import type {
  WikiResponse,
  WikiCategory,
  WikiCreateRequest,
  WikiUpdateRequest,
  WikiSearchResult,
} from '../api/wiki';
import { WIKI_CATEGORY_LABELS } from '../api/wiki';
import { useUserId } from '../hooks/useUserId';
import NvcWikiCard from '../components/nvc/NvcWikiCard';
import NvcWikiEditor from '../components/nvc/NvcWikiEditor';
import NvcWikiSearch from '../components/nvc/NvcWikiSearch';

const CATEGORIES: (WikiCategory | 'ALL')[] = [
  'ALL',
  'CONVERSATION_CASE',
  'REAL_SCENARIO',
  'LEARNING_SUMMARY',
  'BOOK_KNOWLEDGE',
  'OTHER',
];

export default function NvcWikiPage() {
  const [userId] = useUserId();
  const [wikis, setWikis] = useState<WikiResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedCategory, setSelectedCategory] = useState<WikiCategory | 'ALL'>('ALL');
  const [showEditor, setShowEditor] = useState(false);
  const [editingWiki, setEditingWiki] = useState<WikiResponse | null>(null);
  const [showSearch, setShowSearch] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  // 加载 Wiki 列表
  const loadWikis = useCallback(async () => {
    setLoading(true);
    try {
      const category = selectedCategory === 'ALL' ? undefined : selectedCategory;
      const result = await wikiApi.list(userId, category, page, 20);
      setWikis(result.content);
      setTotalPages(result.totalPages);
    } catch (error) {
      console.error('Failed to load wikis:', error);
    } finally {
      setLoading(false);
    }
  }, [userId, selectedCategory, page]);

  useEffect(() => {
    loadWikis();
  }, [loadWikis]);

  // 创建 Wiki
  const handleCreate = async (data: WikiCreateRequest | WikiUpdateRequest) => {
    try {
      await wikiApi.create(userId, data as WikiCreateRequest);
      setShowEditor(false);
      loadWikis();
    } catch (error) {
      console.error('Failed to create wiki:', error);
    }
  };

  // 更新 Wiki
  const handleUpdate = async (data: WikiCreateRequest | WikiUpdateRequest) => {
    if (!editingWiki) return;
    try {
      await wikiApi.update(userId, editingWiki.id, data as WikiUpdateRequest);
      setEditingWiki(null);
      setShowEditor(false);
      loadWikis();
    } catch (error) {
      console.error('Failed to update wiki:', error);
    }
  };

  // 点击 Wiki 卡片
  const handleCardClick = (wiki: WikiResponse) => {
    setEditingWiki(wiki);
    setShowEditor(true);
  };

  // 语义搜索
  const handleSearch = async (query: string): Promise<WikiSearchResult[]> => {
    return wikiApi.search(userId, query, 10);
  };

  // 搜索结果点击
  const handleSearchResultClick = async (result: WikiSearchResult) => {
    try {
      const wiki = await wikiApi.get(userId, result.id);
      setEditingWiki(wiki);
      setShowEditor(true);
      setShowSearch(false);
    } catch (error) {
      console.error('Failed to load wiki:', error);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-900">
      <div className="max-w-6xl mx-auto px-4 py-8">
        {/* 头部 */}
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-2xl font-bold text-slate-800 dark:text-white flex items-center gap-2">
              <BookOpen className="w-7 h-7 text-primary-500" />
              我的知识库
            </h1>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              记录你的 NVC 学习心得、案例和知识
            </p>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={() => setShowSearch(!showSearch)}
              className="px-4 py-2 text-sm bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors flex items-center gap-2"
            >
              <Search className="w-4 h-4" />
              搜索
            </button>
            <button
              onClick={() => {
                setEditingWiki(null);
                setShowEditor(true);
              }}
              className="px-4 py-2 text-sm bg-primary-500 text-white rounded-lg hover:bg-primary-600 transition-colors flex items-center gap-2"
            >
              <Plus className="w-4 h-4" />
              新建
            </button>
          </div>
        </div>

        {/* 搜索面板 */}
        <AnimatePresence>
          {showSearch && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              className="mb-6 overflow-hidden"
            >
              <div className="bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 p-4">
                <NvcWikiSearch
                  onSearch={handleSearch}
                  onSelectResult={handleSearchResultClick}
                />
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* 编辑器 */}
        <AnimatePresence>
          {showEditor && (
            <motion.div
              initial={{ opacity: 0, y: -20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              className="mb-6"
            >
              <NvcWikiEditor
                initialTitle={editingWiki?.title || ''}
                initialContent={editingWiki?.content || ''}
                initialCategory={editingWiki?.category || 'OTHER'}
                initialTags={editingWiki?.tags || []}
                onSave={editingWiki ? handleUpdate : handleCreate}
                onCancel={() => {
                  setShowEditor(false);
                  setEditingWiki(null);
                }}
                isEditing={!!editingWiki}
              />
            </motion.div>
          )}
        </AnimatePresence>

        {/* 分类筛选 */}
        <div className="flex gap-2 mb-6 overflow-x-auto pb-2">
          {CATEGORIES.map((cat) => (
            <button
              key={cat}
              onClick={() => {
                setSelectedCategory(cat);
                setPage(0);
              }}
              className={`px-4 py-2 text-sm rounded-lg whitespace-nowrap transition-colors ${
                selectedCategory === cat
                  ? 'bg-primary-500 text-white'
                  : 'bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-400 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700'
              }`}
            >
              {cat === 'ALL' ? '全部' : WIKI_CATEGORY_LABELS[cat]}
            </button>
          ))}
        </div>

        {/* Wiki 列表 */}
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-8 h-8 animate-spin text-primary-500" />
          </div>
        ) : wikis.length === 0 ? (
          <div className="text-center py-20">
            <BookOpen className="w-16 h-16 text-slate-300 dark:text-slate-600 mx-auto mb-4" />
            <h3 className="text-lg font-medium text-slate-500 dark:text-slate-400 mb-2">
              还没有 Wiki 条目
            </h3>
            <p className="text-sm text-slate-400 dark:text-slate-500">
              点击「新建」按钮创建你的第一条 Wiki
            </p>
          </div>
        ) : (
          <>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {wikis.map((wiki, idx) => (
                <motion.div
                  key={wiki.id}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: idx * 0.03 }}
                >
                  <NvcWikiCard
                    wiki={wiki}
                    onClick={() => handleCardClick(wiki)}
                  />
                </motion.div>
              ))}
            </div>

            {/* 分页 */}
            {totalPages > 1 && (
              <div className="flex items-center justify-center gap-2 mt-8">
                <button
                  onClick={() => setPage(Math.max(0, page - 1))}
                  disabled={page === 0}
                  className="px-3 py-2 text-sm bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors disabled:opacity-50"
                >
                  上一页
                </button>
                <span className="text-sm text-slate-500 dark:text-slate-400">
                  {page + 1} / {totalPages}
                </span>
                <button
                  onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
                  disabled={page >= totalPages - 1}
                  className="px-3 py-2 text-sm bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors disabled:opacity-50"
                >
                  下一页
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
