// src/main/java/org/example/model/GameHistoryAggregate.java
package org.example.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_history_aggregate",
        uniqueConstraints = @UniqueConstraint(columnNames = {"utilisateur_id", "game"}),
        indexes = {
                @Index(name = "gha_user_idx", columnList = "utilisateur_id"),
                @Index(name = "gha_game_idx", columnList = "game")
        })
public class GameHistoryAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "utilisateur_id", nullable = false)
    private Long utilisateurId;

    @Column(nullable = false, length = 40)
    private String game;

    // JSON compact (array of entries), stocké en TEXT / LOB
    @Lob
    @Column(name = "entries_json", columnDefinition = "text")
    private String entriesJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public GameHistoryAggregate() {}

    public Long getId() { return id; }
    public Long getUtilisateurId() { return utilisateurId; }
    public void setUtilisateurId(Long utilisateurId) { this.utilisateurId = utilisateurId; }
    public String getGame() { return game; }
    public void setGame(String game) { this.game = game; }
    public String getEntriesJson() { return entriesJson; }
    public void setEntriesJson(String entriesJson) { this.entriesJson = entriesJson; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
