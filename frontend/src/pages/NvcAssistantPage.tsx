import { useCallback, useEffect, useRef, useState } from 'react';
import { Bot } from 'lucide-react';
import { assistantApi, sendChatStream } from '../api/nvc-assistant';
import type { ConversationResponse, ToolCallRecord, StreamEvent } from '../api/nvc-assistant';
import { useUserId } from '../hooks/useUserId';
import NvcAssistantSidebar from '../components/nvc/NvcAssistantSidebar';
import NvcAssistantChat from '../components/nvc/NvcAssistantChat';
import type { DisplayMessage, PracticePreviewData } from '../components/nvc/NvcAssistantChat';

export default function NvcAssistantPage() {
  const [userId] = useUserId();

  // 对话状态
  const [conversations, setConversations] = useState<ConversationResponse[]>([]);
  const [activeConversationId, setActiveConversationId] = useState<number | null>(null);
  const [messages, setMessages] = useState<DisplayMessage[]>([]);
  const [loadingConversations, setLoadingConversations] = useState(true);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [isStreaming, setIsStreaming] = useState(false);

  // 流式状态跟踪
  const streamAbortRef = useRef<AbortController | null>(null);
  const streamContentRef = useRef('');
  const streamToolCallsRef = useRef<ToolCallRecord[]>([]);
  const streamAiMsgIdRef = useRef<string>('');

  // 加载对话列表
  useEffect(() => {
    setLoadingConversations(true);
    assistantApi.getConversations(userId)
      .then(setConversations)
      .catch(console.error)
      .finally(() => setLoadingConversations(false));
  }, [userId]);

  // 加载对话消息
  const loadMessages = useCallback(async (conversationId: number) => {
    setLoadingMessages(true);
    try {
      const raw = await assistantApi.getMessages(userId, conversationId);
      const display: DisplayMessage[] = raw.map((m) => ({
        id: `msg-${m.id}`,
        role: m.role as 'USER' | 'ASSISTANT',
        content: m.content,
        toolCalls: m.toolCalls || [],
        practicePreview: null,
      }));
      setMessages(display);
    } catch (err) {
      console.error('Failed to load messages:', err);
      setMessages([]);
    } finally {
      setLoadingMessages(false);
    }
  }, [userId]);

  // 切换对话
  const handleSelectConversation = useCallback((id: number) => {
    setActiveConversationId(id);
    loadMessages(id);
  }, [loadMessages]);

  // 新建对话
  const handleNewConversation = useCallback(() => {
    setActiveConversationId(null);
    setMessages([]);
  }, []);

  // 删除对话
  const handleDeleteConversation = useCallback(async (id: number) => {
    try {
      await assistantApi.deleteConversation(userId, id);
      setConversations((prev) => prev.filter((c) => c.id !== id));
      if (activeConversationId === id) {
        setActiveConversationId(null);
        setMessages([]);
      }
    } catch (err) {
      console.error('Failed to delete conversation:', err);
    }
  }, [userId, activeConversationId]);

  // 停止流式
  const handleStopStream = useCallback(() => {
    streamAbortRef.current?.abort();
    streamAbortRef.current = null;
    setIsStreaming(false);

    // 将当前流式内容标记为完成
    if (streamAiMsgIdRef.current) {
      setMessages((prev) =>
        prev.map((m) =>
          m.id === streamAiMsgIdRef.current
            ? { ...m, isStreaming: false, isThinking: false }
            : m
        )
      );
    }
  }, []);

  // 重新生成
  const handleRegenerate = useCallback(() => {
    if (messages.length < 2) return;
    // 找到最后一个用户消息
    const lastUserIdx = [...messages].reverse().findIndex((m) => m.role === 'USER');
    if (lastUserIdx === -1) return;
    const lastUserMsg = messages[messages.length - 1 - lastUserIdx];
    // 移除最后的助手消息
    setMessages((prev) => prev.filter((m) => m.role === 'USER'));
    // 重新发送
    handleSend(lastUserMsg.content);
  }, [messages]);

  // 发送消息
  const handleSend = useCallback((text: string) => {
    // 添加用户消息
    const userMsg: DisplayMessage = {
      id: `user-${Date.now()}`,
      role: 'USER',
      content: text,
      toolCalls: [],
      practicePreview: null,
    };

    // 添加 AI 占位消息
    const aiMsgId = `ai-${Date.now()}`;
    const aiMsg: DisplayMessage = {
      id: aiMsgId,
      role: 'ASSISTANT',
      content: '',
      toolCalls: [],
      isStreaming: true,
      isThinking: true,
      practicePreview: null,
    };

    setMessages((prev) => [...prev, userMsg, aiMsg]);
    setIsStreaming(true);

    // 重置流式状态
    streamContentRef.current = '';
    streamToolCallsRef.current = [];
    streamAiMsgIdRef.current = aiMsgId;

    // 当前 tool_call 的临时存储
    let currentToolCall: { toolName: string; arguments: string; startTime: number } | null = null;

    const abortController = sendChatStream(
      userId,
      { conversationId: activeConversationId, message: text },
      (event: StreamEvent) => {
        switch (event.type) {
          case 'thinking':
            setMessages((prev) =>
              prev.map((m) =>
                m.id === aiMsgId ? { ...m, isThinking: true } : m
              )
            );
            break;

          case 'tool_call':
            // 开始一个新的工具调用
            currentToolCall = {
              toolName: (event.data.toolName as string) || 'unknown',
              arguments: (event.data.arguments as string) || '{}',
              startTime: Date.now(),
            };
            setMessages((prev) =>
              prev.map((m) => {
                if (m.id !== aiMsgId) return m;
                return {
                  ...m,
                  isThinking: false,
                  toolCalls: [
                    ...m.toolCalls,
                    {
                      toolName: currentToolCall!.toolName,
                      arguments: currentToolCall!.arguments,
                      result: '执行中...',
                      success: true,
                      durationMs: 0,
                    },
                  ],
                };
              })
            );
            break;

          case 'tool_result': {
            const duration = currentToolCall
              ? Date.now() - currentToolCall.startTime
              : ((event.data.durationMs as number) || 0);
            const resultStr = typeof event.data.result === 'string'
              ? event.data.result
              : JSON.stringify(event.data.result);
            const success = event.data.success !== false;

            setMessages((prev) =>
              prev.map((m) => {
                if (m.id !== aiMsgId) return m;
                const tcs = [...m.toolCalls];
                if (tcs.length > 0) {
                  tcs[tcs.length - 1] = {
                    ...tcs[tcs.length - 1],
                    result: resultStr,
                    success,
                    durationMs: duration,
                  };
                }

                // 检查是否是 practice_start 工具结果
                let practicePreview: PracticePreviewData | null = m.practicePreview ?? null;
                if (currentToolCall?.toolName === 'create_practice_session' && success) {
                  try {
                    const parsed = JSON.parse(resultStr);
                    if (parsed.sessionId) {
                      practicePreview = {
                        sessionId: parsed.sessionId,
                        scenarioTitle: parsed.scenarioTitle || parsed.title || '练习场景',
                        scenarioDescription: parsed.scenarioDescription || parsed.description,
                        practiceMode: parsed.practiceMode || 'SCENARIO',
                        difficulty: parsed.difficulty,
                      };
                    }
                  } catch {
                    // ignore parse errors
                  }
                }

                return { ...m, toolCalls: tcs, practicePreview, isThinking: false };
              })
            );
            currentToolCall = null;
            break;
          }

          case 'content': {
            const token = (event.data.token as string) || (event.data.content as string) || '';
            streamContentRef.current += token;
            const content = streamContentRef.current;
            setMessages((prev) =>
              prev.map((m) =>
                m.id === aiMsgId
                  ? { ...m, content, isThinking: false, isStreaming: true }
                  : m
              )
            );
            break;
          }

          case 'done':
            setMessages((prev) =>
              prev.map((m) =>
                m.id === aiMsgId
                  ? { ...m, isStreaming: false, isThinking: false }
                  : m
              )
            );
            setIsStreaming(false);
            streamAbortRef.current = null;

            // 刷新对话列表（可能创建了新对话）
            assistantApi.getConversations(userId)
              .then(setConversations)
              .catch(() => {});
            break;

          case 'error':
            setMessages((prev) =>
              prev.map((m) =>
                m.id === aiMsgId
                  ? {
                      ...m,
                      content: m.content || '抱歉，发生了错误，请重试。',
                      isStreaming: false,
                      isThinking: false,
                    }
                  : m
              )
            );
            setIsStreaming(false);
            streamAbortRef.current = null;
            break;
        }
      },
      (error: Error) => {
        console.error('Stream error:', error);
        setMessages((prev) =>
          prev.map((m) =>
            m.id === aiMsgId
              ? {
                  ...m,
                  content: m.content || '发送失败，请检查网络后重试。',
                  isStreaming: false,
                  isThinking: false,
                }
              : m
          )
        );
        setIsStreaming(false);
      }
    );

    streamAbortRef.current = abortController;
  }, [userId, activeConversationId]);

  // 组件卸载时取消流式请求
  useEffect(() => {
    return () => {
      streamAbortRef.current?.abort();
    };
  }, []);

  return (
    <div className="h-[calc(100vh-5rem)] flex flex-col">
      {/* 头部 */}
      <div className="flex items-center gap-3 mb-4 flex-shrink-0">
        <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary-500 to-indigo-500 flex items-center justify-center shadow-lg shadow-primary-500/30">
          <Bot className="w-5 h-5 text-white" />
        </div>
        <div>
          <h1 className="text-xl font-bold text-slate-800 dark:text-white">
            NVC AI 助手
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            你的非暴力沟通练习伙伴
          </p>
        </div>
      </div>

      {/* 主体区域 */}
      <div className="flex-1 flex gap-0 min-h-0 bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 overflow-hidden">
        {/* 左侧对话列表 */}
        <NvcAssistantSidebar
          conversations={conversations}
          activeId={activeConversationId}
          loading={loadingConversations}
          onSelect={handleSelectConversation}
          onNew={handleNewConversation}
          onDelete={handleDeleteConversation}
        />

        {/* 右侧对话区域 */}
        <div className="flex-1 flex flex-col min-w-0">
          <NvcAssistantChat
            messages={messages}
            loading={loadingMessages}
            isStreaming={isStreaming}
            onSend={handleSend}
            onStopStream={handleStopStream}
            onRegenerate={handleRegenerate}
          />
        </div>
      </div>
    </div>
  );
}
