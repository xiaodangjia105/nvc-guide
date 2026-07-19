package nvc.guide.modules.nvcpractice.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.knowledgebase.model.KnowledgeBaseType;
import nvc.guide.modules.nvcpractice.dto.RagResult;
import nvc.guide.modules.nvcpractice.service.NvcRagService;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class RagSearchTool implements NvcTool {

    private final NvcRagService ragService;

    @Override
    public String name() { return "rag_search"; }

    @Override
    public String description() {
        return "搜索 NVC 知识库，检索相关理论、话术模板、情绪词汇等信息。当用户询问 NVC 相关知识或需要参考资料时使用。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(RagSearchInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            RagSearchInput params = JsonParser.fromJson(input, RagSearchInput.class);
            int topK = params.topK() != null ? params.topK() : 5;

            List<RagResult> results = ragService.retrieve(
                params.query(),
                List.of(KnowledgeBaseType.NVC_THEORY,
                        KnowledgeBaseType.SPEECH_TEMPLATE,
                        KnowledgeBaseType.EMOTION_VOCAB),
                topK
            );

            String formatted = results.stream()
                .map(r -> "- " + r.text())
                .collect(Collectors.joining("\n"));

            return NvcToolResult.success(formatted.isEmpty() ? "未找到相关知识" : formatted);
        } catch (Exception e) {
            log.error("[RagSearchTool] Execution failed", e);
            return NvcToolResult.failure("知识检索失败: " + e.getMessage());
        }
    }

    record RagSearchInput(String query, Integer topK) {}
}
