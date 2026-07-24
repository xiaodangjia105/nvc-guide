package nvc.guide.modules.nvcwiki.dto;

import nvc.guide.modules.nvcwiki.model.NvcWikiCategory;
import nvc.guide.modules.nvcwiki.model.NvcWikiSourceType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Wiki 响应
 */
public record WikiResponse(
        Long id,
        String title,
        NvcWikiCategory category,
        NvcWikiSourceType sourceType,
        String content,
        List<String> tags,
        Long sessionId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
