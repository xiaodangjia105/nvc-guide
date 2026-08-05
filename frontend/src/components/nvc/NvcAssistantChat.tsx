import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Send, Loader2, Bot, User, StopCircle, RefreshCw } from 'lucide-react';
import NvcToolCallCard from './NvcToolCallCard';
import NvcPracticePreviewCard from './NvcPracticePreviewCard';
import NvcFeedbackButtons from './NvcFeedbackButtons';
import type { ToolCallRecord } from '../../api/nvc-assistant';

/**
 * LLM 输出用 \n 换行，但 Markdown 规范中单个 \n 是软换行（渲染为空格）。
 * 转成 \n\n 让 ReactMarkdown 正确渲染为段落/换行。
 */
function normalizeLlmNewlines(text: string): string {
  // 已经是 \n\n 的不动，单个 \n 转成 \n\n
  return text.replace(/(?<!\n)\n(?!\n)/g, '\n\n');
}

// ==================== 消息类型 ====================

interface DisplayMessage {
  id: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  toolCalls: ToolCallRecord[];
  isStreaming?: boolean;
  isThinking?: boolean;
  practicePreview?: PracticePreviewData | null;
  /** 数据库消息 ID（用于反馈） */
  dbMessageId?: number;
  /** 会话 ID（用于反馈） */
  sessionId?: number;
  /** Agent 场景（用于反馈） */
  agentScene?: string;
}

interface PracticePreviewData {
  sessionId: number;
  scenarioTitle: string;
  scenarioDescription?: string;
  practiceMode: string;
  difficulty?: string;
}

interface NvcAssistantChatProps {
  messages: DisplayMessage[];
  loading: boolean;
  isStreaming: boolean;
  onSend: (message: string) => void;
  onStopStream?: () => void;
  onRegenerate?: () => void;
}

export type { DisplayMessage, PracticePreviewData };

export default function NvcAssistantChat({
  messages,
  loading,
  isStreaming,
  onSend,
  onStopStream,
  onRegenerate,
}: NvcAssistantChatProps) {
  const [input, setInput] = useState('');
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // 自动滚动到底部
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const handleSend = useCallback(() => {
    const text = input.trim();
    if (!text || isStreaming) return;
    setInput('');
    onSend(text);
  }, [input, isStreaming, onSend]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSend();
      }
    },
    [handleSend]
  );

  // 自动调整 textarea 高度
  const handleInputChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInput(e.target.value);
    const el = e.target;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 120)}px`;
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full">
        <Loader2 className="w-6 h-6 animate-spin text-primary-500" />
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full">
      {/* 消息列表 */}
      <div
        ref={scrollRef}
        className="flex-1 overflow-y-auto px-6 py-4 space-y-4"
      >
        {messages.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full text-slate-400">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-primary-500 to-indigo-500 flex items-center justify-center mb-4 shadow-lg shadow-primary-500/30">
              <Bot className="w-8 h-8 text-white" />
            </div>
            <h3 className="text-lg font-semibold text-slate-600 dark:text-slate-300 mb-2">
              NVC AI 助手
            </h3>
            <p className="text-sm text-slate-400 dark:text-slate-500 text-center max-w-md">
              我是你的非暴力沟通练习助手。你可以向我提问 NVC 理论、请求推荐练习场景、查看练习数据，或者直接开始一段对话练习。
            </p>
            <div className="flex flex-wrap justify-center gap-2 mt-6">
              {['推荐一个练习场景', '什么是非暴力沟通的四要素？', '查看我的练习数据'].map((hint) => (
                <button
                  key={hint}
                  onClick={() => {
                    setInput(hint);
                    inputRef.current?.focus();
                  }}
                  className="px-3 py-1.5 text-xs bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors text-slate-600 dark:text-slate-400"
                >
                  {hint}
                </button>
              ))}
            </div>
          </div>
        )}

        <AnimatePresence initial={false}>
          {messages.map((msg) => {
            const isUser = msg.role === 'USER';
            return (
              <motion.div
                key={msg.id}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}
              >
                <div className={`flex gap-3 max-w-[85%] ${isUser ? 'flex-row-reverse' : ''}`}>
                  {/* 头像 */}
                  <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${
                    isUser
                      ? 'bg-primary-500 text-white'
                      : 'bg-gradient-to-br from-primary-500 to-indigo-500 text-white shadow-md shadow-primary-500/20'
                  }`}>
                    {isUser ? <User className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
                  </div>

                  {/* 消息内容区 */}
                  <div className="flex flex-col gap-2 min-w-0">
                    {/* 思考中指示器 */}
                    {msg.isThinking && (
                      <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        className="flex items-center gap-2 text-sm text-slate-400 dark:text-slate-500"
                      >
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        <span>思考中...</span>
                      </motion.div>
                    )}

                    {/* 工具调用卡片 */}
                    {msg.toolCalls.length > 0 && (
                      <div className="space-y-2">
                        {msg.toolCalls.map((tc, idx) => (
                          <NvcToolCallCard key={`${msg.id}-tc-${idx}`} toolCall={tc} />
                        ))}
                      </div>
                    )}

                    {/* 练习预览卡片 */}
                    {msg.practicePreview && (
                      <NvcPracticePreviewCard data={msg.practicePreview} />
                    )}

                    {/* 文本消息气泡 + 反馈按钮 */}
                    {msg.content && (
                      <div className="flex flex-col">
                        <div className={`px-4 py-3 rounded-2xl text-sm leading-relaxed ${
                          isUser
                            ? 'bg-primary-500 text-white rounded-br-md'
                            : 'bg-slate-100 dark:bg-slate-700 text-slate-800 dark:text-slate-200 rounded-bl-md'
                        }`}>
                          {isUser ? (
                            <span className="whitespace-pre-wrap">{msg.content}</span>
                          ) : (
                            <div className="prose prose-sm dark:prose-invert max-w-none">
                              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                {normalizeLlmNewlines(msg.content)}
                              </ReactMarkdown>
                            </div>
                          )}
                          {msg.isStreaming && (
                            <motion.span
                              className="inline-block w-1.5 h-4 bg-primary-400 ml-0.5 align-middle"
                              animate={{ opacity: [1, 0.25, 1] }}
                              transition={{ duration: 0.8, repeat: Infinity }}
                            />
                          )}
                        </div>
                        {/* 👍/👎 反馈按钮 — 仅对已完成的 AI 消息显示 */}
                        {!isUser && !msg.isStreaming && !msg.isThinking && msg.dbMessageId && msg.sessionId && (
                          <NvcFeedbackButtons
                            messageId={msg.dbMessageId}
                            sessionId={msg.sessionId}
                            messageSource="ASSISTANT"
                            agentScene={msg.agentScene}
                          />
                        )}
                      </div>
                    )}
                  </div>
                </div>
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>

      {/* 输入区域 */}
      <div className="border-t border-slate-200 dark:border-slate-700 px-6 py-4">
        <div className="flex gap-3 items-end">
          <textarea
            ref={inputRef}
            value={input}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            placeholder="输入你的问题或 NVC 表达..."
            disabled={isStreaming}
            rows={1}
            className="flex-1 border border-slate-200 dark:border-slate-700 rounded-xl px-4 py-3 text-sm bg-white dark:bg-slate-800 text-slate-800 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent disabled:opacity-50 resize-none min-h-[44px] max-h-[120px]"
          />
          <div className="flex gap-2">
            {isStreaming && onStopStream && (
              <button
                onClick={onStopStream}
                className="px-4 py-3 bg-red-500 text-white rounded-xl hover:bg-red-600 transition-colors flex items-center gap-2"
                title="停止生成"
              >
                <StopCircle className="w-4 h-4" />
              </button>
            )}
            {!isStreaming && messages.length > 0 && messages[messages.length - 1].role === 'ASSISTANT' && onRegenerate && (
              <button
                onClick={onRegenerate}
                className="px-4 py-3 bg-slate-200 dark:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl hover:bg-slate-300 dark:hover:bg-slate-600 transition-colors flex items-center gap-2"
                title="重新生成"
              >
                <RefreshCw className="w-4 h-4" />
              </button>
            )}
            <button
              onClick={handleSend}
              disabled={!input.trim() || isStreaming}
              className="px-5 py-3 bg-primary-500 text-white rounded-xl hover:bg-primary-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
            >
              <Send className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
