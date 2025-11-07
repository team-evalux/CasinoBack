package org.example.dto;

import java.util.List;

public class MinesPickResponse {
    public boolean bomb;        // true si bombe touchée
    public int index;           // la case révélée
    public int safeCount;       // nb de diamants révélés dans la session
    public int mines;
    public double currentMultiplier; // multiplicateur atteint (0 si bombe)
    public long potentialPayout;     // floor(mise * currentMultiplier)
    public boolean finished;    // true si partie terminée (bombe)
    public List<Integer> bombs; // renvoyé si bombe touchée (pour révéler tout)

    public MinesPickResponse(boolean bomb, int index, int safeCount, int mines, double currentMultiplier, long potentialPayout, boolean finished, List<Integer> bombs) {
        this.bomb = bomb;
        this.index = index;
        this.safeCount = safeCount;
        this.mines = mines;
        this.currentMultiplier = currentMultiplier;
        this.potentialPayout = potentialPayout;
        this.finished = finished;
        this.bombs = bombs;
    }
}
