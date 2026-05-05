package ru.local.llmchat.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.local.llmchat.entity.ChatSession;
import ru.local.llmchat.repository.ChatRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatRepository chatRepository;

    @InjectMocks
    private ChatService chatService;

    @Test
    void createChatUsesDefaultTitleWhenTitleBlank() {
        when(chatRepository.save(org.mockito.ArgumentMatchers.any(ChatSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        chatService.createChat("  ");

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo(ChatService.DEFAULT_CHAT_TITLE);
    }
}
