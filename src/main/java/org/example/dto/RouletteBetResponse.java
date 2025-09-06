package org.example.dto;

public class RouletteBetResponse {
    public int number; // 0..36
    public String color; // "red", "black", "green" (0 is green)
    public boolean win;
    public long montantJoue;
    public long montantGagne; // montant crédité (0 si perdu)
    public long solde; // solde final

    public RouletteBetResponse() {}
    public RouletteBetResponse(int number, String color, boolean win, long montantJoue, long montantGagne, long solde) {
        this.number = number;
        this.color = color;
        this.win = win;
        this.montantJoue = montantJoue;
        this.montantGagne = montantGagne;
        this.solde = solde;
    }
}
