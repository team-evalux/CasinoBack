package org.example.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nom;
    private String imagePath; // chemin relatif (ex: assets/images/avatars/avatar1.png)
    private int prix;

    @Enumerated(EnumType.STRING)
    private Rarete rarete;
}
