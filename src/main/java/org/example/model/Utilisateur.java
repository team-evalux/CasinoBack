package org.example.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity // Indique que cette classe est une entité JPA (table dans la base de données)
@Table(name = "utilisateur") // Nom de la table associée
@Getter // Lombok génère automatiquement les getters
@Setter // Lombok génère automatiquement les setters
@NoArgsConstructor // Lombok génère un constructeur vide
@AllArgsConstructor // Lombok génère un constructeur avec tous les champs
@Builder // Lombok permet d'utiliser le pattern Builder pour créer l'objet
public class Utilisateur {
    @Id // Clé primaire
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-incrément dans la BDD
    private Long id;

    @Column(unique = true, nullable = false) // Email unique et obligatoire
    private String email;

    @Column(nullable = false) // Le pseudo est obligatoire
    private String pseudo;

    @Column(nullable = false) // Stockage du mot de passe haché (jamais en clair)
    private String motDePasseHash;

    // Date de création de l’utilisateur (par défaut = maintenant)
    private LocalDateTime dateCreation = LocalDateTime.now();

    // Statut de l’utilisateur (actif ou désactivé)
    private boolean active = true;

    // Dernière fois où le joueur a réclamé son bonus quotidien (Instant précis)
    private Instant lastBonusClaim;

    // Rôle de l’utilisateur : USER par défaut, peut être ADMIN
    @Column(nullable = false)
    private String role = "USER";
}
