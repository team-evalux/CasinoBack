package org.example.dto.blackjack;

import lombok.Data;

@Data
public class ActionMsg {
    public enum Type { HIT, STAND, DOUBLE }
    private Long tableId;
    private Integer seatIndex;
    private Type type;
}
