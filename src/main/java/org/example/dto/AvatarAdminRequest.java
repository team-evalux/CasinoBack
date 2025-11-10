package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AvatarAdminRequest {
    private String code;
    private String nom;
    private String rarete;   // "COMMUN" | "RARE" | "EPIQUE" | "LEGENDAIRE"
    private Long prix;
    private String imageUrl;
    private boolean actif = true;
    private boolean defaut = false;
}
