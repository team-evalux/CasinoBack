// src/main/java/org/example/dto/blackjack/BjTableSummaryDTO.java
package org.example.dto.blackjack;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BjTableSummaryDTO {
    private Long id;
    private String name;
    private Integer maxSeats;
    private Long minBet;
    private Long maxBet;
    private boolean isPrivate;
    private String phase;
    private String creatorEmail;
}
