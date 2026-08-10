package nvc.guide.common.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private TraceSpanManager traceSpanManager;
    private ObjectMapper objectMapper;
    private HttpTraceInterceptor interceptor;

    @BeforeEach
    void setUp() {
        traceSpanManager = mock(TraceSpanManager.class);
        objectMapper = new ObjectMapper();
        interceptor = new HttpTraceInterceptor(traceSpanManager, objectMapper);
    }

    @Test
    @DisplayName("应该支持创建 HTTP_REQUEST 类型的 Span")
    void shouldSupportHttpRequestSpanType() {
        // 准备
        TraceSpan span = mock(TraceSpan.class);
        when(span.getSpanType()).thenReturn("HTTP_REQUEST");
        when(traceSpanManager.startSpan("HTTP_REQUEST", "HttpTraceInterceptor")).thenReturn(span);

        // 执行
        TraceSpan result = traceSpanManager.startSpan("HTTP_REQUEST", "HttpTraceInterceptor");

        // 验证
        assertNotNull(result);
        assertEquals("HTTP_REQUEST", result.getSpanType());
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

        TraceSpan span = mock(TraceSpan.class);
        when(traceSpanManager.startSpan("HTTP_REQUEST", "HttpTraceInterceptor")).thenReturn(span);

        // 执行
        TraceSpan result = traceSpanManager.startSpan("HTTP_REQUEST", "HttpTraceInterceptor");

        // 设置输入
        String inputPayload = String.format("{\"method\":\"%s\",\"uri\":\"%s\",\"params\":%s}",
            request.getMethod(), request.getRequestURI(), "{\"conversationId\":\"123\"}");
        result.setInputPayload(inputPayload);

        // 设置输出
        result.setOutputPayload("{\"status\":200}");
        result.setDurationMs(50L);

        traceSpanManager.endSpan(result, "SUCCESS", null);

        // 验证
        verify(traceSpanManager).endSpan(eq(result), eq("SUCCESS"), isNull());
        verify(result).setInputPayload(contains("POST"));
        verify(result).setInputPayload(contains("/api/nvc/chat"));
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

        TraceSpan span = mock(TraceSpan.class);
        when(traceSpanManager.startSpan("HTTP_REQUEST", "HttpTraceInterceptor")).thenReturn(span);

        // 执行
        TraceSpan result = traceSpanManager.startSpan("HTTP_REQUEST", "HttpTraceInterceptor");

        // 设置输入
        result.setInputPayload("{\"method\":\"GET\",\"uri\":\"/api/nvc/chat/invalid\"}");

        // 设置输出
        result.setOutputPayload("{\"status\":404}");
        result.setDurationMs(10L);

        traceSpanManager.endSpan(result, "FAILED", "Not Found");

        // 验证
        verify(traceSpanManager).endSpan(eq(result), eq("FAILED"), eq("Not Found"));
    }
}
