import { useState } from 'react';
import { Search, Loader2 } from 'lucide-react';
import type { WikiSearchResult } from '../../api/wiki';
import { WIKI_CATEGORY_LABELS } from '../../api/wiki';

interface NvcWikiSearchProps {
  onSearch: (query: string) => Promise<WikiSearchResult[]>;
  onSelectResult: (result: WikiSearchResult) => void;
}

export default function NvcWikiSearch({ onSearch, onSelectResult }: NvcWikiSearchProps) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<WikiSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;

    setLoading(true);
    setSearched(true);
    try {
      const searchResults = await onSearch(query.trim());
      setResults(searchResults);
    } catch (error) {
      console.error('Wiki search failed:', error);
      setResults([]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-4">
      {/* 搜索框 */}
      <form onSubmit={handleSearch} className="flex gap-2">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="语义搜索你的知识库..."
            className="w-full pl-10 pr-4 py-2 border border-slate-200 dark:border-slate-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent bg-white dark:bg-slate-700 text-slate-900 dark:text-white"
          />
        </div>
        <button
          type="submit"
          disabled={loading || !query.trim()}
          className="px-4 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {loading ? (
            <Loader2 className="w-4 h-4 animate-spin" />
          ) : (
            '搜索'
          )}
        </button>
      </form>

      {/* 搜索结果 */}
      {searched && !loading && (
        <div className="space-y-2">
          {results.length === 0 ? (
            <p className="text-sm text-slate-500 dark:text-slate-400 text-center py-4">
              没有找到相关内容
            </p>
          ) : (
            results.map((result) => (
              <button
                key={result.id}
                onClick={() => onSelectResult(result)}
                className="w-full text-left p-4 bg-white dark:bg-slate-800 rounded-lg border border-slate-200 dark:border-slate-700 hover:border-primary-300 dark:hover:border-primary-600 transition-all"
              >
                <div className="flex items-center justify-between mb-2">
                  <h4 className="font-medium text-slate-800 dark:text-white truncate">
                    {result.title}
                  </h4>
                  <span className="text-xs text-slate-400 ml-2 flex-shrink-0">
                    相似度: {(result.score * 100).toFixed(0)}%
                  </span>
                </div>
                <div className="flex items-center gap-2 mb-2">
                  <span className="px-2 py-0.5 rounded-full text-xs bg-slate-100 dark:bg-slate-700 text-slate-500">
                    {WIKI_CATEGORY_LABELS[result.category] || result.category}
                  </span>
                </div>
                <p className="text-sm text-slate-500 dark:text-slate-400 line-clamp-2">
                  {result.contentSnippet}
                </p>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
}
