package nvc.guide.modules.nvcwiki.service;

import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.ai.StructuredOutputInvoker;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcwiki.dto.WikiCreateRequest;
import nvc.guide.modules.nvcwiki.dto.WikiResponse;
import nvc.guide.modules.nvcwiki.model.NvcWikiCategory;
import nvc.guide.modules.nvcwiki.model.NvcWikiSourceType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wiki 自动生成服务
 * 在练习结束后，根据对话内容自动生成个人知识库笔记
 */
@Service
@Slf4j
public class NvcWikiAutoGenerateService {

    private final NvcWikiService wikiService;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final ChatClient chatClient;

    public NvcWikiAutoGenerateService(
            NvcWikiService wikiService,
            StructuredOutputInvoker structuredOutputInvoker,
            @Qualifier("defaultChatClient") ChatClient chatClient) {
        this.wikiService = wikiService;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.chatClient = chatClient;
    }

    /**
     * 从练习会话生成 Wiki 笔记
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param messages  会话消息列表
     * @return 生成的 Wiki 响应
     */
    public WikiResponse generateFromSession(Long userId, Long sessionId,
                                             List<NvcPracticeMessageEntity> messages) {
        log.info("Generating wiki from session: userId={}, sessionId={}, messageCount={}",
                userId, sessionId, messages.size());

        try {
            // 1. 加载 Prompt 模板
            String systemPrompt = loadPromptTemplate();

            // 2. 构建 user prompt（包含对话历史）
            String userPrompt = buildUserPrompt(messages);

            // 3. 调用 LLM 生成笔记
            BeanOutputConverter<WikiGenerateResult> converter =
                    new BeanOutputConverter<>(WikiGenerateResult.class);

            WikiGenerateResult result = structuredOutputInvoker.invoke(
                    chatClient,
                    systemPrompt,
                    userPrompt,
                    converter,
                    ErrorCode.WIKI_GENERATION_FAILED,
                    "Wiki 自动生成失败: ",
                    "WikiAutoGenerate",
                    log
            );

            if (result == null) {
                log.warn("Wiki generation returned null, using fallback");
                result = buildFallbackResult(messages);
            }

            // 4. 保存为 Wiki 条目
            WikiCreateRequest request = new WikiCreateRequest(
                    result.title(),
                    NvcWikiCategory.CONVERSATION_CASE,
                    NvcWikiSourceType.AUTO_GENERATED,
                    result.content(),
                    result.tags() != null ? result.tags() : List.of(),
                    sessionId
            );

            WikiResponse wiki = wikiService.createWiki(userId, request);
            log.info("Wiki auto-generated: wikiId={}, title={}", wiki.id(), wiki.title());
            return wiki;

        } catch (Exception e) {
            log.error("Wiki auto-generation failed: userId={}, sessionId={}", userId, sessionId, e);
            // 降级：生成简化版笔记
            WikiResponse fallback = generateFallbackWiki(userId, sessionId, messages);
            if (fallback != null) {
                return fallback;
            }
            throw e;
        }
    }

    /**
     * 加载 Prompt 模板
     */
    private String loadPromptTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/nvc-wiki-auto-generate.st");
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load wiki auto-generate prompt template", e);
            return getDefaultSystemPrompt();
        }
    }

    /**
     * 构建用户提示词（包含对话历史）
     */
    private String buildUserPrompt(List<NvcPracticeMessageEntity> messages) {
        StringBuilder sb = new StringBuilder("以下是用户的 NVC 练习对话记录：\n\n");

        for (NvcPracticeMessageEntity msg : messages) {
            String role = msg.getRole() != null ? msg.getRole().name() : "UNKNOWN";
            sb.append("【").append(role).append("】: ");
            sb.append(msg.getContent()).append("\n\n");
        }

        sb.append("请根据以上对话内容，生成一份个人知识库笔记。");
        return sb.toString();
    }

    /**
     * 降级：生成简化版 Wiki
     */
    private WikiResponse generateFallbackWiki(Long userId, Long sessionId,
                                                List<NvcPracticeMessageEntity> messages) {
        try {
            String summary = messages.stream()
                    .limit(5)
                    .map(m -> m.getContent())
                    .collect(Collectors.joining(" "));

            String title = "练习笔记 - " + sessionId;
            String content = "## 练习对话摘要\n\n" + summary;

            WikiCreateRequest request = new WikiCreateRequest(
                    title,
                    NvcWikiCategory.CONVERSATION_CASE,
                    NvcWikiSourceType.AUTO_GENERATED,
                    content,
                    List.of("练习笔记", "自动降级"),
                    sessionId
            );

            return wikiService.createWiki(userId, request);
        } catch (Exception e) {
            log.error("Fallback wiki generation also failed", e);
            return null;
        }
    }

    /**
     * 构建默认系统提示词（模板加载失败时使用）
     */
    private String getDefaultSystemPrompt() {
        return """
                你是一个 NVC（非暴力沟通）学习助手。用户刚完成一次 NVC 练习，请根据对话内容生成一份个人知识库笔记。

                ## 要求

                1. 聚焦案例和知识，不要记录评估分数或改进建议
                2. 记录用户在对话中实际使用的 NVC 技巧
                3. 提取关键对话片段作为案例
                4. 总结用户学到的知识点

                ## 输出格式

                请返回 JSON 格式：
                {
                  "title": "简洁的标题",
                  "content": "Markdown 格式的笔记内容",
                  "tags": ["标签1", "标签2"]
                }
                """;
    }

    /**
     * LLM 返回的结果结构
     */
    public record WikiGenerateResult(
            String title,
            String content,
            List<String> tags
    ) {}

    /**
     * 构建降级结果
     */
    private WikiGenerateResult buildFallbackResult(List<NvcPracticeMessageEntity> messages) {
        String summary = messages.stream()
                .limit(3)
                .map(NvcPracticeMessageEntity::getContent)
                .collect(Collectors.joining(" "));
        return new WikiGenerateResult(
                "练习笔记",
                "## 练习摘要\n\n" + summary,
                List.of("练习笔记")
        );
    }
}
