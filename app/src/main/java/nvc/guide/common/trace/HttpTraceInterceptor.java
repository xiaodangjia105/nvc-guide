package nvc.guide.common.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 请求追踪拦截器
 *
 * <p>记录 HTTP 请求的入口信息，包括：
 * <ul>
 *   <li>请求方法（GET/POST/PUT/DELETE）</li>
 *   <li>请求 URI</li>
 *   <li>请求参数</li>
 *   <li>响应状态码</li>
 *   <li>处理耗时</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HttpTraceInterceptor implements HandlerInterceptor {

    private final TraceSpanManager traceSpanManager;
    private final ObjectMapper objectMapper;

    private static final String SPAN_TYPE = "HTTP_REQUEST";
    private static final String COMPONENT_NAME = "HttpTraceInterceptor";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        long startTime = System.currentTimeMillis();
        request.setAttribute("traceStartTime", startTime);

        // 创建 Span
        TraceSpan span = traceSpanManager.startSpan(SPAN_TYPE, COMPONENT_NAME);
        request.setAttribute("traceSpan", span);

        // 记录请求信息
        try {
            Map<String, Object> inputPayload = new HashMap<>();
            inputPayload.put("method", request.getMethod());
            inputPayload.put("uri", request.getRequestURI());
            inputPayload.put("queryString", request.getQueryString());

            // 记录请求参数
            Map<String, String[]> parameterMap = request.getParameterMap();
            if (parameterMap != null && !parameterMap.isEmpty()) {
                Map<String, String> params = new HashMap<>();
                parameterMap.forEach((key, values) -> {
                    if (values != null && values.length > 0) {
                        params.put(key, values[0]);
                    }
                });
                inputPayload.put("params", params);
            }

            // 记录客户端信息
            inputPayload.put("remoteAddr", request.getRemoteAddr());
            inputPayload.put("userAgent", request.getHeader("User-Agent"));

            span.setInputPayload(objectMapper.writeValueAsString(inputPayload));
        } catch (Exception e) {
            log.warn("[HttpTraceInterceptor] Failed to record request info: {}", e.getMessage());
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        TraceSpan span = (TraceSpan) request.getAttribute("traceSpan");
        Long startTime = (Long) request.getAttribute("traceStartTime");

        if (span == null || startTime == null) {
            return;
        }

        long duration = System.currentTimeMillis() - startTime;
        span.setDurationMs(duration);

        // 记录响应信息
        try {
            Map<String, Object> outputPayload = new HashMap<>();
            outputPayload.put("status", response.getStatus());
            outputPayload.put("contentType", response.getContentType());

            span.setOutputPayload(objectMapper.writeValueAsString(outputPayload));
        } catch (Exception e) {
            log.warn("[HttpTraceInterceptor] Failed to record response info: {}", e.getMessage());
        }

        // 判断状态
        String status = "SUCCESS";
        String failureReason = null;

        if (response.getStatus() >= 400) {
            status = "FAILED";
            failureReason = "HTTP " + response.getStatus();
        } else if (response.getStatus() >= 300) {
            status = "DEGRADED";
        }

        if (ex != null) {
            status = "FAILED";
            failureReason = ex.getMessage();
        }

        traceSpanManager.endSpan(span, status, failureReason);
    }
}
