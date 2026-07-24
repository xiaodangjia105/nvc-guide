package nvc.guide.modules.nvcpractice.tool;

import lombok.extern.slf4j.Slf4j;
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
    public String description() { return "将知识、案例或学习心得写入用户的个人 Wiki 知识库"; }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(WikiWriteInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
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
                    context.getSessionId()
            );

            WikiResponse wiki = wikiService.createWiki(context.getUserId(), request);

            log.info("Wiki written by agent: wikiId={}, userId={}, title={}",
                    wiki.id(), context.getUserId(), wiki.title());

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
            throw new RuntimeException("解析 Wiki 写入参数失败: " + e.getMessage());
        }
    }

    record WikiWriteInput(String title, String content, String category, java.util.List<String> tags) {}
}
