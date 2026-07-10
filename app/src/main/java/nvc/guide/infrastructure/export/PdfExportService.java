package nvc.guide.infrastructure.export;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * PDF Export Service
 * 通用 PDF 导出服务，后续会扩展 NVC 练习报告导出功能
 */
@Slf4j
@Service
public class PdfExportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // 主色调 - 靛蓝色
    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(79, 70, 229);
    private static final DeviceRgb TEXT_COLOR = new DeviceRgb(30, 41, 59);
    private static final DeviceRgb LIGHT_TEXT = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb TABLE_HEADER_BG = new DeviceRgb(238, 242, 255);
    private static final DeviceRgb TABLE_BORDER = new DeviceRgb(203, 213, 225);
    private static final DeviceRgb WHITE = new DeviceRgb(255, 255, 255);

    /**
     * 创建 PDF 文档的通用方法
     */
    protected PdfDocument createPdfDocument(ByteArrayOutputStream outputStream) {
        PdfWriter writer = new PdfWriter(outputStream);
        return new PdfDocument(writer);
    }

    /**
     * 创建中文字体
     */
    protected PdfFont createChineseFont() {
        try {
            // 使用内置字体，中文显示为方块但不报错
            // 生产环境应该配置中文字体路径
            return PdfFontFactory.createFont("Helvetica", "UniGB-UCS2-H", EmbeddingStrategy.PREFER_EMBEDDED);
        } catch (Exception e) {
            log.warn("创建中文字体失败，使用默认字体: {}", e.getMessage());
            try {
                return PdfFontFactory.createFont("Helvetica");
            } catch (Exception ex) {
                throw new RuntimeException("无法创建PDF字体", ex);
            }
        }
    }

    /**
     * 创建标题段落
     */
    protected Paragraph createTitle(String text, PdfFont font) {
        return new Paragraph(text)
                .setFont(font)
                .setFontSize(24)
                .setFontColor(PRIMARY_COLOR)
                .setBold()
                .setMarginBottom(20);
    }

    /**
     * 创建子标题
     */
    protected Paragraph createSubtitle(String text, PdfFont font) {
        return new Paragraph(text)
                .setFont(font)
                .setFontSize(16)
                .setFontColor(TEXT_COLOR)
                .setBold()
                .setMarginTop(15)
                .setMarginBottom(10);
    }

    /**
     * 创建正文段落
     */
    protected Paragraph createBody(String text, PdfFont font) {
        return new Paragraph(text)
                .setFont(font)
                .setFontSize(11)
                .setFontColor(TEXT_COLOR)
                .setMarginBottom(8);
    }

    /**
     * 创建表格头单元格
     */
    protected Cell createHeaderCell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(11).setBold())
                .setBackgroundColor(TABLE_HEADER_BG)
                .setFontColor(TEXT_COLOR)
                .setPadding(8)
                .setBorder(null);
    }

    /**
     * 创建表格数据单元格
     */
    protected Cell createDataCell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(10))
                .setFontColor(TEXT_COLOR)
                .setPadding(8)
                .setBorder(null);
    }

    /**
     * 导出 NVC 练习报告为 PDF
     */
    public byte[] exportNvcReport(nvc.guide.modules.nvcpractice.dto.NvcPracticeReport report) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfDocument pdf = createPdfDocument(outputStream);
            Document document = new Document(pdf);
            PdfFont font = createChineseFont();

            // 1. 标题
            document.add(createTitle("NVC 练习报告", font));

            // 2. 会话信息
            document.add(createSubtitle("会话信息", font));
            document.add(createBody(String.format(
                "练习模式：%s  |  难度：%s  |  总轮次：%d",
                report.practiceMode(), report.difficulty(), report.totalRounds()), font));
            if (report.startedAt() != null) {
                document.add(createBody(String.format(
                    "开始时间：%s  |  完成时间：%s",
                    report.startedAt().format(DATE_FORMAT),
                    report.completedAt() != null
                        ? report.completedAt().format(DATE_FORMAT) : "进行中"), font));
            }

            // 3. 五维度评分表格
            document.add(createSubtitle("五维度评分", font));
            Table scoreTable = new Table(UnitValue.createPercentArray(
                new float[]{25, 15, 60}))
                .useAllAvailableWidth();
            scoreTable.addHeaderCell(createHeaderCell("维度", font));
            scoreTable.addHeaderCell(createHeaderCell("分数", font));
            scoreTable.addHeaderCell(createHeaderCell("分析", font));

            addScoreRow(scoreTable, "观察", report.observationScore(),
                report.observationDetail(), font);
            addScoreRow(scoreTable, "感受", report.feelingScore(),
                report.feelingDetail(), font);
            addScoreRow(scoreTable, "需求", report.needScore(),
                report.needDetail(), font);
            addScoreRow(scoreTable, "请求", report.requestScore(),
                report.requestDetail(), font);
            addScoreRow(scoreTable, "共情", report.empathyScore(),
                report.empathyDetail(), font);
            addScoreRow(scoreTable, "综合", report.overallScore(),
                null, font);
            document.add(scoreTable);

            // 4. 优势
            if (report.strengths() != null) {
                document.add(createSubtitle("优势", font));
                document.add(createBody(report.strengths(), font));
            }

            // 5. 改进建议
            if (report.improvements() != null) {
                document.add(createSubtitle("改进建议", font));
                document.add(createBody(report.improvements(), font));
            }

            // 6. 参考表达
            if (report.referenceExpressions() != null) {
                document.add(createSubtitle("参考 NVC 表达", font));
                document.add(createBody(report.referenceExpressions(), font));
            }

            // 7. 综合评价
            if (report.summary() != null) {
                document.add(createSubtitle("综合评价", font));
                document.add(createBody(report.summary(), font));
            }

            document.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("NVC报告PDF导出失败: sessionId={}", report.sessionId(), e);
            throw new nvc.guide.common.exception.BusinessException(
                nvc.guide.common.exception.ErrorCode.EXPORT_PDF_FAILED,
                "NVC报告PDF导出失败: " + e.getMessage());
        }
    }

    private void addScoreRow(Table table, String dimension, Integer score,
                              String detail, PdfFont font) {
        table.addCell(createDataCell(dimension, font));
        table.addCell(createDataCell(
            score != null ? String.valueOf(score) : "-", font));
        table.addCell(createDataCell(
            detail != null ? detail : "-", font));
    }
}
