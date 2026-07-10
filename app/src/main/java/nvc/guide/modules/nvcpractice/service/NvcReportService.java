package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.infrastructure.export.PdfExportService;
import nvc.guide.modules.nvcpractice.dto.NvcPracticeReport;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcMessageRole;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcReportService {

    private final NvcPracticeSessionRepository sessionRepository;
    private final NvcPracticeMessageRepository messageRepository;
    private final NvcEvaluationService evaluationService;
    private final PdfExportService pdfExportService;

    /**
     * 生成练习报告
     */
    public NvcPracticeReport generateReport(Long sessionId) {
        NvcPracticeSessionEntity session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NVC_SESSION_NOT_FOUND, "Session not found: " + sessionId));

        List<NvcPracticeMessageEntity> messages =
            messageRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);

        NvcEvaluationEntity finalEval = evaluationService.getFinalEvaluation(sessionId);

        // 如果还没有最终评估，先执行一次
        if (finalEval == null) {
            finalEval = evaluationService.evaluateFinal(
                sessionId, session.getUserId(), messages);
        }

        // 统计用户消息轮次
        long totalRounds = messages.stream()
            .filter(m -> m.getRole() == NvcMessageRole.USER)
            .count();

        return new NvcPracticeReport(
            sessionId,
            session.getPracticeMode(),
            session.getDifficulty(),
            totalRounds,
            session.getStartedAt(),
            session.getCompletedAt(),
            finalEval.getObservationScore(),
            finalEval.getFeelingScore(),
            finalEval.getNeedScore(),
            finalEval.getRequestScore(),
            finalEval.getEmpathyScore(),
            finalEval.getOverallScore(),
            finalEval.getObservationDetail(),
            finalEval.getFeelingDetail(),
            finalEval.getNeedDetail(),
            finalEval.getRequestDetail(),
            null, // empathyDetail — Entity 没有此字段，暂为 null
            finalEval.getStrengths(),
            finalEval.getImprovements(),
            finalEval.getReferenceExpressions(),
            finalEval.getSummary()
        );
    }

    /**
     * 导出练习报告为 PDF
     */
    public byte[] exportReportPdf(Long sessionId) {
        NvcPracticeReport report = generateReport(sessionId);
        return pdfExportService.exportNvcReport(report);
    }
}
