package nvc.guide.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ========== 通用错误 1xxx ==========
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // ========== NVC 练习模块错误 3xxx ==========
    NVC_SESSION_NOT_FOUND(3001, "NVC 练习会话不存在"),
    NVC_SESSION_EXPIRED(3002, "NVC 练习会话已过期"),
    NVC_EVALUATION_FAILED(3003, "NVC 评估失败"),
    NVC_SCENARIO_NOT_FOUND(3004, "NVC 场景不存在"),
    NVC_PROFILE_NOT_FOUND(3005, "用户档案不存在"),
    NVC_AGENT_CONFIG_NOT_FOUND(3006, "Agent 配置不存在"),
    INVALID_OPERATION(3007, "操作不允许"),

    // ========== 存储模块错误 4xxx ==========
    STORAGE_UPLOAD_FAILED(4001, "文件上传失败"),
    STORAGE_DOWNLOAD_FAILED(4002, "文件下载失败"),
    STORAGE_DELETE_FAILED(4003, "文件删除失败"),

    // ========== 导出模块错误 5xxx ==========
    EXPORT_PDF_FAILED(5001, "PDF导出失败"),

    // ========== 知识库模块错误 6xxx ==========
    KNOWLEDGE_BASE_NOT_FOUND(6001, "知识库不存在"),
    KNOWLEDGE_BASE_PARSE_FAILED(6002, "知识库文件解析失败"),
    KNOWLEDGE_BASE_QUERY_FAILED(6004, "知识库查询失败"),
    KNOWLEDGE_BASE_DELETE_FAILED(6005, "知识库删除失败"),
    KNOWLEDGE_BASE_VECTORIZATION_FAILED(6006, "知识库向量化失败"),

    // ========== Wiki 模块错误 68xx ==========
    WIKI_NOT_FOUND(6801, "Wiki 条目不存在"),
    WIKI_GENERATION_FAILED(6802, "Wiki 自动生成失败"),
    WIKI_ACCESS_DENIED(6803, "无权访问该 Wiki 条目"),

    // ========== AI服务错误 7xxx ==========
    AI_SERVICE_UNAVAILABLE(7001, "AI服务暂时不可用，请稍后重试"),
    AI_SERVICE_TIMEOUT(7002, "AI服务响应超时"),
    AI_SERVICE_ERROR(7003, "AI服务调用失败"),
    AI_API_KEY_INVALID(7004, "AI服务密钥无效"),
    AI_RATE_LIMIT_EXCEEDED(7005, "AI服务调用频率超限"),

    // ========== 限流模块错误 8xxx ==========
    RATE_LIMIT_EXCEEDED(8001, "请求过于频繁，请稍后再试"),

    // ========== NVC 语音练习模块错误 10xxx ==========
    NVC_VOICE_SESSION_NOT_FOUND(10001, "NVC 语音练习会话不存在"),
    NVC_VOICE_EVALUATION_FAILED(10004, "NVC 语音评估失败"),
    NVC_VOICE_EVALUATION_NOT_FOUND(10006, "NVC 语音评估结果不存在"),

    // ========== Provider管理模块错误 11xxx ==========
    PROVIDER_NOT_FOUND(11001, "LLM Provider 不存在"),
    PROVIDER_ALREADY_EXISTS(11002, "LLM Provider 已存在"),
    PROVIDER_CONFIG_READ_FAILED(11004, "读取 Provider 配置失败"),
    PROVIDER_CONFIG_WRITE_FAILED(11005, "写入 Provider 配置失败"),
    PROVIDER_TEST_FAILED(11006, "Provider 连通性测试失败"),
    PROVIDER_DEFAULT_CANNOT_DELETE(11007, "默认 Provider 不可删除"),
    MODULE_NOT_FOUND(11008, "模块不存在"),
    VOICE_CONFIG_READ_FAILED(11009, "读取语音服务配置失败"),
    VOICE_CONFIG_WRITE_FAILED(11010, "写入语音服务配置失败"),
    VOICE_CONFIG_TEST_FAILED(11011, "语音服务连通性测试失败");

    private final Integer code;
    private final String message;
}
