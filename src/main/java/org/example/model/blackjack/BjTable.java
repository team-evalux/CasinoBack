package org.example.model.blackjack;

import lombok.Data;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class BjTable {
    private static final AtomicLong SEQ = new AtomicLong(1);

    private final Long id = SEQ.getAndIncrement();
    private final String code; // null si publique
    private final boolean isPrivate;
    private final int maxSeats;

    private final Map<Integer, Seat> seats = new HashMap<>();
    private final Shoe shoe = new Shoe(6);

    private TablePhase phase = TablePhase.WAITING_FOR_PLAYERS;

    // dealer
    private final PlayerState dealer = new PlayerState();

    // jeu courant
    private Integer currentSeatIndex = null;
    private long phaseDeadlineEpochMs = 0L;

    public BjTable(int maxSeats, boolean isPrivate, String code) {
        this.maxSeats = maxSeats;
        this.isPrivate = isPrivate;
        this.code = code;
        for (int i=0;i<maxSeats;i++) seats.put(i, new Seat());
    }
}
