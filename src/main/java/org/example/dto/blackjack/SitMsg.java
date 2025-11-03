package org.example.dto.blackjack;

import lombok.Data;

/**
 * @deprecated Plus utilisée (JOIN = auto-seat). Conservée pour compat WS.
 */
@Deprecated
@Data
public class SitMsg {
    private Long tableId;
    private Integer seatIndex;
    private String code;
}
