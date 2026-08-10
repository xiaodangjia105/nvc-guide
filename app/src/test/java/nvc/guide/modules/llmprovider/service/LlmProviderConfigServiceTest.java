package nvc.guide.modules.llmprovider.service;

import nvc.guide.modules.llmprovider.dto.AsrConfigDTO;
import nvc.guide.modules.llmprovider.dto.AsrConfigRequest;
import nvc.guide.modules.llmprovider.dto.CreateProviderRequest;
import nvc.guide.modules.llmprovider.dto.DefaultProviderDTO;
import nvc.guide.modules.llmprovider.dto.ProviderDTO;
import nvc.guide.modules.llmprovider.dto.ProviderTestResult;
import nvc.guide.modules.llmprovider.dto.TtsConfigDTO;
import nvc.guide.modules.llmprovider.dto.TtsConfigRequest;
import nvc.guide.modules.llmprovider.dto.UpdateProviderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmProviderConfigService (Facade) 测试")
class LlmProviderConfigServiceTest {

    @Mock private LlmProviderCrudService crudService;
    @Mock private AsrTtsConfigService asrTtsService;

    private LlmProviderConfigService service;

    @BeforeEach
    void setUp() {
        service = new LlmProviderConfigService(crudService, asrTtsService);
    }

    @Test
    @DisplayName("listProviders 委托给 crudService")
    void listProvidersDelegates() {
        ProviderDTO dto = ProviderDTO.builder().id("dashscope").build();
        when(crudService.listProviders()).thenReturn(List.of(dto));

        List<ProviderDTO> result = service.listProviders();

        assertEquals(1, result.size());
        assertEquals("dashscope", result.get(0).id());
        verify(crudService).listProviders();
    }

    @Test
    @DisplayName("getProvider 委托给 crudService")
    void getProviderDelegates() {
        ProviderDTO dto = ProviderDTO.builder().id("glm").build();
        when(crudService.getProvider("glm")).thenReturn(dto);

        ProviderDTO result = service.getProvider("glm");

        assertEquals("glm", result.id());
        verify(crudService).getProvider("glm");
    }

    @Test
    @DisplayName("getDefaultProvider 委托给 crudService")
    void getDefaultProviderDelegates() {
        DefaultProviderDTO dto = new DefaultProviderDTO("glm", "glm");
        when(crudService.getDefaultProvider()).thenReturn(dto);

        DefaultProviderDTO result = service.getDefaultProvider();

        assertEquals("glm", result.defaultProvider());
        verify(crudService).getDefaultProvider();
    }

    @Test
    @DisplayName("testProvider 委托给 crudService")
    void testProviderDelegates() {
        ProviderTestResult result = ProviderTestResult.builder().success(true).message("ok").build();
        when(crudService.testProvider("dashscope")).thenReturn(result);

        ProviderTestResult actual = service.testProvider("dashscope");

        assertEquals(true, actual.success());
        verify(crudService).testProvider("dashscope");
    }

    @Test
    @DisplayName("createProvider 委托给 crudService")
    void createProviderDelegates() {
        CreateProviderRequest request = new CreateProviderRequest("kimi", "http://url", "key", "model", null, null);

        service.createProvider(request);

        verify(crudService).createProvider(request);
    }

    @Test
    @DisplayName("updateProvider 委托给 crudService")
    void updateProviderDelegates() {
        UpdateProviderRequest request = new UpdateProviderRequest("http://url", "key", "model", null, null);

        service.updateProvider("kimi", request);

        verify(crudService).updateProvider("kimi", request);
    }

    @Test
    @DisplayName("deleteProvider 委托给 crudService")
    void deleteProviderDelegates() {
        service.deleteProvider("kimi");

        verify(crudService).deleteProvider("kimi");
    }

    @Test
    @DisplayName("updateDefaultProvider 委托给 crudService")
    void updateDefaultProviderDelegates() {
        DefaultProviderDTO request = new DefaultProviderDTO("glm");

        service.updateDefaultProvider(request);

        verify(crudService).updateDefaultProvider(request);
    }

    @Test
    @DisplayName("updateDefaultEmbeddingProvider 委托给 crudService")
    void updateDefaultEmbeddingProviderDelegates() {
        DefaultProviderDTO request = new DefaultProviderDTO(null, "dashscope");

        service.updateDefaultEmbeddingProvider(request);

        verify(crudService).updateDefaultEmbeddingProvider(request);
    }

    @Test
    @DisplayName("reloadProviders 委托给 crudService")
    void reloadProvidersDelegates() {
        service.reloadProviders();

        verify(crudService).reloadProviders();
    }

    @Test
    @DisplayName("getAsrConfig 委托给 asrTtsService")
    void getAsrConfigDelegates() {
        AsrConfigDTO dto = AsrConfigDTO.builder().url("ws://test").build();
        when(asrTtsService.getAsrConfig()).thenReturn(dto);

        AsrConfigDTO result = service.getAsrConfig();

        assertEquals("ws://test", result.getUrl());
        verify(asrTtsService).getAsrConfig();
    }

    @Test
    @DisplayName("getTtsConfig 委托给 asrTtsService")
    void getTtsConfigDelegates() {
        TtsConfigDTO dto = TtsConfigDTO.builder().model("test-model").build();
        when(asrTtsService.getTtsConfig()).thenReturn(dto);

        TtsConfigDTO result = service.getTtsConfig();

        assertEquals("test-model", result.getModel());
        verify(asrTtsService).getTtsConfig();
    }

    @Test
    @DisplayName("testAsrConfig 委托给 asrTtsService")
    void testAsrConfigDelegates() {
        ProviderTestResult result = ProviderTestResult.builder().success(true).message("ok").build();
        when(asrTtsService.testAsrConfig()).thenReturn(result);

        ProviderTestResult actual = service.testAsrConfig();

        assertEquals(true, actual.success());
        verify(asrTtsService).testAsrConfig();
    }

    @Test
    @DisplayName("updateAsrConfig 委托给 asrTtsService")
    void updateAsrConfigDelegates() {
        AsrConfigRequest request = new AsrConfigRequest("ws://url", "model", "key", "zh", "wav", 16000, null, null, null, null);

        service.updateAsrConfig(request);

        verify(asrTtsService).updateAsrConfig(request);
    }

    @Test
    @DisplayName("updateTtsConfig 委托给 asrTtsService")
    void updateTtsConfigDelegates() {
        TtsConfigRequest request = new TtsConfigRequest("model", "key", "voice", "wav", 16000, "mode", "zh", 1.0f, 1);

        service.updateTtsConfig(request);

        verify(asrTtsService).updateTtsConfig(request);
    }
}
