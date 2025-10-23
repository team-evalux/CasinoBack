package org.example.dto;

public class SlotPlayRequest {
    public long montant; // mise en crédits
    public Integer reelsCount; // facultatif : nombre de rouleaux demandé par le joueur

    public SlotPlayRequest() {}
    public SlotPlayRequest(long montant) { this.montant = montant; }
    public SlotPlayRequest(long montant, Integer reelsCount) { this.montant = montant; this.reelsCount = reelsCount; }
}
