package nvc.guide.modules.nvcpractice.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WikiSearchTool implements NvcTool {

    @Override
    public String name() { return "wiki_search"; }

    @Override
    public String description() { return "搜索个人 Wiki 知识库"; }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(WikiSearchInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        return NvcToolResult.failure("Wiki 功能暂未开放，敬请期待");
    }

    record WikiSearchInput(String query, Integer topK) {}
}
