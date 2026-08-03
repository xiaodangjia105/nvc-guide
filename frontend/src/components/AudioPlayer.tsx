import React, { useRef, useEffect, useState } from 'react';
import { Play, Pause, Volume2, VolumeX } from 'lucide-react';

interface AudioPlayerProps {
  audioData: string; // Base64 encoded PCM audio
  text?: string;
  onPlayEnd?: () => void;
}

/**
 * 将 PCM 16-bit 数据转换为 WAV 格式（添加 WAV header）
 *
 * @param pcmBase64 Base64 编码的 PCM 数据
 * @param sampleRate 采样率（默认 24000）
 * @param numChannels 声道数（默认 1）
 * @param bitsPerSample 采样位数（默认 16）
 * @returns WAV 格式的 Blob URL
 */
function pcmToWavUrl(
  pcmBase64: string,
  sampleRate = 24000,
  numChannels = 1,
  bitsPerSample = 16
): string {
  // 解码 Base64 到二进制
  const binaryString = atob(pcmBase64);
  const pcmLength = binaryString.length;
  const pcmData = new Uint8Array(pcmLength);
  for (let i = 0; i < pcmLength; i++) {
    pcmData[i] = binaryString.charCodeAt(i);
  }

  // 构建 WAV header
  const byteRate = sampleRate * numChannels * (bitsPerSample / 8);
  const blockAlign = numChannels * (bitsPerSample / 8);
  const wavLength = 44 + pcmLength;
  const wavBuffer = new ArrayBuffer(wavLength);
  const view = new DataView(wavBuffer);

  // RIFF header
  writeString(view, 0, 'RIFF');
  view.setUint32(4, wavLength - 8, true);
  writeString(view, 8, 'WAVE');

  // fmt chunk
  writeString(view, 12, 'fmt ');
  view.setUint32(16, 16, true); // chunk size
  view.setUint16(20, 1, true);  // PCM format
  view.setUint16(22, numChannels, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, byteRate, true);
  view.setUint16(32, blockAlign, true);
  view.setUint16(34, bitsPerSample, true);

  // data chunk
  writeString(view, 36, 'data');
  view.setUint32(40, pcmLength, true);

  // 写入 PCM 数据
  const wavBytes = new Uint8Array(wavBuffer);
  wavBytes.set(pcmData, 44);

  const blob = new Blob([wavBytes], { type: 'audio/wav' });
  return URL.createObjectURL(blob);
}

function writeString(view: DataView, offset: number, str: string): void {
  for (let i = 0; i < str.length; i++) {
    view.setUint8(offset + i, str.charCodeAt(i));
  }
}

/**
 * 音频播放器组件
 * 用于播放AI语音合成生成的 PCM 音频（转换为 WAV 格式播放）
 */
export default function AudioPlayer({ audioData, text, onPlayEnd }: AudioPlayerProps) {
  const audioRef = useRef<HTMLAudioElement>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isMuted, setIsMuted] = useState(false);
  const [volume, setVolume] = useState(1);
  const objectUrlRef = useRef<string | null>(null);

  useEffect(() => {
    if (audioData && audioRef.current) {
      // 清理上一次的 Object URL
      if (objectUrlRef.current) {
        URL.revokeObjectURL(objectUrlRef.current);
        objectUrlRef.current = null;
      }

      // 将 PCM 数据转换为 WAV 格式
      const wavUrl = pcmToWavUrl(audioData);
      objectUrlRef.current = wavUrl;
      audioRef.current.src = wavUrl;

      // Auto-play when new audio data arrives
      audioRef.current.play().then(() => {
        setIsPlaying(true);
      }).catch((error) => {
        console.error('Auto-play failed:', error);
        // Auto-play may fail if user hasn't interacted with the page yet
      });
    }

    // 组件卸载时清理 Object URL
    return () => {
      if (objectUrlRef.current) {
        URL.revokeObjectURL(objectUrlRef.current);
        objectUrlRef.current = null;
      }
    };
  }, [audioData]);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    const handleEnded = () => {
      setIsPlaying(false);
      onPlayEnd?.();
    };

    audio.addEventListener('ended', handleEnded);
    return () => audio.removeEventListener('ended', handleEnded);
  }, [onPlayEnd]);

  const togglePlay = () => {
    if (!audioRef.current) return;

    if (isPlaying) {
      audioRef.current.pause();
    } else {
      audioRef.current.play();
    }
    setIsPlaying(!isPlaying);
  };

  const toggleMute = () => {
    if (!audioRef.current) return;
    audioRef.current.muted = !isMuted;
    setIsMuted(!isMuted);
  };

  const handleVolumeChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newVolume = parseFloat(e.target.value);
    if (audioRef.current) {
      audioRef.current.volume = newVolume;
    }
    setVolume(newVolume);
  };

  if (!audioData) {
    return null;
  }

  return (
    <div className="flex flex-col gap-3">
      {/* Audio element (hidden) */}
      <audio ref={audioRef} />

      {/* Text display */}
      {text && (
        <div className="p-4 bg-slate-50 rounded-lg border border-slate-200">
          <p className="text-slate-700">{text}</p>
        </div>
      )}

      {/* Controls */}
      <div className="flex items-center gap-4">
        {/* Play/Pause button */}
        <button
          onClick={togglePlay}
          className="w-12 h-12 rounded-full bg-primary-500 hover:bg-primary-600
                     flex items-center justify-center text-white transition-colors"
        >
          {isPlaying ? (
            <Pause className="w-6 h-6" />
          ) : (
            <Play className="w-6 h-6" />
          )}
        </button>

        {/* Volume controls */}
        <div className="flex items-center gap-2">
          <button onClick={toggleMute} className="text-slate-400 hover:text-slate-600">
            {isMuted ? (
              <VolumeX className="w-5 h-5" />
            ) : (
              <Volume2 className="w-5 h-5" />
            )}
          </button>
          <input
            type="range"
            min="0"
            max="1"
            step="0.1"
            value={volume}
            onChange={handleVolumeChange}
            className="w-24"
          />
        </div>
      </div>
    </div>
  );
}
