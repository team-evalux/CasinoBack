package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.GameHistoryAggregate;
import org.example.model.Utilisateur;
import org.example.repo.GameHistoryAggregateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class GameHistoryService {

    private static final int MAX_ENTRIES = 10;

    @Autowired
    private GameHistoryAggregateRepository repo;

    @Autowired
    private ObjectMapper objectMapper;

    // In-memory cache: utilisateurId -> (game -> list(entries))
    // NOTE: this is a simple cache kept in memory for fast reads. Consider lazy-loading / LRU if you have many users.
    private final Map<Long, Map<String, List<Entry>>> cache = new ConcurrentHashMap<>();

    // DTO used internally to (de)serialize entries
    public static class Entry {
        public long id; // epoch millis
        public long createdAt; // epoch millis
        public long montantJoue;
        public long montantGagne;
        public Integer multiplier;
        public String outcome;
    }

    // DTO returned to controller (shape compatible with old API)
    public static class HistoryRecord {
        public Long id;
        public String game;
        public String outcome;
        public long montantJoue;
        public long montantGagne;
        public Integer multiplier;
        public String createdAt; // ISO string

        public HistoryRecord(Long id, String game, String outcome, long montantJoue, long montantGagne, Integer multiplier, String createdAt) {
            this.id = id;
            this.game = game;
            this.outcome = outcome;
            this.montantJoue = montantJoue;
            this.montantGagne = montantGagne;
            this.multiplier = multiplier;
            this.createdAt = createdAt;
        }
    }

    private List<Entry> readEntries(GameHistoryAggregate ag) {
        if (ag == null || ag.getEntriesJson() == null || ag.getEntriesJson().isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(ag.getEntriesJson(), new TypeReference<List<Entry>>() {});
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ArrayList<>();
        }
    }

    private String writeEntries(List<Entry> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception ex) {
            ex.printStackTrace();
            return "[]";
        }
    }

    private String isoOfEpochMilli(long epochMilli) {
        return Instant.ofEpochMilli(epochMilli).atOffset(ZoneOffset.UTC).toString();
    }

    /**
     * Charge tous les aggregates depuis la DB au démarrage et remplit le cache.
     * Comportement volontairement simple : on remplit le cache entièrement.
     * Si trop gros pour la RAM, passe à une stratégie lazy.
     */
    @PostConstruct
    public void initFromDb() {
        try {
            List<GameHistoryAggregate> all = repo.findAll();
            for (GameHistoryAggregate ag : all) {
                Long userId = ag.getUtilisateurId();
                if (userId == null) continue;
                Map<String, List<Entry>> perUser = cache.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
                List<Entry> entries = readEntries(ag);
                if (entries == null) entries = new ArrayList<>();
                perUser.put(ag.getGame(), entries);
            }
            // Optionnel : log le nombre chargé
            System.out.println("[GameHistoryService] initFromDb loaded aggregates: " + cache.values().stream().mapToInt(m -> m.size()).sum() + " (users: " + cache.size() + ")");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Record a new game result for user+game. Threadsafe per JVM (synchronized).
     * It will prepend the entry, keep up to MAX_ENTRIES, update cache and save the aggregate.
     *
     * outcome: can be any string (kept as-is in entry.outcome)
     */
    @Transactional
    public synchronized void record(Utilisateur u, String game, String outcome, long montantJoue, long montantGagne, Integer multiplier) {
        if (u == null || game == null) return;
        Long userId = u.getId();
        if (userId == null) return;

        // build new entry
        Entry e = new Entry();
        long now = System.currentTimeMillis();
        e.id = now; // simple numeric id
        e.createdAt = now;
        e.montantJoue = montantJoue;
        e.montantGagne = montantGagne;
        e.multiplier = multiplier;
        e.outcome = outcome;

        // update cache
        Map<String, List<Entry>> perUser = cache.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
        List<Entry> entries = perUser.getOrDefault(game, new ArrayList<>());
        List<Entry> newList = new ArrayList<>();
        newList.add(e);
        for (Entry ex : entries) {
            if (newList.size() >= MAX_ENTRIES) break;
            newList.add(ex);
        }
        perUser.put(game, newList);

        // persist aggregate (create or update)
        GameHistoryAggregate ag = repo.findByUtilisateurIdAndGame(userId, game).orElse(null);
        if (ag == null) {
            ag = new GameHistoryAggregate();
            ag.setUtilisateurId(userId);
            ag.setGame(game);
        }
        ag.setEntriesJson(writeEntries(newList));
        ag.setUpdatedAt(LocalDateTime.now());
        repo.save(ag);
    }

    /**
     * Return recent entries across all games flattened and sorted by createdAt desc.
     * Each item keeps the same JSON shape the front expects.
     * Uses the in-memory cache for faster reads.
     */
    public List<HistoryRecord> recentForUser(Utilisateur u, int limit) {
        if (limit <= 0) limit = 15;
        if (u == null) return Collections.emptyList();

        Long userId = u.getId();
        if (userId == null) return Collections.emptyList();

        Map<String, List<Entry>> perUser = cache.get(userId);
        // fallback to DB if not cached
        if (perUser == null) {
            List<GameHistoryAggregate> ags = repo.findByUtilisateurId(userId);
            perUser = new HashMap<>();
            for (GameHistoryAggregate ag : ags) {
                perUser.put(ag.getGame(), readEntries(ag));
            }
            cache.put(userId, perUser);
        }

        List<HistoryRecord> all = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> kv : perUser.entrySet()) {
            String game = kv.getKey();
            List<Entry> list = kv.getValue();
            if (list == null) continue;

            // TRIE par epoch ici
            list.stream()
                    .sorted(Comparator.comparingLong((Entry e) -> e.createdAt).reversed())
                    .limit(limit) // tu peux limiter par jeu si tu veux
                    .forEach(e -> all.add(new HistoryRecord(
                            e.id, game, e.outcome, e.montantJoue, e.montantGagne, e.multiplier, isoOfEpochMilli(e.createdAt)
                    )));
        }

        // Puis re-trie le mix final et limite globalement
        return all.stream()
                .sorted(Comparator.comparing((HistoryRecord r) -> r.createdAt).reversed()) // ou refais un tri par epoch si tu ajoutes createdAtMs
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Return recent entries for a single game for this user.
     * Uses cache when possible.
     */
    public List<HistoryRecord> recentForUserByGame(Utilisateur u, String game, int limit) {
        if (limit <= 0) limit = 15;
        if (u == null || game == null) return Collections.emptyList();

        Long userId = u.getId();
        if (userId == null) return Collections.emptyList();

        Map<String, List<Entry>> perUser = cache.get(userId);
        List<Entry> list;
        if (perUser != null && perUser.containsKey(game)) {
            list = perUser.get(game);
        } else {
            Optional<GameHistoryAggregate> opt = repo.findByUtilisateurIdAndGame(userId, game);
            if (opt.isEmpty()) return Collections.emptyList();
            list = readEntries(opt.get());
            // populate cache for future
            cache.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(game, list);
        }

        if (list == null) return Collections.emptyList();

        return list.stream()
                .map(e -> new HistoryRecord(
                        e.id,
                        game,
                        e.outcome,
                        e.montantJoue,
                        e.montantGagne,
                        e.multiplier,
                        isoOfEpochMilli(e.createdAt)
                ))
                .sorted(Comparator.comparing((HistoryRecord r) -> r.createdAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
}
