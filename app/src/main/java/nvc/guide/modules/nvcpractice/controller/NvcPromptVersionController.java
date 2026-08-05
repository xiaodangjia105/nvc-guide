package nvc.guide.modules.nvcpractice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcpractice.dto.CreatePromptVersionRequest;
import nvc.guide.modules.nvcpractice.dto.PromptVersionResponse;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.service.NvcPromptVersionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nvc/prompt-versions")
@RequiredArgsConstructor
public class NvcPromptVersionController {

    private final NvcPromptVersionService versionService;

    /**
     * 获取某个场景的所有 Prompt 版本
     */
    @GetMapping("/{scene}")
    public Result<List<PromptVersionResponse>> getVersions(
            @PathVariable NvcAgentScene scene) {
        return Result.success(versionService.getVersions(scene));
    }

    /**
     * 创建新版本（不自动激活）
     */
    @PostMapping("/{scene}")
    public Result<PromptVersionResponse> createVersion(
            @PathVariable NvcAgentScene scene,
            @Valid @RequestBody CreatePromptVersionRequest request) {
        return Result.success(versionService.createVersion(scene, request));
    }

    /**
     * 激活版本（全量切换）
     */
    @PostMapping("/{scene}/versions/{version}/activate")
    public Result<PromptVersionResponse> activateVersion(
            @PathVariable NvcAgentScene scene,
            @PathVariable Integer version) {
        return Result.success(versionService.activateVersion(scene, version));
    }

    /**
     * A/B 流量分配
     */
    @PostMapping("/{scene}/ab-test")
    public Result<Void> setTrafficSplit(
            @PathVariable NvcAgentScene scene,
            @RequestParam Integer version1,
            @RequestParam Integer pct1,
            @RequestParam Integer version2,
            @RequestParam Integer pct2) {
        versionService.setTrafficSplit(scene, version1, pct1, version2, pct2);
        return Result.success();
    }
}
