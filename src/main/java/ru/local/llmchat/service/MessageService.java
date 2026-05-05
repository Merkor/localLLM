package ru.local.llmchat.service;

import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import ru.local.llmchat.config.AppLlmProperties;
import ru.local.llmchat.dto.MessageDto;
import ru.local.llmchat.dto.StreamEventDto;
import ru.local.llmchat.entity.ChatMessage;
import ru.local.llmchat.entity.ChatSession;
import ru.local.llmchat.entity.MessageRole;
import ru.local.llmchat.exception.BadRequestException;
import ru.local.llmchat.llm.LlmMessage;
import ru.local.llmchat.llm.LlmService;
import ru.local.llmchat.llm.LlmStreamEvent;
import ru.local.llmchat.repository.ChatMessageRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MessageService {
    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final ChatService chatService;
    private final ChatMessageRepository chatMessageRepository;
    private final LlmService llmService;
    private final AppLlmProperties properties;
    private final TransactionTemplate transactionTemplate;

    public MessageService(ChatService chatService,
                          ChatMessageRepository chatMessageRepository,
                          LlmService llmService,
                          AppLlmProperties properties,
                          TransactionTemplate transactionTemplate) {
        this.chatService = chatService;
        this.chatMessageRepository = chatMessageRepository;
        this.llmService = llmService;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional(readOnly = true)
    public List<MessageDto> getMessages(UUID chatId) {
        chatService.getChatEntity(chatId);
        return chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chatId).stream()
                .map(this::toDto)
                .toList();
    }

    public Flux<StreamEventDto> streamAssistantReply(UUID chatId, String rawUserMessage) {
        String userMessage = sanitizeContent(rawUserMessage);

        ChatSession chat = transactionTemplate.execute(status -> saveUserMessage(chatId, userMessage));
        if (chat == null) {
            throw new IllegalStateException("Failed to save user message.");
        }

        List<LlmMessage> context = transactionTemplate.execute(status -> buildContext(chatId));
        if (context == null) {
            throw new IllegalStateException("Failed to build chat context.");
        }

        StringBuilder assistantContent = new StringBuilder();
        AtomicReference<Long> durationMsRef = new AtomicReference<>(null);
        AtomicReference<Integer> totalTokensRef = new AtomicReference<>(null);
        AtomicBoolean completedRef = new AtomicBoolean(false);

        return llmService.streamChat(context)
                .handle((event, sink) -> {
                    if (event.type() == LlmStreamEvent.Type.TOKEN) {
                        assistantContent.append(event.content());
                        sink.next(new StreamEventDto("token", event.content(), null));
                        return;
                    }

                    if (event.type() == LlmStreamEvent.Type.COMPLETE) {
                        completedRef.set(true);
                        durationMsRef.set(event.durationMs());
                        totalTokensRef.set(event.totalTokens());
                        sink.next(new StreamEventDto("done", "", null));
                        return;
                    }

                    sink.next(new StreamEventDto("error", "", event.error()));
                })
                .cast(StreamEventDto.class)
                .doOnComplete(() -> {
                    if (!completedRef.get()) {
                        return;
                    }

                    String assistantText = assistantContent.toString().strip();
                    if (assistantText.isBlank()) {
                        return;
                    }

                    log.info(
                            "Assistant response completed for chat {} (durationMs={}, totalTokens={})",
                            chatId,
                            durationMsRef.get(),
                            totalTokensRef.get()
                    );
                    log.info(
                            "Assistant raw response START chat={} chars={}",
                            chatId,
                            assistantText.length()
                    );
                    log.info("Assistant raw response (multiline) chat={}:\n{}", chatId, assistantText);
                    log.info("Assistant raw response (escaped) chat={}: {}", chatId, escapeForSingleLineLog(assistantText));
                    log.info("Assistant raw response END chat={}", chatId);

                    transactionTemplate.executeWithoutResult(status ->
                            saveAssistantMessage(chat, userMessage, assistantText, durationMsRef.get(), totalTokensRef.get()));
                });
    }

    protected ChatSession saveUserMessage(UUID chatId, String userMessage) {
        ChatSession chat = chatService.getChatEntity(chatId);

        ChatMessage message = new ChatMessage();
        message.setChat(chat);
        message.setRole(MessageRole.USER);
        message.setContent(userMessage);
        message.setCreatedAt(Instant.now());
        chatMessageRepository.save(message);

        chatService.touch(chat);
        return chat;
    }

    protected void saveAssistantMessage(ChatSession chat,
                                        String userMessage,
                                        String assistantText,
                                        Long durationMs,
                                        Integer totalTokens) {
        long assistantMessagesBefore = chatMessageRepository.countByChatIdAndRole(chat.getId(), MessageRole.ASSISTANT);

        ChatMessage message = new ChatMessage();
        message.setChat(chat);
        message.setRole(MessageRole.ASSISTANT);
        message.setContent(assistantText);
        message.setModel(properties.getModel());
        message.setDurationMs(durationMs);
        message.setTotalTokens(totalTokens);
        message.setCreatedAt(Instant.now());
        chatMessageRepository.save(message);

        if (assistantMessagesBefore == 0
                && (chat.getTitle() == null
                || chat.getTitle().isBlank()
                || ChatService.DEFAULT_CHAT_TITLE.equals(chat.getTitle()))) {
            String suggestedTitle = llmService.suggestChatTitle(userMessage, assistantText);
            chat.setTitle(normalizeSuggestedTitle(suggestedTitle, assistantText));
        }

        chatService.touch(chat);
    }

    protected List<LlmMessage> buildContext(UUID chatId) {
        List<ChatMessage> recent = new ArrayList<>(chatMessageRepository.findByChatIdOrderByCreatedAtDesc(
                chatId,
                PageRequest.of(0, properties.getMaxContextMessages())
        ));
        recent.sort(Comparator.comparing(ChatMessage::getCreatedAt));

        List<LlmMessage> llmMessages = new ArrayList<>();
        llmMessages.add(new LlmMessage("system", properties.getSystemPrompt()));

        for (ChatMessage message : recent) {
            llmMessages.add(new LlmMessage(
                    message.getRole().name().toLowerCase(Locale.ROOT),
                    message.getContent()
            ));
        }

        return llmMessages;
    }

    private MessageDto toDto(ChatMessage entity) {
        return new MessageDto(
                entity.getId(),
                entity.getChat().getId(),
                entity.getRole(),
                entity.getContent(),
                entity.getModel(),
                entity.getDurationMs(),
                entity.getTotalTokens(),
                entity.getCreatedAt()
        );
    }

    private String sanitizeContent(String content) {
        if (content == null) {
            throw new BadRequestException("Message must not be blank.");
        }
        String value = content.strip();
        if (value.isBlank()) {
            throw new BadRequestException("Message must not be blank.");
        }
        return value;
    }

    private String deriveChatTitle(String message) {
        String normalized = message.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= 40) {
            return normalized;
        }
        return normalized.substring(0, 40) + "...";
    }

    private String normalizeSuggestedTitle(String suggestedTitle, String fallbackText) {
        if (suggestedTitle == null || suggestedTitle.isBlank()) {
            return deriveChatTitle(fallbackText);
        }

        String normalized = suggestedTitle.strip().replaceAll("\\s+", " ");
        if (normalized.length() > 120) {
            normalized = normalized.substring(0, 120).strip();
        }
        return normalized.isBlank() ? deriveChatTitle(fallbackText) : normalized;
    }

    private String escapeForSingleLineLog(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
