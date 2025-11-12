package org.example.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaderboardEntryDto {
    /** Rang 1..N attribué côté service */
    private int rang;

    /** Pseudo public de l’utilisateur */
    private String pseudo;

    /** Solde (crédits) */
    private long solde;
}
