package nvc.guide.modules.llmprovider.service;

import nvc.guide.common.config.LlmProviderProperties;
import nvc.guide.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigFilePersistenceService 测试")
class ConfigFilePersistenceServiceTest {

    @Mock private LlmProviderProperties properties;

    private ConfigFilePersistenceService service;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        Path tempYaml = tempDir.resolve("application.yml");
        Path tempEnv = tempDir.resolve(".env");
        Files.writeString(tempYaml, """
            app:
              ai:
                providers: {}
            """);
        Files.writeString(tempEnv, "");

        when(properties.getConfigYamlPath()).thenReturn(tempYaml.toString());
        when(properties.getConfigEnvPath()).thenReturn(tempEnv.toString());

        service = new ConfigFilePersistenceService(properties);
    }

    @Nested
    @DisplayName("启动校验")
    class Bootstrap {

        @Test
        @DisplayName("validateWritablePaths 对不可创建的父目录 fail-fast")
        void validateWritablePathsFailsFastWhenParentUnwritable(@TempDir Path tempDir) throws IOException {
            Path sentinel = tempDir.resolve("not-a-dir");
            Files.writeString(sentinel, "");
            Path unreachableYaml = sentinel.resolve("child/llm-providers.yml");

            when(properties.getConfigYamlPath()).thenReturn(unreachableYaml.toString());
            when(properties.getConfigEnvPath()).thenReturn(tempDir.resolve(".env").toString());

            ConfigFilePersistenceService failing = new ConfigFilePersistenceService(properties);

            assertThrows(BusinessException.class, failing::validateWritablePaths);
        }
    }

    @Nested
    @DisplayName("env 文件操作")
    class EnvFileOperations {

        private ConfigFilePersistenceService createServiceWithEnv(Path envFile) {
            when(properties.getConfigYamlPath()).thenReturn(null);
            when(properties.getConfigEnvPath()).thenReturn(envFile.toString());
            return new ConfigFilePersistenceService(properties);
        }

        @Test
        @DisplayName("writeEnvValue 在空文件追加新键")
        void appendNewKeyToEmptyFile(@TempDir Path tempDir) throws Exception {
            Path envFile = tempDir.resolve(".env");
            Files.writeString(envFile, "");

            ConfigFilePersistenceService envService = createServiceWithEnv(envFile);
            invokeMethod(envService, "writeEnvValue", "PROVIDER_KIMI_API_KEY", "sk-123");

            String content = Files.readString(envFile, StandardCharsets.UTF_8);
            assertTrue(content.contains("PROVIDER_KIMI_API_KEY=sk-123"));
        }

        @Test
        @DisplayName("writeEnvValue 在已有文件追加新键并补换行")
        void appendNewKeyToExistingFile(@TempDir Path tempDir) throws Exception {
            Path envFile = tempDir.resolve(".env");
            Files.writeString(envFile, "EXISTING_KEY=val1");

            ConfigFilePersistenceService envService = createServiceWithEnv(envFile);
            invokeMethod(envService, "writeEnvValue", "PROVIDER_KIMI_API_KEY", "sk-123");

            String content = Files.readString(envFile, StandardCharsets.UTF_8);
            assertTrue(content.contains("EXISTING_KEY=val1"));
            assertTrue(content.contains("PROVIDER_KIMI_API_KEY=sk-123"));
        }

        @Test
        @DisplayName("writeEnvValue 替换已有键的值")
        void replaceExistingKey(@TempDir Path tempDir) throws Exception {
            Path envFile = tempDir.resolve(".env");
            Files.writeString(envFile, "PROVIDER_KIMI_API_KEY=old-value\nOTHER_KEY=x\n");

            ConfigFilePersistenceService envService = createServiceWithEnv(envFile);
            invokeMethod(envService, "writeEnvValue", "PROVIDER_KIMI_API_KEY", "new-value");

            String content = Files.readString(envFile, StandardCharsets.UTF_8);
            assertTrue(content.contains("PROVIDER_KIMI_API_KEY=new-value"));
            assertTrue(content.contains("OTHER_KEY=x"));
        }

        @Test
        @DisplayName("removeFromEnv 移除指定键")
        void removeFromEnvDeletesKey(@TempDir Path tempDir) throws Exception {
            Path envFile = tempDir.resolve(".env");
            Files.writeString(envFile, "PROVIDER_KIMI_API_KEY=sk-123\nKEEP_ME=yes\n");

            ConfigFilePersistenceService envService = createServiceWithEnv(envFile);
            invokeMethod(envService, "removeFromEnv", "PROVIDER_KIMI_API_KEY");

            String content = Files.readString(envFile, StandardCharsets.UTF_8);
            assertFalse(content.contains("PROVIDER_KIMI_API_KEY"));
            assertTrue(content.contains("KEEP_ME=yes"));
        }
    }

    @Nested
    @DisplayName("YAML 文件操作")
    class YamlMutations {

        @Test
        @DisplayName("mutateYaml 在文件不存在时创建新文件")
        void createsNewFileWhenMissing(@TempDir Path tempDir) throws IOException {
            Path yamlFile = tempDir.resolve("new-config.yml");
            when(properties.getConfigYamlPath()).thenReturn(yamlFile.toString());
            when(properties.getConfigEnvPath()).thenReturn(null);

            ConfigFilePersistenceService yamlService = new ConfigFilePersistenceService(properties);

            LlmProviderProperties.ProviderConfig config = new LlmProviderProperties.ProviderConfig();
            config.setBaseUrl("https://api.moonshot.cn/v1");
            config.setApiKey("key");
            config.setModel("kimi-latest");

            yamlService.writeProviderToYaml("kimi", config, "PROVIDER_KIMI_API_KEY");

            assertTrue(Files.exists(yamlFile));
            String content = Files.readString(yamlFile, StandardCharsets.UTF_8);
            assertTrue(content.contains("kimi"));
            assertTrue(content.contains("kimi-latest"));
        }

        @Test
        @DisplayName("mutateYaml 保留已有 YAML 结构")
        void preservesExistingStructure(@TempDir Path tempDir) throws IOException {
            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, """
                app:
                  ai:
                    default-provider: dashscope
                  nvc:
                    voice:
                      qwen-asr:
                        model: qwen3-asr-flash-realtime
                """);

            when(properties.getConfigYamlPath()).thenReturn(yamlFile.toString());
            when(properties.getConfigEnvPath()).thenReturn(null);

            ConfigFilePersistenceService yamlService = new ConfigFilePersistenceService(properties);

            LlmProviderProperties.ProviderConfig config = new LlmProviderProperties.ProviderConfig();
            config.setBaseUrl("https://api.moonshot.cn/v1");
            config.setApiKey("key");
            config.setModel("kimi-latest");

            yamlService.writeProviderToYaml("kimi", config, "PROVIDER_KIMI_API_KEY");

            String content = Files.readString(yamlFile, StandardCharsets.UTF_8);
            assertTrue(content.contains("default-provider"));
            assertTrue(content.contains("qwen3-asr-flash-realtime"));
            assertTrue(content.contains("kimi"));
        }

        @Test
        @DisplayName("yamlPath 为 null 时不抛异常")
        void nullYamlPathIsNoOp() {
            when(properties.getConfigYamlPath()).thenReturn(null);
            when(properties.getConfigEnvPath()).thenReturn(null);

            ConfigFilePersistenceService nullYamlService = new ConfigFilePersistenceService(properties);

            assertDoesNotThrow(() -> nullYamlService.writeEnvValue("KEY", "value"));
        }
    }

    private void invokeMethod(Object target, String methodName, Object... args)
        throws Exception {
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i].getClass();
        }
        Method method = ConfigFilePersistenceService.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        method.invoke(target, args);
    }
}
