import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowLeft, Loader2, Pause, Play, StopCircle,
} from 'lucide-react';
import { voiceApi } from '../api/nvc';
import AudioRecorder from '../components/AudioRecorder';
import AudioPlayer from '../components/AudioPlayer';
import RealtimeSubtitle from '../components/RealtimeSubtitle';
import type { VoiceSession } from '../types/nvc';

interface VoiceMessage {
  id: string;
  role: 'user' | 'ai';
  text: string;
}

type WSMessage =
  | { type: 'subtitle'; text: string; isFinal: boolean }
  | { type: 'audio'; data: string; text?: string }
  | { type: 'audio_chunk'; data: string; index: number; isLast: boolean }
  | { type: 'text'; content: string; final?: boolean }
  | { type: 'control'; action: string; message?: string }
  | { type: 'error'; message: string };

export default function NvcVoicePage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const sid = Number(sessionId);

  const [session, setSession] = useState<VoiceSession | null>(null);
  const [loading, setLoading] = useState(true);
  const [isRecording, setIsRecording] = useState(false);
  const [isAiSpeaking, setIsAiSpeaking] = useState(false);
  const [userText, setUserText] = useState('');
  const [aiText, setAiText] = useState('');
  const [messages, setMessages] = useState<VoiceMessage[]>([]);
  const [audioData, setAudioData] = useState<string | null>(null);
  const [isPaused, setIsPaused] = useState(false);

  const wsRef = useRef<WebSocket | null>(null);
  const audioChunksRef = useRef<Map<number, string>>(new Map());
  const msgCounterRef = useRef(0);

  // 加载会话信息
  useEffect(() => {
    if (!sid) return;
    voiceApi.getSession(sid)
      .then(setSession)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [sid]);

  // WebSocket 连接
  useEffect(() => {
    if (!session) return;

    const wsUrl = session.webSocketUrl
      || `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/nvc-voice/${sid}`;

    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    ws.onopen = () => {
      console.log('Voice WebSocket connected');
    };

    ws.onmessage = (event) => {
      try {
        const msg: WSMessage = JSON.parse(event.data);
        handleWsMessage(msg);
      } catch (err) {
        console.error('Failed to parse WS message:', err);
      }
    };

    ws.onclose = () => {
      console.log('Voice WebSocket closed');
    };

    ws.onerror = (err) => {
      console.error('Voice WebSocket error:', err);
    };

    return () => {
      ws.close();
      wsRef.current = null;
    };
  }, [session, sid]);

  const handleWsMessage = useCallback((msg: WSMessage) => {
    switch (msg.type) {
      case 'subtitle':
        if (msg.isFinal) {
          setUserText('');
          const id = `user-${++msgCounterRef.current}`;
          setMessages((prev) => [...prev, { id, role: 'user', text: msg.text }]);
        } else {
          setUserText(msg.text);
        }
        break;

      case 'audio':
        setIsAiSpeaking(true);
        if (msg.text) {
          setAiText(msg.text);
        }
        setAudioData(msg.data);
        if (msg.text) {
          const id = `ai-${++msgCounterRef.current}`;
          setMessages((prev) => [...prev, { id, role: 'ai', text: msg.text! }]);
        }
        break;

      case 'audio_chunk':
        audioChunksRef.current.set(msg.index, msg.data);
        if (msg.isLast) {
          const chunks: string[] = [];
          const sorted = [...audioChunksRef.current.entries()]
            .sort(([a], [b]) => a - b);
          for (const [, data] of sorted) {
            chunks.push(data);
          }
          audioChunksRef.current.clear();
          // 合并 chunks 并播放
          setAudioData(chunks.join(''));
        }
        break;

      case 'text':
        setAiText(msg.content);
        if (msg.final) {
          const id = `ai-${++msgCounterRef.current}`;
          setMessages((prev) => [...prev, { id, role: 'ai', text: msg.content }]);
          setAiText('');
        }
        break;

      case 'control':
        if (msg.action === 'pause') {
          setIsPaused(true);
        } else if (msg.action === 'resume') {
          setIsPaused(false);
        }
        break;

      case 'error':
        console.error('Voice error:', msg.message);
        break;
    }
  }, []);

  const handleAudioData = useCallback((base64: string) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({
        type: 'audio',
        data: base64,
        timestamp: Date.now(),
      }));
    }
  }, []);

  const handleSpeechEnd = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({
        type: 'control',
        action: 'submit',
        timestamp: Date.now(),
      }));
    }
  }, []);

  const handlePlayEnd = useCallback(() => {
    setIsAiSpeaking(false);
    setAudioData(null);
  }, []);

  const handlePause = useCallback(async () => {
    if (!sid) return;
    try {
      await voiceApi.pauseSession(sid);
      setIsPaused(true);
    } catch (err) {
      console.error('Failed to pause:', err);
    }
  }, [sid]);

  const handleResume = useCallback(async () => {
    if (!sid) return;
    try {
      await voiceApi.resumeSession(sid);
      setIsPaused(false);
    } catch (err) {
      console.error('Failed to resume:', err);
    }
  }, [sid]);

  const handleEnd = useCallback(async () => {
    if (!sid) return;
    try {
      await voiceApi.endSession(sid);
      wsRef.current?.close();
      navigate('/nvc');
    } catch (err) {
      console.error('Failed to end session:', err);
    }
  }, [sid, navigate]);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-[60vh]">
        <Loader2 className="w-8 h-8 animate-spin text-primary-500" />
      </div>
    );
  }

  if (!session) {
    return (
      <div className="text-center py-20">
        <p className="text-slate-500">语音会话不存在</p>
        <button
          onClick={() => navigate('/nvc')}
          className="mt-4 text-primary-500 hover:underline"
        >
          返回练习中心
        </button>
      </div>
    );
  }

  return (
    <div className="h-[calc(100vh-8rem)] flex flex-col">
      {/* 顶部栏 */}
      <motion.div
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex items-center justify-between mb-4 flex-shrink-0"
      >
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate('/nvc')}
            className="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          >
            <ArrowLeft className="w-5 h-5 text-slate-500" />
          </button>
          <div>
            <h2 className="text-lg font-semibold text-slate-800 dark:text-white">
              语音练习
            </h2>
            <span className="text-sm text-slate-500">
              {session.status === 'IN_PROGRESS' ? '进行中'
                : session.status === 'PAUSED' ? '已暂停'
                : session.status}
            </span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {session.status === 'IN_PROGRESS' && (
            <>
              <button
                onClick={isPaused ? handleResume : handlePause}
                className="flex items-center gap-2 px-4 py-2 bg-slate-200 dark:bg-slate-700 text-slate-700 dark:text-slate-300 rounded-xl hover:bg-slate-300 dark:hover:bg-slate-600 transition-colors"
              >
                {isPaused ? <Play className="w-4 h-4" /> : <Pause className="w-4 h-4" />}
                {isPaused ? '恢复' : '暂停'}
              </button>
              <button
                onClick={handleEnd}
                className="flex items-center gap-2 px-4 py-2 bg-red-500 text-white rounded-xl hover:bg-red-600 transition-colors"
              >
                <StopCircle className="w-4 h-4" />
                结束
              </button>
            </>
          )}
        </div>
      </motion.div>

      {/* 主体区域 */}
      <div className="flex-1 flex gap-4 min-h-0">
        {/* 对话实录 */}
        <div className="flex-1 bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 overflow-hidden">
          <RealtimeSubtitle
            messages={messages}
            userText={userText}
            aiText={aiText}
            isAiSpeaking={isAiSpeaking}
          />
        </div>

        {/* 右侧控制面板 */}
        <div className="w-80 flex flex-col items-center justify-center gap-6">
          {/* 录音按钮 */}
          <AudioRecorder
            isRecording={isRecording}
            disabled={isPaused || session.status !== 'IN_PROGRESS'}
            onRecordingChange={setIsRecording}
            onAudioData={handleAudioData}
            onSpeechEnd={handleSpeechEnd}
          />

          {/* AI 语音播放 */}
          {audioData && (
            <AudioPlayer
              audioData={audioData}
              onPlayEnd={handlePlayEnd}
            />
          )}

          {/* 状态提示 */}
          <p className="text-sm text-slate-400 text-center">
            {isPaused
              ? '练习已暂停'
              : isRecording
                ? '正在聆听...'
                : isAiSpeaking
                  ? 'AI 正在说话...'
                  : '点击麦克风开始说话'}
          </p>
        </div>
      </div>
    </div>
  );
}
