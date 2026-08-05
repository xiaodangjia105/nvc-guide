package nvc.guide.modules.nvcpractice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nvc.guide.common.annotation.RateLimit;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcpractice.dto.FeedbackStatsResponse;
import nvc.guide.modules.nvcpractice.dto.SubmitFeedbackRequest;
import nvc.guide.modules.nvcpractice.model.NvcFeedbackEntity;
import nvc.guide.modules.nvcpractice.service.NvcFeedbackService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/nvc/feedback")
@RequiredArgsConstructor
public class NvcFeedbackController {

    private final NvcFeedbackService feedbackService;

    /**
     * 提交反馈（👍/👎 + 可选评论）
     */
    @RateLimit(count = 30)
    @PostMapping
    public Result<NvcFeedbackEntity> submitFeedback(
            @RequestParam Long userId,
            @Valid @RequestBody SubmitFeedbackRequest request) {
        NvcFeedbackEntity feedback = feedbackService.submitFeedback(userId, request);
        return Result.success(feedback);
    }

    /**
     * 获取反馈统计
     */
    @GetMapping("/stats")
    public Result<FeedbackStatsResponse> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.success(feedbackService.getFeedbackStats(from, to));
    }

    /**
     * 获取最近差评
     */
    @GetMapping("/negative")
    public Result<List<NvcFeedbackEntity>> getNegativeFeedback(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(feedbackService.getRecentNegativeFeedback(limit));
    }
}
