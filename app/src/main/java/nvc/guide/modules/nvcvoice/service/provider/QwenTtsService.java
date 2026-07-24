package nvc.guide.modules.nvcvoice.service.provider;

import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtime;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeAudioFormat;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeCallback;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeConfig;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeParam;
import com.google.gson.JsonObject;
import nvc.guide.modules.nvcvoice.config.NvcVoiceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Qwen TTS Realtime Service (WebSocket-based)
 *
 * Provides real-time text-to-speech synthesis using Alibaba Cloud DashScope's
 * qwen-tts-realtime model via WebSocket API.
 *
 * Key Features:
 * - WebSocket-based real-time TTS synthesis
 * - User-commit mode for manual control
 * - Synchronous synthesis API with timeout protection
 * - Automatic audio chunk collection via response.audio.delta events
 * - Support for Chinese language with configurable voice, speech rate, and volume
 *
 * Configuration:
 * - Model: qwen-tts-realtime
 * - Voice: Configurable (Cherry, Serena, Ethan, etc.)
 * - Audio format: PCM, 24kHz sample rate
 * - Mode: commit (user-controlled)
 *
 * @see QwenTtsRealtime
 * @see QwenTtsRealtimeCallback
 */
@Slf4j
@Service
public class QwenTtsService implements TtsProvider {

    // Runtime configuration values (loaded from NvcVoiceProperties)
    private String model;
    private String apiKey;
    private String voice;
    private String format;
    private Integer sampleRate;
    private String mode;
    private String languageType;
    private Float speechRate;
    private Integer volume;

    public QwenTtsService(NvcVoiceProperties properties) {
        applyTtsConfig(properties.getQwenTts());
    }

    public void reload(NvcVoiceProperties properties) {
        applyTtsConfig(properties.getQwenTts());
        log.info("QwenTtsService reloaded: model={}, voice={}", model, voice);
    }

    private void applyTtsConfig(NvcVoiceProperties.QwenTtsConfig tts) {
        this.model = tts.getModel();
        this.apiKey = tts.getApiKey();
        this.voice = tts.getVoice();
        this.format = tts.getFormat();
        this.sampleRate = tts.getSampleRate();
        this.mode = tts.getMode();
        this.languageType = tts.getLanguageType();
        this.speechRate = tts.getSpeechRate();
        this.volume = tts.getVolume();
    }

    /**
     * Initialize the TTS service.
     * This method is automatically called by Spring after the service is constructed.
     */
    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("QwenTtsService: TTS disabled — API key not configured"); return;
        }
        log.info("QwenTtsService initialized with model: {}, voice: {}, sampleRate: {}Hz",
                 model, voice, sampleRate);
    }

    // === TtsProvider 接口实现 ===

    @Override
    public byte[] synthesize(String text) {
        if (text == null || text.isBlank()) {
            log.warn("[TTS] Empty text, skipping synthesis");
            return new byte[0];
        }
        return doSynthesize(text);
    }

    @Override
    public byte[] synthesize(String text, int timeoutSeconds) {
        if (text == null || text.isBlank()) {
            log.warn("[TTS] Empty text, skipping synthesis");
            return new byte[0];
        }
        return doSynthesize(text);
    }

    // === 内部方法 ===

    private byte[] doSynthesize(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.debug("Empty or null text provided, returning empty audio array");
            return new byte[0];
        }

        log.debug("Starting TTS synthesis for text: {} characters", text.length());

        CountDownLatch synthesisLatch = new CountDownLatch(1);
        ByteArrayContainer audioContainer = new ByteArrayContainer();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<String> responseIdRef = new AtomicReference<>();

        try {
            QwenTtsRealtimeParam param = QwenTtsRealtimeParam.builder()
                    .model(model)
                    .apikey(apiKey)
                    .build();

            QwenTtsRealtimeCallback callback = new QwenTtsRealtimeCallback() {
                @Override
                public void onOpen() {
                    log.debug("TTS WebSocket connection established");
                }

                @Override
                public void onEvent(JsonObject message) {
                    handleServerEvent(message, audioContainer, synthesisLatch, errorRef, responseIdRef);
                }

                @Override
                public void onClose(int code, String reason) {
                    log.debug("TTS WebSocket closed - code: {}, reason: {}", code, reason);
                    synthesisLatch.countDown();
                }
            };

            QwenTtsRealtime qwenTtsRealtime = new QwenTtsRealtime(param, callback);

            try {
                qwenTtsRealtime.connect();

                QwenTtsRealtimeConfig config = QwenTtsRealtimeConfig.builder()
                        .voice(voice)
                        .responseFormat(getAudioFormat())
                        .mode(mode)
                        .languageType(languageType)
                        .speechRate(speechRate)
                        .volume(volume)
                        .build();

                qwenTtsRealtime.updateSession(config);

                log.info("[TTS] Session configured with voice: {}, triggering synthesis for text (length: {})",
                         voice, text.length());

                qwenTtsRealtime.appendText(text);
                qwenTtsRealtime.commit();

                log.info("[TTS] Text sent to TTS service, waiting for audio response...");

                boolean completed = synthesisLatch.await(30, TimeUnit.SECONDS);

                if (!completed) {
                    log.error("TTS synthesis timeout after 30 seconds");
                    return new byte[0];
                }

                Throwable error = errorRef.get();
                if (error != null) {
                    log.error("TTS synthesis failed", error);
                    return new byte[0];
                }

                byte[] audioData = audioContainer.toByteArray();
                log.info("[TTS] Synthesis completed successfully - {} bytes of audio data, responseId: {}",
                         audioData.length, responseIdRef.get());

                return audioData;

            } finally {
                try {
                    qwenTtsRealtime.close();
                } catch (Exception e) {
                    log.error("Error closing TTS connection", e);
                }
            }

        } catch (InterruptedException e) {
            log.error("TTS synthesis interrupted", e);
            Thread.currentThread().interrupt();
            return new byte[0];
        } catch (Exception e) {
            log.error("Failed to synthesize text", e);
            return new byte[0];
        }
    }

    private QwenTtsRealtimeAudioFormat getAudioFormat() {
        return QwenTtsRealtimeAudioFormat.PCM_24000HZ_MONO_16BIT;
    }

    @PreDestroy
    public void destroy() {
        log.info("QwenTtsService destroyed successfully");
    }

    private void handleServerEvent(JsonObject message, ByteArrayContainer audioContainer,
                                    CountDownLatch synthesisLatch, AtomicReference<Throwable> errorRef,
                                    AtomicReference<String> responseIdRef) {
        try {
            String eventType = message.get("type").getAsString();

            if (log.isTraceEnabled()) {
                log.trace("Received TTS event: {}, full message: {}", eventType, message);
            } else {
                log.debug("Received TTS event: {}", eventType);
            }

            switch (eventType) {
                case "session.created":
                    String sessionId = message.has("session") && message.get("session").isJsonObject()
                            ? message.get("session").getAsJsonObject().get("id").getAsString()
                            : "unknown";
                    log.debug("TTS session created: {}", sessionId);
                    break;

                case "session.updated":
                    log.debug("TTS session configuration updated");
                    break;

                case "response.audio.delta":
                    if (message.has("delta")) {
                        String audioBase64 = message.get("delta").getAsString();
                        if (audioBase64 != null && !audioBase64.isEmpty()) {
                            byte[] audioChunk = Base64.getDecoder().decode(audioBase64);
                            audioContainer.append(audioChunk);
                            log.trace("Received audio chunk - {} bytes", audioChunk.length);
                        }
                    }
                    break;

                case "response.done":
                    String responseId = responseIdRef.get();
                    log.debug("TTS response completed - responseId: {}", responseId);
                    synthesisLatch.countDown();
                    break;

                case "error":
                    if (message.has("error")) {
                        var errorElement = message.get("error");
                        String errorType = "unknown";
                        String errorCode = "unknown";
                        String errorMessage = "Unknown error";

                        if (errorElement.isJsonObject()) {
                            JsonObject errorObj = errorElement.getAsJsonObject();
                            errorType = errorObj.has("type") ? errorObj.get("type").getAsString() : "unknown";
                            errorCode = errorObj.has("code") ? errorObj.get("code").getAsString() : "unknown";
                            errorMessage = errorObj.has("message") ? errorObj.get("message").getAsString() : "Unknown error";
                        } else {
                            errorMessage = errorElement.toString();
                        }

                        String fullErrorMessage = String.format("TTS Error [%s/%s]: %s", errorType, errorCode, errorMessage);
                        log.error("{}", fullErrorMessage);

                        errorRef.set(new IllegalStateException(fullErrorMessage));
                        synthesisLatch.countDown();
                    }
                    break;

                default:
                    log.trace("Unhandled TTS event type: {}", eventType);
            }

        } catch (Exception e) {
            log.error("Error processing TTS server event", e);
            errorRef.set(e);
            synthesisLatch.countDown();
        }
    }

    private static class ByteArrayContainer {
        private final java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();

        public synchronized void append(byte[] chunk) {
            baos.write(chunk, 0, chunk.length);
        }

        public synchronized byte[] toByteArray() {
            return baos.toByteArray();
        }
    }
}
