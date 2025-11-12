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

    private static final List<String> BAD_WORDS = List.of(
            "fdp", "ntm", "pute", "connard", "salope", "enculé", "batard",
            "tg", "nique", "merde", "pd", "bouffon", "chienne"
    );

    public List<ChatMessage> getAll() {
        return repo.findAll().stream()
                .sorted((a,b) -> a.getDate().compareTo(b.getDate()))
                .toList();
    }

    public ChatMessage save(String pseudo, String contenu) {
        if (contenu == null || contenu.trim().isEmpty() || contenu.length() > 150)
            throw new IllegalArgumentException("Message invalide");

        String clean = sanitizeMessage(contenu.trim());

        ChatMessage msg = new ChatMessage();
        msg.setPseudo(pseudo);
        msg.setContenu(clean);
        msg.setDate(LocalDateTime.now());
        return repo.save(msg);
    }

    private String sanitizeMessage(String contenu) {
        String texte = contenu;
        for (String bad : BAD_WORDS) {
            String regex = "(?i)\\b" + bad + "\\b"; // insensible à la casse
            texte = texte.replaceAll(regex, "***");
        }
        return texte;
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

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteById(Long id) {
        repo.deleteById(id);
    }

}
