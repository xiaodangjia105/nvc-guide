package nvc.guide.modules.nvcwiki.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import nvc.guide.modules.knowledgebase.model.KnowledgeBaseType;
import nvc.guide.modules.knowledgebase.model.VectorStatus;
import nvc.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import nvc.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import nvc.guide.modules.nvcpractice.dto.RagResult;
import nvc.guide.modules.nvcpractice.service.NvcRagService;
import nvc.guide.modules.nvcwiki.dto.WikiCreateRequest;
import nvc.guide.modules.nvcwiki.dto.WikiResponse;
import nvc.guide.modules.nvcwiki.dto.WikiSearchResult;
import nvc.guide.modules.nvcwiki.dto.WikiUpdateRequest;
import nvc.guide.modules.nvcwiki.model.NvcWikiCategory;
import nvc.guide.modules.nvcwiki.model.NvcWikiSourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wiki 核心服务
 * 提供 CRUD + 语义搜索 + 标签管理
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcWikiService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseVectorService vectorService;
    private final NvcRagService ragService;

    /**
     * 创建 Wiki 条目
     */
    @Transactional
    public WikiResponse createWiki(Long userId, WikiCreateRequest request) {
        log.info("Creating wiki: userId={}, title={}, category={}", userId, request.title(), request.category());

        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setUserId(userId);
        entity.setType(KnowledgeBaseType.PERSONAL_WIKI);
        entity.setName(request.title());
        entity.setCategory(request.category().name());
        entity.setContentType("text/markdown");
        entity.setOriginalFilename(request.title() + ".md");
        entity.setFileSize(request.content() != null ? (long) request.content().length() : 0L);
        entity.setFileHash(generateContentHash(userId, request.title(), request.content()));
        entity.setVectorStatus(VectorStatus.PENDING);

        entity = knowledgeBaseRepository.save(entity);

        // 异步向量化（内容非空时）
        if (request.content() != null && !request.content().isBlank()) {
            try {
                vectorService.vectorizeAndStore(entity.getId(), request.content());
                entity.setVectorStatus(VectorStatus.COMPLETED);
                entity.setChunkCount(1); // 简化：单条目不细分 chunk
            } catch (Exception e) {
                log.error("Wiki vectorization failed: wikiId={}", entity.getId(), e);
                entity.setVectorStatus(VectorStatus.FAILED);
                entity.setVectorError(e.getMessage());
            }
            knowledgeBaseRepository.save(entity);
        }

        log.info("Wiki created: wikiId={}", entity.getId());
        return toResponse(entity, request.content(), request.tags(), request.sessionId());
    }

    /**
     * 更新 Wiki 条目
     */
    @Transactional
    public WikiResponse updateWiki(Long userId, Long wikiId, WikiUpdateRequest request) {
        KnowledgeBaseEntity entity = getWikiOrThrow(wikiId, userId);

        if (request.title() != null) {
            entity.setName(request.title());
        }
        if (request.category() != null) {
            entity.setCategory(request.category().name());
        }

        entity = knowledgeBaseRepository.save(entity);

        // 重新向量化（内容变更时）
        if (request.content() != null) {
            try {
                vectorService.vectorizeAndStore(entity.getId(), request.content());
                entity.setVectorStatus(VectorStatus.COMPLETED);
            } catch (Exception e) {
                log.error("Wiki re-vectorization failed: wikiId={}", entity.getId(), e);
                entity.setVectorStatus(VectorStatus.FAILED);
                entity.setVectorError(e.getMessage());
            }
            knowledgeBaseRepository.save(entity);
        }

        log.info("Wiki updated: wikiId={}", wikiId);
        return toResponse(entity, request.content(), request.tags(), null);
    }

    /**
     * 删除 Wiki 条目
     */
    @Transactional
    public void deleteWiki(Long userId, Long wikiId) {
        KnowledgeBaseEntity entity = getWikiOrThrow(wikiId, userId);
        vectorService.deleteByKnowledgeBaseId(wikiId);
        knowledgeBaseRepository.delete(entity);
        log.info("Wiki deleted: wikiId={}", wikiId);
    }

    /**
     * 获取单个 Wiki
     */
    @Transactional(readOnly = true)
    public WikiResponse getWiki(Long userId, Long wikiId) {
        KnowledgeBaseEntity entity = getWikiOrThrow(wikiId, userId);
        return toResponse(entity, null, null, null);
    }

    /**
     * 列出用户所有 Wiki（分页 + 分类筛选）
     */
    @Transactional(readOnly = true)
    public Page<WikiResponse> listWikis(Long userId, NvcWikiCategory category, Pageable pageable) {
        Page<KnowledgeBaseEntity> page;
        if (category != null) {
            page = knowledgeBaseRepository.findByTypeAndUserIdAndCategoryOrderByUploadedAtDesc(
                    KnowledgeBaseType.PERSONAL_WIKI, userId, category.name(), pageable);
        } else {
            page = knowledgeBaseRepository.findByTypeAndUserIdOrderByUploadedAtDesc(
                    KnowledgeBaseType.PERSONAL_WIKI, userId, pageable);
        }
        return page.map(entity -> toResponse(entity, null, null, null));
    }

    /**
     * 语义搜索
     */
    public List<WikiSearchResult> searchWikis(Long userId, String query, int topK) {
        List<RagResult> results = ragService.retrieve(
                query, List.of(KnowledgeBaseType.PERSONAL_WIKI), topK, userId);

        return results.stream().map(r -> {
            // 从 metadata 中提取 wiki 信息
            Object kbIdObj = r.metadata().get("kb_id");
            Long kbId = kbIdObj != null ? Long.parseLong(kbIdObj.toString()) : null;
            String title = kbId != null ? getWikiTitle(kbId) : "未知条目";

            return new WikiSearchResult(
                    kbId,
                    title,
                    NvcWikiCategory.OTHER, // 语义搜索结果不细分分类
                    r.text().length() > 200 ? r.text().substring(0, 200) + "..." : r.text(),
                    r.score(),
                    null
            );
        }).toList();
    }

    /**
     * 关键词搜索
     */
    @Transactional(readOnly = true)
    public List<WikiResponse> searchByKeyword(Long userId, String keyword) {
        List<KnowledgeBaseEntity> entities = knowledgeBaseRepository
                .searchByTypeAndUserIdAndKeyword(KnowledgeBaseType.PERSONAL_WIKI, userId, keyword);
        return entities.stream()
                .map(entity -> toResponse(entity, null, null, null))
                .toList();
    }

    // ==================== 私有方法 ====================

    private KnowledgeBaseEntity getWikiOrThrow(Long wikiId, Long userId) {
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findById(wikiId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WIKI_NOT_FOUND,
                        "Wiki 条目不存在: " + wikiId));

        // 校验所有权
        if (entity.getUserId() == null || !entity.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.WIKI_ACCESS_DENIED,
                    "无权访问该 Wiki 条目: " + wikiId);
        }

        return entity;
    }

    private String getWikiTitle(Long wikiId) {
        return knowledgeBaseRepository.findById(wikiId)
                .map(KnowledgeBaseEntity::getName)
                .orElse("未知条目");
    }

    private String generateContentHash(Long userId, String title, String content) {
        String raw = userId + ":" + title + ":" + (content != null ? content.hashCode() : 0);
        return String.valueOf(raw.hashCode());
    }

    private WikiResponse toResponse(KnowledgeBaseEntity entity, String content,
                                     List<String> tags, Long sessionId) {
        NvcWikiCategory category;
        try {
            category = NvcWikiCategory.valueOf(entity.getCategory());
        } catch (Exception e) {
            category = NvcWikiCategory.OTHER;
        }

        return new WikiResponse(
                entity.getId(),
                entity.getName(),
                category,
                NvcWikiSourceType.MANUAL, // 默认，实际应从 metadata 获取
                content,
                tags != null ? tags : List.of(),
                sessionId,
                entity.getUploadedAt(),
                entity.getLastAccessedAt()
        );
    }
}
