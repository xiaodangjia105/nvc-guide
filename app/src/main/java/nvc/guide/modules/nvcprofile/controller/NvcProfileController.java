package nvc.guide.modules.nvcprofile.controller;

import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcprofile.dto.*;
import nvc.guide.modules.nvcprofile.model.NvcCommunicationRecordEntity;
import nvc.guide.modules.nvcprofile.model.NvcUserProfileEntity;
import nvc.guide.modules.nvcprofile.service.NvcCommunicationAnalysisService;
import nvc.guide.modules.nvcprofile.service.NvcProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nvc/profile")
@RequiredArgsConstructor
public class NvcProfileController {

    private final NvcProfileService profileService;
    private final NvcCommunicationAnalysisService analysisService;

    /**
     * 获取当前用户档案
     */
    @GetMapping
    public Result<UserProfileDTO> getProfile(@RequestParam Long userId) {
        NvcUserProfileEntity profile = profileService.getOrCreateProfile(userId);
        return Result.success(profileService.toDTO(profile));
    }

    /**
     * 更新用户档案
     */
    @PutMapping
    public Result<UserProfileDTO> updateProfile(
            @RequestParam Long userId,
            @Valid @RequestBody UserProfileUpdateRequest request) {
        NvcUserProfileEntity profile = profileService.updateProfile(userId, request);
        return Result.success(profileService.toDTO(profile));
    }

    /**
     * 获取能力雷达图数据
     */
    @GetMapping("/ability-radar")
    public Result<AbilityRadarDTO> getAbilityRadar(@RequestParam Long userId) {
        return Result.success(profileService.getAbilityRadar(userId));
    }

    /**
     * 获取能力趋势数据
     */
    @GetMapping("/ability-trends")
    public Result<List<AbilityTrendDTO>> getAbilityTrends(@RequestParam Long userId) {
        return Result.success(profileService.getAbilityTrends(userId));
    }

    /**
     * 分析沟通记录
     */
    @PostMapping("/communication-records/analyze")
    public Result<CommunicationRecordDTO> analyzeCommunication(
            @RequestParam Long userId,
            @Valid @RequestBody CommunicationAnalysisRequest request) {
        NvcCommunicationRecordEntity record = analysisService.analyzeAndSave(userId, request);
        return Result.success(toRecordDTO(record));
    }

    /**
     * 获取沟通记录列表
     */
    @GetMapping("/communication-records")
    public Result<List<CommunicationRecordDTO>> getCommunicationRecords(@RequestParam Long userId) {
        var records = analysisService.getUserRecords(userId).stream()
            .map(this::toRecordDTO)
            .toList();
        return Result.success(records);
    }

    private CommunicationRecordDTO toRecordDTO(NvcCommunicationRecordEntity record) {
        return new CommunicationRecordDTO(
            record.getId(),
            record.getTitle(),
            record.getScenarioType(),
            record.getRawContent(),
            record.getAnalysisResult(),
            record.getNvcSuggestion(),
            record.getCreatedAt()
        );
    }
}
