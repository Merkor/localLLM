package ru.local.llmchat.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.local.llmchat.entity.ChatMessage;
import ru.local.llmchat.entity.MessageRole;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByChatIdOrderByCreatedAtAsc(UUID chatId);

    List<ChatMessage> findByChatIdOrderByCreatedAtDesc(UUID chatId, Pageable pageable);

    long countByChatIdAndRole(UUID chatId, MessageRole role);
}
