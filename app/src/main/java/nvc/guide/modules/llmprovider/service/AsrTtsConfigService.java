package nvc.guide.modules.llmprovider.service;

import nvc.guide.modules.llmprovider.dto.AsrConfigDTO;
import nvc.guide.modules.llmprovider.dto.AsrConfigRequest;
import nvc.guide.modules.llmprovider.dto.ProviderTestResult;
import nvc.guide.modules.llmprovider.dto.TtsConfigDTO;
import nvc.guide.modules.llmprovider.dto.TtsConfigRequest;
import nvc.guide.modules.nvcvoice.config.NvcVoiceProperties;
import nvc.guide.modules.nvcvoice.service.provider.QwenAsrService;
import nvc.guide.modules.nvcvoice.service.provider.QwenTtsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ASR / TTS 语音配置管理服务。
 * <p>
 * 职责：读取、更新 ASR/TTS 配置，持久化到 YAML，测试 ASR 连通性。
 */
@Service
@Slf4j
public class AsrTtsConfigService {

  private final NvcVoiceProperties voiceProperties;
  private final QwenAsrService asrService;
  private final QwenTtsService ttsService;
  private final ConfigFilePersistenceService configFilePersistence;
  private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

  public AsrTtsConfigService(
      NvcVoiceProperties voiceProperties,
      QwenAsrService asrService,
      QwenTtsService ttsService,
      ConfigFilePersistenceService configFilePersistence) {
    this.voiceProperties = voiceProperties;
    this.asrService = asrService;
    this.ttsService = ttsService;
    this.configFilePersistence = configFilePersistence;
  }

  // ===== Read operations (read lock) =====

  public AsrConfigDTO getAsrConfig() {
    rwLock.readLock().lock();
    try {
      NvcVoiceProperties.QwenAsrConfig asr = voiceProperties.getQwenAsr();
      return AsrConfigDTO.builder()
          .url(asr.getUrl())
          .model(asr.getModel())
          .maskedApiKey(maskApiKey(asr.getApiKey()))
          .language(asr.getLanguage())
          .format(asr.getFormat())
          .sampleRate(asr.getSampleRate())
          .enableTurnDetection(asr.isEnableTurnDetection())
          .turnDetectionType(asr.getTurnDetectionType())
          .turnDetectionThreshold(asr.getTurnDetectionThreshold())
          .turnDetectionSilenceDurationMs(asr.getTurnDetectionSilenceDurationMs())
          .build();
    } finally {
      rwLock.readLock().unlock();
    }
  }

  public TtsConfigDTO getTtsConfig() {
    rwLock.readLock().lock();
    try {
      NvcVoiceProperties.QwenTtsConfig tts = voiceProperties.getQwenTts();
      return TtsConfigDTO.builder()
          .model(tts.getModel())
          .maskedApiKey(maskApiKey(tts.getApiKey()))
          .voice(tts.getVoice())
          .format(tts.getFormat())
          .sampleRate(tts.getSampleRate())
          .mode(tts.getMode())
          .languageType(tts.getLanguageType())
          .speechRate(tts.getSpeechRate())
          .volume(tts.getVolume())
          .build();
    } finally {
      rwLock.readLock().unlock();
    }
  }

  public ProviderTestResult testAsrConfig() {
    rwLock.readLock().lock();
    try {
      NvcVoiceProperties.QwenAsrConfig asr = voiceProperties.getQwenAsr();
      try {
        java.net.URI wsUri = java.net.URI.create(asr.getUrl());
        String host = wsUri.getHost();
        int port = wsUri.getPort() > 0 ? wsUri.getPort() : (wsUri.getScheme().equals("wss") ? 443 : 80);
        java.net.InetSocketAddress address = new java.net.InetSocketAddress(host, port);
        java.net.Socket socket = new java.net.Socket();
        socket.connect(address, 5000);
        socket.close();
        return ProviderTestResult.builder()
            .success(true)
            .message("ASR WebSocket 连接成功: " + host)
            .model(asr.getModel())
            .build();
      } catch (Exception e) {
        return ProviderTestResult.builder()
            .success(false)
            .message("ASR 连接失败: " + e.getMessage())
            .model(asr.getModel())
            .build();
      }
    } finally {
      rwLock.readLock().unlock();
    }
  }

  // ===== Write operations (write lock) =====

  public void updateAsrConfig(AsrConfigRequest request) {
    rwLock.writeLock().lock();
    try {
      NvcVoiceProperties.QwenAsrConfig asr = voiceProperties.getQwenAsr();
      NvcVoiceProperties.QwenTtsConfig tts = voiceProperties.getQwenTts();
      if (request.url() != null) asr.setUrl(request.url());
      if (request.model() != null) asr.setModel(request.model());
      if (request.language() != null) asr.setLanguage(request.language());
      if (request.format() != null) asr.setFormat(request.format());
      if (request.sampleRate() != null) asr.setSampleRate(request.sampleRate());
      if (request.enableTurnDetection() != null) asr.setEnableTurnDetection(request.enableTurnDetection());
      if (request.turnDetectionType() != null) asr.setTurnDetectionType(request.turnDetectionType());
      if (request.turnDetectionThreshold() != null) asr.setTurnDetectionThreshold(request.turnDetectionThreshold());
      if (request.turnDetectionSilenceDurationMs() != null) asr.setTurnDetectionSilenceDurationMs(request.turnDetectionSilenceDurationMs());
      if (request.apiKey() != null) {
        asr.setApiKey(request.apiKey());
        tts.setApiKey(request.apiKey());
        configFilePersistence.updateEnvValue("AI_BAILIAN_API_KEY", request.apiKey());
      }

      configFilePersistence.writeAsrConfigToYaml(asr);
      asrService.reload(voiceProperties);
      if (request.apiKey() != null) {
        ttsService.reload(voiceProperties);
      }
      log.info("Updated ASR config");
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  public void updateTtsConfig(TtsConfigRequest request) {
    rwLock.writeLock().lock();
    try {
      NvcVoiceProperties.QwenAsrConfig asr = voiceProperties.getQwenAsr();
      NvcVoiceProperties.QwenTtsConfig tts = voiceProperties.getQwenTts();
      if (request.model() != null) tts.setModel(request.model());
      if (request.voice() != null) tts.setVoice(request.voice());
      if (request.format() != null) tts.setFormat(request.format());
      if (request.sampleRate() != null) tts.setSampleRate(request.sampleRate());
      if (request.mode() != null) tts.setMode(request.mode());
      if (request.languageType() != null) tts.setLanguageType(request.languageType());
      if (request.speechRate() != null) tts.setSpeechRate(request.speechRate());
      if (request.volume() != null) tts.setVolume(request.volume());
      if (request.apiKey() != null) {
        tts.setApiKey(request.apiKey());
        asr.setApiKey(request.apiKey());
        configFilePersistence.updateEnvValue("AI_BAILIAN_API_KEY", request.apiKey());
      }

      configFilePersistence.writeTtsConfigToYaml(tts);
      ttsService.reload(voiceProperties);
      if (request.apiKey() != null) {
        asrService.reload(voiceProperties);
      }
      log.info("Updated TTS config");
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  // ===== Internal helpers =====

  private String maskApiKey(String apiKey) {
    if (apiKey == null || apiKey.length() <= 6) {
      return "***";
    }
    return apiKey.substring(0, 3) + "***" + apiKey.substring(apiKey.length() - 3);
  }
}
