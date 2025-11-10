package org.example.controller;

import org.example.model.ChatMessage;
import org.example.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @GetMapping
    public List<ChatMessage> list() {
        return chatService.getAll();
    }

    @PostMapping
    public ResponseEntity<?> envoyer(@RequestBody Map<String, String> body, Authentication auth) {
        String contenu = body.get("contenu");
        String pseudo = (auth != null) ? auth.getName() : "Invité";
        try {
            ChatMessage saved = chatService.save(pseudo, contenu);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/clear")
    public ResponseEntity<?> clear() {
        chatService.clear();
        return ResponseEntity.noContent().build();
    }
}
