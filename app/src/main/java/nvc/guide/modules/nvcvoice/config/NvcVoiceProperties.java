package nvc.guide.modules.nvcvoice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * NVC 语音练习配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.nvc.voice")
public class NvcVoiceProperties {

  // === 语音输出 ===

  /** AI 回复最大字符数（超出会截断到句子边界） */
  private int aiQuestionMaxChars = 120;

  /** 是否启用 LLM 流式文本下发 */
  private boolean llmStreamingEnabled = true;

  /** 流式文本最小推送间隔（毫秒） */
  private int aiStreamPushIntervalMs = 180;

  /** 流式文本推送最小增量字符数 */
  private int aiStreamMinCharsDelta = 12;

  // === TTS ===

  /** 每会话允许的并发 TTS 合成调用上限 */
  private int maxConcurrentTtsPerSession = 3;

  /** 是否启用分块音频推送 */
  private boolean chunkedAudioEnabled = true;

  /** 单句 TTS 合成超时（秒） */
  private int ttsTimeoutSeconds = 8;

  // === 阶段配置 ===

  private PhaseDurationConfig intro = new PhaseDurationConfig(1, 2, 3);
  private PhaseDurationConfig practice = new PhaseDurationConfig(10, 20, 30);
  private PhaseDurationConfig wrapUp = new PhaseDurationConfig(2, 3, 5);

  // === 暂停超时 ===

  /** 暂停警告时间（秒），默认 4:30 */
  private int pauseWarningSeconds = 270;

  /** 自动暂停超时（秒），默认 5:00 */
  private int pauseTimeoutSeconds = 300;

  // === ASR 配置 ===

  private QwenAsrConfig qwenAsr = new QwenAsrConfig();

  // === TTS 配置 ===

  private QwenTtsConfig qwenTts = new QwenTtsConfig();

  // === 限流 ===

  private RateLimitConfig rateLimit = new RateLimitConfig();

  // === 内部配置类 ===

  @Data
  public static class PhaseDurationConfig {

    private int minDuration;
    private int suggestedDuration;
    private int maxDuration;

    public PhaseDurationConfig(int min, int suggested, int max) {
      this.minDuration = min;
      this.suggestedDuration = suggested;
      this.maxDuration = max;
    }
  }

  @Data
  public static class QwenAsrConfig {

    private String url = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime";
    private String model = "qwen3-asr-flash-realtime";
    private String apiKey;
    private String language = "zh";
    private String format = "pcm";
    private int sampleRate = 16000;
    private boolean enableTurnDetection = true;
    private String turnDetectionType = "server_vad";
    private float turnDetectionThreshold = 0.0f;
    private int turnDetectionSilenceDurationMs = 1000;
  }

  @Data
  public static class QwenTtsConfig {

    private String model = "qwen3-tts-flash-realtime";
    private String apiKey;
    private String voice = "Cherry";
    private String format = "pcm";
    private int sampleRate = 24000;
    private String mode = "commit";
    private String languageType = "Chinese";
    private float speechRate = 1.0f;
    private int volume = 60;
  }

  @Data
  public static class RateLimitConfig {

    private int maxPerSession = 10;
    private int maxPerIp = 3;
    private int maxConcurrent = 50;
  }
}
