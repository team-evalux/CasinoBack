package org.example.dto.blackjack;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class BetMsg {
    @NotNull
    private Long tableId;
    private Integer seatIndex; // ignoré côté serveur, on garde pour compat
    @Positive(message="La mise doit être > 0")
    private long amount;
}
