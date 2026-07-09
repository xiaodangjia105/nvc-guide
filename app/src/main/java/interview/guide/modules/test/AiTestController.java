package interview.guide.modules.test;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 调用 Hello World 测试接口。
 * 验证 LLM 连接 + Prompt 工程 + JSON 结构化的最简范例。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "AI 测试", description = "Hello World AI 调用验证")
public class AiTestController {

    private final LlmProviderRegistry llmProviderRegistry;

    /**
     * NVC 评分测试接口
     * POST /api/test/nvc-score
     */
    @Operation(summary = "NVC 评分测试", description = "发送一句话，AI 返回 NVC 四步评分")
    @PostMapping("/api/test/nvc-score")
    public Result<AiTestDTO.NvcScoreResponse> nvcScore(@RequestBody AiTestDTO.NvcScoreRequest request) {
        log.info("收到 NVC 评分测试请求: {}", request.userMessage());

        String systemPrompt = """
            你是一位非暴力沟通（NVC）教练。请分析用户的话语，按以下 JSON 格式返回分析结果：
            {
              "observation": "观察到的事实",
              "feeling": "说话者的感受",
              "need": "背后的需要",
              "request": "可以提出的请求",
              "nvcScore": 评分(0-100),
              "comment": "一句话评语"
            }
            请仅返回 JSON，不要 Markdown 代码块，不要解释。
            """;

        String response = llmProviderRegistry.getDefaultChatClient().prompt()
                .system(systemPrompt)
                .user(request.userMessage())
                .call()
                .content();

        log.info("AI 返回: {}", response);
        return Result.success(new AiTestDTO.NvcScoreResponse(response));
    }
}