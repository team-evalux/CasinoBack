// src/main/java/org/example/service/ChatService.java
package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.ChatAggregate;
import org.example.dto.ChatMessage;
import org.example.repo.ChatAggregateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ChatService {

    private static final long CHAT_ID = 1L;
    private static final int MAX_MESSAGES = 500;

    @Autowired
    private ChatAggregateRepository repo;

    @Autowired
    private ObjectMapper objectMapper;

    private static final List<String> BAD_WORDS = List.of(
            "fdp", "ntm", "pute", "connard", "salope", "enculé", "batard",
            "tg", "nique", "merde", "pd", "bouffon", "chienne"
    );

    // ---- Structure interne JSON ----
    public static class Entry {
        public Long id;
        public String pseudo;
        public String contenu;
        public LocalDateTime date;
    }

    private List<Entry> readEntries(ChatAggregate ag) {
        if (ag == null || ag.getEntriesJson() == null || ag.getEntriesJson().isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(ag.getEntriesJson(), new TypeReference<List<Entry>>() {});
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private String writeEntries(List<Entry> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            e.printStackTrace();
            return "[]";
        }
    }

    private ChatAggregate getOrCreateAggregate() {
        return repo.findById(CHAT_ID).orElseGet(() -> {
            ChatAggregate ag = new ChatAggregate();
            ag.setId(CHAT_ID);
            ag.setEntriesJson("[]");
            ag.setUpdatedAt(LocalDateTime.now());
            return repo.save(ag);
        });
    }

    private String sanitizeMessage(String contenu) {
        String texte = contenu;
        for (String bad : BAD_WORDS) {
            String regex = "(?i)\\b" + bad + "\\b"; // insensible à la casse
            texte = texte.replaceAll(regex, "***");
        }
        return texte;
    }

    // ---- API Service ----

    public List<ChatMessage> getAll() {
        ChatAggregate ag = repo.findById(CHAT_ID).orElse(null);
        List<Entry> entries = readEntries(ag);

        // du plus ancien au plus récent (comme avant)
        entries.sort(Comparator.comparing(e -> e.date));

        List<ChatMessage> result = new ArrayList<>();
        for (Entry e : entries) {
            ChatMessage cm = new ChatMessage();
            cm.setId(e.id);
            cm.setPseudo(e.pseudo);
            cm.setContenu(e.contenu);
            cm.setDate(e.date);
            result.add(cm);
        }
        return result;
    }

    public ChatMessage save(String pseudo, String contenu) {
        if (contenu == null || contenu.trim().isEmpty() || contenu.length() > 150) {
            throw new IllegalArgumentException("Message invalide");
        }

        String clean = sanitizeMessage(contenu.trim());
        ChatAggregate ag = getOrCreateAggregate();
        List<Entry> entries = readEntries(ag);

        Entry e = new Entry();
        long nowMs = System.currentTimeMillis();
        e.id = nowMs;
        e.pseudo = pseudo;
        e.contenu = clean;
        e.date = LocalDateTime.now();

        // ajoute en tête
        List<Entry> newList = new ArrayList<>();
        newList.add(e);
        newList.addAll(entries);

        // tronque à MAX_MESSAGES
        while (newList.size() > MAX_MESSAGES) {
            newList.remove(newList.size() - 1);
        }

        ag.setEntriesJson(writeEntries(newList));
        ag.setUpdatedAt(LocalDateTime.now());
        repo.save(ag);

        ChatMessage cm = new ChatMessage();
        cm.setId(e.id);
        cm.setPseudo(e.pseudo);
        cm.setContenu(e.contenu);
        cm.setDate(e.date);
        return cm;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void clear() {
        ChatAggregate ag = getOrCreateAggregate();
        ag.setEntriesJson("[]");
        ag.setUpdatedAt(LocalDateTime.now());
        repo.save(ag);
    }

    // Auto-reset chaque heure
    @Scheduled(cron = "0 0 2 * * *")
    public void autoClear() {
        ChatAggregate ag = getOrCreateAggregate();
        ag.setEntriesJson("[]");
        ag.setUpdatedAt(LocalDateTime.now());
        repo.save(ag);
        System.out.println("[Chat] Vidé automatiquement à " + LocalDateTime.now());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteById(Long id) {
        ChatAggregate ag = getOrCreateAggregate();
        List<Entry> entries = readEntries(ag);

        boolean changed = entries.removeIf(e -> e.id != null && e.id.equals(id));
        if (changed) {
            ag.setEntriesJson(writeEntries(entries));
            ag.setUpdatedAt(LocalDateTime.now());
            repo.save(ag);
        }
    }
}
