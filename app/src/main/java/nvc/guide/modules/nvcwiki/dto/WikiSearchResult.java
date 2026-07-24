package nvc.guide.modules.nvcwiki.dto;

import nvc.guide.modules.nvcwiki.model.NvcWikiCategory;

import java.time.LocalDateTime;

/**
 * Wiki 语义搜索结果
 */
public record WikiSearchResult(
        Long id,
        String title,
        NvcWikiCategory category,
        String contentSnippet,
        double score,
        LocalDateTime createdAt
) {}
