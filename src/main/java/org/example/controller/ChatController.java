package org.example.controller;

import org.example.model.ChatMessage;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UtilisateurService utilisateurService; // ✅ injection du service utilisateur

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // --- Récupération de tous les messages ---
    @GetMapping
    public List<ChatMessage> list() {
        return chatService.getAll();
    }

    // --- Envoi d’un message ---
    @PostMapping
    public ResponseEntity<?> envoyer(@RequestBody Map<String, String> body, Authentication auth) {
        String contenu = body.get("contenu");

        // ✅ récupère le pseudo via le service utilisateur (grâce à l’e-mail de auth)
        String pseudo = "Invité";
        if (auth != null) {
            String email = auth.getName();
            Utilisateur u = utilisateurService.trouverParEmail(email);
            if (u != null && u.getPseudo() != null && !u.getPseudo().isBlank()) {
                pseudo = u.getPseudo();
            }
        }

        try {
            ChatMessage saved = chatService.save(pseudo, contenu);
            // Envoi d’une notification via WebSocket pour refresh instantané
            messagingTemplate.convertAndSend("/topic/chat-new", Map.of("new", true, "id", saved.getId()));
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // --- Supprimer 1 message (ADMIN uniquement) ---
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteMessage(@PathVariable Long id) {
        chatService.deleteById(id);
        messagingTemplate.convertAndSend("/topic/chat-new", Map.of("deleted", id));
        return ResponseEntity.noContent().build();
    }

    // --- Vider tout le chat (ADMIN uniquement) ---
    @DeleteMapping("/clear")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> clear() {
        chatService.clear();
        messagingTemplate.convertAndSend("/topic/chat-new", Map.of("cleared", true));
        return ResponseEntity.noContent().build();
    }

    // --- WebSocket : indicateur de saisie (typing) ---
    @MessageMapping("/chat/typing")
    public void typing(Map<String, String> payload, Authentication auth) {
        if (auth == null) return;

        String email = auth.getName();
        Utilisateur u = utilisateurService.trouverParEmail(email);

        if (u == null || u.getPseudo() == null || u.getPseudo().isBlank()) return;

        String pseudo = u.getPseudo();
        messagingTemplate.convertAndSend("/topic/chat-typing", Map.of("pseudo", pseudo));
    }
}
