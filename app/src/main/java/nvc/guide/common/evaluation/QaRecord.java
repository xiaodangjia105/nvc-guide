package nvc.guide.common.evaluation;

/**
 * NVC 练习对话记录（文字练习和语音练习共用）
 */
public record QaRecord(
    int questionIndex,
    String question,
    String category,
    String userAnswer   // null 表示未回答
) {}
