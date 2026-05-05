package ru.local.llmchat.dto;

public record StreamEventDto(
        String type,
        String content,
        String error
) {
}
