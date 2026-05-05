package ru.local.llmchat.llm;

import reactor.core.publisher.Flux;

import java.util.List;

public interface LlmService {
    Flux<LlmStreamEvent> streamChat(List<LlmMessage> messages);

    String suggestChatTitle(String userMessage, String assistantMessage);
}
