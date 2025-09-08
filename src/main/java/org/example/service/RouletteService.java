package org.example.service;

import org.example.dto.RouletteBetRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RouletteService {

    private final Random rand = new Random();

    // poids personnalisés : numéro -> poids (entier >= 0)
    // si null ou vide => tirage équitable
    private Map<Integer, Integer> customWeights = null;

    // ensemble des numéros rouges
    private static final Set<Integer> RED_SET = Set.of(
            1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36
    );

    // ---------- ADMIN ----------
    public synchronized void setCustomWeights(Map<Integer, Integer> weights) {
        if (weights == null || weights.isEmpty()) {
            this.customWeights = null;
            return;
        }
        // sanitize: only keep keys 0..36 and non-negative integer weights
        Map<Integer, Integer> cleaned = new ConcurrentHashMap<>();
        for (Map.Entry<Integer, Integer> e : weights.entrySet()) {
            Integer k = e.getKey();
            Integer v = e.getValue();
            if (k == null || v == null) continue;
            if (k < 0 || k > 36) continue;
            if (v <= 0) continue; // ignore zero or negative -> treated as not allowed
            cleaned.put(k, v);
        }
        this.customWeights = cleaned.isEmpty() ? null : cleaned;
    }

    public synchronized Map<Integer, Integer> getCustomWeights() {
        return customWeights == null ? null : new HashMap<>(customWeights);
    }

    public synchronized void resetWeights() {
        this.customWeights = null;
    }





    // ---------- TIRAGE ----------
    public int tirerNumero() {
        if (customWeights == null || customWeights.isEmpty()) {
            return rand.nextInt(37); // 0..36 uniform
        }
        return weightedRandom(customWeights);
    }

    private int weightedRandom(Map<Integer, Integer> weights) {
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) return rand.nextInt(37);
        int r = rand.nextInt(total);
        for (Map.Entry<Integer, Integer> e : weights.entrySet()) {
            r -= e.getValue();
            if (r < 0) return e.getKey();
        }
        // fallback (shouldn't happen)
        return rand.nextInt(37);
    }

    public String couleurPour(int numero) {
        if (numero == 0) return "green";
        return RED_SET.contains(numero) ? "red" : "black";
    }

    /**
     * Vérifie si un pari est gagnant
     * betType : "straight", "color", "parity", "range", "dozen"
     * betValue : depends on type (string)
     */
    public boolean estGagnant(String betType, String betValue, int numero) {
        if (betType == null || betValue == null) return false;
        switch (betType) {
            case "straight":
                try {
                    int n = Integer.parseInt(betValue);
                    return n == numero;
                } catch (NumberFormatException ex) { return false; }
            case "color":
                String color = couleurPour(numero);
                return betValue.equalsIgnoreCase(color);
            case "parity":
                if (numero == 0) return false;
                if ("even".equalsIgnoreCase(betValue)) return numero % 2 == 0;
                if ("odd".equalsIgnoreCase(betValue)) return numero % 2 == 1;
                return false;
            case "range":
                if ("low".equalsIgnoreCase(betValue)) return numero >= 1 && numero <= 18;
                if ("high".equalsIgnoreCase(betValue)) return numero >= 19 && numero <= 36;
                return false;
            case "dozen":
                if ("1".equals(betValue)) return numero >= 1 && numero <= 12;
                if ("2".equals(betValue)) return numero >= 13 && numero <= 24;
                if ("3".equals(betValue)) return numero >= 25 && numero <= 36;
                return false;
            default:
                return false;
        }
    }

    /**
     * Multiplicateur (utilisé pour calculer montantGagne = mise * mult)
     * - straight : 35 (payout 35x)
     * - color / parity / range : 2
     * - dozen : 3
     */
    public long payoutMultiplier(String betType) {
        if (betType == null) return 0L;
        switch (betType) {
            case "straight": return 35L;
            case "dozen": return 3L;
            case "color":
            case "parity":
            case "range":
                return 2L;
            default: return 0L;
        }
    }
}
