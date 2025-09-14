package org.example.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "utilisateur")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Utilisateur {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String pseudo;

    @Column(nullable = false)
    private String motDePasseHash;

    private LocalDateTime dateCreation = LocalDateTime.now();

    private boolean active = true;

    private Instant lastBonusClaim;

    // rôle : "USER" ou "ADMIN"
    @Column(nullable = false)
    private String role = "USER";
}
