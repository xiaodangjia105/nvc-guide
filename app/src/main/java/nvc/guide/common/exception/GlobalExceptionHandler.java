package nvc.guide.common.exception;

import nvc.guide.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.context.request.WebRequest;

import java.net.SocketTimeoutException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 处理业务异常
     *
     * <p>根据 ErrorCode 映射到合适的 HTTP 状态码
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, WebRequest request) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        HttpStatus status = mapErrorCodeToHttpStatus(e.getCode());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 将 ErrorCode 映射到 HTTP 状态码
     */
    private HttpStatus mapErrorCodeToHttpStatus(int code) {
        if (code >= 400 && code < 500) return HttpStatus.BAD_REQUEST;
        if (code == 404 || code == 3001) return HttpStatus.NOT_FOUND;
        if (code == 403) return HttpStatus.FORBIDDEN;
        if (code == 401) return HttpStatus.UNAUTHORIZED;
        if (code == 429 || code == 8001) return HttpStatus.TOO_MANY_REQUESTS;
        if (code >= 500 && code < 600) return HttpStatus.INTERNAL_SERVER_ERROR;
        if (code >= 7000 && code < 8000) return HttpStatus.SERVICE_UNAVAILABLE;
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return Result.error(ErrorCode.BAD_REQUEST, message);
    }
    
    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败: {}", message);
        return Result.error(ErrorCode.BAD_REQUEST, message);
    }

    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("文件上传大小超限: {}", e.getMessage());
        return Result.error(ErrorCode.BAD_REQUEST, "文件大小超过限制");
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.error(ErrorCode.BAD_REQUEST, e.getMessage());
    }

    /**
     * 处理 AI 服务网络异常（SSL握手失败、连接超时等）
     */
    @ExceptionHandler(ResourceAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleResourceAccessException(ResourceAccessException e) {
        log.error("AI服务连接失败: {}", e.getMessage(), e);
        
        // 判断具体异常类型
        Throwable cause = e.getCause();
        if (cause instanceof SocketTimeoutException) {
            return Result.error(ErrorCode.AI_SERVICE_TIMEOUT, "AI服务响应超时，请稍后重试");
        }
        
        // SSL握手失败或其他网络问题
        String message = e.getMessage();
        if (message != null && message.contains("handshake")) {
            return Result.error(ErrorCode.AI_SERVICE_UNAVAILABLE, "AI服务连接失败（网络不稳定），请检查网络或稍后重试");
        }
        
        return Result.error(ErrorCode.AI_SERVICE_UNAVAILABLE, "AI服务暂时不可用，请稍后重试");
    }
    
    /**
     * 处理 AI 服务调用异常
     */
    @ExceptionHandler(RestClientException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleRestClientException(RestClientException e) {
        log.error("AI服务调用失败: {}", e.getMessage(), e);

        String message = e.getMessage();
        if (message != null) {
            if (message.contains("401") || message.contains("Unauthorized")) {
                return Result.error(ErrorCode.AI_API_KEY_INVALID, "AI服务密钥无效，请联系管理员");
            }
            if (message.contains("429") || message.contains("Too Many Requests")) {
                return Result.error(ErrorCode.AI_RATE_LIMIT_EXCEEDED, "AI服务调用过于频繁，请稍后重试");
            }
        }

        return Result.error(ErrorCode.AI_SERVICE_ERROR, "AI服务调用失败，请稍后重试");
    }

    /**
     * 处理 404 - 资源未找到异常
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFoundException(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        log.warn("资源未找到: {}", e.getResourcePath());
        return Result.error(ErrorCode.NOT_FOUND, "API 接口不存在");
    }

    /**
     * 处理请求方法不支持异常
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleHttpRequestMethodNotSupportedException(org.springframework.web.HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {} {}", e.getMethod(), e.getSupportedHttpMethods());
        return Result.error(ErrorCode.METHOD_NOT_ALLOWED, "请求方法不支持: " + e.getMethod());
    }

    /**
     * 处理其他未知异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, WebRequest request) {
        // SSE 请求不处理（客户端断开连接等）
        if (isSseRequest(request)) {
            log.debug("SSE request exception (ignored): {}", e.getMessage());
            return null;
        }
        log.error("系统异常: {}", e.getMessage(), e);
        return Result.error(ErrorCode.INTERNAL_ERROR, "系统繁忙，请稍后重试");
    }

    /**
     * 判断是否是 SSE 请求
     *
     * <p>通过 Accept 头或 URL 路径判断，兼容前端 fetch 未设置 Accept 的情况。
     */
    private boolean isSseRequest(WebRequest request) {
        // 1. 检查 Accept 头（标准方式）
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            return true;
        }
        // 2. 检查 URL 路径（SSE 端点以 /stream 结尾，且在 /api/ 路径下）
        String description = request.getDescription(false);
        if (description != null && description.contains("uri=/api/")
            && description.endsWith("/stream")) {
            return true;
        }
        return false;
    }
}
