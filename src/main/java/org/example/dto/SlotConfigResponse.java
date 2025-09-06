package org.example.dto;

import java.util.List;
import java.util.Map;

public class SlotConfigResponse {
    public List<String> symbols;
    public List<List<Integer>> reelWeights;
    public int reelsCount;
    public Map<Integer,Integer> payouts;

    public SlotConfigResponse() {}

    public SlotConfigResponse(List<String> symbols, List<List<Integer>> reelWeights, int reelsCount, Map<Integer,Integer> payouts) {
        this.symbols = symbols;
        this.reelWeights = reelWeights;
        this.reelsCount = reelsCount;
        this.payouts = payouts;
    }
}
