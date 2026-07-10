package nvc.guide.modules.nvcpractice.controller;

import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcpractice.dto.NvcPracticeReport;
import nvc.guide.modules.nvcpractice.service.NvcReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nvc/report")
@RequiredArgsConstructor
public class NvcReportController {

    private final NvcReportService reportService;

    /**
     * 获取练习报告
     */
    @GetMapping("/sessions/{sessionId}")
    public Result<NvcPracticeReport> getReport(@PathVariable Long sessionId) {
        NvcPracticeReport report = reportService.generateReport(sessionId);
        return Result.success(report);
    }

    /**
     * 导出练习报告为 PDF
     */
    @GetMapping("/sessions/{sessionId}/pdf")
    public ResponseEntity<byte[]> exportReportPdf(@PathVariable Long sessionId) {
        byte[] pdf = reportService.exportReportPdf(sessionId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=nvc-report-" + sessionId + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }
}
