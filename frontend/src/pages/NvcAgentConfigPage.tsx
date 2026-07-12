import { useCallback, useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import {
  Bot, ChevronDown, ChevronUp, Loader2, Save, Settings, ToggleLeft, ToggleRight,
} from 'lucide-react';
import { agentApi } from '../api/nvc';
import type { AgentConfig, AgentConfigUpdateRequest } from '../types/nvc';

const SCENE_LABELS: Record<string, string> = {
  SCENARIO_GENERATOR: '场景生成官',
  DIALOGUE_GUIDE: '对话引导官',
  DIFFICULT_PARTNER: '困难搭档',
  STEP_OBSERVE_COACH: '观察步骤教练',
  STEP_FEELING_COACH: '感受步骤教练',
  STEP_NEED_COACH: '需求步骤教练',
  STEP_REQUEST_COACH: '请求步骤教练',
  NVC_EXPRESSION_EVALUATOR: 'NVC 表达评估官',
  EMPATHY_COACH: '共情教练',
  NVC_KNOWLEDGE_ADVISOR: 'NVC 知识顾问',
};

function AgentCard({
  config,
  onSave,
}: {
  config: AgentConfig;
  onSave: (scene: string, data: AgentConfigUpdateRequest) => Promise<void>;
}) {
  const [expanded, setExpanded] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<AgentConfigUpdateRequest>({
    systemPrompt: config.systemPrompt,
    temperature: config.temperature,
    maxTokens: config.maxTokens,
    topP: config.topP,
    isEnabled: config.isEnabled,
    stepAdvanceThreshold: config.stepAdvanceThreshold ?? undefined,
    maxStepAttempts: config.maxStepAttempts ?? undefined,
    stepTimeoutMinutes: config.stepTimeoutMinutes ?? undefined,
  });

  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      await onSave(config.agentScene, form);
    } finally {
      setSaving(false);
    }
  }, [config.agentScene, form, onSave]);

  const updateForm = useCallback(
    <K extends keyof AgentConfigUpdateRequest>(
      key: K,
      value: AgentConfigUpdateRequest[K]
    ) => {
      setForm((prev) => ({ ...prev, [key]: value }));
    },
    []
  );

  const hasStepConfig = config.agentScene.startsWith('STEP_');

  return (
    <div className="bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 overflow-hidden">
      {/* 头部 */}
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center gap-3 p-4 hover:bg-slate-50 dark:hover:bg-slate-750 transition-colors"
      >
        <div className="w-10 h-10 rounded-lg bg-primary-50 dark:bg-primary-900/30 flex items-center justify-center">
          <Bot className="w-5 h-5 text-primary-500" />
        </div>
        <div className="flex-1 text-left">
          <h3 className="font-semibold text-slate-800 dark:text-white">
            {config.displayName}
          </h3>
          <p className="text-xs text-slate-500">
            {SCENE_LABELS[config.agentScene] || config.agentScene}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className={`px-2 py-0.5 rounded-full text-xs ${
            config.isEnabled
              ? 'bg-emerald-100 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-400'
              : 'bg-slate-100 text-slate-500 dark:bg-slate-700'
          }`}>
            {config.isEnabled ? '已启用' : '已禁用'}
          </span>
          {expanded ? (
            <ChevronUp className="w-5 h-5 text-slate-400" />
          ) : (
            <ChevronDown className="w-5 h-5 text-slate-400" />
          )}
        </div>
      </button>

      {/* 展开内容 */}
      {expanded && (
        <motion.div
          initial={{ opacity: 0, height: 0 }}
          animate={{ opacity: 1, height: 'auto' }}
          className="border-t border-slate-200 dark:border-slate-700 p-4 space-y-4"
        >
          {/* 描述 */}
          {config.description && (
            <p className="text-sm text-slate-500">{config.description}</p>
          )}

          {/* 启用开关 */}
          <div className="flex items-center justify-between">
            <span className="text-sm text-slate-700 dark:text-slate-300">
              启用状态
            </span>
            <button
              onClick={() => updateForm('isEnabled', !form.isEnabled)}
              className="flex items-center gap-2"
            >
              {form.isEnabled ? (
                <ToggleRight className="w-8 h-8 text-emerald-500" />
              ) : (
                <ToggleLeft className="w-8 h-8 text-slate-400" />
              )}
            </button>
          </div>

          {/* 系统提示词 */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
              系统提示词
            </label>
            <textarea
              value={form.systemPrompt || ''}
              onChange={(e) => updateForm('systemPrompt', e.target.value)}
              rows={6}
              className="w-full border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-slate-800 dark:text-white font-mono focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
            />
          </div>

          {/* 基本参数 */}
          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-sm text-slate-500 mb-1">温度</label>
              <input
                type="number"
                step="0.1"
                min="0"
                max="2"
                value={form.temperature ?? 0.7}
                onChange={(e) => updateForm('temperature', parseFloat(e.target.value))}
                className="w-full border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-slate-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>
            <div>
              <label className="block text-sm text-slate-500 mb-1">最大 Token</label>
              <input
                type="number"
                step="100"
                min="100"
                value={form.maxTokens ?? 2000}
                onChange={(e) => updateForm('maxTokens', parseInt(e.target.value))}
                className="w-full border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-slate-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>
            <div>
              <label className="block text-sm text-slate-500 mb-1">Top P</label>
              <input
                type="number"
                step="0.1"
                min="0"
                max="1"
                value={form.topP ?? 0.9}
                onChange={(e) => updateForm('topP', parseFloat(e.target.value))}
                className="w-full border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-slate-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>
          </div>

          {/* 高级配置（步骤教练专属） */}
          {hasStepConfig && (
            <div>
              <button
                onClick={() => setShowAdvanced(!showAdvanced)}
                className="flex items-center gap-2 text-sm text-slate-500 hover:text-slate-700 dark:hover:text-slate-300"
              >
                <Settings className="w-4 h-4" />
                高级配置（步骤练习）
                {showAdvanced ? (
                  <ChevronUp className="w-4 h-4" />
                ) : (
                  <ChevronDown className="w-4 h-4" />
                )}
              </button>

              {showAdvanced && (
                <div className="grid grid-cols-3 gap-4 mt-3 p-3 bg-slate-50 dark:bg-slate-900 rounded-lg">
                  <div>
                    <label className="block text-xs text-slate-500 mb-1">
                      推进阈值
                    </label>
                    <input
                      type="number"
                      min="0"
                      max="100"
                      value={form.stepAdvanceThreshold ?? 70}
                      onChange={(e) => updateForm('stepAdvanceThreshold', parseInt(e.target.value))}
                      className="w-full border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-800 text-slate-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-slate-500 mb-1">
                      最大尝试次数
                    </label>
                    <input
                      type="number"
                      min="1"
                      value={form.maxStepAttempts ?? 3}
                      onChange={(e) => updateForm('maxStepAttempts', parseInt(e.target.value))}
                      className="w-full border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-800 text-slate-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-slate-500 mb-1">
                      超时（分钟）
                    </label>
                    <input
                      type="number"
                      min="1"
                      value={form.stepTimeoutMinutes ?? 10}
                      onChange={(e) => updateForm('stepTimeoutMinutes', parseInt(e.target.value))}
                      className="w-full border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-800 text-slate-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500"
                    />
                  </div>
                </div>
              )}
            </div>
          )}

          {/* 保存按钮 */}
          <div className="flex justify-end pt-2">
            <button
              onClick={handleSave}
              disabled={saving}
              className="flex items-center gap-2 px-4 py-2 bg-primary-500 text-white rounded-xl hover:bg-primary-600 transition-colors disabled:opacity-50"
            >
              {saving ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Save className="w-4 h-4" />
              )}
              保存配置
            </button>
          </div>
        </motion.div>
      )}
    </div>
  );
}

export default function NvcAgentConfigPage() {
  const [configs, setConfigs] = useState<AgentConfig[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    agentApi.getAllConfigs()
      .then(setConfigs)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const handleSave = useCallback(
    async (scene: string, data: AgentConfigUpdateRequest) => {
      const updated = await agentApi.updateConfig(scene, data);
      setConfigs((prev) =>
        prev.map((c) => (c.agentScene === scene ? updated : c))
      );
    },
    []
  );

  if (loading) {
    return (
      <div className="flex items-center justify-center h-[60vh]">
        <Loader2 className="w-8 h-8 animate-spin text-primary-500" />
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800 dark:text-white">
          Agent 配置
        </h1>
        <p className="text-slate-500 dark:text-slate-400 mt-1">
          管理 AI 角色的行为参数和系统提示词，修改后立即生效
        </p>
      </div>

      <div className="space-y-4">
        {configs.map((config) => (
          <AgentCard
            key={config.agentScene}
            config={config}
            onSave={handleSave}
          />
        ))}
      </div>
    </div>
  );
}
