package ru.local.llmchat.dto;

import ru.local.llmchat.entity.MessageRole;

import java.time.Instant;
import java.util.UUID;

public record MessageDto(
        UUID id,
        UUID chatId,
        MessageRole role,
        String content,
        String model,
        Long durationMs,
        Integer totalTokens,
        Instant createdAt
) {
}
