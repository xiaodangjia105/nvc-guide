import { motion } from 'framer-motion';
import {
  Plus, Trash2, Plug, CheckCircle, XCircle,
  Loader2, RefreshCw, Server, Edit2, Database,
} from 'lucide-react';
import type { ProviderItem, ProviderTestResult } from '../../types/llmProvider';
import {
  CARD_CLASS, ICON_WRAP_CLASS, DETAILS_CLASS, ACTION_BAR_CLASS, ACTION_BUTTON_CLASS,
  StatusBadge, ConfigRow,
} from './shared';

export interface ProviderListProps {
  providers: ProviderItem[];
  defaultProviderId: string;
  defaultEmbeddingProviderId: string;
  testResults: Record<string, ProviderTestResult>;
  testingId: string | null;
  settingDefault: boolean;
  settingEmbeddingDefault: boolean;
  onOpenCreateModal: () => void;
  onOpenEditModal: (provider: ProviderItem) => void;
  onTest: (id: string) => void;
  onSetDefault: (id: string) => void;
  onSetEmbeddingDefault: (provider: ProviderItem) => void;
  onDelete: (id: string) => void;
}

export default function ProviderList({
  providers,
  defaultProviderId,
  defaultEmbeddingProviderId,
  testResults,
  testingId,
  settingDefault,
  settingEmbeddingDefault,
  onOpenCreateModal,
  onOpenEditModal,
  onTest,
  onSetDefault,
  onSetEmbeddingDefault,
  onDelete,
}: ProviderListProps) {
  const isGlobalDefaultProvider = (providerId: string) => defaultProviderId === providerId;
  const isDefaultEmbeddingProvider = (providerId: string) => defaultEmbeddingProviderId === providerId;

  return (
    <>
      {/* Provider header */}
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-bold text-slate-800 dark:text-white">
          模型服务
        </h2>
        <motion.button
          onClick={onOpenCreateModal}
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl font-medium text-sm
            bg-gradient-to-r from-primary-500 to-primary-600 text-white shadow-lg shadow-primary-500/25
            hover:from-primary-600 hover:to-primary-700 transition-all"
        >
          <Plus className="w-4 h-4" />
          新增 Provider
        </motion.button>
      </div>

      {/* Provider grid */}
      {providers.length === 0 ? (
        <div className="text-center py-16 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700">
          <Server className="w-12 h-12 text-slate-300 dark:text-slate-600 mx-auto mb-3" />
          <p className="text-slate-500 dark:text-slate-400 text-sm">暂无 Provider，点击上方按钮新增</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 items-stretch gap-4 md:grid-cols-2">
          {providers.map((provider, index) => {
            const isGlobalDefault = isGlobalDefaultProvider(provider.id);
            const isEmbeddingDefault = isDefaultEmbeddingProvider(provider.id);
            const canUseEmbedding = provider.supportsEmbedding && !!provider.embeddingModel;

            return (
            <motion.div
              key={provider.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: index * 0.05 }}
              className={CARD_CLASS}
            >
              {/* Card header */}
              <div className="mb-4 flex items-start justify-between gap-3">
                <div className="flex min-w-0 items-center gap-3">
                  <div className={ICON_WRAP_CLASS}>
                    <Server className="h-4 w-4" />
                  </div>
                  <div className="min-w-0">
                    <h3 className="truncate text-sm font-semibold text-slate-800 dark:text-white">
                      {provider.id}
                    </h3>
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">聊天/向量 Provider</p>
                  </div>
                </div>
                <div className="flex flex-col items-end gap-1">
                  {isGlobalDefault && (
                    <StatusBadge icon={<Plug className="h-3 w-3" />}>文字默认</StatusBadge>
                  )}
                  {isEmbeddingDefault && (
                    <StatusBadge icon={<Database className="h-3 w-3" />}>向量默认</StatusBadge>
                  )}
                </div>
              </div>

              {/* Card details */}
              <dl className={DETAILS_CLASS}>
                <ConfigRow label="Base URL" value={provider.baseUrl} title={provider.baseUrl} emphasis />
                <ConfigRow label="聊天模型" value={provider.model} title={provider.model} emphasis />
                <ConfigRow
                  label="向量模型"
                  value={canUseEmbedding ? '支持' : '不支持'}
                  title={canUseEmbedding ? provider.embeddingModel ?? '' : '不能用于知识库向量化'}
                />
                {provider.embeddingModel && (
                  <ConfigRow label="实际向量" value={provider.embeddingModel} title={provider.embeddingModel} emphasis={isEmbeddingDefault} />
                )}
                {canUseEmbedding && (
                  <ConfigRow label="向量维度" value={`${provider.embeddingDimensions ?? 1024} 维`} emphasis={isEmbeddingDefault} />
                )}
                {provider.temperature != null && (
                  <ConfigRow label="温度" value={provider.temperature} />
                )}
                <ConfigRow
                  label="API Key"
                  value={provider.maskedApiKey}
                  title={provider.maskedApiKey}
                  monospace
                  emphasis
                />
              </dl>

              {/* Test result */}
              {testResults[provider.id] && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  className={`mb-3 px-3 py-2 rounded-lg text-xs font-medium ${
                    testResults[provider.id].success
                      ? 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-300'
                      : 'bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300'
                  }`}
                >
                  <div className="flex items-center gap-1.5">
                    {testResults[provider.id].success
                      ? <CheckCircle className="w-3.5 h-3.5 flex-shrink-0" />
                      : <XCircle className="w-3.5 h-3.5 flex-shrink-0" />
                    }
                    <span>{testResults[provider.id].message}</span>
                  </div>
                </motion.div>
              )}

              {/* Card actions */}
              <div className={ACTION_BAR_CLASS}>
                <button
                  onClick={() => onOpenEditModal(provider)}
                  className={`${ACTION_BUTTON_CLASS} text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-700`}
                  title="编辑"
                >
                  <Edit2 className="w-3.5 h-3.5" />
                  编辑
                </button>
                <button
                  onClick={() => onTest(provider.id)}
                  disabled={testingId === provider.id}
                  className={`${ACTION_BUTTON_CLASS} text-blue-600 hover:bg-blue-50 dark:text-blue-400 dark:hover:bg-blue-900/20`}
                  title="测试连接"
                >
                  {testingId === provider.id
                    ? <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    : <RefreshCw className="w-3.5 h-3.5" />
                  }
                  测试
                </button>
                <button
                  onClick={() => onSetDefault(provider.id)}
                  disabled={isGlobalDefault || settingDefault}
                  className={`${ACTION_BUTTON_CLASS} text-primary-600 hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-900/20 disabled:hover:bg-transparent dark:disabled:hover:bg-transparent`}
                  title="设为默认文字服务"
                >
                  <Plug className="w-3.5 h-3.5" />
                  设为文字
                </button>
                <button
                  onClick={() => onSetEmbeddingDefault(provider)}
                  disabled={!canUseEmbedding || isEmbeddingDefault || settingEmbeddingDefault}
                  className={`${ACTION_BUTTON_CLASS} text-emerald-600 hover:bg-emerald-50 dark:text-emerald-400 dark:hover:bg-emerald-900/20 disabled:hover:bg-transparent dark:disabled:hover:bg-transparent`}
                  title={canUseEmbedding ? '设为默认向量服务' : '该 Provider 不支持 Embedding'}
                >
                  <Database className="w-3.5 h-3.5" />
                  设为向量
                </button>
                <button
                  onClick={() => onDelete(provider.id)}
                  className={`${ACTION_BUTTON_CLASS} ml-auto text-slate-400 hover:bg-red-50 hover:text-red-500 dark:text-slate-500 dark:hover:bg-red-900/20 dark:hover:text-red-300`}
                  title="删除"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            </motion.div>
            );
          })}
        </div>
      )}
    </>
  );
}
