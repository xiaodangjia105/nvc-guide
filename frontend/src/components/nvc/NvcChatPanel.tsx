import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Send, Loader2, Bot, User } from 'lucide-react';
import { practiceApi } from '../../api/nvc';
import type { EvaluationCardData } from '../../types/nvc';

interface NvcChatPanelProps {
  sessionId: number;
  onEvaluation?: (data: EvaluationCardData) => void;
  onStepAdvance?: () => void;
}

interface DisplayMessage {
  id: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  agentScene?: string;
  isStreaming?: boolean;
}

function parseEvaluationFromSSE(text: string): EvaluationCardData | null {
  try {
    const match = text.match(/<evaluation>([\s\S]*?)<\/evaluation>/);
    if (match) {
      return JSON.parse(match[1]);
    }
  } catch {
    // ignore
  }
  return null;
}

export default function NvcChatPanel({
  sessionId,
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
            content: m.content,
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

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.slice(5).trim();
            if (data === '[DONE]') continue;

            fullContent += data;
            setMessages((prev) =>
              prev.map((m) =>
                m.id === aiMsgId
                  ? { ...m, content: fullContent }
                  : m
              )
            );
          }
        }
      }

      // 解析评估数据
      const evaluation = parseEvaluationFromSSE(fullContent);
      if (evaluation) {
        onEvaluation?.(evaluation);
      }

      // 检查步骤推进
      if (fullContent.includes('STEP_ADVANCE')) {
        onStepAdvance?.();
      }

      // 标记流式结束
      setMessages((prev) =>
        prev.map((m) =>
          m.id === aiMsgId
            ? { ...m, isStreaming: false }
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
            <p className="text-sm">开始你的 NVC 练习吧</p>
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
            placeholder="输入你的 NVC 表达..."
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
