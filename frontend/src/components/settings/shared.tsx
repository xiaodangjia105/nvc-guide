import type { ReactNode } from 'react';

// Provider 预设：已知 Provider 的 Base URL、推荐模型和向量模型
export const PROVIDER_PRESETS: Record<string, {
  baseUrl: string;
  models: { value: string; label: string }[];
  embeddingModels?: { value: string; label: string }[];
  embeddingDimensions?: number;
  supportsEmbedding: boolean;
}> = {
  dashscope: {
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    models: [
      { value: 'qwen3.6-flash', label: 'Qwen3.6 Flash — 最新旗舰' },
      { value: 'qwen3.5-plus', label: 'Qwen3.5 Plus — 高性能' },
      { value: 'qwen3.5-flash', label: 'Qwen3.5 Flash — 性价比' },
      { value: 'qwen3-max', label: 'Qwen3 Max — 旗舰' },
      { value: 'qwen-max', label: 'Qwen Max — 稳定版' },
      { value: 'qwen-plus', label: 'Qwen Plus — 均衡' },
      { value: 'qwen-flash', label: 'Qwen Flash — 经济' },
      { value: 'qwq-32b', label: 'QwQ-32B — 推理专用' },
    ],
    embeddingModels: [
      { value: 'text-embedding-v3', label: 'text-embedding-v3 — 推荐' },
    ],
    embeddingDimensions: 1024,
    supportsEmbedding: true,
  },
  deepseek: {
    baseUrl: 'https://api.deepseek.com',
    models: [
      { value: 'deepseek-v4-flash', label: 'DeepSeek V4 Flash — 最新·快速' },
      { value: 'deepseek-v4-pro', label: 'DeepSeek V4 Pro — 最强推理' },
      { value: 'deepseek-chat', label: 'DeepSeek V3.2 — 旧版对话（即将弃用）' },
      { value: 'deepseek-reasoner', label: 'DeepSeek R1 — 旧版推理（即将弃用）' },
    ],
    supportsEmbedding: false,
  },
  glm: {
    baseUrl: 'https://open.bigmodel.cn/api/coding/paas/v4',
    models: [
      { value: 'glm-5.1', label: 'GLM-5.1 — 最新旗舰' },
      { value: 'glm-5', label: 'GLM-5 — 旗舰' },
      { value: 'glm-4.7', label: 'GLM-4.7 — Coding 强' },
      { value: 'glm-4.7-flash', label: 'GLM-4.7 Flash — 免费' },
      { value: 'glm-4.6', label: 'GLM-4.6 — 200K 上下文' },
      { value: 'glm-4-plus', label: 'GLM-4 Plus — 高性能' },
      { value: 'glm-4-air-250414', label: 'GLM-4 Air — 高性价比' },
      { value: 'glm-4-flash-250414', label: 'GLM-4 Flash — 免费' },
    ],
    embeddingModels: [
      { value: 'embedding-3', label: 'embedding-3 — 推荐' },
    ],
    embeddingDimensions: 1024,
    supportsEmbedding: true,
  },
  kimi: {
    baseUrl: 'https://api.moonshot.cn/v1',
    models: [
      { value: 'kimi-k2.6', label: 'Kimi K2.6 — 最新最智能' },
      { value: 'kimi-k2.5', label: 'Kimi K2.5 — 多模态' },
      { value: 'kimi-k2', label: 'Kimi K2 — MoE 基座' },
      { value: 'kimi-k2-thinking', label: 'Kimi K2 Thinking — 深度推理' },
      { value: 'kimi-latest', label: 'kimi-latest — 自动最新' },
    ],
    supportsEmbedding: false,
  },
};

// --- Shared CSS classes ---

export const CARD_CLASS = `flex h-full min-h-[330px] flex-col rounded-xl border border-slate-200
  bg-white p-5 shadow-sm transition-shadow hover:shadow-md dark:border-slate-700
  dark:bg-slate-800`;

export const ICON_WRAP_CLASS = `flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg
  bg-primary-50 text-primary-600 dark:bg-primary-900/30 dark:text-primary-300`;

export const DETAILS_CLASS = `mb-4 flex-1 space-y-1 rounded-lg border border-slate-100 bg-slate-50/70
  p-3 dark:border-slate-700/80 dark:bg-slate-900/30`;

export const ACTION_BAR_CLASS = `mt-auto flex min-h-12 flex-wrap items-center gap-2 border-t
  border-slate-100 pt-3 dark:border-slate-700`;

export const ACTION_BUTTON_CLASS = `inline-flex h-8 items-center gap-1.5 rounded-lg px-3 text-xs
  font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50`;

// --- Shared small components ---

type ConfigRowProps = {
  label: string;
  value: ReactNode;
  title?: string;
  monospace?: boolean;
  emphasis?: boolean;
};

type StatusBadgeProps = {
  icon: ReactNode;
  children: ReactNode;
};

export function StatusBadge({ icon, children }: StatusBadgeProps) {
  return (
    <span className="inline-flex h-6 items-center gap-1.5 rounded-full bg-primary-50 px-2.5 text-xs font-semibold text-primary-700 dark:bg-primary-900/30 dark:text-primary-300">
      {icon}
      {children}
    </span>
  );
}

export function ConfigRow({ label, value, title, monospace = false, emphasis = false }: ConfigRowProps) {
  return (
    <div
      className={`grid grid-cols-[108px_minmax(0,1fr)] items-start gap-3 rounded-md px-2 py-2 text-xs ${
        emphasis ? 'bg-white shadow-sm ring-1 ring-slate-100 dark:bg-slate-800/80 dark:ring-slate-700' : ''
      }`}
    >
      <dt className="whitespace-nowrap text-slate-500 dark:text-slate-400">{label}</dt>
      <dd
        className={`min-w-0 truncate text-right font-medium text-slate-700 dark:text-slate-200 ${
          monospace ? 'font-mono' : ''
        }`}
        title={title}
      >
        {value}
      </dd>
    </div>
  );
}
