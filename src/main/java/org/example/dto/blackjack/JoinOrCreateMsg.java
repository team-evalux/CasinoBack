package org.example.dto.blackjack;

import lombok.Data;

@Data
public class JoinOrCreateMsg {
    private Long tableId;
    private Boolean createPublic;   // use wrapper Boolean so client can omit
    private Boolean createPrivate;
    private Integer maxSeats;
    private String code;
    private String name;
    private Long minBet;
    private Long maxBet;
}
