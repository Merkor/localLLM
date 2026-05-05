package ru.local.llmchat.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import ru.local.llmchat.dto.MessageDto;
import ru.local.llmchat.dto.SendMessageRequest;
import ru.local.llmchat.dto.StreamEventDto;
import ru.local.llmchat.service.MessageService;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chats")
public class MessageRestController {

    private static final Logger log = LoggerFactory.getLogger(MessageRestController.class);

    private final MessageService messageService;

    public MessageRestController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/{id}/messages")
    public List<MessageDto> getMessages(@PathVariable UUID id) {
        return messageService.getMessages(id);
    }

    @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@PathVariable UUID id, @Valid @RequestBody SendMessageRequest request) {
        SseEmitter emitter = new SseEmitter(0L);

        Disposable disposable = messageService.streamAssistantReply(id, request.content())
                .subscribe(
                        event -> sendEvent(emitter, event),
                        error -> {
                            log.error("SSE stream failed for chat {}", id, error);
                            emitter.completeWithError(error);
                        },
                        emitter::complete
                );

        emitter.onCompletion(disposable::dispose);
        emitter.onTimeout(() -> {
            disposable.dispose();
            emitter.complete();
        });

        return emitter;
    }

    private void sendEvent(SseEmitter emitter, StreamEventDto event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.type())
                    .data(event));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
