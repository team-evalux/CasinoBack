package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.SlotConfig;
import org.example.repo.SlotConfigRepository;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SlotService {

    private static final int MAX_REELS = 5;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    private final SlotConfigRepository repo;

    public SlotService(SlotConfigRepository repo) {
        this.repo = repo;
    }

    public static class SlotRuntimeConfig {
        public final List<String> symbols;
        public final List<List<Integer>> reelWeights;
        public final LinkedHashMap<Integer,Integer> payouts;
        public final LinkedHashMap<String,Double> symbolValues;
        public final int reelsCount;

        public SlotRuntimeConfig(List<String> symbols, List<List<Integer>> reelWeights,
                                 LinkedHashMap<Integer,Integer> payouts, LinkedHashMap<String,Double> symbolValues,
                                 int reelsCount) {
            this.symbols = symbols;
            this.reelWeights = reelWeights;
            this.payouts = payouts;
            this.symbolValues = symbolValues;
            this.reelsCount = reelsCount;
        }
    }

    private final Map<Integer, SlotRuntimeConfig> configs = new ConcurrentHashMap<>();

    @PostConstruct
    public void initFromDb() {
        List<SlotConfig> all = repo.findAll();
        configs.clear();
        if (all.isEmpty()) {
            configs.put(3, buildDefaultConfig(3));
            configs.put(4, buildDefaultConfig(4));
            configs.put(5, buildDefaultConfig(5));
            persistAllConfigs();
            return;
        }
        for (SlotConfig cfg : all) {
            try {
                List<String> sym = cfg.getSymbolsJson() != null ?
                        objectMapper.readValue(cfg.getSymbolsJson(), new TypeReference<List<String>>() {}) :
                        new ArrayList<>(List.of("🍒","🍋","🍊","⭐","7️⃣"));
                List<List<Integer>> rw = cfg.getReelWeightsJson() != null ?
                        objectMapper.readValue(cfg.getReelWeightsJson(), new TypeReference<List<List<Integer>>>() {}) :
                        null;
                Map<String,Integer> p = cfg.getPayoutsJson() != null ?
                        objectMapper.readValue(cfg.getPayoutsJson(), new TypeReference<Map<String,Integer>>() {}) :
                        null;
                Map<String,Double> sv = cfg.getSymbolValuesJson() != null ?
                        objectMapper.readValue(cfg.getSymbolValuesJson(), new TypeReference<Map<String,Double>>() {}) :
                        null;

                LinkedHashMap<Integer,Integer> payoutsMap = new LinkedHashMap<>();
                if (p != null && !p.isEmpty()) {
                    p.entrySet().stream()
                            .sorted(Map.Entry.<String,Integer>comparingByKey(Comparator.reverseOrder()))
                            .forEach(e -> payoutsMap.put(Integer.valueOf(e.getKey()), e.getValue()));
                } else {
                    payoutsMap.put(3, 10);
                    payoutsMap.put(2, 2);
                }

                if (rw == null || rw.isEmpty()) {
                    rw = new ArrayList<>();
                    int rc = (cfg.getReelsCount() != null && cfg.getReelsCount() > 0) ? cfg.getReelsCount() : 3;
                    for (int r = 0; r < rc; r++) {
                        List<Integer> w = new ArrayList<>();
                        for (int i = 0; i < sym.size(); i++) w.add(100);
                        rw.add(w);
                    }
                } else {
                    for (int r = 0; r < rw.size(); r++) {
                        List<Integer> row = rw.get(r);
                        if (row == null) row = new ArrayList<>();
                        while (row.size() < sym.size()) row.add(100);
                        if (row.size() > sym.size()) row = row.subList(0, sym.size());
                        rw.set(r, new ArrayList<>(row));
                    }
                }

                LinkedHashMap<String, Double> symbolValuesMap = new LinkedHashMap<>();
                if (sv != null) {
                    for (String s : sym) {
                        Double v = sv.get(s);
                        symbolValuesMap.put(s, v == null ? 1.0 : v);
                    }
                } else {
                    for (String s : sym) symbolValuesMap.put(s, 1.0);
                }

                int rc = (cfg.getReelsCount() != null && cfg.getReelsCount() > 0) ? cfg.getReelsCount() : rw.size();
                SlotRuntimeConfig runtime = new SlotRuntimeConfig(new ArrayList<>(sym),
                        rw.stream().map(ArrayList::new).collect(Collectors.toList()),
                        payoutsMap,
                        symbolValuesMap,
                        rc);
                configs.put(rc, runtime);
            } catch (Exception ex) {
                // ignore malformed row
            }
        }
        for (int r : new int[]{3,4,5}) configs.computeIfAbsent(r, this::buildDefaultConfig);
    }

    private SlotRuntimeConfig buildDefaultConfig(int reelsCount) {
        List<String> sym = new ArrayList<>(List.of("🍒","🍋","🍊","⭐","7️⃣"));
        List<List<Integer>> rw = new ArrayList<>();
        for (int r = 0; r < reelsCount; r++) {
            List<Integer> row = new ArrayList<>();
            for (int i = 0; i < sym.size(); i++) row.add(100);
            rw.add(row);
        }
        LinkedHashMap<Integer,Integer> payouts = new LinkedHashMap<>();
        payouts.put(3, 10);
        payouts.put(2, 2);
        LinkedHashMap<String,Double> sv = new LinkedHashMap<>();
        for (String s : sym) sv.put(s, 1.0);
        return new SlotRuntimeConfig(sym, rw, payouts, sv, reelsCount);
    }

    private void persistAllConfigs() {
        for (SlotRuntimeConfig cfg : configs.values()) persistRuntimeConfig(cfg);
    }

    private void persistRuntimeConfig(SlotRuntimeConfig cfg) {
        try {
            String symbolsJson = objectMapper.writeValueAsString(cfg.symbols);
            String reelWeightsJson = objectMapper.writeValueAsString(cfg.reelWeights);
            Map<String,Integer> payoutsObj = new LinkedHashMap<>();
            for (Map.Entry<Integer,Integer> e : cfg.payouts.entrySet()) payoutsObj.put(String.valueOf(e.getKey()), e.getValue());
            String payoutsJson = objectMapper.writeValueAsString(payoutsObj);
            String symbolValuesJson = objectMapper.writeValueAsString(cfg.symbolValues);

            List<SlotConfig> all = repo.findAll();
            SlotConfig toUpdate = null;
            for (SlotConfig sc : all) {
                if (sc.getReelsCount() != null && sc.getReelsCount().intValue() == cfg.reelsCount) { toUpdate = sc; break; }
            }
            if (toUpdate == null) {
                SlotConfig sc = new SlotConfig(symbolsJson, reelWeightsJson, payoutsJson, symbolValuesJson, cfg.reelsCount);
                repo.save(sc);
            } else {
                toUpdate.setSymbolsJson(symbolsJson);
                toUpdate.setReelWeightsJson(reelWeightsJson);
                toUpdate.setPayoutsJson(payoutsJson);
                toUpdate.setSymbolValuesJson(symbolValuesJson);
                toUpdate.setReelsCount(cfg.reelsCount);
                repo.save(toUpdate);
            }
        } catch (Exception ex) {
            // ignore
        }
    }

    public SlotRuntimeConfig getRuntimeConfigFor(int reelsCount) {
        return configs.get(reelsCount);
    }

    public Map<Integer, SlotRuntimeConfig> getAllRuntimeConfigs() {
        return new LinkedHashMap<>(configs);
    }

    public List<String> spinForReels(Integer requestedReels) {
        int rc = (requestedReels != null && requestedReels > 0) ? requestedReels : 3;
        rc = Math.max(1, rc);
        SlotRuntimeConfig cfg = findNearestConfigOrDefault(rc);
        List<List<Integer>> tempWeights = new ArrayList<>();
        for (int r = 0; r < rc; r++) {
            if (r < cfg.reelWeights.size() && cfg.reelWeights.get(r) != null && cfg.reelWeights.get(r).size() == cfg.symbols.size()) {
                tempWeights.add(new ArrayList<>(cfg.reelWeights.get(r)));
            } else {
                List<Integer> def = new ArrayList<>();
                for (int i = 0; i < cfg.symbols.size(); i++) def.add(100);
                tempWeights.add(def);
            }
        }
        List<String> res = new ArrayList<>(rc);
        for (int r = 0; r < rc; r++) {
            int idx = weightedPickIndexUsingWeights(tempWeights.get(r));
            if (idx < 0 || idx >= cfg.symbols.size()) idx = 0;
            res.add(cfg.symbols.get(idx));
        }
        return res;
    }

    private SlotRuntimeConfig findNearestConfigOrDefault(int rc) {
        if (configs.containsKey(rc)) return configs.get(rc);
        int bestLower = -1, bestHigher = Integer.MAX_VALUE;
        for (Integer k : configs.keySet()) {
            if (k < rc && k > bestLower) bestLower = k;
            if (k > rc && k < bestHigher) bestHigher = k;
        }
        if (bestLower != -1) return configs.get(bestLower);
        if (bestHigher != Integer.MAX_VALUE) return configs.get(bestHigher);
        return buildDefaultConfig(rc);
    }

    private int weightedPickIndexUsingWeights(List<Integer> weights) {
        if (weights == null || weights.isEmpty()) return random.nextInt(Math.max(1, weights.size()));
        int total = 0;
        for (Integer w : weights) total += (w == null ? 0 : w);
        if (total <= 0) return random.nextInt(weights.size());
        int v = random.nextInt(total);
        int cum = 0;
        for (int i = 0; i < weights.size(); i++) {
            cum += (weights.get(i) == null ? 0 : weights.get(i));
            if (v < cum) return i;
        }
        return weights.size() - 1;
    }

    public long computePayout(List<String> reels, long mise) {
        if (reels == null || reels.isEmpty()) return 0L;
        int rc = reels.size();
        SlotRuntimeConfig cfg = findNearestConfigOrDefault(rc);
        Map<String, Long> counts = reels.stream().collect(Collectors.groupingBy(s -> s, Collectors.counting()));
        List<Integer> keysDesc = new ArrayList<>(cfg.payouts.keySet());
        keysDesc.sort(Comparator.reverseOrder());
        for (Integer k : keysDesc) {
            if (k == null || k < 1) continue;
            List<String> candidates = counts.entrySet().stream()
                    .filter(e -> e.getValue() >= k)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            if (!candidates.isEmpty()) {
                Integer baseMult = cfg.payouts.get(k);
                if (baseMult == null || baseMult <= 0) return 0L;
                double bestSv = 1.0;
                for (String sym : candidates) {
                    Double sv = cfg.symbolValues.get(sym);
                    if (sv == null) sv = 1.0;
                    if (sv > bestSv) bestSv = sv;
                }
                double gain = ((double) mise) * baseMult * bestSv;
                return (long) Math.floor(gain);
            }
        }
        return 0L;
    }

    private int defaultPayoutForK(int k) {
        return Math.max(1, (int)Math.floor(Math.pow(3, k - 2)));
    }

    public synchronized void updateConfigForReels(int reelsCount,
                                                  List<String> newSymbols,
                                                  List<List<Integer>> newReelWeights,
                                                  Map<Integer,Integer> newPayouts,
                                                  Map<String,Double> newSymbolValues) {
        List<String> symbols = (newSymbols != null && !newSymbols.isEmpty()) ? new ArrayList<>(newSymbols) : new ArrayList<>(List.of("🍒","🍋","🍊","⭐","7️⃣"));

        List<List<Integer>> reelWeights = new ArrayList<>();
        if (newReelWeights != null) {
            for (int r = 0; r < reelsCount; r++) {
                List<Integer> row = (r < newReelWeights.size() ? newReelWeights.get(r) : null);
                List<Integer> finalRow = new ArrayList<>();
                if (row != null && row.size() == symbols.size()) finalRow.addAll(row);
                else {
                    for (int i = 0; i < symbols.size(); i++) finalRow.add(100);
                }
                reelWeights.add(finalRow);
            }
        } else {
            for (int r = 0; r < reelsCount; r++) {
                List<Integer> row = new ArrayList<>();
                for (int i = 0; i < symbols.size(); i++) row.add(100);
                reelWeights.add(row);
            }
        }

        // ✅ Correction: construire payouts via TreeMap (ordre décroissant), puis LinkedHashMap
        NavigableMap<Integer, Integer> tm = new TreeMap<>(Comparator.reverseOrder());
        if (newPayouts != null && !newPayouts.isEmpty()) {
            tm.putAll(newPayouts);
        }
        for (int k = 2; k <= MAX_REELS; k++) {
            tm.putIfAbsent(k, defaultPayoutForK(k));
        }
        LinkedHashMap<Integer, Integer> payouts = new LinkedHashMap<>(tm);

        LinkedHashMap<String,Double> symbolValues = new LinkedHashMap<>();
        for (String s : symbols) {
            Double v = (newSymbolValues != null) ? newSymbolValues.get(s) : null;
            symbolValues.put(s, v == null ? 1.0 : v);
        }

        SlotRuntimeConfig runtime = new SlotRuntimeConfig(symbols, reelWeights, payouts, symbolValues, reelsCount);
        configs.put(reelsCount, runtime);
        persistRuntimeConfig(runtime);
    }

    public synchronized List<String> getSymbols() {
        SlotRuntimeConfig cfg = configs.get(3);
        return cfg == null ? List.of() : new ArrayList<>(cfg.symbols);
    }

    public synchronized List<List<Integer>> getReelWeights() {
        SlotRuntimeConfig cfg = configs.get(3);
        return cfg == null ? List.of() : cfg.reelWeights.stream().map(ArrayList::new).collect(Collectors.toList());
    }

    public synchronized Map<Integer,Integer> getPayouts() {
        SlotRuntimeConfig cfg = configs.get(3);
        return cfg == null ? new LinkedHashMap<>() : new LinkedHashMap<>(cfg.payouts);
    }

    public synchronized Map<String,Double> getSymbolValues() {
        SlotRuntimeConfig cfg = configs.get(3);
        return cfg == null ? new LinkedHashMap<>() : new LinkedHashMap<>(cfg.symbolValues);
    }

    public int getReelsCount() {
        SlotRuntimeConfig cfg = configs.get(3);
        return cfg == null ? 3 : cfg.reelsCount;
    }

    public int getTotalWeightForReel(int reelIndex) {
        SlotRuntimeConfig cfg = configs.get(3);
        if (cfg == null || reelIndex < 0 || reelIndex >= cfg.reelWeights.size()) return 0;
        int sum = 0;
        for (Integer w : cfg.reelWeights.get(reelIndex)) sum += (w == null ? 0 : w);
        return sum;
    }
}
