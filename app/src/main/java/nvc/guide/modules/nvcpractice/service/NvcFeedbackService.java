package nvc.guide.modules.nvcpractice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcpractice.dto.FeedbackStatsResponse;
import nvc.guide.modules.nvcpractice.dto.FeedbackStatsResponse.SceneFeedbackStats;
import nvc.guide.modules.nvcpractice.dto.SubmitFeedbackRequest;
import nvc.guide.modules.nvcpractice.model.NvcFeedbackEntity;
import nvc.guide.modules.nvcpractice.model.NvcFeedbackSource;
import nvc.guide.modules.nvcpractice.repository.NvcFeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcFeedbackService {

    private final NvcFeedbackRepository feedbackRepository;

    /**
     * 提交反馈（同一消息重复提交为更新）
     */
    @Transactional
    public NvcFeedbackEntity submitFeedback(Long userId, SubmitFeedbackRequest request) {
        // 查找已有反馈
        NvcFeedbackEntity existing = feedbackRepository
            .findBySessionIdAndMessageIdAndMessageSource(
                request.sessionId(), request.messageId(), request.messageSource())
            .orElse(null);

        if (existing != null) {
            // 更新已有反馈
            existing.setRating(request.rating());
            existing.setComment(request.comment());
            if (request.agentScene() != null) {
                existing.setAgentScene(request.agentScene());
            }
            log.info("Feedback updated: userId={}, messageId={}, rating={}",
                userId, request.messageId(), request.rating());
            return feedbackRepository.save(existing);
        }

        // 新建反馈
        NvcFeedbackEntity feedback = NvcFeedbackEntity.builder()
            .userId(userId)
            .sessionId(request.sessionId())
            .messageId(request.messageId())
            .messageSource(request.messageSource())
            .agentScene(request.agentScene())
            .rating(request.rating())
            .comment(request.comment())
            .build();

        NvcFeedbackEntity saved = feedbackRepository.save(feedback);
        log.info("Feedback submitted: userId={}, messageId={}, rating={}",
            userId, request.messageId(), request.rating());
        return saved;
    }

    /**
     * 获取反馈统计
     */
    @Transactional(readOnly = true)
    public FeedbackStatsResponse getFeedbackStats(LocalDateTime from, LocalDateTime to) {
        // 总体统计
        Object[] total = feedbackRepository.countTotalAndThumbsUp(from, to);
        long totalCount = total[0] != null ? ((Number) total[0]).longValue() : 0;
        long thumbsUpTotal = total[1] != null ? ((Number) total[1]).longValue() : 0;
        double overallRate = totalCount > 0 ? (double) thumbsUpTotal / totalCount : 0.0;

        // 按场景分组统计
        List<Object[]> sceneRows = feedbackRepository.countByAgentScene(from, to);
        List<SceneFeedbackStats> perScene = new ArrayList<>();
        for (Object[] row : sceneRows) {
            String scene = row[0] != null ? row[0].toString() : "UNKNOWN";
            long count = row[1] != null ? ((Number) row[1]).longValue() : 0;
            long thumbsUp = row[2] != null ? ((Number) row[2]).longValue() : 0;
            double rate = count > 0 ? (double) thumbsUp / count : 0.0;
            perScene.add(new SceneFeedbackStats(scene, count, thumbsUp, rate));
        }

        return new FeedbackStatsResponse(totalCount, overallRate, perScene);
    }

    /**
     * 获取最近差评
     */
    @Transactional(readOnly = true)
    public List<NvcFeedbackEntity> getRecentNegativeFeedback(int limit) {
        return feedbackRepository.findByRatingOrderByCreatedAtDesc(1,
            org.springframework.data.domain.PageRequest.of(0, limit));
    }
}
