import { useState, useMemo } from 'react';
import { ChevronDown } from 'lucide-react';
import { PROVIDER_PRESETS } from './shared';

export interface EmbeddingConfigProps {
  formId: string;
  formSupportsEmbedding: boolean;
  formEmbeddingModel: string;
  formEmbeddingDimensions: string;
  onSupportsEmbeddingChange: (value: boolean) => void;
  onEmbeddingModelChange: (value: string) => void;
  onEmbeddingDimensionsChange: (value: string) => void;
}

export default function EmbeddingConfig({
  formId,
  formSupportsEmbedding,
  formEmbeddingModel,
  formEmbeddingDimensions,
  onSupportsEmbeddingChange,
  onEmbeddingModelChange,
  onEmbeddingDimensionsChange,
}: EmbeddingConfigProps) {
  const [showEmbeddingDropdown, setShowEmbeddingDropdown] = useState(false);

  const currentPreset = useMemo(
    () => PROVIDER_PRESETS[formId.toLowerCase()],
    [formId],
  );

  return (
    <>
      {/* Embedding Model */}
      <div>
        <div className="mb-1.5 flex items-center justify-between gap-3">
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
            向量模型 <span className="text-slate-400 font-normal">(知识库向量化，例如 GLM 填 embedding-3)</span>
          </label>
          <label className="inline-flex items-center gap-2 text-xs font-medium text-slate-600 dark:text-slate-300">
            <input
              type="checkbox"
              checked={formSupportsEmbedding}
              onChange={(e) => {
                onSupportsEmbeddingChange(e.target.checked);
                if (!e.target.checked) {
                  onEmbeddingModelChange('');
                  onEmbeddingDimensionsChange('1024');
                }
              }}
              className="h-4 w-4 rounded border-slate-300 text-primary-600 focus:ring-primary-500"
            />
            支持 Embedding
          </label>
        </div>
        <div className="relative">
          <input
            type="text"
            value={formEmbeddingModel}
            onChange={(e) => {
              onEmbeddingModelChange(e.target.value);
              setShowEmbeddingDropdown(false);
            }}
            onFocus={() => formSupportsEmbedding && currentPreset?.embeddingModels && setShowEmbeddingDropdown(true)}
            onBlur={() => setTimeout(() => setShowEmbeddingDropdown(false), 150)}
            disabled={!formSupportsEmbedding}
            placeholder={formSupportsEmbedding
              ? (currentPreset?.embeddingModels ? '从下拉列表选择或输入自定义向量模型名' : '例如: text-embedding-v3, embedding-3')
              : 'DeepSeek / Kimi 等 Provider 通常不支持 Embedding'}
            className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600
              bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white
              placeholder:text-slate-400 focus:outline-none focus:ring-2
              focus:ring-primary-500/50 focus:border-primary-400 transition-shadow
              disabled:cursor-not-allowed disabled:opacity-60"
          />
          {formSupportsEmbedding && currentPreset?.embeddingModels && (
            <button
              type="button"
              onClick={() => setShowEmbeddingDropdown(!showEmbeddingDropdown)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400
                hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
            >
              <ChevronDown className="w-4 h-4" />
            </button>
          )}
          {formSupportsEmbedding && showEmbeddingDropdown && currentPreset?.embeddingModels && (
            <div className="absolute z-10 mt-1 w-full bg-white dark:bg-slate-700
              border border-slate-200 dark:border-slate-600 rounded-xl shadow-lg
              max-h-60 overflow-auto">
              {currentPreset.embeddingModels.map((m) => (
                <button
                  key={m.value}
                  type="button"
                  onClick={() => {
                    onEmbeddingModelChange(m.value);
                    setShowEmbeddingDropdown(false);
                  }}
                  className={`w-full px-4 py-2.5 text-left text-sm hover:bg-primary-50
                    dark:hover:bg-slate-600 transition-colors flex justify-between items-center
                    ${formEmbeddingModel === m.value
                      ? 'text-primary-600 dark:text-primary-400 font-medium bg-primary-50 dark:bg-slate-600'
                      : 'text-slate-700 dark:text-slate-200'}`}
                >
                  <span className="font-mono">{m.value}</span>
                  <span className="text-xs text-slate-400 dark:text-slate-500 ml-2 whitespace-nowrap">{m.label}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Embedding Dimensions */}
      {formSupportsEmbedding && (
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            向量维度 <span className="text-slate-400 font-normal">(必须与 pgvector 表一致，当前为 1024)</span>
          </label>
          <input
            type="number"
            min={1}
            value={formEmbeddingDimensions}
            onChange={(e) => onEmbeddingDimensionsChange(e.target.value)}
            placeholder="1024"
            className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600
              bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white
              placeholder:text-slate-400 focus:outline-none focus:ring-2
              focus:ring-primary-500/50 focus:border-primary-400 transition-shadow"
          />
        </div>
      )}
    </>
  );
}
