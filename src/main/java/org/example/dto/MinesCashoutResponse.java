package org.example.dto;

import java.util.List;

public class MinesCashoutResponse {
    public boolean ok;
    public int safeCount;
    public double multiplier;
    public long payout;       // crédité
    public long solde;        // solde après crédit
    public List<Integer> bombs;

    public MinesCashoutResponse(boolean ok, int safeCount, double multiplier, long payout, long solde, List<Integer> bombs) {
        this.ok = ok;
        this.safeCount = safeCount;
        this.multiplier = multiplier;
        this.payout = payout;
        this.solde = solde;
        this.bombs = bombs;
    }
}
