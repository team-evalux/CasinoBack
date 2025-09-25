package org.example.dto.blackjack;

import lombok.Data;

@Data
public class SitMsg {
    private Long tableId;
    private Integer seatIndex;
    private String code;
}
