package ru.local.llmchat.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.local.llmchat.dto.ChatDto;
import ru.local.llmchat.entity.ChatSession;
import ru.local.llmchat.exception.NotFoundException;
import ru.local.llmchat.repository.ChatRepository;

import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    public static final String DEFAULT_CHAT_TITLE = "New chat";

    private final ChatRepository chatRepository;

    public ChatService(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    @Transactional(readOnly = true)
    public List<ChatDto> getChats() {
        return chatRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatDto getChat(UUID id) {
        return toDto(getChatEntity(id));
    }

    @Transactional
    public ChatDto createChat(String title) {
        ChatSession chatSession = new ChatSession();
        chatSession.setTitle(normalizeTitle(title));
        return toDto(chatRepository.save(chatSession));
    }

    @Transactional
    public ChatDto renameChat(UUID id, String newTitle) {
        ChatSession chatSession = getChatEntity(id);
        chatSession.setTitle(normalizeTitle(newTitle));
        return toDto(chatRepository.save(chatSession));
    }

    @Transactional
    public void deleteChat(UUID id) {
        ChatSession chatSession = getChatEntity(id);
        chatRepository.delete(chatSession);
    }

    @Transactional(readOnly = true)
    public ChatSession getChatEntity(UUID id) {
        return chatRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Chat not found: " + id));
    }

    @Transactional
    public void touch(ChatSession chat) {
        chat.setUpdatedAt(java.time.Instant.now());
        chatRepository.save(chat);
    }

    public ChatDto toDto(ChatSession entity) {
        return new ChatDto(
                entity.getId(),
                entity.getTitle(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_CHAT_TITLE;
        }
        String normalized = title.strip();
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }
}
