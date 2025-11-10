package org.example.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "utilisateur_avatar",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"utilisateur_id", "avatar_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilisateurAvatar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @ManyToOne(optional = false)
    @JoinColumn(name = "avatar_id", nullable = false)
    private Avatar avatar;

    // True = avatar actuellement équipé par cet utilisateur
    @Column(nullable = false)
    private boolean equipe = false;

    @Column(nullable = false)
    private LocalDateTime dateAcquisition;
}
