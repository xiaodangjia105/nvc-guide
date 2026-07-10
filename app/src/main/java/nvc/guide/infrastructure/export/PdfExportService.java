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
     * TODO: Task 6 中实现完整 PDF 渲染逻辑
     */
    public byte[] exportNvcReport(nvc.guide.modules.nvcpractice.dto.NvcPracticeReport report) {
        log.info("Exporting NVC report for session: {}", report.sessionId());
        // TODO: 实现完整的 PDF 渲染
        return new byte[0];
    }
}
