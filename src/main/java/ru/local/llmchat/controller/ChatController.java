package ru.local.llmchat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.local.llmchat.dto.ChatDto;
import ru.local.llmchat.service.ChatService;
import ru.local.llmchat.service.MessageService;

import java.util.List;
import java.util.UUID;

@Controller
public class ChatController {

    private final ChatService chatService;
    private final MessageService messageService;

    public ChatController(ChatService chatService, MessageService messageService) {
        this.chatService = chatService;
        this.messageService = messageService;
    }

    @GetMapping("/")
    public String index(Model model) {
        List<ChatDto> chats = chatService.getChats();
        UUID activeChatId = chats.isEmpty() ? null : chats.get(0).id();
        fillModel(model, chats, activeChatId);
        return "chat";
    }

    @GetMapping("/chats/{id}")
    public String openChat(@PathVariable UUID id, Model model) {
        List<ChatDto> chats = chatService.getChats();
        fillModel(model, chats, id);
        return "chat";
    }

    private void fillModel(Model model, List<ChatDto> chats, UUID activeChatId) {
        model.addAttribute("chats", chats);
        model.addAttribute("activeChatId", activeChatId);
        model.addAttribute("activeMessages",
                activeChatId != null ? messageService.getMessages(activeChatId) : List.of());
    }
}
