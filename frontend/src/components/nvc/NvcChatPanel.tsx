import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Send, Loader2, Bot, User } from 'lucide-react';
import { practiceApi } from '../../api/nvc';
import { consumeSSEEvents } from '../../utils/sse';
import NvcFeedbackButtons from './NvcFeedbackButtons';

interface NvcChatPanelProps {
  sessionId: number;
  practiceMode?: 'SCENARIO' | 'FREE_DIALOG' | 'STRUCTURED_FOUR_STEP';
  onMessageSent?: () => void;
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
  /** 数据库消息 ID（历史消息有，流式消息完成后更新） */
  dbMessageId?: number;
}

/**
 * 清理 AI 回复中的 JSON/代码块（前端兜底）
 */
function cleanAiResponse(raw: string): string {
  let result = raw;
  // 1. 去除代码块
  result = result.replace(/```[a-zA-Z]*\s*\n[\s\S]*?```/g, '');
  // 2. 去除独立 JSON 对象/数组段落
  result = result.replace(/^\s*\{[\s\S]*?\}\s*$/gm, '');
  result = result.replace(/^\s*\[[\s\S]*?\]\s*$/gm, '');
  // 3. 去除混合内容中的 JSON 块（大括号计数处理嵌套）
  result = removeInlineJson(result);
  // 4. 压缩多余空行
  result = result.replace(/\n{3,}/g, '\n\n');
  return result.trim();
}

/**
 * 移除混合内容中的 JSON 块
 */
function removeInlineJson(text: string): string {
  let result = '';
  let i = 0;
  while (i < text.length) {
    if (text[i] === '{') {
      let j = i + 1;
      while (j < text.length && /\s/.test(text[j])) j++;
      if (j < text.length && text[j] === '"') {
        let depth = 0;
        let valid = true;
        let end = i;
        for (let k = i; k < text.length; k++) {
          if (text[k] === '{') depth++;
          else if (text[k] === '}') {
            depth--;
            if (depth === 0) { end = k + 1; break; }
          }
          if (k === text.length - 1 && depth > 0) valid = false;
        }
        if (valid && end > i) { i = end; continue; }
      }
    }
    result += text[i];
    i++;
  }
  return result;
}

export default function NvcChatPanel({
  sessionId,
  practiceMode = 'FREE_DIALOG',
  onMessageSent,
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
      .then((res) => {
        // 后端返回分页结果，提取 content 数组
        const msgs = (res as any)?.content ?? res;
        const display: DisplayMessage[] = (Array.isArray(msgs) ? msgs : [])
          .filter((m: any) => m.role === 'USER' || m.role === 'ASSISTANT')
          .map((m: any) => ({
            id: String(m.id),
            role: m.role as 'USER' | 'ASSISTANT',
            content: m.role === 'ASSISTANT' ? cleanAiResponse(m.content) : m.content,
            agentScene: m.agentScene ?? undefined,
            dbMessageId: m.id,
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
      let fullContent = '';

      await consumeSSEEvents(response, {
        onEvent: (type, data) => {
          // 只处理 message 事件（跳过 metadata 和 done）
          if (type && type !== 'message') return;
          const token = (data as string).replace(/\\n/g, '\n').replace(/\\r/g, '\r');
          fullContent += token;
          setMessages((prev) =>
            prev.map((m) =>
              m.id === aiMsgId
                ? { ...m, content: fullContent }
                : m
            )
          );
        },
        onComplete: () => {
          // 标记流式结束，应用前端兜底清理
          setMessages((prev) =>
            prev.map((m) =>
              m.id === aiMsgId
                ? { ...m, content: cleanAiResponse(m.content), isStreaming: false }
                : m
            )
          );
          // 重新加载消息以获取数据库 ID（用于反馈按钮）
          practiceApi.getMessages(sessionId)
            .then((res) => {
              // 后端返回分页结果，提取 content 数组
              const msgs = (res as any)?.content ?? res;
              const arr = Array.isArray(msgs) ? msgs : [];
              const lastAiMsg = [...arr].reverse().find((m: any) => m.role === 'ASSISTANT');
              if (lastAiMsg) {
                setMessages((prev) =>
                  prev.map((m) =>
                    m.id === aiMsgId
                      ? { ...m, dbMessageId: lastAiMsg.id, agentScene: lastAiMsg.agentScene ?? undefined }
                      : m
                  )
                );
              }
            })
            .catch(() => {}); // 静默失败，不影响用户体验
        },
        onError: (err) => {
          throw err;
        },
      });
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
      onMessageSent?.();
    }
  }, [sessionId, input, isStreaming, onMessageSent]);

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

                  {/* 消息气泡 + 反馈按钮 */}
                  <div className="flex flex-col">
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
                    {/* 👍/👎 反馈按钮 — 仅对已完成的 AI 消息显示 */}
                    {!isUser && !msg.isStreaming && msg.dbMessageId && (
                      <NvcFeedbackButtons
                        messageId={msg.dbMessageId}
                        sessionId={sessionId}
                        messageSource="PRACTICE"
                        agentScene={msg.agentScene}
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
