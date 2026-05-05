package ru.local.llmchat.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.local.llmchat.dto.ChatDto;
import ru.local.llmchat.dto.CreateChatRequest;
import ru.local.llmchat.dto.RenameChatRequest;
import ru.local.llmchat.service.ChatService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chats")
public class ChatRestController {

    private final ChatService chatService;

    public ChatRestController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public List<ChatDto> getChats() {
        return chatService.getChats();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatDto createChat(@RequestBody(required = false) CreateChatRequest request) {
        return chatService.createChat(request != null ? request.title() : null);
    }

    @GetMapping("/{id}")
    public ChatDto getChat(@PathVariable UUID id) {
        return chatService.getChat(id);
    }

    @PatchMapping("/{id}")
    public ChatDto renameChat(@PathVariable UUID id, @Valid @RequestBody RenameChatRequest request) {
        return chatService.renameChat(id, request.title());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChat(@PathVariable UUID id) {
        chatService.deleteChat(id);
    }
}
