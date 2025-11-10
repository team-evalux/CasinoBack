package org.example.service;

import org.example.model.ChatMessage;
import org.example.repo.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatService {
    @Autowired
    private ChatMessageRepository repo;

    public List<ChatMessage> getAll() {
        return repo.findAll().stream()
                .sorted((a,b) -> a.getDate().compareTo(b.getDate()))
                .toList();
    }

    public ChatMessage save(String pseudo, String contenu) {
        if (contenu == null || contenu.trim().isEmpty() || contenu.length() > 150)
            throw new IllegalArgumentException("Message invalide");
        ChatMessage msg = new ChatMessage();
        msg.setPseudo(pseudo);
        msg.setContenu(contenu.trim());
        msg.setDate(LocalDateTime.now());
        return repo.save(msg);
    }


    @DeleteMapping("/clear")
    @PreAuthorize("hasRole('ADMIN')")
    public void clear() {
        repo.deleteAll();
    }

    // Auto-reset chaque heure
    @Scheduled(cron = "0 0 * * * *") // chaque heure pile
    public void autoClear() {
        repo.deleteAll();
        System.out.println("[Chat] Vidé automatiquement à " + LocalDateTime.now());
    }
}
