// src/main/java/org/example/service/RouletteService.java
package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.RouletteConfig;
import org.example.repo.RouletteConfigRepository;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RouletteService {

    private final Random rand = new Random();
    private Map<Integer, Integer> customWeights = null;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RouletteConfigRepository repo;

    private static final Set<Integer> RED_SET = Set.of(
            1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36
    );

    public RouletteService(RouletteConfigRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    public void initFromDb() {
        List<RouletteConfig> all = repo.findAll();
        if (all.isEmpty()) {
            // default: null => uniform
            repo.save(new RouletteConfig(null));
            this.customWeights = null;
        } else {
            String json = all.get(0).getWeightsJson();
            if (json == null) {
                this.customWeights = null;
            } else {
                try {
                    Map<String,Integer> tmp = objectMapper.readValue(json, new TypeReference<>(){});
                    Map<Integer,Integer> converted = new ConcurrentHashMap<>();
                    for (Map.Entry<String,Integer> e : tmp.entrySet()) {
                        converted.put(Integer.valueOf(e.getKey()), e.getValue());
                    }
                    this.customWeights = converted;
                } catch (Exception ex) {
                    this.customWeights = null;
                }
            }
        }
    }

    public synchronized void setCustomWeights(Map<Integer, Integer> weights) {
        if (weights == null || weights.isEmpty()) {
            this.customWeights = null;
            persistWeightsJson(null);
            return;
        }
        Map<Integer, Integer> cleaned = new ConcurrentHashMap<>();
        for (Map.Entry<Integer, Integer> e : weights.entrySet()) {
            Integer k = e.getKey();
            Integer v = e.getValue();
            if (k == null || v == null) continue;
            if (k < 0 || k > 36) continue;
            if (v <= 0) continue;
            cleaned.put(k, v);
        }
        this.customWeights = cleaned.isEmpty() ? null : cleaned;
        try {
            persistWeightsJson(this.customWeights == null ? null : objectMapper.writeValueAsString(this.customWeights));
        } catch (Exception ex) {
            // ignore persist error (log si tu veux)
        }
    }

    public synchronized Map<Integer, Integer> getCustomWeights() {
        return customWeights == null ? null : new HashMap<>(customWeights);
    }

    public synchronized void resetWeights() {
        this.customWeights = null;
        persistWeightsJson(null);
    }

    private void persistWeightsJson(String json) {
        List<RouletteConfig> all = repo.findAll();
        RouletteConfig cfg;
        if (all.isEmpty()) {
            cfg = new RouletteConfig(json);
        } else {
            cfg = all.get(0);
            cfg.setWeightsJson(json);
        }
        repo.save(cfg);
    }

    // ----- tirage idem que avant -----
    public int tirerNumero() {
        if (customWeights == null || customWeights.isEmpty()) {
            return rand.nextInt(37);
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
        return rand.nextInt(37);
    }

    public String couleurPour(int numero) {
        if (numero == 0) return "green";
        return RED_SET.contains(numero) ? "red" : "black";
    }

    // reste de tes méthodes (estGagnant, payoutMultiplier) inchangées
    public boolean estGagnant(String betType, String betValue, int numero) {
        // ... copie ta logique existante ...
        if (betType == null || betValue == null) return false;
        switch (betType) {
            case "straight":
                try { return Integer.parseInt(betValue) == numero; } catch (Exception e) { return false;}
            case "color":
                return betValue.equalsIgnoreCase(couleurPour(numero));
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
