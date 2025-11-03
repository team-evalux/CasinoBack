package org.example.dto;

import java.util.List;
import java.util.Map;

public class SlotConfigRequest {
    public List<String> symbols;                // ex: ["🍒","🍋","🍊","⭐","7️⃣"]
    public List<List<Integer>> reelWeights;     // ex: [[30,20,20,20,10], [30,20,20,20,10], [30,20,20,20,10]]
    public Integer reelsCount;                  // nombre de rouleaux
    public Map<Integer, Integer> payouts;       // ex: {5:100,4:25,3:5,2:1}
    public Map<String, Double> symbolValues; // <- NEW

    public SlotConfigRequest() {}
}
