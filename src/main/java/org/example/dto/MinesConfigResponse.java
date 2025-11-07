package org.example.dto;

import java.util.Map;

public class MinesConfigResponse {
    public int gridSize;
    public int mines;
    public double houseEdge;
    public Map<Integer, Double> multipliers; // k -> multiplicateur

    public MinesConfigResponse(int gridSize, int mines, double houseEdge, Map<Integer, Double> multipliers) {
        this.gridSize = gridSize;
        this.mines = mines;
        this.houseEdge = houseEdge;
        this.multipliers = multipliers;
    }
}
