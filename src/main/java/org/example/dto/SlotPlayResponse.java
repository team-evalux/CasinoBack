package org.example.dto;

import java.util.List;

public class SlotPlayResponse {
    public List<String> reels;   // symboles tirés, ex: ["🍒","🍋","🍒"]
    public long montantJoue;
    public long montantGagne;
    public boolean win;
    public long solde;

    public SlotPlayResponse() {}

    public SlotPlayResponse(List<String> reels, long montantJoue, long montantGagne, boolean win, long solde) {
        this.reels = reels;
        this.montantJoue = montantJoue;
        this.montantGagne = montantGagne;
        this.win = win;
        this.solde = solde;
    }
}
