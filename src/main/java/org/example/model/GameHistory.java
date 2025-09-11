// src/main/java/org/example/model/GameHistory.java
package org.example.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_history")
public class GameHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(nullable = false, length = 80)
    private String game;

    @Column(length = 512)
    private String outcome;

    @Column(nullable = false)
    private long montantJoue;

    @Column(nullable = false)
    private long montantGagne;

    private Integer multiplier;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public GameHistory() {}

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Utilisateur getUtilisateur() {
        return utilisateur;
    }

    public void setUtilisateur(Utilisateur utilisateur) {
        this.utilisateur = utilisateur;
    }

    public String getGame() {
        return game;
    }

    public void setGame(String game) {
        this.game = game;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public long getMontantJoue() {
        return montantJoue;
    }

    public void setMontantJoue(long montantJoue) {
        this.montantJoue = montantJoue;
    }

    public long getMontantGagne() {
        return montantGagne;
    }

    public void setMontantGagne(long montantGagne) {
        this.montantGagne = montantGagne;
    }

    public Integer getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(Integer multiplier) {
        this.multiplier = multiplier;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
