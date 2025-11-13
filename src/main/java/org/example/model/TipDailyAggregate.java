package org.example.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "tip_daily_aggregate")
public class TipDailyAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long utilisateurId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Long totalReceived;

    public TipDailyAggregate(Long utilisateurId, LocalDate date, Long totalReceived) {
        this.utilisateurId = utilisateurId;
        this.date = date;
        this.totalReceived = totalReceived;
    }
}
