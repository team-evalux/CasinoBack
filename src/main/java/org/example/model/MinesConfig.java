package org.example.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class MinesConfig {

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // nombre de mines pour lequel s'applique cette config (1..24)
    @Column(name = "mines_count", nullable = false, unique = true)
    private Integer minesCount;

    // JSON: { "1": 1.03, "2": 1.08, ... } => nb de cases sûres révélées -> multiplicateur
    @Lob
    @Column(name = "multipliers_json", columnDefinition = "text")
    private String multipliersJson;

    // marge de la maison (ex: 0.98) appliquée aux multiplicateurs "fair"
    @Column(name = "house_edge", nullable = false)
    private Double houseEdge;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


}
