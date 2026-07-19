package nvc.guide.modules.nvcpractice.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WikiWriteTool implements NvcTool {

    @Override
    public String name() { return "wiki_write"; }

    @Override
    public String description() { return "写入个人 Wiki 知识条目"; }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(WikiWriteInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        return NvcToolResult.failure("Wiki 功能暂未开放，敬请期待");
    }

    record WikiWriteInput(String title, String content, String category) {}
}
