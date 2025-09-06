package org.example.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class SlotService {

    // symboles par défaut
    private volatile List<String> symbols = new CopyOnWriteArrayList<>(List.of("🍒", "🍋", "🍊", "⭐", "7️⃣"));

    // pour chaque rouleau : liste d'entiers (poids alignés avec symbols)
    private volatile List<List<Integer>> reelWeights = new CopyOnWriteArrayList<>();

    // payout map : clé = nombre d'occurrences identiques (k), valeur = multiplicateur
    // ex : {5:100, 4:25, 3:5, 2:1}
    private volatile LinkedHashMap<Integer, Integer> payouts = new LinkedHashMap<>();

    // nombre de rouleaux
    private volatile int reelsCount = 3;

    private final SecureRandom random = new SecureRandom();

    public SlotService() {
        // init
        resetDefaultWeights();
        resetDefaultPayouts();
    }

    private void resetDefaultWeights() {
        reelWeights.clear();
        for (int r = 0; r < reelsCount; r++) {
            List<Integer> weights = new ArrayList<>();
            for (int i = 0; i < symbols.size(); i++) {
                weights.add(100); // poids identiques par défaut
            }
            reelWeights.add(weights);
        }
    }

    private void resetDefaultPayouts() {
        payouts.clear();
        // comportement rétrocompatible par défaut (pour 3 rouleaux)
        // priorité: clé la plus grande doit être testée en premier (LinkedHashMap insertion order)
        payouts.put(3, 10);
        payouts.put(2, 2);
    }

    /**
     * Effectue un spin : pour chaque rouleau on choisit un index selon les poids du rouleau,
     * puis on renvoie la liste des symboles correspondants.
     */
    public List<String> spin() {
        List<String> res = new ArrayList<>(reelsCount);
        // si reelWeights n'est pas correctement initialisé, rebuild fallback
        ensureWeightsShape();
        for (int r = 0; r < reelsCount; r++) {
            int idx = weightedPickIndex(reelWeights.get(r));
            // sécurité : si idx hors borne, clamp
            if (idx < 0 || idx >= symbols.size()) idx = 0;
            res.add(symbols.get(idx));
        }
        return res;
    }

    private void ensureWeightsShape() {
        // assure que reelWeights contient reelsCount lignes et que chaque ligne a symbols.size() colonnes
        if (reelWeights == null) {
            resetDefaultWeights();
            return;
        }
        if (reelWeights.size() != reelsCount) {
            // rebuild default (safer)
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

    /**
     * Sélection pondérée d'un index dans la liste weights.
     */
    private int weightedPickIndex(List<Integer> weights) {
        if (weights == null || weights.isEmpty()) return random.nextInt(symbols.size());
        int total = 0;
        for (Integer w : weights) total += (w == null ? 0 : w);
        if (total <= 0) {
            return random.nextInt(symbols.size());
        }
        int v = random.nextInt(total);
        int cum = 0;
        for (int i = 0; i < weights.size(); i++) {
            cum += (weights.get(i) == null ? 0 : weights.get(i));
            if (v < cum) return i;
        }
        return weights.size() - 1;
    }

    /**
     * Calcule le payout en fonction des occurrences et de la table payouts.
     * La table payouts est consultée en priorité pour les valeurs les plus grandes.
     */
    public long computePayout(List<String> reels, long mise) {
        if (reels == null || reels.isEmpty()) return 0L;
        Map<String, Long> counts = reels.stream().collect(Collectors.groupingBy(s -> s, Collectors.counting()));
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);

        // trouver la clé la plus grande k telle que max >= k (itérer les clés en ordre décroissant)
        List<Integer> keysDesc = payouts.keySet().stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        for (Integer k : keysDesc) {
            if (k != null && max >= k) {
                Integer mult = payouts.get(k);
                if (mult != null && mult > 0) {
                    return mise * mult;
                } else {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    /* =========================
       Admin / getters / setters
       ========================= */

    /**
     * Met à jour la configuration (symbols, reelWeights, reelsCount, payouts).
     * Les paramètres peuvent être partiels (null) — on applique des règles de fallback.
     */
    public synchronized void updateConfig(List<String> newSymbols,
                                          List<List<Integer>> newReelWeights,
                                          Integer newReelsCount,
                                          Map<Integer, Integer> newPayouts) {

        // update symbols si fournis
        if (newSymbols != null && !newSymbols.isEmpty()) {
            this.symbols = new CopyOnWriteArrayList<>(newSymbols);
        }

        // reelsCount
        if (newReelsCount != null && newReelsCount > 0) {
            this.reelsCount = Math.max(1, newReelsCount);
        }

        // normalize weights
        if (newReelWeights != null) {
            List<List<Integer>> normalized = new ArrayList<>();
            for (int r = 0; r < this.reelsCount; r++) {
                List<Integer> row = (r < newReelWeights.size() ? newReelWeights.get(r) : null);
                List<Integer> finalRow = new ArrayList<>();
                if (row != null && row.size() == this.symbols.size()) {
                    finalRow.addAll(row);
                } else {
                    // fallback: equal weights
                    for (int i = 0; i < this.symbols.size(); i++) finalRow.add(100);
                }
                normalized.add(finalRow);
            }
            this.reelWeights = new CopyOnWriteArrayList<>(normalized);
        } else {
            // rebuild default weights aligned to symbols & reelsCount
            resetDefaultWeights();
        }

        // payouts
        if (newPayouts != null && !newPayouts.isEmpty()) {
            // copy into LinkedHashMap preserving natural order of keys (but we'll sort on use)
            LinkedHashMap<Integer,Integer> copy = new LinkedHashMap<>();
            // optionally sort keys descending to store in descending insertion order
            newPayouts.entrySet().stream()
                    .sorted(Map.Entry.<Integer,Integer>comparingByKey(Comparator.reverseOrder()))
                    .forEach(e -> copy.put(e.getKey(), e.getValue()));
            this.payouts = copy;
        } else {
            // keep existing payouts (no-op) or reset defaults? keep existing to avoid accidental wipe
        }
    }

    public synchronized List<String> getSymbols() {
        return new ArrayList<>(symbols);
    }

    public synchronized List<List<Integer>> getReelWeights() {
        // defensive copy
        return reelWeights.stream().map(ArrayList::new).collect(Collectors.toList());
    }

    public synchronized Map<Integer,Integer> getPayouts() {
        return new LinkedHashMap<>(payouts);
    }

    public int getReelsCount() {
        return reelsCount;
    }

    // setter direct pour admin
    public synchronized void setPayouts(Map<Integer,Integer> p) {
        if (p == null || p.isEmpty()) return;
        LinkedHashMap<Integer,Integer> copy = new LinkedHashMap<>();
        p.entrySet().stream()
                .sorted(Map.Entry.<Integer,Integer>comparingByKey(Comparator.reverseOrder()))
                .forEach(e -> copy.put(e.getKey(), e.getValue()));
        this.payouts = copy;
    }

    // helper utilitaire : retourne la somme des poids pour un rouleau
    public int getTotalWeightForReel(int reelIndex) {
        if (reelIndex < 0 || reelIndex >= reelWeights.size()) return 0;
        int sum = 0;
        for (Integer w : reelWeights.get(reelIndex)) sum += (w == null ? 0 : w);
        return sum;
    }
}
