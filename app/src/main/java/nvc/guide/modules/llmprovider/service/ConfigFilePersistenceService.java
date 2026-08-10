package nvc.guide.modules.llmprovider.service;

import nvc.guide.common.config.LlmProviderProperties;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcvoice.config.NvcVoiceProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YAML / .env 配置文件持久化服务。
 * <p>
 * 职责：文本级 YAML 编辑（保留注释与格式）、.env 键值读写。
 */
@Service
@Slf4j
public class ConfigFilePersistenceService {

  private final String yamlPath;
  private final String envPath;

  public ConfigFilePersistenceService(LlmProviderProperties properties) {
    this.yamlPath = properties.getConfigYamlPath();
    this.envPath = properties.getConfigEnvPath();
  }

  @PostConstruct
  void validateWritablePaths() {
    ensureParentWritable(yamlPath, "config-yaml-path");
    ensureParentWritable(envPath, "config-env-path");
  }

  private void ensureParentWritable(String rawPath, String label) {
    if (rawPath == null || rawPath.isBlank()) {
      log.warn("{} is not configured; runtime Provider edits will be skipped", label);
      return;
    }
    Path parent = Path.of(rawPath).toAbsolutePath().getParent();
    if (parent == null) {
      return;
    }
    try {
      Files.createDirectories(parent);
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.PROVIDER_CONFIG_WRITE_FAILED,
          label + " 的父目录不可创建: " + parent, e);
    }
    if (!Files.isWritable(parent)) {
      throw new BusinessException(ErrorCode.PROVIDER_CONFIG_WRITE_FAILED,
          label + " 的父目录不可写: " + parent);
    }
    log.info("{} resolved to {} (parent writable)", label, rawPath);
  }

  // ===== YAML Provider 配置 =====

  public void writeProviderToYaml(String id, LlmProviderProperties.ProviderConfig config, String envKey) {
    mutateYamlText(ErrorCode.PROVIDER_CONFIG_WRITE_FAILED, "写入 YAML 配置失败", editor -> {
      LinkedHashMap<String, Object> values = new LinkedHashMap<>();
      values.put("base-url", config.getBaseUrl());
      values.put("api-key", "${" + envKey + "}");
      values.put("model", config.getModel());
      if (config.getEmbeddingModel() != null) {
        values.put("embedding-model", config.getEmbeddingModel());
      }
      if (config.getEmbeddingDimensions() != null) {
        values.put("embedding-dimensions", config.getEmbeddingDimensions());
      }
      if (config.getTemperature() != null) {
        values.put("temperature", config.getTemperature());
      }
      editor.setBlock(new String[]{"app", "ai", "providers"}, id, values);
    });
  }

  public void removeProviderFromYaml(String id) {
    mutateYamlText(ErrorCode.PROVIDER_CONFIG_WRITE_FAILED, "删除 YAML 配置失败", editor -> {
      editor.removeSection(new String[]{"app", "ai", "providers"}, id);
    });
  }

  public void writeDefaultProviderToYaml(String defaultProvider) {
    mutateYamlText(ErrorCode.PROVIDER_CONFIG_WRITE_FAILED, "写入默认 Provider 配置失败", editor -> {
      editor.setScalar(new String[]{"app", "ai", "default-provider"}, defaultProvider);
      editor.removeSection(new String[]{"app", "ai"}, "module-defaults");
    });
  }

  public void writeDefaultEmbeddingProviderToYaml(String defaultEmbeddingProvider) {
    mutateYamlText(ErrorCode.PROVIDER_CONFIG_WRITE_FAILED, "写入默认 Embedding Provider 配置失败", editor -> {
      editor.setScalar(new String[]{"app", "ai", "default-embedding-provider"}, defaultEmbeddingProvider);
    });
  }

  // ===== YAML Voice 配置 =====

  public void writeAsrConfigToYaml(NvcVoiceProperties.QwenAsrConfig asr) {
    mutateYamlText(ErrorCode.VOICE_CONFIG_WRITE_FAILED, "写入 ASR 配置失败", editor -> {
      LinkedHashMap<String, Object> values = new LinkedHashMap<>();
      values.put("url", asr.getUrl());
      values.put("model", asr.getModel());
      values.put("api-key", "${AI_BAILIAN_API_KEY}");
      values.put("language", asr.getLanguage());
      values.put("format", asr.getFormat());
      values.put("sample-rate", asr.getSampleRate());
      values.put("enable-turn-detection", asr.isEnableTurnDetection());
      values.put("turn-detection-type", asr.getTurnDetectionType());
      values.put("turn-detection-threshold", asr.getTurnDetectionThreshold());
      values.put("turn-detection-silence-duration-ms", asr.getTurnDetectionSilenceDurationMs());
      editor.setBlock(new String[]{"app", "nvc", "voice"}, "qwen-asr", values);
    });
  }

  public void writeTtsConfigToYaml(NvcVoiceProperties.QwenTtsConfig tts) {
    mutateYamlText(ErrorCode.VOICE_CONFIG_WRITE_FAILED, "写入 TTS 配置失败", editor -> {
      LinkedHashMap<String, Object> values = new LinkedHashMap<>();
      values.put("model", tts.getModel());
      values.put("api-key", "${AI_BAILIAN_API_KEY}");
      values.put("voice", tts.getVoice());
      values.put("format", tts.getFormat());
      values.put("sample-rate", tts.getSampleRate());
      values.put("mode", tts.getMode());
      values.put("language-type", tts.getLanguageType());
      values.put("speech-rate", tts.getSpeechRate());
      values.put("volume", tts.getVolume());
      editor.setBlock(new String[]{"app", "nvc", "voice"}, "qwen-tts", values);
    });
  }

  // ===== YAML 文本编辑核心 =====

  private void mutateYamlText(ErrorCode errorCode, String errorMessage, Consumer<YamlTextEditor> mutator) {
    if (yamlPath == null || yamlPath.isBlank()) {
      log.warn("YAML path not configured, skip writing");
      return;
    }
    try {
      Path path = Path.of(yamlPath);
      List<String> lines;
      if (Files.exists(path)) {
        lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
      } else {
        lines = new ArrayList<>();
      }

      YamlTextEditor editor = new YamlTextEditor(lines);
      mutator.accept(editor);

      String content = String.join("\n", editor.getLines());
      if (!content.endsWith("\n")) {
        content += "\n";
      }
      Files.writeString(path, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new BusinessException(errorCode, errorMessage + ": " + e.getMessage());
    }
  }

  // ===== .env 文件操作 =====

  public void writeEnvValue(String key, String value) {
    if (envPath == null || envPath.isBlank()) return;
    try {
      Path path = Path.of(envPath);
      if (!Files.exists(path)) {
        Files.writeString(path, key + "=" + value + "\n", StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return;
      }
      String content = Files.readString(path, StandardCharsets.UTF_8);
      if (content.contains(key + "=")) {
        content = content.replaceAll("(?m)^" + Pattern.quote(key) + "=.*",
            Matcher.quoteReplacement(key + "=" + value));
      } else {
        if (!content.endsWith("\n")) {
          content += "\n";
        }
        content += key + "=" + value + "\n";
      }
      Files.writeString(path, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error("写入 .env 失败: {}", e.getMessage(), e);
      throw new BusinessException(ErrorCode.PROVIDER_CONFIG_WRITE_FAILED,
          "写入 .env 配置文件失败，API Key 可能未持久化");
    }
  }

  public void updateEnvValue(String key, String value) {
    writeEnvValue(key, value);
  }

  public void removeFromEnv(String key) {
    if (envPath == null || envPath.isBlank()) return;
    try {
      Path path = Path.of(envPath);
      if (!Files.exists(path)) return;
      String content = Files.readString(path, StandardCharsets.UTF_8);
      content = content.replaceAll("(?m)^" + Pattern.quote(key) + "=.*\\R?", "");
      Files.writeString(path, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.warn("删除 .env 条目失败: {}", e.getMessage());
    }
  }

  // ===== YamlTextEditor: 文本级 YAML 编辑（保留注释与格式） =====

  static class YamlTextEditor {
    private final List<String> lines;

    YamlTextEditor(List<String> lines) {
      this.lines = new ArrayList<>(lines);
    }

    List<String> getLines() {
      return lines;
    }

    void setScalar(String[] path, String value) {
      int searchFrom = path.length > 1
          ? ensureParents(Arrays.copyOf(path, path.length - 1))
          : 0;
      int indent = (path.length - 1) * 2;
      String key = path[path.length - 1];
      String newLine = " ".repeat(indent) + key + ": " + value;

      int found = findKey(key, indent, searchFrom);
      if (found >= 0) {
        lines.set(found, newLine);
      } else {
        int parentIndent = indent >= 2 ? indent - 2 : -1;
        int insertPos = findSectionEnd(searchFrom, parentIndent);
        lines.add(insertPos, newLine);
      }
    }

    void setBlock(String[] parentPath, String blockKey, LinkedHashMap<String, Object> values) {
      int parentSearchFrom = ensureParents(parentPath);
      int blockIndent = parentPath.length * 2;
      int valueIndent = blockIndent + 2;

      int blockLine = findKey(blockKey, blockIndent, parentSearchFrom);
      if (blockLine < 0) {
        int parentEnd = parentPath.length >= 1 ? blockIndent - 2 : -1;
        int insertPos = findSectionEnd(parentSearchFrom, parentEnd);
        lines.add(insertPos, " ".repeat(blockIndent) + blockKey + ":");
        blockLine = insertPos;
      }

      int blockEnd = findSectionEnd(blockLine + 1, blockIndent);

      for (Map.Entry<String, Object> entry : values.entrySet()) {
        String valueLine = " ".repeat(valueIndent) + entry.getKey() + ": " + formatValue(entry.getValue());
        int existing = findKeyInRange(entry.getKey(), valueIndent, blockLine + 1, blockEnd);
        if (existing >= 0) {
          lines.set(existing, valueLine);
        } else {
          lines.add(blockEnd, valueLine);
          blockEnd++;
        }
      }
    }

    void removeSection(String[] parentPath, String sectionKey) {
      int parentSearchFrom = navigateTo(parentPath);
      if (parentSearchFrom < 0) return;

      int sectionIndent = parentPath.length * 2;
      int sectionLine = findKey(sectionKey, sectionIndent, parentSearchFrom);
      if (sectionLine < 0) return;

      int endLine = sectionLine + 1;
      while (endLine < lines.size()) {
        String line = lines.get(endLine);
        if (line.isBlank()) { endLine++; continue; }
        if (indentOf(line) <= sectionIndent) break;
        endLine++;
      }

      for (int i = endLine - 1; i >= sectionLine; i--) {
        lines.remove(i);
      }
    }

    private int ensureParents(String[] path) {
      int searchFrom = 0;
      for (int i = 0; i < path.length; i++) {
        int indent = i * 2;
        int found = findKey(path[i], indent, searchFrom);
        if (found < 0) {
          int parentIndent = i > 0 ? indent - 2 : -1;
          int insertPos = findSectionEnd(searchFrom, parentIndent);
          lines.add(insertPos, " ".repeat(indent) + path[i] + ":");
          searchFrom = insertPos + 1;
        } else {
          searchFrom = found + 1;
        }
      }
      return searchFrom;
    }

    private int navigateTo(String[] path) {
      int searchFrom = 0;
      for (int i = 0; i < path.length; i++) {
        int indent = i * 2;
        int found = findKey(path[i], indent, searchFrom);
        if (found < 0) return -1;
        searchFrom = found + 1;
      }
      return searchFrom;
    }

    private int findKey(String key, int indent, int searchFrom) {
      String prefix = " ".repeat(indent) + key + ":";
      for (int i = searchFrom; i < lines.size(); i++) {
        String line = lines.get(i);
        if (line.isBlank() || line.trim().startsWith("#")) continue;
        if (line.startsWith(prefix)) return i;
        if (indentOf(line) < indent) break;
      }
      return -1;
    }

    private int findKeyInRange(String key, int indent, int start, int end) {
      String prefix = " ".repeat(indent) + key + ":";
      for (int i = start; i < end && i < lines.size(); i++) {
        String line = lines.get(i);
        if (line.isBlank() || line.trim().startsWith("#")) continue;
        if (line.startsWith(prefix)) return i;
        if (indentOf(line) < indent) break;
      }
      return -1;
    }

    private int findSectionEnd(int searchFrom, int parentIndent) {
      for (int i = searchFrom; i < lines.size(); i++) {
        String line = lines.get(i);
        if (line.isBlank() || line.trim().startsWith("#")) continue;
        if (indentOf(line) <= parentIndent) return i;
      }
      return lines.size();
    }

    private int indentOf(String line) {
      int count = 0;
      while (count < line.length() && line.charAt(count) == ' ') count++;
      return count;
    }

    private String formatValue(Object value) {
      if (value instanceof Boolean b) return b.toString();
      if (value instanceof Number n) return n.toString();
      return value.toString();
    }
  }
}
