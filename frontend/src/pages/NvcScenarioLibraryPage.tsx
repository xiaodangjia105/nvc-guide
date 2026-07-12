import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Loader2, Plus, RefreshCw, Sparkles,
} from 'lucide-react';
import { useUserId } from '../hooks/useUserId';
import { scenarioApi, practiceApi } from '../api/nvc';
import NvcScenarioCard from '../components/nvc/NvcScenarioCard';
import type {
  NvcScenario, NvcScenarioType, NvcDifficulty,
} from '../types/nvc';

const TYPE_OPTIONS: { value: string; label: string }[] = [
  { value: '', label: '全部类型' },
  { value: 'WORKPLACE', label: '职场' },
  { value: 'FAMILY', label: '家庭' },
  { value: 'INTIMATE', label: '亲密关系' },
  { value: 'SOCIAL', label: '社交' },
  { value: 'SELF', label: '自我对话' },
];

const DIFFICULTY_OPTIONS: { value: string; label: string }[] = [
  { value: '', label: '全部难度' },
  { value: 'EASY', label: '简单' },
  { value: 'MEDIUM', label: '中等' },
  { value: 'HARD', label: '困难' },
];

export default function NvcScenarioLibraryPage() {
  const [userId] = useUserId();
  const navigate = useNavigate();
  const [scenarios, setScenarios] = useState<NvcScenario[]>([]);
  const [loading, setLoading] = useState(true);
  const [typeFilter, setTypeFilter] = useState('');
  const [difficultyFilter, setDifficultyFilter] = useState('');
  const [generating, setGenerating] = useState(false);
  const [creating, setCreating] = useState<number | null>(null);

  // 生成表单
  const [showGenerate, setShowGenerate] = useState(false);
  const [genType, setGenType] = useState<NvcScenarioType>('WORKPLACE');
  const [genDifficulty, setGenDifficulty] = useState<NvcDifficulty>('MEDIUM');
  const [genDescription, setGenDescription] = useState('');

  const loadScenarios = useCallback(() => {
    setLoading(true);
    scenarioApi.getScenarios(typeFilter || undefined, difficultyFilter || undefined)
      .then(setScenarios)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [typeFilter, difficultyFilter]);

  useEffect(() => {
    loadScenarios();
  }, [loadScenarios]);

  const handleSelectScenario = useCallback(async (scenarioId: number) => {
    setCreating(scenarioId);
    try {
      const session = await practiceApi.createSession(userId, {
        practiceMode: 'SCENARIO',
        scenarioId,
        difficulty: difficultyFilter as NvcDifficulty || undefined,
      });
      navigate(`/nvc/practice/${session.id}`);
    } catch (err) {
      console.error('Failed to create session:', err);
    } finally {
      setCreating(null);
    }
  }, [userId, difficultyFilter, navigate]);

  const handleGenerate = useCallback(async () => {
    setGenerating(true);
    try {
      const scenario = await scenarioApi.generateScenario({
        scenarioType: genType,
        difficulty: genDifficulty,
        description: genDescription || undefined,
      });
      setScenarios((prev) => [scenario, ...prev]);
      setShowGenerate(false);
      setGenDescription('');
      // 从服务器重新拉取完整列表，确保数据一致
      loadScenarios();
    } catch (err) {
      console.error('Failed to generate scenario:', err);
      alert('场景生成失败，请稍后重试');
    } finally {
      setGenerating(false);
    }
  }, [genType, genDifficulty, genDescription, loadScenarios]);

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800 dark:text-white">
            场景库
          </h1>
          <p className="text-slate-500 dark:text-slate-400 mt-1">
            选择场景开始练习，或让 AI 为你生成新场景
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={loadScenarios}
            className="flex items-center gap-2 px-4 py-2 bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 rounded-xl hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors"
          >
            <RefreshCw className="w-4 h-4" />
            刷新
          </button>
          <button
            onClick={() => setShowGenerate(!showGenerate)}
            className="flex items-center gap-2 px-4 py-2 bg-primary-500 text-white rounded-xl hover:bg-primary-600 transition-colors"
          >
            <Sparkles className="w-4 h-4" />
            AI 生成
          </button>
        </div>
      </div>

      {/* 生成表单 */}
      {showGenerate && (
        <motion.div
          initial={{ opacity: 0, height: 0 }}
          animate={{ opacity: 1, height: 'auto' }}
          className="bg-white dark:bg-slate-800 rounded-2xl p-6 border border-slate-200 dark:border-slate-700"
        >
          <h3 className="font-semibold text-slate-800 dark:text-white mb-4">
            AI 生成新场景
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
            <div>
              <label className="block text-sm text-slate-500 mb-1">场景类型</label>
              <select
                value={genType}
                onChange={(e) => setGenType(e.target.value as NvcScenarioType)}
                className="w-full border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-slate-800 dark:text-white"
              >
                {TYPE_OPTIONS.filter((o) => o.value).map((opt) => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm text-slate-500 mb-1">难度</label>
              <select
                value={genDifficulty}
                onChange={(e) => setGenDifficulty(e.target.value as NvcDifficulty)}
                className="w-full border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-slate-800 dark:text-white"
              >
                {DIFFICULTY_OPTIONS.filter((o) => o.value).map((opt) => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm text-slate-500 mb-1">
                补充描述（可选）
              </label>
              <input
                value={genDescription}
                onChange={(e) => setGenDescription(e.target.value)}
                placeholder="描述你想要的场景..."
                className="w-full border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-slate-800 dark:text-white placeholder-slate-400"
              />
            </div>
          </div>
          <div className="flex justify-end">
            <button
              onClick={handleGenerate}
              disabled={generating}
              className="flex items-center gap-2 px-5 py-2.5 bg-primary-500 text-white rounded-xl hover:bg-primary-600 transition-colors disabled:opacity-50"
            >
              {generating ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Plus className="w-4 h-4" />
              )}
              生成场景
            </button>
          </div>
        </motion.div>
      )}

      {/* 筛选 */}
      <div className="flex items-center gap-3">
        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value)}
          className="border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-400"
        >
          {TYPE_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>
        <select
          value={difficultyFilter}
          onChange={(e) => setDifficultyFilter(e.target.value)}
          className="border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-400"
        >
          {DIFFICULTY_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>
      </div>

      {/* 场景列表 */}
      {loading ? (
        <div className="flex items-center justify-center h-40">
          <Loader2 className="w-6 h-6 animate-spin text-primary-500" />
        </div>
      ) : scenarios.length === 0 ? (
        <div className="text-center py-20">
          <p className="text-slate-500">暂无场景</p>
          <p className="text-sm text-slate-400 mt-1">
            点击"AI 生成"创建新场景
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {scenarios.map((scenario, idx) => (
            <motion.div
              key={scenario.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: idx * 0.03 }}
            >
              <NvcScenarioCard
                scenario={scenario}
                onClick={() => handleSelectScenario(scenario.id)}
              />
              {creating === scenario.id && (
                <div className="flex items-center justify-center mt-2">
                  <Loader2 className="w-4 h-4 animate-spin text-primary-500" />
                  <span className="text-xs text-slate-500 ml-1">创建中...</span>
                </div>
              )}
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}
