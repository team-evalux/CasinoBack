// src/main/java/org/example/controller/ChatController.java
package org.example.controller;

import org.example.dto.ChatEvent;
import org.example.dto.ChatInputMessage;
import org.example.dto.ChatMessage;
import org.example.model.Utilisateur;
import org.example.service.ChatService;
import org.example.service.UtilisateurService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UtilisateurService utilisateurService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // ---------- REST : historique ----------

    @GetMapping
    public List<ChatMessage> list() {
        return chatService.getAll();
    }

    @PostMapping
    public ResponseEntity<?> envoyer(@RequestBody Map<String, String> body,
                                     Authentication auth) {
        String contenu = body.get("contenu");
        String pseudo = resolvePseudo(auth);

        try {
            ChatMessage saved = chatService.save(pseudo, contenu);
            broadcastMessage(saved); // 🔥 push WebSocket
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteMessage(@PathVariable Long id) {
        chatService.deleteById(id);
        broadcastDelete(id); // 🔥 push WebSocket
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/clear")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> clear() {
        chatService.clear();
        broadcastClear(); // 🔥 push WebSocket
        return ResponseEntity.noContent().build();
    }

    // ---------- STOMP : envoi via /app/chat.send ----------

    @MessageMapping("/chat.send")
    public void envoyerWs(ChatInputMessage payload, Principal principal) {
        String pseudo = resolvePseudoFromPrincipal(principal);
        try {
            ChatMessage saved = chatService.save(pseudo, payload.getContenu());
            broadcastMessage(saved);
        } catch (IllegalArgumentException e) {
            // si tu veux tu peux gérer les erreurs vers /user/queue/errors
        }
    }

    // ---------- Utils ----------

    private String resolvePseudo(Authentication auth) {
        String pseudo = "Invité";
        if (auth != null) {
            String email = auth.getName();
            Utilisateur u = utilisateurService.trouverParEmail(email);
            if (u != null && u.getPseudo() != null && !u.getPseudo().isBlank()) {
                pseudo = u.getPseudo();
            }
        }
        return pseudo;
    }

    private String resolvePseudoFromPrincipal(Principal principal) {
        if (principal instanceof Authentication auth) {
            return resolvePseudo(auth);
        }
        return "Invité";
    }

    private void broadcastMessage(ChatMessage saved) {
        ChatEvent event = new ChatEvent();
        event.setType(ChatEvent.Type.MESSAGE);
        event.setMessage(saved);
        messagingTemplate.convertAndSend("/topic/chat", event);
    }

    private void broadcastDelete(Long id) {
        ChatEvent event = new ChatEvent();
        event.setType(ChatEvent.Type.DELETE);
        event.setId(id);
        messagingTemplate.convertAndSend("/topic/chat", event);
    }

    private void broadcastClear() {
        ChatEvent event = new ChatEvent();
        event.setType(ChatEvent.Type.CLEAR);
        messagingTemplate.convertAndSend("/topic/chat", event);
    }
}
