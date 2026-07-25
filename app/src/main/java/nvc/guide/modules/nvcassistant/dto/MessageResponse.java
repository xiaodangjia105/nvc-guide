package nvc.guide.modules.nvcassistant.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息响应 DTO
 */
@Data
@Builder
public class MessageResponse {

    private Long id;
    private String role;
    private String content;
    private List<ToolCallRecord> toolCalls;
    private LocalDateTime createdAt;
}
