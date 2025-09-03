package org.example.dto;

public class CoinFlipResponse {
    public String outcome;      // "pile" ou "face"
    public boolean win;
    public long montantJoue;    // mise fournie
    public long montantGagne;   // montant crédité (0 si perdu, sinon 2 * mise)
    public long solde;          // solde actuel après l'opération (sera mis à jour)

    public CoinFlipResponse() {}

    public CoinFlipResponse(String outcome, boolean win, long montantJoue, long montantGagne, long solde) {
        this.outcome = outcome;
        this.win = win;
        this.montantJoue = montantJoue;
        this.montantGagne = montantGagne;
        this.solde = solde;
    }
}
