// src/main/java/org/example/model/blackjack/BjTable.java
package org.example.model.blackjack;

import lombok.Data;

import java.time.Instant;
import java.util.*;

@Data
public class BjTable {
    private Long id; // sera défini par la BD lors de la création persistée
    private final String code; // null si publique
    private final boolean isPrivate;
    private final int maxSeats;

    private final Map<Integer, Seat> seats = new HashMap<>();
    private final Shoe shoe = new Shoe(6);

    private TablePhase phase = TablePhase.BETTING;

    // dealer
    private final PlayerState dealer = new PlayerState();

    // jeu courant
    private Integer currentSeatIndex = null;
    private Long phaseDeadlineEpochMs = 0L;

    // nouveau : qui a créé la table (peut être null)
    private String creatorEmail;
    private Instant createdAt = Instant.now();

    private String  name;
    private Long    minBet;
    private Long    maxBet;

    // constructeur : id peut être null (on récupère l'id DB si présent)
    public BjTable(Long id, int maxSeats, boolean isPrivate, String code) {
        this.id = id;
        this.maxSeats = maxSeats;
        this.isPrivate = isPrivate;
        this.code = code;
        for (int i = 0; i < maxSeats; i++) seats.put(i, new Seat());
    }

    // fallback si on veut construire sans id (compatibilité)
    public BjTable(int maxSeats, boolean isPrivate, String code) {
        this(null, maxSeats, isPrivate, code);
    }
}
