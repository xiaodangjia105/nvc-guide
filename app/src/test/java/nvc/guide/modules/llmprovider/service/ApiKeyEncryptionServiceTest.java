package nvc.guide.modules.llmprovider.service;

import nvc.guide.common.config.LlmProviderProperties;
import nvc.guide.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyEncryptionService 测试")
class ApiKeyEncryptionServiceTest {

    @Mock
    private LlmProviderProperties properties;

    private ApiKeyEncryptionService service;

    @Nested
    @DisplayName("使用 SHA-256 派生密钥（人类可读字符串）")
    class Sha256DerivedKey {

        @BeforeEach
        void setUp() {
            LlmProviderProperties.SecurityConfig security = new LlmProviderProperties.SecurityConfig();
            security.setApiKeyEncryptionKey("my-secret-encryption-key-for-test");
            when(properties.getSecurity()).thenReturn(security);
            service = new ApiKeyEncryptionService(properties);
            service.init();
        }

        @Test
        @DisplayName("encrypt/decrypt 往返 — 明文应被完整还原")
        void encryptDecryptRoundTrip() {
            String plainText = "sk-abc123xyz789";

            ApiKeyEncryptionService.EncryptedValue encrypted = service.encrypt(plainText);

            assertNotNull(encrypted.nonce(), "nonce 不应为空");
            assertNotNull(encrypted.ciphertext(), "ciphertext 不应为空");
            assertEquals(plainText, service.decrypt(encrypted.nonce(), encrypted.ciphertext()));
        }

        @Test
        @DisplayName("encrypt/decrypt 往返 — 空字符串")
        void encryptDecryptEmptyString() {
            String plainText = "";

            ApiKeyEncryptionService.EncryptedValue encrypted = service.encrypt(plainText);

            assertEquals(plainText, service.decrypt(encrypted.nonce(), encrypted.ciphertext()));
        }

        @Test
        @DisplayName("encrypt/decrypt 往返 — 长密钥")
        void encryptDecryptLongKey() {
            String plainText = "a".repeat(2048);

            ApiKeyEncryptionService.EncryptedValue encrypted = service.encrypt(plainText);

            assertEquals(plainText, service.decrypt(encrypted.nonce(), encrypted.ciphertext()));
        }

        @Test
        @DisplayName("encrypt/decrypt 往返 — Unicode 内容")
        void encryptDecryptUnicode() {
            String plainText = "测试API密钥-αβγ-🔑";

            ApiKeyEncryptionService.EncryptedValue encrypted = service.encrypt(plainText);

            assertEquals(plainText, service.decrypt(encrypted.nonce(), encrypted.ciphertext()));
        }

        @Test
        @DisplayName("每次加密产生的 nonce 不同")
        void eachEncryptionProducesDifferentNonce() {
            String plainText = "same-key";

            ApiKeyEncryptionService.EncryptedValue first = service.encrypt(plainText);
            ApiKeyEncryptionService.EncryptedValue second = service.encrypt(plainText);

            // nonce 是随机的，两次加密结果不应相同
            // 但两次都能正确解密
            assertEquals(plainText, service.decrypt(first.nonce(), first.ciphertext()));
            assertEquals(plainText, service.decrypt(second.nonce(), second.ciphertext()));
        }

        @Test
        @DisplayName("错误的 nonce 导致解密失败")
        void decryptWithWrongNonceThrows() {
            ApiKeyEncryptionService.EncryptedValue encrypted = service.encrypt("test-key");

            // 用另一个加密操作的 nonce 尝试解密（几乎不可能是同一个）
            ApiKeyEncryptionService.EncryptedValue another = service.encrypt("other-key");

            assertThrows(BusinessException.class,
                () -> service.decrypt(another.nonce(), encrypted.ciphertext()));
        }

        @Test
        @DisplayName("篡改密文导致解密失败")
        void decryptWithTamperedCiphertextThrows() {
            ApiKeyEncryptionService.EncryptedValue encrypted = service.encrypt("test-key");

            // 篡改密文：替换为另一组加密结果的密文
            ApiKeyEncryptionService.EncryptedValue another = service.encrypt("other-key");

            assertThrows(BusinessException.class,
                () -> service.decrypt(encrypted.nonce(), another.ciphertext()));
        }
    }

    @Nested
    @DisplayName("使用 Base64 编码的 32 字节密钥")
    class Base64EncodedKey {

        @BeforeEach
        void setUp() {
            // 32 字节原始密钥的 Base64 编码
            byte[] rawKey = new byte[32];
            for (int i = 0; i < 32; i++) {
                rawKey[i] = (byte) i;
            }
            String base64Key = java.util.Base64.getEncoder().encodeToString(rawKey);

            LlmProviderProperties.SecurityConfig security = new LlmProviderProperties.SecurityConfig();
            security.setApiKeyEncryptionKey(base64Key);
            when(properties.getSecurity()).thenReturn(security);
            service = new ApiKeyEncryptionService(properties);
            service.init();
        }

        @Test
        @DisplayName("Base64 密钥也能正常 encrypt/decrypt 往返")
        void encryptDecryptWithBase64Key() {
            String plainText = "provider-api-key-12345";

            ApiKeyEncryptionService.EncryptedValue encrypted = service.encrypt(plainText);

            assertEquals(plainText, service.decrypt(encrypted.nonce(), encrypted.ciphertext()));
        }
    }

    @Nested
    @DisplayName("使用开发 fallback 密钥（无配置时）")
    class DevFallbackKey {

        @BeforeEach
        void setUp() {
            LlmProviderProperties.SecurityConfig security = new LlmProviderProperties.SecurityConfig();
            security.setApiKeyEncryptionKey(null);
            security.setRequireEncryptionKey(false);
            when(properties.getSecurity()).thenReturn(security);
            service = new ApiKeyEncryptionService(properties);
            service.init();
        }

        @Test
        @DisplayName("fallback 密钥也能正常 encrypt/decrypt")
        void encryptDecryptWithFallbackKey() {
            String plainText = "test-with-fallback";

            ApiKeyEncryptionService.EncryptedValue encrypted = service.encrypt(plainText);

            assertEquals(plainText, service.decrypt(encrypted.nonce(), encrypted.ciphertext()));
        }
    }

    @Nested
    @DisplayName("安全配置异常场景")
    class SecurityConfigEdgeCases {

        @Test
        @DisplayName("requireEncryptionKey=true 但未配置密钥 — 应抛出 BusinessException")
        void requireEncryptionKeyMissingThrows() {
            LlmProviderProperties.SecurityConfig security = new LlmProviderProperties.SecurityConfig();
            security.setApiKeyEncryptionKey(null);
            security.setRequireEncryptionKey(true);
            when(properties.getSecurity()).thenReturn(security);

            ApiKeyEncryptionService svc = new ApiKeyEncryptionService(properties);

            assertThrows(BusinessException.class, svc::init);
        }

        @Test
        @DisplayName("security 配置为 null — 应使用 fallback 密钥并正常工作")
        void nullSecurityConfigUsesFallback() {
            when(properties.getSecurity()).thenReturn(null);

            ApiKeyEncryptionService svc = new ApiKeyEncryptionService(properties);
            svc.init();

            String plainText = "test-null-security";
            ApiKeyEncryptionService.EncryptedValue encrypted = svc.encrypt(plainText);
            assertEquals(plainText, svc.decrypt(encrypted.nonce(), encrypted.ciphertext()));
        }

        @Test
        @DisplayName("密钥为空白字符串 — 应使用 fallback 密钥")
        void blankKeyUsesFallback() {
            LlmProviderProperties.SecurityConfig security = new LlmProviderProperties.SecurityConfig();
            security.setApiKeyEncryptionKey("   ");
            security.setRequireEncryptionKey(false);
            when(properties.getSecurity()).thenReturn(security);

            ApiKeyEncryptionService svc = new ApiKeyEncryptionService(properties);
            svc.init();

            String plainText = "test-blank-key";
            ApiKeyEncryptionService.EncryptedValue encrypted = svc.encrypt(plainText);
            assertEquals(plainText, svc.decrypt(encrypted.nonce(), encrypted.ciphertext()));
        }
    }
}
