package org.example.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BonusStatusDTO {
    private boolean canClaim;          // bonus disponible ?
    private Long lastClaimEpochMs;     // null si jamais réclamé
    private long nextResetEpochMs;     // prochain reset (ms epoch)
    private long serverNowEpochMs;     // horloge serveur (ms epoch)
    private long amount;               // montant du bonus
    private Long solde;                // optionnel (rempli après claim)
}
