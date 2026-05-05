package ru.local.llmchat.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
        @NotBlank
        String content
) {
}
