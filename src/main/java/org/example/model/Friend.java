package org.example.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Entity
@Table(name = "friends")
@Data
public class Friend {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ownerEmail;   // celui qui a la liste

    @Column(nullable = false)
    private String friendEmail;  // l’ami ajouté

    private boolean online;
    private Instant lastSeen;
}
