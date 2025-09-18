package org.example.dto.blackjack;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class TableEvent {
    private String type;
    private Object payload;
}
