package ru.local.llmchat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameChatRequest(
        @NotBlank
        @Size(max = 120)
        String title
) {
}
