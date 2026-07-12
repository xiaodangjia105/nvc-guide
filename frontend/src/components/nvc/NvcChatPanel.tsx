import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Send, Loader2, Bot, User } from 'lucide-react';
import { practiceApi } from '../../api/nvc';
import type { EvaluationCardData } from '../../types/nvc';

interface NvcChatPanelProps {
  sessionId: number;
  practiceMode?: 'SCENARIO' | 'FREE_DIALOG' | 'STRUCTURED_FOUR_STEP';
  onEvaluation?: (data: EvaluationCardData) => void;
  onStepAdvance?: () => void;
}

const PLACEHOLDER_MAP: Record<string, string> = {
  FREE_DIALOG: '描述你遇到的沟通问题，我来帮你梳理...',
  SCENARIO: '用 NVC 的方式回应对方...',
  STRUCTURED_FOUR_STEP: '试着用 NVC 的方式表达...',
};

interface DisplayMessage {
  id: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  agentScene?: string;
  isStreaming?: boolean;
}

function mapEvaluationToCardData(raw: any): EvaluationCardData | null {
  if (!raw) return null;
  const dim = (score: number | null, detail: string | null) => ({
    score: score ?? 0,
    passed: (score ?? 0) >= 60,
    detail: detail ?? '',
  });
  return {
    observation: dim(raw.observationScore, raw.observationDetail),
    feeling: dim(raw.feelingScore, raw.feelingDetail),
    need: dim(raw.needScore, raw.needDetail),
    request: dim(raw.requestScore, raw.requestDetail),
    empathy: dim(raw.empathyScore, raw.empathyDetail),
    overall: raw.overallScore ?? 0,
  };
}

/**
 * 清理 AI 回复中的 JSON/代码块（前端兜底）
 */
function cleanAiResponse(raw: string): string {
  let result = raw;
  // 去除 ```json ... ``` 代码块
  result = result.replace(/```[a-zA-Z]*\s*\n[\s\S]*?```/g, '');
  // 去除独立的 JSON 对象段落（以 { 开头，以 } 结尾的段落）
  result = result.replace(/^\s*\{[\s\S]*?\}\s*$/gm, '');
  // 压缩多余空行
  result = result.replace(/\n{3,}/g, '\n\n');
  return result.trim();
}

export default function NvcChatPanel({
  sessionId,
  practiceMode = 'FREE_DIALOG',
  onEvaluation,
  onStepAdvance,
}: NvcChatPanelProps) {
  const [messages, setMessages] = useState<DisplayMessage[]>([]);
  const [input, setInput] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [isLoadingHistory, setIsLoadingHistory] = useState(true);
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // 加载历史消息
  useEffect(() => {
    practiceApi.getMessages(sessionId)
      .then((msgs) => {
        const display: DisplayMessage[] = msgs
          .filter((m) => m.role === 'USER' || m.role === 'ASSISTANT')
          .map((m) => ({
            id: String(m.id),
            role: m.role as 'USER' | 'ASSISTANT',
            content: m.role === 'ASSISTANT' ? cleanAiResponse(m.content) : m.content,
            agentScene: m.agentScene ?? undefined,
          }));
        setMessages(display);
      })
      .catch(console.error)
      .finally(() => setIsLoadingHistory(false));
  }, [sessionId]);

  // 自动滚动到底部
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const sendMessage = useCallback(async () => {
    const text = input.trim();
    if (!text || isStreaming) return;

    setInput('');
    setIsStreaming(true);

    // 添加用户消息
    const userMsg: DisplayMessage = {
      id: `user-${Date.now()}`,
      role: 'USER',
      content: text,
    };
    setMessages((prev) => [...prev, userMsg]);

    // 添加 AI 占位消息
    const aiMsgId = `ai-${Date.now()}`;
    const aiMsg: DisplayMessage = {
      id: aiMsgId,
      role: 'ASSISTANT',
      content: '',
      isStreaming: true,
    };
    setMessages((prev) => [...prev, aiMsg]);

    try {
      const response = await practiceApi.sendMessageStream(sessionId, text);
      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error('No reader available');
      }

      const decoder = new TextDecoder();
      let buffer = '';
      let fullContent = '';
      let currentEvent = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            const data = line.slice(5).trim();
            if (data === '[DONE]') continue;

            // 只处理 message 事件的内容
            if (currentEvent === 'message') {
              // 后端转义了换行符，还原回来
              const token = data.replace(/\\n/g, '\n').replace(/\\r/g, '\r');
              fullContent += token;
              setMessages((prev) =>
                prev.map((m) =>
                  m.id === aiMsgId
                    ? { ...m, content: fullContent }
                    : m
                )
              );
            }
            currentEvent = '';
          }
        }
      }

      // 从 API 获取最新实时评估（带重试，等待异步评估完成）
      for (let attempt = 0; attempt < 3; attempt++) {
        try {
          if (attempt > 0) {
            await new Promise((r) => setTimeout(r, 1500));
          }
          const rawEval = await practiceApi.getEvaluation(sessionId);
          const evaluation = mapEvaluationToCardData(rawEval);
          if (evaluation && evaluation.overall > 0) {
            onEvaluation?.(evaluation);
            break;
          }
        } catch {
          // 评估可能尚未完成，重试
        }
      }

      // 标记流式结束，应用前端兜底清理
      setMessages((prev) =>
        prev.map((m) =>
          m.id === aiMsgId
            ? { ...m, content: cleanAiResponse(m.content), isStreaming: false }
            : m
        )
      );
    } catch (err) {
      console.error('Stream error:', err);
      setMessages((prev) =>
        prev.map((m) =>
          m.id === aiMsgId
            ? { ...m, content: '发送失败，请重试', isStreaming: false }
            : m
        )
      );
    } finally {
      setIsStreaming(false);
      inputRef.current?.focus();
    }
  }, [sessionId, input, isStreaming, onEvaluation, onStepAdvance]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
      }
    },
    [sendMessage]
  );

  if (isLoadingHistory) {
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
        className="flex-1 overflow-y-auto p-4 space-y-4"
      >
        {messages.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full text-slate-400">
            <Bot className="w-12 h-12 mb-3" />
            <p className="text-sm">
              {practiceMode === 'FREE_DIALOG'
                ? '描述你遇到的沟通问题，我来帮你梳理'
                : practiceMode === 'SCENARIO'
                ? '准备好了吗？开始场景对话'
                : '开始你的 NVC 四要素练习'}
            </p>
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
                <div className={`flex gap-2 max-w-[80%] ${isUser ? 'flex-row-reverse' : ''}`}>
                  {/* 头像 */}
                  <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${
                    isUser
                      ? 'bg-primary-500 text-white'
                      : 'bg-slate-200 dark:bg-slate-700 text-slate-500'
                  }`}>
                    {isUser ? <User className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
                  </div>

                  {/* 消息气泡 */}
                  <div className={`px-4 py-2.5 rounded-2xl text-sm leading-relaxed ${
                    isUser
                      ? 'bg-primary-500 text-white rounded-br-md'
                      : 'bg-slate-100 dark:bg-slate-700 text-slate-800 dark:text-slate-200 rounded-bl-md'
                  }`}>
                    {isUser ? (
                      msg.content
                    ) : (
                      <div className="prose prose-sm dark:prose-invert max-w-none">
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>
                          {msg.content}
                        </ReactMarkdown>
                      </div>
                    )}
                    {msg.isStreaming && (
                      <motion.span
                        className="inline-block w-1.5 h-4 bg-primary-400 ml-0.5"
                        animate={{ opacity: [1, 0.25, 1] }}
                        transition={{ duration: 0.8, repeat: Infinity }}
                      />
                    )}
                  </div>
                </div>
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>

      {/* 输入区域 */}
      <div className="border-t border-slate-200 dark:border-slate-700 p-4">
        <div className="flex gap-2">
          <input
            ref={inputRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={PLACEHOLDER_MAP[practiceMode] || '输入你的 NVC 表达...'}
            disabled={isStreaming}
            className="flex-1 border border-slate-200 dark:border-slate-700 rounded-xl px-4 py-2.5 text-sm bg-white dark:bg-slate-800 text-slate-800 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent disabled:opacity-50"
          />
          <button
            onClick={sendMessage}
            disabled={!input.trim() || isStreaming}
            className="px-5 py-2.5 bg-primary-500 text-white rounded-xl hover:bg-primary-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
          >
            {isStreaming ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Send className="w-4 h-4" />
            )}
            发送
          </button>
        </div>
      </div>
    </div>
  );
}
