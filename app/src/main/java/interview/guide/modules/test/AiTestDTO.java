package interview.guide.modules.test;

/**
 * AI 测试接口的请求/响应 DTO
 */
public class AiTestDTO {

    /** 请求 */
    public record NvcScoreRequest(String userMessage) {}

    /** 响应：直接返回 AI 的原始 JSON */
    public record NvcScoreResponse(String rawJson) {}
}
