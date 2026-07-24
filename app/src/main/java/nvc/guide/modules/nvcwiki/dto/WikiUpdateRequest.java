package nvc.guide.modules.nvcwiki.dto;

import nvc.guide.modules.nvcwiki.model.NvcWikiCategory;

import java.util.List;

/**
 * Wiki 更新请求
 */
public record WikiUpdateRequest(
        String title,
        NvcWikiCategory category,
        String content,
        List<String> tags
) {}
