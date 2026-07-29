package nvc.guide.modules.nvcassistant.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.knowledgebase.model.KnowledgeBaseType;
import nvc.guide.modules.nvcpractice.dto.RagResult;
import nvc.guide.modules.nvcpractice.service.NvcRagService;
import nvc.guide.modules.nvcpractice.tool.NvcToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 错误增强钩子 — 为失败的工具调用注入上下文信息，帮助 LLM 优雅降级
 *
 * <p>只处理以下工具的失败结果：
 * <ul>
 *   <li>rag_search</li>
 *   <li>wiki_search</li>
 *   <li>evaluate_nvc</li>
 * </ul>
 *
 * <p>失败时追加提示信息，帮助 LLM 理解错误并基于自身知识回复用户。
 * 如果 NvcRagService 可用，会尝试用 RAG 检索注入相关知识作为降级参考。
 *
 * <p>Order=4（在缓存之后执行）
 */
@Component
@Slf4j
@Order(4)
public class ErrorEnhanceHook implements NvcToolHook {

    /** 需要错误增强的工具 */
    private static final Set<String> ENHANCED_TOOLS = Set.of(
        "rag_search",
        "wiki_search",
        "evaluate_nvc"
    );

    /** 错误增强提示信息 */
    private static final String ERROR_GUIDANCE =
        "\n\n[系统提示] 此工具调用失败，请基于你的 NVC 知识回复用户，或建议稍后重试。";

    @Autowired(required = false)
    private NvcRagService nvcRagService;

    @Override
    public CompletableFuture<String> afterToolCall(String toolName, String result, NvcToolContext context) {
        if (!ENHANCED_TOOLS.contains(toolName)) {
            return CompletableFuture.completedFuture(result);
        }

        // 只处理失败结果
        if (result == null || !result.startsWith("Error:")) {
            return CompletableFuture.completedFuture(result);
        }

        log.info("[ErrorEnhanceHook] Enhancing error for tool={}, userId={}", toolName, context.getUserId());

        StringBuilder enhanced = new StringBuilder(result);
        enhanced.append(ERROR_GUIDANCE);

        // 尝试 RAG 检索注入降级知识
        if (nvcRagService != null) {
            try {
                String query = buildFallbackQuery(toolName, context);
                if (query != null) {
                    List<RagResult> ragResults = nvcRagService.retrieve(
                        query,
                        List.of(KnowledgeBaseType.NVC_THEORY),
                        3
                    );
                    if (!ragResults.isEmpty()) {
                        String knowledge = ragResults.stream()
                            .map(RagResult::text)
                            .collect(Collectors.joining("\n\n"));
                        enhanced.append("\n\n[参考知识]\n").append(knowledge);
                        log.info("[ErrorEnhanceHook] Injected {} RAG results as fallback", ragResults.size());
                    }
                }
            } catch (Exception e) {
                log.warn("[ErrorEnhanceHook] RAG fallback failed, continuing without it", e);
            }
        }

        return CompletableFuture.completedFuture(enhanced.toString());
    }

    /**
     * 构建降级 RAG 查询
     */
    private String buildFallbackQuery(String toolName, NvcToolContext context) {
        return switch (toolName) {
            case "rag_search" -> "NVC 非暴力沟通基础知识";
            case "wiki_search" -> "NVC 沟通技巧和案例";
            case "evaluate_nvc" -> "NVC 四要素 观察 感受 需要 请求";
            default -> null;
        };
    }
}
