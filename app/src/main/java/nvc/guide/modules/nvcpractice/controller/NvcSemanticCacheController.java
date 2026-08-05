package nvc.guide.modules.nvcpractice.controller;

import lombok.RequiredArgsConstructor;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcpractice.service.NvcSemanticCacheService;
import nvc.guide.modules.nvcpractice.service.NvcSemanticCacheService.CacheStats;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/nvc/semantic-cache")
@RequiredArgsConstructor
public class NvcSemanticCacheController {

    private final NvcSemanticCacheService cacheService;

    /**
     * 获取缓存统计
     */
    @GetMapping("/stats")
    public Result<CacheStats> getStats() {
        return Result.success(cacheService.getStats());
    }

    /**
     * 清空所有缓存
     */
    @DeleteMapping
    public Result<Void> clearAll() {
        cacheService.clearAll();
        return Result.success();
    }
}
