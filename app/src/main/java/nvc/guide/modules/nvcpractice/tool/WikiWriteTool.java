package nvc.guide.modules.nvcpractice.tool;

import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcwiki.dto.WikiCreateRequest;
import nvc.guide.modules.nvcwiki.dto.WikiResponse;
import nvc.guide.modules.nvcwiki.model.NvcWikiCategory;
import nvc.guide.modules.nvcwiki.model.NvcWikiSourceType;
import nvc.guide.modules.nvcwiki.service.NvcWikiService;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

/**
 * Wiki 写入工具 — Agent 可调用，将对话中的知识沉淀到用户个人 Wiki
 */
@Component
@Slf4j
public class WikiWriteTool implements NvcTool {

    private final NvcWikiService wikiService;

    public WikiWriteTool(NvcWikiService wikiService) {
        this.wikiService = wikiService;
    }

    @Override
    public String name() { return "wiki_write"; }

    @Override
    public String description() { return "【保存笔记】仅当用户说「帮我记下来」「保存这个」「记录笔记」时调用。"; }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(WikiWriteInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            Long userId = context.getUserId();
            Long sessionId = context.getSessionId();
            if (userId == null) {
                return NvcToolResult.failure("缺少用户ID");
            }
            if (sessionId == null) {
                return NvcToolResult.failure("缺少会话ID");
            }

            WikiWriteInput params = parseInput(input);

            NvcWikiCategory category;
            try {
                category = NvcWikiCategory.valueOf(params.category());
            } catch (Exception e) {
                category = NvcWikiCategory.OTHER;
            }

            WikiCreateRequest request = new WikiCreateRequest(
                    params.title(),
                    category,
                    NvcWikiSourceType.AI_ASSISTED,
                    params.content(),
                    params.tags() != null ? params.tags() : java.util.List.of(),
                    sessionId
            );

            WikiResponse wiki = wikiService.createWiki(userId, request);

            log.info("Wiki written by agent: wikiId={}, userId={}, title={}",
                    wiki.id(), userId, wiki.title());

            return NvcToolResult.success(
                    "已保存到你的个人知识库：「" + wiki.title() + "」(ID: " + wiki.id() + ")");

        } catch (Exception e) {
            log.error("Wiki write failed", e);
            return NvcToolResult.failure("写入 Wiki 失败: " + e.getMessage());
        }
    }

    private WikiWriteInput parseInput(String input) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(input, WikiWriteInput.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "解析 Wiki 写入参数失败: " + e.getMessage(), e);
        }
    }

    record WikiWriteInput(String title, String content, String category, java.util.List<String> tags) {}
}
