package ru.local.llmchat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import ru.local.llmchat.config.AppLlmProperties;
import ru.local.llmchat.entity.ChatMessage;
import ru.local.llmchat.entity.ChatSession;
import ru.local.llmchat.entity.MessageRole;
import ru.local.llmchat.llm.LlmMessage;
import ru.local.llmchat.llm.LlmService;
import ru.local.llmchat.llm.LlmStreamEvent;
import ru.local.llmchat.repository.ChatMessageRepository;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private ChatService chatService;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private LlmService llmService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        AppLlmProperties properties = new AppLlmProperties();
        properties.setModel("qwen2.5:7b-instruct-q4_K_M");
        properties.setMaxContextMessages(20);
        properties.setSystemPrompt("system");

        messageService = new MessageService(
                chatService,
                chatMessageRepository,
                llmService,
                properties,
                transactionTemplate
        );

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void streamAssistantReplyPersistsAssistantMessageOnComplete() {
        UUID chatId = UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setId(chatId);
        session.setTitle(ChatService.DEFAULT_CHAT_TITLE);

        ChatMessage contextMessage = new ChatMessage();
        contextMessage.setChat(session);
        contextMessage.setRole(MessageRole.USER);
        contextMessage.setContent("Hi");

        when(chatService.getChatEntity(chatId)).thenReturn(session);
        when(chatMessageRepository.countByChatIdAndRole(chatId, MessageRole.ASSISTANT)).thenReturn(0L);
        when(chatMessageRepository.findByChatIdOrderByCreatedAtDesc(eq(chatId), any()))
                .thenReturn(List.of(contextMessage));
        when(llmService.streamChat(any())).thenReturn(Flux.just(
                LlmStreamEvent.token("Hello"),
                LlmStreamEvent.complete(42L, 7)
        ));
        when(llmService.suggestChatTitle("Hi", "Hello")).thenReturn("Greeting");
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        messageService.streamAssistantReply(chatId, "Hi").collectList().block();

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        List<ChatMessage> savedMessages = captor.getAllValues();

        assertThat(savedMessages.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(savedMessages.get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(savedMessages.get(1).getContent()).isEqualTo("Hello");
        assertThat(savedMessages.get(1).getDurationMs()).isEqualTo(42L);
        assertThat(savedMessages.get(1).getTotalTokens()).isEqualTo(7);
        assertThat(session.getTitle()).isEqualTo("Greeting");

        verify(llmService).streamChat(any(List.class));
        verify(chatService, times(2)).touch(session);
    }
}
