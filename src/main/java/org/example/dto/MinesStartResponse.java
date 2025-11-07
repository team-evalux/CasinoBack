package org.example.dto;

public class MinesStartResponse {
    public String sessionId;
    public int gridSize;
    public int mines;
    public int safeMax;
    public int safeCount;       // 0 au début
    public double nextMultiplier; // multiplicateur si on révèle 1er diamant
    public long solde;            // solde après débit

    public MinesStartResponse(String sessionId, int gridSize, int mines, int safeMax, int safeCount, double nextMultiplier, long solde) {
        this.sessionId = sessionId;
        this.gridSize = gridSize;
        this.mines = mines;
        this.safeMax = safeMax;
        this.safeCount = safeCount;
        this.nextMultiplier = nextMultiplier;
        this.solde = solde;
    }
}
