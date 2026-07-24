package nvc.guide.modules.nvcpractice.tool;

import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcwiki.dto.WikiSearchResult;
import nvc.guide.modules.nvcwiki.service.NvcWikiService;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Wiki 搜索工具 — Agent 可调用，检索用户个人 Wiki 知识库
 */
@Component
@Slf4j
public class WikiSearchTool implements NvcTool {

    private final NvcWikiService wikiService;

    public WikiSearchTool(NvcWikiService wikiService) {
        this.wikiService = wikiService;
    }

    @Override
    public String name() { return "wiki_search"; }

    @Override
    public String description() { return "搜索用户的个人 Wiki 知识库，查找相关案例、知识和经验"; }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(WikiSearchInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            WikiSearchInput params = parseInput(input);
            int topK = params.topK() != null ? Math.min(params.topK(), 10) : 5;

            List<WikiSearchResult> results = wikiService.searchWikis(
                    context.getUserId(), params.query(), topK);

            if (results.isEmpty()) {
                return NvcToolResult.success("在你的个人知识库中没有找到相关内容。");
            }

            StringBuilder sb = new StringBuilder("根据你的个人知识库，找到以下相关内容：\n\n");
            for (int i = 0; i < results.size(); i++) {
                WikiSearchResult r = results.get(i);
                sb.append(String.format("%d. 【%s】(分类: %s, 相似度: %.2f)\n",
                        i + 1, r.title(), r.category().name(), r.score()));
                sb.append(r.contentSnippet()).append("\n\n");
            }

            log.info("Wiki search completed: userId={}, query={}, results={}",
                    context.getUserId(), params.query(), results.size());

            return NvcToolResult.success(sb.toString());

        } catch (Exception e) {
            log.error("Wiki search failed", e);
            return NvcToolResult.failure("搜索 Wiki 失败: " + e.getMessage());
        }
    }

    private WikiSearchInput parseInput(String input) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(input, WikiSearchInput.class);
        } catch (Exception e) {
            throw new RuntimeException("解析 Wiki 搜索参数失败: " + e.getMessage());
        }
    }

    record WikiSearchInput(String query, Integer topK) {}
}
