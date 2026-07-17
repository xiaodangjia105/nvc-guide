package nvc.guide.modules.nvcpractice.service;

import nvc.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import nvc.guide.modules.knowledgebase.model.KnowledgeBaseType;
import nvc.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import nvc.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import nvc.guide.modules.nvcpractice.dto.RagResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * NVC 专用 RAG 检索服务
 * 封装向量检索逻辑，支持多知识库类型过滤和个性化加权
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcRagService {

  private final KnowledgeBaseVectorService vectorService;
  private final KnowledgeBaseRepository knowledgeBaseRepository;

  private static final double DEFAULT_MIN_SCORE = 0.3;

  /**
   * 统一 RAG 检索
   *
   * @param query          检索查询文本
   * @param knowledgeTypes 检索的知识库类型
   * @param topK           返回结果数量
   * @return 按相似度降序排列的检索结果
   */
  public List<RagResult> retrieve(String query, List<KnowledgeBaseType> knowledgeTypes, int topK) {
    if (query == null || query.isBlank() || knowledgeTypes == null || knowledgeTypes.isEmpty()) {
      return List.of();
    }

    // 1. 按类型查出所有匹配的知识库 ID
    List<Long> kbIds = knowledgeBaseRepository.findByTypeInOrderByUploadedAtDesc(knowledgeTypes)
        .stream()
        .map(KnowledgeBaseEntity::getId)
        .toList();

    if (kbIds.isEmpty()) {
      log.debug("No knowledge bases found for types: {}", knowledgeTypes);
      return List.of();
    }

    // 2. 向量检索
    List<Document> docs = vectorService.similaritySearch(query, kbIds, topK, DEFAULT_MIN_SCORE);

    // 3. 转换为 RagResult
    return docs.stream()
        .map(doc -> new RagResult(
            doc.getText(),
            doc.getMetadata(),
            extractScore(doc)
        ))
        .toList();
  }

  /**
   * 个性化检索（基于用户薄弱要素加权排序）
   *
   * @param query        检索查询文本
   * @param weakKeyword  薄弱要素关键词（如 "observation"、"feeling"）
   * @param topK         返回结果数量
   * @return 加权排序后的检索结果
   */
  public List<RagResult> retrievePersonalized(String query, String weakKeyword, int topK) {
    // 1. 基础检索，多取一些候选
    List<RagResult> baseResults = retrieve(query,
        List.of(KnowledgeBaseType.NVC_THEORY, KnowledgeBaseType.SPEECH_TEMPLATE),
        topK * 2);

    if (baseResults.isEmpty() || weakKeyword == null || weakKeyword.isBlank()) {
      return limitResults(baseResults, topK);
    }

    // 2. 按薄弱要素加权排序
    String keyword = weakKeyword.toLowerCase();
    return baseResults.stream()
        .sorted((a, b) -> {
          double scoreA = a.score() * (containsKeyword(a, keyword) ? 1.5 : 1.0);
          double scoreB = b.score() * (containsKeyword(b, keyword) ? 1.5 : 1.0);
          return Double.compare(scoreB, scoreA);
        })
        .limit(topK)
        .toList();
  }

  /**
   * 将检索结果格式化为可注入 Prompt 的文本
   *
   * @param results 检索结果列表
   * @return 拼接后的文本，可直接注入 PracticeContext.ragContext
   */
  public String formatForPrompt(List<RagResult> results) {
    if (results == null || results.isEmpty()) {
      return null;
    }
    return results.stream()
        .map(RagResult::text)
        .collect(Collectors.joining("\n\n"));
  }

  private boolean containsKeyword(RagResult result, String keyword) {
    return result.text() != null && result.text().toLowerCase().contains(keyword);
  }

  private double extractScore(Document doc) {
    Object distance = doc.getMetadata().getOrDefault("distance", 0.0);
    if (distance instanceof Double d) {
      return d;
    }
    try {
      return Double.parseDouble(distance.toString());
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  private List<RagResult> limitResults(List<RagResult> results, int limit) {
    if (results.size() <= limit) {
      return results;
    }
    return results.subList(0, limit);
  }
}
