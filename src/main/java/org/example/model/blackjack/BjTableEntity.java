package org.example.model.blackjack;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "bj_table")
@Data
public class BjTableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    // mot de passe / code en clair (conforme à ta demande)
    @Column(name = "code")
    private String code;

    @Column(name = "max_seats")
    private Integer maxSeats;

    @Column(name = "name")
    private String name;

    @Column(name = "min_bet")
    private Long minBet;

    @Column(name = "max_bet")
    private Long maxBet;

    @Column(name = "creator_email")
    private String creatorEmail;

    @Column(name = "created_at")
    private Instant createdAt;

}

