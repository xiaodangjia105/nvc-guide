package nvc.guide.modules.knowledgebase.service;

import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.modules.nvcassistant.trace.AgentSpanEntity;
import nvc.guide.modules.nvcassistant.trace.TraceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("KnowledgeBaseQueryService Trace 埋点")
class KnowledgeBaseQueryServiceTraceTest {

    private LlmProviderRegistry llmProviderRegistry;
    private KnowledgeBaseVectorService vectorService;
    private KnowledgeBaseListService listService;
    private KnowledgeBaseCountService countService;
    private KnowledgeBaseQueryProperties queryProperties;
    private ResourceLoader resourceLoader;
    private TraceManager traceManager;
    private KnowledgeBaseQueryService queryService;

    @BeforeEach
    void setUp() throws IOException {
        llmProviderRegistry = mock(LlmProviderRegistry.class);
        vectorService = mock(KnowledgeBaseVectorService.class);
        listService = mock(KnowledgeBaseListService.class);
        countService = mock(KnowledgeBaseCountService.class);
        resourceLoader = mock(ResourceLoader.class);
        traceManager = mock(TraceManager.class);

        // Mock resource loader
        org.springframework.core.io.Resource mockResource = mock(org.springframework.core.io.Resource.class);
        when(mockResource.getContentAsString(any())).thenReturn("test prompt");
        when(resourceLoader.getResource(anyString())).thenReturn(mockResource);

        // 创建查询属性
        queryProperties = new KnowledgeBaseQueryProperties();
        queryProperties.getRewrite().setEnabled(false);
        queryProperties.getSearch().setShortQueryLength(4);
        queryProperties.getSearch().setTopkShort(20);
        queryProperties.getSearch().setTopkMedium(12);
        queryProperties.getSearch().setTopkLong(8);
        queryProperties.getSearch().setMinScoreShort(0.18);
        queryProperties.getSearch().setMinScoreDefault(0.28);

        queryService = new KnowledgeBaseQueryService(
            llmProviderRegistry, vectorService, listService, countService,
            queryProperties, resourceLoader);
    }

    @Test
    @DisplayName("应该支持创建 RAG_RETRIEVAL 类型的 Span")
    void shouldSupportRagRetrievalSpanType() {
        // 准备
        AgentSpanEntity span = AgentSpanEntity.builder()
            .spanId("test-span")
            .spanType("RAG_RETRIEVAL")
            .componentName("KnowledgeBaseQueryService")
            .build();
        when(traceManager.startSpan("RAG_RETRIEVAL", "KnowledgeBaseQueryService")).thenReturn(span);

        // 执行
        AgentSpanEntity result = traceManager.startSpan("RAG_RETRIEVAL", "KnowledgeBaseQueryService");

        // 验证
        assertNotNull(result);
        assertEquals("RAG_RETRIEVAL", result.getSpanType());
        assertEquals("KnowledgeBaseQueryService", result.getComponentName());
    }

    @Test
    @DisplayName("应该记录 RAG 检索的输入输出")
    void shouldRecordRagRetrievalInputOutput() {
        // 准备
        AgentSpanEntity span = AgentSpanEntity.builder()
            .spanId("test-span")
            .spanType("RAG_RETRIEVAL")
            .componentName("KnowledgeBaseQueryService")
            .build();
        when(traceManager.startSpan("RAG_RETRIEVAL", "KnowledgeBaseQueryService")).thenReturn(span);

        // 模拟向量搜索结果
        Document doc1 = new Document("doc1", "NVC 是一种沟通方式", java.util.Map.of("score", 0.85));
        Document doc2 = new Document("doc2", "观察是 NVC 的第一步", java.util.Map.of("score", 0.72));
        when(vectorService.similaritySearch(anyString(), anyList(), anyInt(), anyDouble()))
            .thenReturn(List.of(doc1, doc2));

        // 执行
        AgentSpanEntity result = traceManager.startSpan("RAG_RETRIEVAL", "KnowledgeBaseQueryService");

        // 设置输入
        result.setInputPayload("{\"query\":\"什么是 NVC\",\"knowledgeBaseId\":1}");

        // 模拟检索结果
        String searchResults = "[{\"id\":\"doc1\",\"content\":\"NVC 是一种沟通方式\",\"score\":0.85}]";
        result.setOutputPayload(searchResults);
        result.setDurationMs(150L);

        traceManager.endSpan(result, "SUCCESS", null);

        // 验证
        verify(traceManager).endSpan(eq(result), eq("SUCCESS"), isNull());
        assertNotNull(result.getInputPayload());
        assertTrue(result.getInputPayload().contains("什么是 NVC"));
        assertNotNull(result.getOutputPayload());
        assertTrue(result.getOutputPayload().contains("doc1"));
    }
}
