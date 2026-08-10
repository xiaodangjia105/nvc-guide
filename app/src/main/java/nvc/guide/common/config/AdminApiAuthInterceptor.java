package nvc.guide.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Admin API 鉴权拦截器
 *
 * <p>通过 X-Api-Key 请求头验证客户端身份，保护 Trace / Metrics 等内部管理端点。
 * <ul>
 *   <li>配置项 {@code app.security.admin-api-key} 非空时启用鉴权</li>
 *   <li>配置项为空或未配置时，拦截器放行所有请求（开发环境友好）</li>
 *   <li>验证失败返回 401 + 统一 JSON 响应</li>
 * </ul>
 */
@Slf4j
@Component
public class AdminApiAuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_NAME = "X-Api-Key";

    private final ObjectMapper objectMapper;

    @Value("${app.security.admin-api-key:}")
    private String configuredApiKey;

    public AdminApiAuthInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 未配置 API Key 时放行（开发环境默认行为）
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            return true;
        }

        String requestApiKey = request.getHeader(HEADER_NAME);
        if (configuredApiKey.equals(requestApiKey)) {
            return true;
        }

        // 鉴权失败
        log.warn("[AdminApiAuth] 鉴权失败: uri={}, remoteAddr={}",
                request.getRequestURI(), request.getRemoteAddr());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Result<Void> body = Result.error(401, "缺少或无效的管理 API Key");
        response.getWriter().write(objectMapper.writeValueAsString(body));
        return false;
    }
}
