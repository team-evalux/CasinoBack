package org.example.dto.blackjack;

import lombok.Data;

@Data
public class JoinOrCreateMsg {
    private Long tableId;
    private String code;

    // flags optionnels pour création via WS
    private Boolean createPublic;
    private Boolean createPrivate;

    private Integer maxSeats;
    private String name;
    private Long minBet;
    private Long maxBet;
}
