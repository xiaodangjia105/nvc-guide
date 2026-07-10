package nvc.guide.modules.nvcpractice.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
    @NotBlank String content
) {}
