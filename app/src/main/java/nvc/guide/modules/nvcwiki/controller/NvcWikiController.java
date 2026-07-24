package nvc.guide.modules.nvcwiki.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcwiki.dto.WikiCreateRequest;
import nvc.guide.modules.nvcwiki.dto.WikiResponse;
import nvc.guide.modules.nvcwiki.dto.WikiSearchResult;
import nvc.guide.modules.nvcwiki.dto.WikiUpdateRequest;
import nvc.guide.modules.nvcwiki.model.NvcWikiCategory;
import nvc.guide.modules.nvcwiki.service.NvcWikiService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Wiki REST 控制器
 */
@RestController
@RequestMapping("/api/nvc/wiki")
@Slf4j
@RequiredArgsConstructor
public class NvcWikiController {

    private final NvcWikiService wikiService;

    /**
     * 创建 Wiki 条目
     */
    @PostMapping
    public Result<WikiResponse> createWiki(
            @RequestParam Long userId,
            @Validated @RequestBody WikiCreateRequest request) {
        return Result.success(wikiService.createWiki(userId, request));
    }

    /**
     * 获取单个 Wiki
     */
    @GetMapping("/{wikiId}")
    public Result<WikiResponse> getWiki(
            @RequestParam Long userId,
            @PathVariable Long wikiId) {
        return Result.success(wikiService.getWiki(userId, wikiId));
    }

    /**
     * 更新 Wiki 条目
     */
    @PutMapping("/{wikiId}")
    public Result<WikiResponse> updateWiki(
            @RequestParam Long userId,
            @PathVariable Long wikiId,
            @Validated @RequestBody WikiUpdateRequest request) {
        return Result.success(wikiService.updateWiki(userId, wikiId, request));
    }

    /**
     * 删除 Wiki 条目
     */
    @DeleteMapping("/{wikiId}")
    public Result<Void> deleteWiki(
            @RequestParam Long userId,
            @PathVariable Long wikiId) {
        wikiService.deleteWiki(userId, wikiId);
        return Result.success(null);
    }

    /**
     * 列出用户所有 Wiki（分页 + 分类筛选）
     */
    @GetMapping
    public Result<Page<WikiResponse>> listWikis(
            @RequestParam Long userId,
            @RequestParam(required = false) NvcWikiCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(wikiService.listWikis(userId, category, PageRequest.of(page, size)));
    }

    /**
     * 语义搜索
     */
    @GetMapping("/search")
    public Result<List<WikiSearchResult>> searchWikis(
            @RequestParam Long userId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        return Result.success(wikiService.searchWikis(userId, query, topK));
    }

    /**
     * 关键词搜索
     */
    @GetMapping("/search/keyword")
    public Result<List<WikiResponse>> searchByKeyword(
            @RequestParam Long userId,
            @RequestParam String keyword) {
        return Result.success(wikiService.searchByKeyword(userId, keyword));
    }
}
