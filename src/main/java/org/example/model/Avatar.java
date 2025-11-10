package org.example.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "avatar")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Avatar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identifiant stable côté front
    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AvatarRarity rarete;

    // Prix en crédits (utilise le wallet)
    @Column(nullable = false)
    private Long prix;

    // Actif = visible en boutique
    @Column(nullable = false)
    private boolean actif = true;

    // Pour l’affichage
    private String imageUrl;

    // Permet de marquer un avatar par défaut donné à l’inscription
    @Column(nullable = false)
    private boolean defaut = false;
}
