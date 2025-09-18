package org.example.dto.blackjack;

import lombok.Data;

@Data
public class BetMsg {
    private Long tableId;
    private Integer seatIndex;
    private long amount;
}
