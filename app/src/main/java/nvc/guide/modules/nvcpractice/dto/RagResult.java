package nvc.guide.modules.nvcpractice.dto;

import java.util.Map;

/**
 * RAG 检索结果
 *
 * @param text     检索到的文本内容
 * @param metadata 文档元数据（kb_id 等）
 * @param score    相似度分数（0~1）
 */
public record RagResult(
    String text,
    Map<String, Object> metadata,
    double score
) {
}
