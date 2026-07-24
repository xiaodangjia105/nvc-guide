package nvc.guide.modules.nvcwiki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import nvc.guide.modules.nvcwiki.model.NvcWikiCategory;
import nvc.guide.modules.nvcwiki.model.NvcWikiSourceType;

import java.util.List;

/**
 * Wiki 创建请求
 */
public record WikiCreateRequest(
        @NotBlank String title,
        @NotNull NvcWikiCategory category,
        @NotNull NvcWikiSourceType sourceType,
        String content,
        List<String> tags,
        Long sessionId
) {}
