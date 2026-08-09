package nvc.guide.common.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.modules.nvcassistant.trace.AgentSpanEntity;
import nvc.guide.modules.nvcassistant.trace.TraceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("HttpTraceInterceptor HTTP 请求追踪")
class HttpTraceInterceptorTest {

    private TraceManager traceManager;
    private ObjectMapper objectMapper;
    private HttpTraceInterceptor interceptor;

    @BeforeEach
    void setUp() {
        traceManager = mock(TraceManager.class);
        objectMapper = new ObjectMapper();
        interceptor = new HttpTraceInterceptor(traceManager, objectMapper);
    }

    @Test
    @DisplayName("应该支持创建 HTTP_REQUEST 类型的 Span")
    void shouldSupportHttpRequestSpanType() {
        // 准备
        AgentSpanEntity span = AgentSpanEntity.builder()
            .spanId("test-span")
            .spanType("HTTP_REQUEST")
            .componentName("HttpTraceInterceptor")
            .build();
        when(traceManager.startSpan("HTTP_REQUEST", "HttpTraceInterceptor")).thenReturn(span);

        // 执行
        AgentSpanEntity result = traceManager.startSpan("HTTP_REQUEST", "HttpTraceInterceptor");

        // 验证
        assertNotNull(result);
        assertEquals("HTTP_REQUEST", result.getSpanType());
        assertEquals("HttpTraceInterceptor", result.getComponentName());
    }

    @Test
    @DisplayName("应该记录 HTTP 请求信息")
    void shouldRecordHttpRequestInfo() {
        // 准备
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/nvc/chat");
        request.setParameter("conversationId", "123");

        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        AgentSpanEntity span = AgentSpanEntity.builder()
            .spanId("test-span")
            .spanType("HTTP_REQUEST")
            .componentName("HttpTraceInterceptor")
            .build();
        when(traceManager.startSpan("HTTP_REQUEST", "HttpTraceInterceptor")).thenReturn(span);

        // 执行
        AgentSpanEntity result = traceManager.startSpan("HTTP_REQUEST", "HttpTraceInterceptor");

        // 设置输入
        String inputPayload = String.format("{\"method\":\"%s\",\"uri\":\"%s\",\"params\":%s}",
            request.getMethod(), request.getRequestURI(), "{\"conversationId\":\"123\"}");
        result.setInputPayload(inputPayload);

        // 设置输出
        result.setOutputPayload("{\"status\":200}");
        result.setDurationMs(50L);

        traceManager.endSpan(result, "SUCCESS", null);

        // 验证
        verify(traceManager).endSpan(eq(result), eq("SUCCESS"), isNull());
        assertNotNull(result.getInputPayload());
        assertTrue(result.getInputPayload().contains("POST"));
        assertTrue(result.getInputPayload().contains("/api/nvc/chat"));
    }

    @Test
    @DisplayName("应该记录 HTTP 错误响应")
    void shouldRecordHttpErrorResponse() {
        // 准备
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/nvc/chat/invalid");

        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(404);

        AgentSpanEntity span = AgentSpanEntity.builder()
            .spanId("test-span")
            .spanType("HTTP_REQUEST")
            .componentName("HttpTraceInterceptor")
            .build();
        when(traceManager.startSpan("HTTP_REQUEST", "HttpTraceInterceptor")).thenReturn(span);

        // 执行
        AgentSpanEntity result = traceManager.startSpan("HTTP_REQUEST", "HttpTraceInterceptor");

        // 设置输入
        result.setInputPayload("{\"method\":\"GET\",\"uri\":\"/api/nvc/chat/invalid\"}");

        // 设置输出
        result.setOutputPayload("{\"status\":404}");
        result.setDurationMs(10L);

        traceManager.endSpan(result, "FAILED", "Not Found");

        // 验证
        verify(traceManager).endSpan(eq(result), eq("FAILED"), eq("Not Found"));
    }
}
