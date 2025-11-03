// src/main/java/org/example/model/blackjack/BjTable.java
package org.example.model.blackjack;

import lombok.Data;

import java.time.Instant;
import java.util.*;

@Data
public class BjTable {
    private Long id;
    private final String code;
    private final boolean isPrivate;
    private final int maxSeats;

    private final Map<Integer, Seat> seats = new HashMap<>();
    private final Shoe shoe = new Shoe(6);

    private TablePhase phase = TablePhase.BETTING;
    private final PlayerState dealer = new PlayerState();

    private Integer currentSeatIndex = null;
    private Long phaseDeadlineEpochMs = 0L;

    private String creatorEmail;
    private String creatorDisplayName; // ✅ ajouté
    private Instant createdAt = Instant.now();

    private String  name;
    private Long    minBet;
    private Long    maxBet;

    private boolean pendingClose = false;
    private Instant lastActiveAt = Instant.now();

    public boolean isPendingClose() { return pendingClose; }
    public void setPendingClose(boolean pendingClose) { this.pendingClose = pendingClose; }

    public BjTable(Long id, int maxSeats, boolean isPrivate, String code) {
        this.id = id;
        this.maxSeats = maxSeats;
        this.isPrivate = isPrivate;
        this.code = code;
        for (int i = 0; i < maxSeats; i++) seats.put(i, new Seat());
    }

    public BjTable(int maxSeats, boolean isPrivate, String code) {
        this(null, maxSeats, isPrivate, code);
    }
}
