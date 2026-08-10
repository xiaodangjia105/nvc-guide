import { motion, AnimatePresence } from 'framer-motion';
import {
  CheckCircle, XCircle, Loader2, RefreshCw, Edit2, Mic, Volume2,
} from 'lucide-react';
import type {
  AsrConfig, TtsConfig, AsrConfigRequest, TtsConfigRequest, ProviderTestResult,
} from '../../types/llmProvider';
import {
  CARD_CLASS, ICON_WRAP_CLASS, DETAILS_CLASS, ACTION_BAR_CLASS, ACTION_BUTTON_CLASS,
  StatusBadge, ConfigRow,
} from './shared';

export interface AsrTtsConfigProps {
  asrConfig: AsrConfig | null;
  ttsConfig: TtsConfig | null;
  testingAsr: boolean;
  asrTestResult: ProviderTestResult | null;
  onOpenAsrModal: () => void;
  onOpenTtsModal: () => void;
  onTestAsr: () => void;
  // Voice modal state
  showVoiceModal: 'asr' | 'tts' | null;
  voiceSaving: boolean;
  asrForm: AsrConfigRequest;
  ttsForm: TtsConfigRequest;
  onAsrFormChange: (updater: (prev: AsrConfigRequest) => AsrConfigRequest) => void;
  onTtsFormChange: (updater: (prev: TtsConfigRequest) => TtsConfigRequest) => void;
  onCloseVoiceModal: () => void;
  onSaveAsr: () => void;
  onSaveTts: () => void;
}

export default function AsrTtsConfig({
  asrConfig,
  ttsConfig,
  testingAsr,
  asrTestResult,
  onOpenAsrModal,
  onOpenTtsModal,
  onTestAsr,
  showVoiceModal,
  voiceSaving,
  asrForm,
  ttsForm,
  onAsrFormChange,
  onTtsFormChange,
  onCloseVoiceModal,
  onSaveAsr,
  onSaveTts,
}: AsrTtsConfigProps) {
  return (
    <>
      {/* Voice service cards */}
      <div className="mt-6">
        <h2 className="text-lg font-bold text-slate-800 dark:text-white mb-4">
          语音服务
        </h2>
        <div className="grid grid-cols-1 items-stretch gap-4 md:grid-cols-2">
          {/* ASR Card */}
          {asrConfig && (
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              className={CARD_CLASS}
            >
              <div className="mb-4 flex items-start justify-between gap-3">
                <div className="flex min-w-0 items-center gap-3">
                  <div className={ICON_WRAP_CLASS}>
                    <Mic className="h-4 w-4" />
                  </div>
                  <div className="min-w-0">
                    <h3 className="truncate text-sm font-semibold text-slate-800 dark:text-white">
                      ASR 语音识别
                    </h3>
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">实时语音转写配置</p>
                  </div>
                </div>
                <StatusBadge icon={<Mic className="h-3 w-3" />}>语音服务</StatusBadge>
              </div>

              <dl className={DETAILS_CLASS}>
                <ConfigRow label="WebSocket URL" value={asrConfig.url} title={asrConfig.url} emphasis />
                <ConfigRow label="识别模型" value={asrConfig.model} title={asrConfig.model} emphasis />
                <ConfigRow label="识别语言" value={asrConfig.language} />
                <ConfigRow label="采样率" value={`${asrConfig.sampleRate}Hz`} />
                <ConfigRow
                  label="API Key"
                  value={asrConfig.maskedApiKey}
                  title={asrConfig.maskedApiKey}
                  monospace
                  emphasis
                />
              </dl>

              {asrTestResult && (
                <div className={`mb-3 px-3 py-2 rounded-lg text-xs font-medium ${
                  asrTestResult.success
                    ? 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-300'
                    : 'bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300'
                }`}>
                  <div className="flex items-center gap-1.5">
                    {asrTestResult.success
                      ? <CheckCircle className="w-3.5 h-3.5 flex-shrink-0" />
                      : <XCircle className="w-3.5 h-3.5 flex-shrink-0" />
                    }
                    <span>{asrTestResult.message}</span>
                  </div>
                </div>
              )}

              <div className={ACTION_BAR_CLASS}>
                <button
                  onClick={onOpenAsrModal}
                  className={`${ACTION_BUTTON_CLASS} text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-700`}
                >
                  <Edit2 className="w-3.5 h-3.5" />
                  编辑
                </button>
                <button
                  onClick={onTestAsr}
                  disabled={testingAsr}
                  className={`${ACTION_BUTTON_CLASS} text-blue-600 hover:bg-blue-50 dark:text-blue-400 dark:hover:bg-blue-900/20`}
                >
                  {testingAsr
                    ? <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    : <RefreshCw className="w-3.5 h-3.5" />
                  }
                  测试
                </button>
              </div>
            </motion.div>
          )}

          {/* TTS Card */}
          {ttsConfig && (
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.05 }}
              className={CARD_CLASS}
            >
              <div className="mb-4 flex items-start justify-between gap-3">
                <div className="flex min-w-0 items-center gap-3">
                  <div className={ICON_WRAP_CLASS}>
                    <Volume2 className="h-4 w-4" />
                  </div>
                  <div className="min-w-0">
                    <h3 className="truncate text-sm font-semibold text-slate-800 dark:text-white">
                      TTS 语音合成
                    </h3>
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">文本转语音输出配置</p>
                  </div>
                </div>
                <StatusBadge icon={<Volume2 className="h-3 w-3" />}>语音服务</StatusBadge>
              </div>

              <dl className={DETAILS_CLASS}>
                <ConfigRow label="合成模型" value={ttsConfig.model} title={ttsConfig.model} emphasis />
                <ConfigRow label="音色" value={ttsConfig.voice} title={ttsConfig.voice} emphasis />
                <ConfigRow label="采样率" value={`${ttsConfig.sampleRate}Hz`} />
                <ConfigRow label="音量" value={ttsConfig.volume} />
                <ConfigRow
                  label="API Key"
                  value={ttsConfig.maskedApiKey}
                  title={ttsConfig.maskedApiKey}
                  monospace
                  emphasis
                />
              </dl>

              <div className={ACTION_BAR_CLASS}>
                <button
                  onClick={onOpenTtsModal}
                  className={`${ACTION_BUTTON_CLASS} text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-700`}
                >
                  <Edit2 className="w-3.5 h-3.5" />
                  编辑
                </button>
              </div>
            </motion.div>
          )}
        </div>
      </div>

      {/* Voice Edit Modal */}
      <AnimatePresence>
        {showVoiceModal && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={onCloseVoiceModal}
              className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50"
            />
            <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
              <motion.div
                initial={{ opacity: 0, scale: 0.95, y: 20 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.95, y: 20 }}
                onClick={(e) => e.stopPropagation()}
                className="bg-white dark:bg-slate-800 rounded-2xl shadow-2xl max-w-lg w-full p-6 max-h-[85vh] overflow-y-auto"
              >
                <h3 className="text-xl font-bold text-slate-900 dark:text-white mb-5">
                  {showVoiceModal === 'asr' ? '编辑 ASR 语音识别' : '编辑 TTS 语音合成'}
                </h3>

                {showVoiceModal === 'asr' ? (
                  <div className="space-y-4">
                    <p className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">连接配置</p>
                    <div>
                      <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">WebSocket URL</label>
                      <input type="text" value={asrForm.url || ''} onChange={(e) => onAsrFormChange(f => ({ ...f, url: e.target.value }))}
                        className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Model</label>
                        <input type="text" value={asrForm.model || ''} onChange={(e) => onAsrFormChange(f => ({ ...f, model: e.target.value }))}
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">API Key <span className="text-slate-400 font-normal">(留空不改)</span></label>
                        <input type="password" value={asrForm.apiKey || ''} onChange={(e) => onAsrFormChange(f => ({ ...f, apiKey: e.target.value }))}
                          placeholder="留空则保持原值"
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                      </div>
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Language</label>
                      <input type="text" value={asrForm.language || ''} onChange={(e) => onAsrFormChange(f => ({ ...f, language: e.target.value }))}
                        className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                    </div>

                    <p className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider pt-2">音频参数</p>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Format</label>
                        <input type="text" value={asrForm.format || ''} onChange={(e) => onAsrFormChange(f => ({ ...f, format: e.target.value }))}
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Sample Rate</label>
                        <input type="number" value={asrForm.sampleRate || 0} onChange={(e) => onAsrFormChange(f => ({ ...f, sampleRate: Number(e.target.value) }))}
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                      </div>
                    </div>

                    <p className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider pt-2">VAD 参数</p>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Turn Detection</label>
                        <select value={asrForm.enableTurnDetection ? 'true' : 'false'} onChange={(e) => onAsrFormChange(f => ({ ...f, enableTurnDetection: e.target.value === 'true' }))}
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow">
                          <option value="true">Enabled</option>
                          <option value="false">Disabled</option>
                        </select>
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Detection Type</label>
                        <input type="text" value={asrForm.turnDetectionType || ''} onChange={(e) => onAsrFormChange(f => ({ ...f, turnDetectionType: e.target.value }))}
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                      </div>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Threshold</label>
                        <input type="number" step="0.1" value={asrForm.turnDetectionThreshold || 0} onChange={(e) => onAsrFormChange(f => ({ ...f, turnDetectionThreshold: Number(e.target.value) }))}
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Silence Duration (ms)</label>
                        <input type="number" value={asrForm.turnDetectionSilenceDurationMs || 0} onChange={(e) => onAsrFormChange(f => ({ ...f, turnDetectionSilenceDurationMs: Number(e.target.value) }))}
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="space-y-4">
                    <p className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">连接配置</p>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Model</label>
                        <input type="text" value={ttsForm.model || ''} onChange={(e) => onTtsFormChange(f => ({ ...f, model: e.target.value }))}
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">API Key <span className="text-slate-400 font-normal">(留空不改)</span></label>
                        <input type="password" value={ttsForm.apiKey || ''} onChange={(e) => onTtsFormChange(f => ({ ...f, apiKey: e.target.value }))}
                          placeholder="留空则保持原值"
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                      </div>
                    </div>

                    <p className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider pt-2">语音参数</p>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Voice</label>
                        <input type="text" value={ttsForm.voice || ''} onChange={(e) => onTtsFormChange(f => ({ ...f, voice: e.target.value }))}
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Format</label>
                        <input type="text" value={ttsForm.format || ''} onChange={(e) => onTtsFormChange(f => ({ ...f, format: e.target.value }))}
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                      </div>
                    </div>
                    <div className="grid grid-cols-3 gap-3">
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Sample Rate</label>
                        <input type="number" value={ttsForm.sampleRate || 0} onChange={(e) => onTtsFormChange(f => ({ ...f, sampleRate: Number(e.target.value) }))}
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Mode</label>
                        <input type="text" value={ttsForm.mode || ''} onChange={(e) => onTtsFormChange(f => ({ ...f, mode: e.target.value }))}
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Language</label>
                        <input type="text" value={ttsForm.languageType || ''} onChange={(e) => onTtsFormChange(f => ({ ...f, languageType: e.target.value }))}
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                      </div>
                    </div>

                    <p className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider pt-2">输出控制</p>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Speech Rate</label>
                        <input type="number" step="0.1" value={ttsForm.speechRate || 0} onChange={(e) => onTtsFormChange(f => ({ ...f, speechRate: Number(e.target.value) }))}
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Volume</label>
                        <input type="number" value={ttsForm.volume || 0} onChange={(e) => onTtsFormChange(f => ({ ...f, volume: Number(e.target.value) }))}
                          className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow" />
                      </div>
                    </div>
                  </div>
                )}

                {/* Modal actions */}
                <div className="flex gap-3 justify-end mt-6">
                  <motion.button
                    onClick={onCloseVoiceModal}
                    disabled={voiceSaving}
                    className="px-5 py-2.5 border border-slate-200 dark:border-slate-600 text-slate-600 dark:text-slate-300 rounded-xl font-medium text-sm hover:bg-slate-50 dark:hover:bg-slate-700 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                    whileHover={{ scale: 1.02 }}
                    whileTap={{ scale: 0.98 }}
                  >
                    取消
                  </motion.button>
                  <motion.button
                    onClick={showVoiceModal === 'asr' ? onSaveAsr : onSaveTts}
                    disabled={voiceSaving}
                    className="px-5 py-2.5 text-white rounded-xl font-semibold text-sm bg-gradient-to-r from-primary-500 to-primary-600 shadow-lg shadow-primary-500/25 hover:from-primary-600 hover:to-primary-700 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                    whileHover={{ scale: 1.02 }}
                    whileTap={{ scale: 0.98 }}
                  >
                    {voiceSaving ? (
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
    </>
  );
}
