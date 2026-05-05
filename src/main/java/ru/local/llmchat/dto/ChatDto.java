package ru.local.llmchat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatDto(
        UUID id,
        String title,
        Instant createdAt,
        Instant updatedAt
) {
}
