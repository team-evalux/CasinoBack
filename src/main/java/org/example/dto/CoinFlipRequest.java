package org.example.dto;

public class CoinFlipRequest {
    public String choix; // "pile" ou "face"
    public long montant; // en crédits entiers

    public CoinFlipRequest() {}
    public CoinFlipRequest(String choix, long montant) {
        this.choix = choix;
        this.montant = montant;
    }
}
