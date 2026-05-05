package ru.local.llmchat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.local.llmchat.entity.ChatSession;

import java.util.List;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<ChatSession, UUID> {
    List<ChatSession> findAllByOrderByUpdatedAtDesc();
}
