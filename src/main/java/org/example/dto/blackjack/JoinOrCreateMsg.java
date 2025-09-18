package org.example.dto.blackjack;

import lombok.Data;

@Data
public class JoinOrCreateMsg {
    private Long tableId;       // rejoindre table publique existante
    private boolean createPublic;
    private boolean createPrivate;
    private String code;        // pour private
    private Integer maxSeats;   // optionnel (default 5)
}
