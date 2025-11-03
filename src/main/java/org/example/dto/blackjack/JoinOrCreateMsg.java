package org.example.dto.blackjack;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class JoinOrCreateMsg {
    private Long tableId;

    @Size(max=32) // évite codes trop longs
    private String code;

    private Boolean createPublic;
    private Boolean createPrivate;

    @Min(2) @Max(7)
    private Integer maxSeats;

    @Size(max=20)
    private String name;

    @Min(0) private Long minBet;
    @Min(0) private Long maxBet;
}
