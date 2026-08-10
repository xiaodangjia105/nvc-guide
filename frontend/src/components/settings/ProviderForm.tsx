import { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Loader2, Eye, EyeOff, ChevronDown } from 'lucide-react';
import type { ProviderItem } from '../../types/llmProvider';
import { PROVIDER_PRESETS } from './shared';
import EmbeddingConfig from './EmbeddingConfig';

export interface ProviderFormProps {
  showModal: boolean;
  editingProvider: ProviderItem | null;
  saving: boolean;
  // Form fields
  formId: string;
  formBaseUrl: string;
  formApiKey: string;
  formModel: string;
  formEmbeddingModel: string;
  formEmbeddingDimensions: string;
  formSupportsEmbedding: boolean;
  formTemperature: string;
  // Form setters
  onFormIdChange: (value: string) => void;
  onFormBaseUrlChange: (value: string) => void;
  onFormApiKeyChange: (value: string) => void;
  onFormModelChange: (value: string) => void;
  onFormEmbeddingModelChange: (value: string) => void;
  onFormEmbeddingDimensionsChange: (value: string) => void;
  onFormSupportsEmbeddingChange: (value: boolean) => void;
  onFormTemperatureChange: (value: string) => void;
  // Actions
  onClose: () => void;
  onSave: () => void;
}

export default function ProviderForm({
  showModal,
  editingProvider,
  saving,
  formId,
  formBaseUrl,
  formApiKey,
  formModel,
  formEmbeddingModel,
  formEmbeddingDimensions,
  formSupportsEmbedding,
  formTemperature,
  onFormIdChange,
  onFormBaseUrlChange,
  onFormApiKeyChange,
  onFormModelChange,
  onFormEmbeddingModelChange,
  onFormEmbeddingDimensionsChange,
  onFormSupportsEmbeddingChange,
  onFormTemperatureChange,
  onClose,
  onSave,
}: ProviderFormProps) {
  const [showApiKey, setShowApiKey] = useState(false);
  const [showModelDropdown, setShowModelDropdown] = useState(false);

  // 当前表单 Provider ID 匹配的预设
  const currentPreset = useMemo(
    () => PROVIDER_PRESETS[formId.toLowerCase()],
    [formId],
  );

  return (
    <AnimatePresence>
      {showModal && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50"
          />
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              onClick={(e) => e.stopPropagation()}
              className="bg-white dark:bg-slate-800 rounded-2xl shadow-2xl max-w-lg w-full p-6"
            >
              <h3 className="text-xl font-bold text-slate-900 dark:text-white mb-5">
                {editingProvider ? '编辑 Provider' : '新增 Provider'}
              </h3>

              <div className="space-y-4">
                {/* Provider ID */}
                <div>
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                    Provider ID <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={formId}
                    onChange={(e) => {
                      const newId = e.target.value;
                      onFormIdChange(newId);
                      // 新建时自动填充已知 Provider 的 Base URL
                      if (!editingProvider) {
                        const preset = PROVIDER_PRESETS[newId.toLowerCase()];
                        if (preset) {
                          onFormBaseUrlChange(preset.baseUrl);
                          onFormSupportsEmbeddingChange(preset.supportsEmbedding);
                          onFormEmbeddingModelChange(preset.embeddingModels?.[0]?.value ?? '');
                          onFormEmbeddingDimensionsChange(String(preset.embeddingDimensions ?? 1024));
                        }
                      }
                    }}
                    disabled={!!editingProvider}
                    placeholder="例如: dashscope, deepseek, glm, kimi"
                    className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600
                      bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white
                      placeholder:text-slate-400 focus:outline-none focus:ring-2
                      focus:ring-primary-500/50 focus:border-primary-400 transition-shadow
                      disabled:opacity-50 disabled:cursor-not-allowed"
                  />
                </div>

                {/* Base URL */}
                <div>
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                    Base URL <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={formBaseUrl}
                    onChange={(e) => onFormBaseUrlChange(e.target.value)}
                    placeholder="例如: https://api.openai.com/v1"
                    className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600
                      bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white
                      placeholder:text-slate-400 focus:outline-none focus:ring-2
                      focus:ring-primary-500/50 focus:border-primary-400 transition-shadow"
                  />
                </div>

                {/* API Key */}
                <div>
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                    API Key{' '}
                    {editingProvider && (
                      <span className="text-slate-400 font-normal">(留空则不修改)</span>
                    )}
                    {!editingProvider && <span className="text-red-500">*</span>}
                  </label>
                  <div className="relative">
                    <input
                      type={showApiKey ? 'text' : 'password'}
                      value={formApiKey}
                      onChange={(e) => onFormApiKeyChange(e.target.value)}
                      placeholder={editingProvider ? '留空则保持原值' : '输入 API Key'}
                      className="w-full px-4 py-2.5 pr-10 rounded-xl border border-slate-200 dark:border-slate-600
                        bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white
                        placeholder:text-slate-400 focus:outline-none focus:ring-2
                        focus:ring-primary-500/50 focus:border-primary-400 transition-shadow"
                    />
                    <button
                      type="button"
                      onClick={() => setShowApiKey(!showApiKey)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400
                        hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
                    >
                      {showApiKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                {/* Chat Model */}
                <div>
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                    聊天模型 <span className="text-red-500">*</span>
                  </label>
                  <div className="relative">
                    <input
                      type="text"
                      value={formModel}
                      onChange={(e) => {
                        onFormModelChange(e.target.value);
                        setShowModelDropdown(false);
                      }}
                      onFocus={() => currentPreset && setShowModelDropdown(true)}
                      onBlur={() => setTimeout(() => setShowModelDropdown(false), 150)}
                      placeholder={currentPreset ? '从下拉列表选择或输入自定义聊天模型名' : '例如: qwen3.5-flash, deepseek-v4-flash, glm-5'}
                      className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600
                        bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white
                        placeholder:text-slate-400 focus:outline-none focus:ring-2
                        focus:ring-primary-500/50 focus:border-primary-400 transition-shadow"
                    />
                    {currentPreset && (
                      <button
                        type="button"
                        onClick={() => setShowModelDropdown(!showModelDropdown)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400
                          hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
                      >
                        <ChevronDown className="w-4 h-4" />
                      </button>
                    )}
                    {showModelDropdown && currentPreset && (
                      <div className="absolute z-10 mt-1 w-full bg-white dark:bg-slate-700
                        border border-slate-200 dark:border-slate-600 rounded-xl shadow-lg
                        max-h-60 overflow-auto">
                        {currentPreset.models.map((m) => (
                          <button
                            key={m.value}
                            type="button"
                            onClick={() => {
                              onFormModelChange(m.value);
                              setShowModelDropdown(false);
                            }}
                            className={`w-full px-4 py-2.5 text-left text-sm hover:bg-primary-50
                              dark:hover:bg-slate-600 transition-colors flex justify-between items-center
                              ${formModel === m.value
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

                {/* Embedding Config */}
                <EmbeddingConfig
                  formId={formId}
                  formSupportsEmbedding={formSupportsEmbedding}
                  formEmbeddingModel={formEmbeddingModel}
                  formEmbeddingDimensions={formEmbeddingDimensions}
                  onSupportsEmbeddingChange={onFormSupportsEmbeddingChange}
                  onEmbeddingModelChange={onFormEmbeddingModelChange}
                  onEmbeddingDimensionsChange={onFormEmbeddingDimensionsChange}
                />

                {/* Temperature */}
                <div>
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                    Temperature <span className="text-slate-400 font-normal">(可选, 默认 0.2)</span>
                  </label>
                  <input
                    type="text"
                    value={formTemperature}
                    onChange={(e) => onFormTemperatureChange(e.target.value)}
                    placeholder="例如: 0.2, 0.7, 1"
                    className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600
                      bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white
                      placeholder:text-slate-400 focus:outline-none focus:ring-2
                      focus:ring-primary-500/50 focus:border-primary-400 transition-shadow"
                  />
                </div>
              </div>

              {/* Modal actions */}
              <div className="flex gap-3 justify-end mt-6">
                <motion.button
                  onClick={onClose}
                  disabled={saving}
                  className="px-5 py-2.5 border border-slate-200 dark:border-slate-600
                    text-slate-600 dark:text-slate-300 rounded-xl font-medium text-sm
                    hover:bg-slate-50 dark:hover:bg-slate-700 transition-all
                    disabled:opacity-50 disabled:cursor-not-allowed"
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                >
                  取消
                </motion.button>
                <motion.button
                  onClick={onSave}
                  disabled={saving}
                  className="px-5 py-2.5 text-white rounded-xl font-semibold text-sm
                    bg-gradient-to-r from-primary-500 to-primary-600
                    shadow-lg shadow-primary-500/25
                    hover:from-primary-600 hover:to-primary-700
                    transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                >
                  {saving ? (
                    <span className="flex items-center gap-2">
                      <Loader2 className="w-4 h-4 animate-spin" />
                      保存中...
                    </span>
                  ) : (
                    '保存'
                  )}
                </motion.button>
              </div>
            </motion.div>
          </div>
        </>
      )}
    </AnimatePresence>
  );
}
