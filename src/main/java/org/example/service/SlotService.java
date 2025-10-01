// src/main/java/org/example/service/SlotService.java
package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.SlotConfig;
import org.example.repo.SlotConfigRepository;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;


@Service // Déclare un service Spring pour la gestion des machines à sous
public class SlotService {

    // Liste des symboles (par défaut quelques emojis classiques)
    private volatile List<String> symbols = new CopyOnWriteArrayList<>(List.of("🍒", "🍋", "🍊", "⭐", "7️⃣"));
    // Poids (probabilités) de chaque symbole pour chaque rouleau
    private volatile List<List<Integer>> reelWeights = new CopyOnWriteArrayList<>();
    // Gains selon les combinaisons (ex: 3 symboles identiques → 10x la mise)
    private volatile LinkedHashMap<Integer, Integer> payouts = new LinkedHashMap<>();
    // Nombre de rouleaux (3 par défaut)
    private volatile int reelsCount = 3;

    // Générateur aléatoire sécurisé
    private final SecureRandom random = new SecureRandom();
    // Utilisé pour sérialiser/désérialiser les JSON en BDD
    private final ObjectMapper objectMapper = new ObjectMapper();
    // Repository pour sauvegarder la config en BDD
    private final SlotConfigRepository repo;

    // Constructeur → initialise repo, poids et gains par défaut
    public SlotService(SlotConfigRepository repo) {
        this.repo = repo;
        resetDefaultWeights();
        resetDefaultPayouts();
    }

    @PostConstruct // Appelé après la création du bean Spring
    public void initFromDb() {
        List<SlotConfig> all = repo.findAll(); // récupère les configs depuis la DB
        if (all.isEmpty()) {
            persistCurrentConfig(); // si rien en DB → enregistre la config par défaut
            return;
        }
        SlotConfig cfg = all.get(0); // prend la première config

        // ----- Lecture JSON (symbols, poids, payouts) -----
        try {
            if (cfg.getSymbolsJson() != null) {
                List<String> sym = objectMapper.readValue(cfg.getSymbolsJson(), new TypeReference<>() {});
                if (sym != null && !sym.isEmpty()) this.symbols = new CopyOnWriteArrayList<>(sym);
            }
        } catch (Exception ex) { /* ignore & garde defaults */ }

        try {
            if (cfg.getReelWeightsJson() != null) {
                List<List<Integer>> rw = objectMapper.readValue(cfg.getReelWeightsJson(), new TypeReference<>() {});
                if (rw != null && !rw.isEmpty()) this.reelWeights = new CopyOnWriteArrayList<>(rw);
            }
        } catch (Exception ex) { /* ignore */ }

        try {
            if (cfg.getPayoutsJson() != null) {
                Map<String,Integer> p = objectMapper.readValue(cfg.getPayoutsJson(), new TypeReference<>() {});
                if (p != null && !p.isEmpty()) {
                    LinkedHashMap<Integer,Integer> map = new LinkedHashMap<>();
                    // trie par clé décroissante (meilleurs combos d’abord)
                    p.entrySet().stream()
                            .sorted(Map.Entry.<String,Integer>comparingByKey(Comparator.reverseOrder()))
                            .forEach(e -> map.put(Integer.valueOf(e.getKey()), e.getValue()));
                    this.payouts = map;
                }
            }
        } catch (Exception ex) { /* ignore */ }

        if (cfg.getReelsCount() != null && cfg.getReelsCount() > 0) {
            this.reelsCount = cfg.getReelsCount();
        }

        // S’assure que les poids correspondent bien à la taille des symboles
        ensureWeightsShape();
        if (payouts == null || payouts.isEmpty()) resetDefaultPayouts();
    }

    // ----- Config par défaut -----
    private void resetDefaultWeights() {
        reelWeights.clear();
        for (int r = 0; r < reelsCount; r++) {
            List<Integer> weights = new ArrayList<>();
            for (int i = 0; i < symbols.size(); i++) weights.add(100);
            reelWeights.add(weights);
        }
    }

    private void resetDefaultPayouts() {
        payouts.clear();
        payouts.put(3, 10); // 3 symboles identiques = x10
        payouts.put(2, 2);  // 2 symboles identiques = x2
    }

    // Sauvegarde la config actuelle en DB
    private void persistCurrentConfig() {
        try {
            String symbolsJson = objectMapper.writeValueAsString(this.symbols);
            String reelWeightsJson = objectMapper.writeValueAsString(this.reelWeights);
            // conversions clés integer -> string pour JSON
            Map<String,Integer> payoutsObj = new LinkedHashMap<>();
            for (Map.Entry<Integer,Integer> e : this.payouts.entrySet()) payoutsObj.put(String.valueOf(e.getKey()), e.getValue());
            String payoutsJson = objectMapper.writeValueAsString(payoutsObj);

            SlotConfig cfg = new SlotConfig(symbolsJson, reelWeightsJson, payoutsJson, this.reelsCount);
            List<SlotConfig> all = repo.findAll();
            if (all.isEmpty()) repo.save(cfg);
            else {
                SlotConfig exist = all.get(0);
                exist.setSymbolsJson(symbolsJson);
                exist.setReelWeightsJson(reelWeightsJson);
                exist.setPayoutsJson(payoutsJson);
                exist.setReelsCount(this.reelsCount);
                repo.save(exist);
            }
        } catch (Exception ex) {
            // pourrait être loggé
        }
    }

    // ----- Tirage -----
    public List<String> spin() {
        ensureWeightsShape();
        List<String> res = new ArrayList<>(reelsCount);
        for (int r = 0; r < reelsCount; r++) {
            int idx = weightedPickIndex(reelWeights.get(r)); // choix pondéré
            if (idx < 0 || idx >= symbols.size()) idx = 0;
            res.add(symbols.get(idx));
        }
        return res;
    }

    // Choix d’un index selon des poids
    private int weightedPickIndex(List<Integer> weights) {
        if (weights == null || weights.isEmpty()) return random.nextInt(symbols.size());
        int total = 0;
        for (Integer w : weights) total += (w == null ? 0 : w);
        if (total <= 0) return random.nextInt(symbols.size());
        int v = random.nextInt(total);
        int cum = 0;
        for (int i = 0; i < weights.size(); i++) {
            cum += (weights.get(i) == null ? 0 : weights.get(i));
            if (v < cum) return i;
        }
        return weights.size() - 1;
    }

    // Calcule le gain selon les combinaisons
    public long computePayout(List<String> reels, long mise) {
        if (reels == null || reels.isEmpty()) return 0L;
        Map<String, Long> counts = reels.stream().collect(Collectors.groupingBy(s->s, Collectors.counting()));
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<Integer> keysDesc = payouts.keySet().stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        for (Integer k : keysDesc) {
            if (k != null && max >= k) {
                Integer mult = payouts.get(k);
                if (mult != null && mult > 0) return mise * mult;
                else return 0L;
            }
        }
        return 0L;
    }

    // Mise à jour admin (avec persistance)
    public synchronized void updateConfig(List<String> newSymbols,
                                          List<List<Integer>> newReelWeights,
                                          Integer newReelsCount,
                                          Map<Integer,Integer> newPayouts) {
        if (newSymbols != null && !newSymbols.isEmpty()) this.symbols = new CopyOnWriteArrayList<>(newSymbols);
        if (newReelsCount != null && newReelsCount > 0) this.reelsCount = Math.max(1, newReelsCount);

        if (newReelWeights != null) {
            List<List<Integer>> normalized = new ArrayList<>();
            for (int r = 0; r < this.reelsCount; r++) {
                List<Integer> row = (r < newReelWeights.size() ? newReelWeights.get(r) : null);
                List<Integer> finalRow = new ArrayList<>();
                if (row != null && row.size() == this.symbols.size()) finalRow.addAll(row);
                else {
                    for (int i = 0; i < this.symbols.size(); i++) finalRow.add(100);
                }
                normalized.add(finalRow);
            }
            this.reelWeights = new CopyOnWriteArrayList<>(normalized);
        } else {
            resetDefaultWeights();
        }

        if (newPayouts != null && !newPayouts.isEmpty()) {
            LinkedHashMap<Integer,Integer> copy = new LinkedHashMap<>();
            newPayouts.entrySet().stream()
                    .sorted(Map.Entry.<Integer,Integer>comparingByKey(Comparator.reverseOrder()))
                    .forEach(e -> copy.put(e.getKey(), e.getValue()));
            this.payouts = copy;
        }

        persistCurrentConfig();
    }

    // ----- Getters sécurisés -----
    public synchronized List<String> getSymbols() { return new ArrayList<>(symbols); }
    public synchronized List<List<Integer>> getReelWeights() { return reelWeights.stream().map(ArrayList::new).collect(Collectors.toList()); }
    public synchronized Map<Integer,Integer> getPayouts() { return new LinkedHashMap<>(payouts); }
    public int getReelsCount() { return reelsCount; }

    private void ensureWeightsShape() {
        if (reelWeights == null || reelWeights.size() != reelsCount) {
            resetDefaultWeights();
            return;
        }
        for (int r = 0; r < reelsCount; r++) {
            List<Integer> row = reelWeights.get(r);
            if (row == null || row.size() != symbols.size()) {
                List<Integer> newRow = new ArrayList<>();
                for (int i = 0; i < symbols.size(); i++) newRow.add(100);
                reelWeights.set(r, newRow);
            }
        }
    }

    public int getTotalWeightForReel(int reelIndex) {
        if (reelIndex < 0 || reelIndex >= reelWeights.size()) return 0;
        int sum = 0;
        for (Integer w : reelWeights.get(reelIndex)) sum += (w == null ? 0 : w);
        return sum;
    }
}
