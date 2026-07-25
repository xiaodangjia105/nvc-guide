package nvc.guide.modules.nvcassistant.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话列表响应
 */
@Data
@Builder
public class ConversationResponse {

    private Long id;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
