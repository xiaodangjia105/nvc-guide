import { useCallback, useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import {
  FileText, Loader2, Plus, Save, Send, User,
} from 'lucide-react';
import { useUserId } from '../hooks/useUserId';
import { profileApi } from '../api/nvc';
import type {
  UserProfile, UserProfileUpdateRequest,
  CommunicationRecord, NvcScenarioType,
} from '../types/nvc';

const SCENARIO_TYPE_OPTIONS: { value: NvcScenarioType; label: string }[] = [
  { value: 'WORKPLACE', label: '职场' },
  { value: 'FAMILY', label: '家庭' },
  { value: 'INTIMATE', label: '亲密关系' },
  { value: 'SOCIAL', label: '社交' },
  { value: 'SELF', label: '自我对话' },
];

export default function NvcProfilePage() {
  const [userId] = useUserId();
  const [, setProfile] = useState<UserProfile | null>(null);
  const [records, setRecords] = useState<CommunicationRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);

  // 编辑表单状态
  const [form, setForm] = useState<UserProfileUpdateRequest>({});

  // 沟通分析表单
  const [analysisTitle, setAnalysisTitle] = useState('');
  const [analysisContent, setAnalysisContent] = useState('');
  const [analysisType, setAnalysisType] = useState<NvcScenarioType>('WORKPLACE');

  useEffect(() => {
    Promise.all([
      profileApi.getProfile(userId).catch(() => null),
      profileApi.getCommunicationRecords(userId).catch(() => []),
    ])
      .then(([p, r]) => {
        setProfile(p);
        setRecords(r);
        if (p) {
          setForm({
            communicationBackground: p.communicationBackground ?? '',
            personalityTraits: p.personalityTraits ?? '',
            communicationStyle: p.communicationStyle ?? '',
            emotionalTriggers: p.emotionalTriggers ?? '',
            commonScenarios: p.commonScenarios ?? '',
            relationshipTypes: p.relationshipTypes ?? '',
          });
        }
      })
      .finally(() => setLoading(false));
  }, [userId]);

  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      const updated = await profileApi.updateProfile(userId, form);
      setProfile(updated);
    } catch (err) {
      console.error('Failed to save profile:', err);
    } finally {
      setSaving(false);
    }
  }, [userId, form]);

  const handleAnalyze = useCallback(async () => {
    if (!analysisTitle.trim() || !analysisContent.trim()) return;
    setAnalyzing(true);
    try {
      const record = await profileApi.analyzeCommunication(userId, {
        title: analysisTitle,
        rawContent: analysisContent,
        scenarioType: analysisType,
      });
      setRecords((prev) => [record, ...prev]);
      setAnalysisTitle('');
      setAnalysisContent('');
    } catch (err) {
      console.error('Failed to analyze:', err);
    } finally {
      setAnalyzing(false);
    }
  }, [userId, analysisTitle, analysisContent, analysisType]);

  const updateForm = useCallback(
    (key: keyof UserProfileUpdateRequest, value: string) => {
      setForm((prev) => ({ ...prev, [key]: value }));
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

  const fields: {
    key: keyof UserProfileUpdateRequest;
    label: string;
    placeholder: string;
  }[] = [
    {
      key: 'communicationBackground',
      label: '沟通背景',
      placeholder: '描述你的沟通环境和背景...',
    },
    {
      key: 'personalityTraits',
      label: '性格特征',
      placeholder: '描述你的性格特点...',
    },
    {
      key: 'communicationStyle',
      label: '沟通风格',
      placeholder: '你的沟通风格是怎样的...',
    },
    {
      key: 'emotionalTriggers',
      label: '情绪触发点',
      placeholder: '哪些情况容易触发你的情绪...',
    },
    {
      key: 'commonScenarios',
      label: '常见场景',
      placeholder: '你经常遇到的沟通场景...',
    },
    {
      key: 'relationshipTypes',
      label: '关系类型',
      placeholder: '你需要改善的关系类型...',
    },
  ];

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800 dark:text-white">
          个人档案
        </h1>
        <p className="text-slate-500 dark:text-slate-400 mt-1">
          完善个人信息，获得更个性化的 NVC 练习体验
        </p>
      </div>

      {/* 档案编辑 */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="bg-white dark:bg-slate-800 rounded-2xl p-6 border border-slate-200 dark:border-slate-700"
      >
        <div className="flex items-center gap-2 mb-6">
          <User className="w-5 h-5 text-primary-500" />
          <h2 className="text-lg font-semibold text-slate-800 dark:text-white">
            档案信息
          </h2>
        </div>

        <div className="space-y-4">
          {fields.map((field) => (
            <div key={field.key}>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                {field.label}
              </label>
              <textarea
                value={form[field.key] || ''}
                onChange={(e) => updateForm(field.key, e.target.value)}
                placeholder={field.placeholder}
                rows={2}
                className="w-full border border-slate-200 dark:border-slate-700 rounded-xl px-4 py-2.5 text-sm bg-white dark:bg-slate-900 text-slate-800 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
              />
            </div>
          ))}
        </div>

        <div className="mt-6 flex justify-end">
          <button
            onClick={handleSave}
            disabled={saving}
            className="flex items-center gap-2 px-5 py-2.5 bg-primary-500 text-white rounded-xl hover:bg-primary-600 transition-colors disabled:opacity-50"
          >
            {saving ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Save className="w-4 h-4" />
            )}
            保存档案
          </button>
        </div>
      </motion.div>

      {/* 沟通记录分析 */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="bg-white dark:bg-slate-800 rounded-2xl p-6 border border-slate-200 dark:border-slate-700"
      >
        <div className="flex items-center gap-2 mb-6">
          <Plus className="w-5 h-5 text-primary-500" />
          <h2 className="text-lg font-semibold text-slate-800 dark:text-white">
            沟通记录分析
          </h2>
        </div>

        <div className="space-y-4">
          <div className="flex gap-4">
            <div className="flex-1">
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                标题
              </label>
              <input
                value={analysisTitle}
                onChange={(e) => setAnalysisTitle(e.target.value)}
                placeholder="给这次沟通起个标题..."
                className="w-full border border-slate-200 dark:border-slate-700 rounded-xl px-4 py-2.5 text-sm bg-white dark:bg-slate-900 text-slate-800 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                场景类型
              </label>
              <select
                value={analysisType}
                onChange={(e) => setAnalysisType(e.target.value as NvcScenarioType)}
                className="border border-slate-200 dark:border-slate-700 rounded-xl px-4 py-2.5 text-sm bg-white dark:bg-slate-900 text-slate-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500"
              >
                {SCENARIO_TYPE_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
              沟通内容
            </label>
            <textarea
              value={analysisContent}
              onChange={(e) => setAnalysisContent(e.target.value)}
              placeholder="粘贴或描述你的沟通内容，AI 将帮你分析并给出 NVC 改进建议..."
              rows={4}
              className="w-full border border-slate-200 dark:border-slate-700 rounded-xl px-4 py-2.5 text-sm bg-white dark:bg-slate-900 text-slate-800 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
            />
          </div>

          <div className="flex justify-end">
            <button
              onClick={handleAnalyze}
              disabled={analyzing || !analysisTitle.trim() || !analysisContent.trim()}
              className="flex items-center gap-2 px-5 py-2.5 bg-primary-500 text-white rounded-xl hover:bg-primary-600 transition-colors disabled:opacity-50"
            >
              {analyzing ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Send className="w-4 h-4" />
              )}
              AI 分析
            </button>
          </div>
        </div>
      </motion.div>

      {/* 历史沟通记录 */}
      {records.length > 0 && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="bg-white dark:bg-slate-800 rounded-2xl p-6 border border-slate-200 dark:border-slate-700"
        >
          <div className="flex items-center gap-2 mb-6">
            <FileText className="w-5 h-5 text-primary-500" />
            <h2 className="text-lg font-semibold text-slate-800 dark:text-white">
              沟通记录
            </h2>
          </div>

          <div className="space-y-4">
            {records.map((record) => (
              <div
                key={record.id}
                className="bg-slate-50 dark:bg-slate-900 rounded-xl p-4"
              >
                <div className="flex items-center justify-between mb-2">
                  <h4 className="font-medium text-slate-800 dark:text-white">
                    {record.title}
                  </h4>
                  <span className="text-xs text-slate-500">
                    {record.createdAt
                      ? new Date(record.createdAt).toLocaleDateString('zh-CN')
                      : ''}
                  </span>
                </div>
                <p className="text-sm text-slate-500 dark:text-slate-400 mb-2 line-clamp-2">
                  {record.rawContent}
                </p>
                {record.analysisResult && (
                  <div className="mt-3 p-3 bg-white dark:bg-slate-800 rounded-lg border border-slate-200 dark:border-slate-700">
                    <p className="text-xs font-medium text-slate-500 mb-1">
                      分析结果
                    </p>
                    <p className="text-sm text-slate-700 dark:text-slate-300">
                      {record.analysisResult}
                    </p>
                  </div>
                )}
                {record.nvcSuggestion && (
                  <div className="mt-2 p-3 bg-primary-50 dark:bg-primary-900/20 rounded-lg">
                    <p className="text-xs font-medium text-primary-500 mb-1">
                      NVC 改进建议
                    </p>
                    <p className="text-sm text-slate-700 dark:text-slate-300">
                      {record.nvcSuggestion}
                    </p>
                  </div>
                )}
              </div>
            ))}
          </div>
        </motion.div>
      )}
    </div>
  );
}
