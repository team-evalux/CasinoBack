package org.example.dto;

import lombok.*;
import org.example.model.Avatar;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvatarDto {
    private Long id;
    private String code;
    private String nom;
    private String rarete;   // "COMMUN" | "RARE" | "EPIQUE" | "LEGENDAIRE"
    private Long prix;
    private String imageUrl;
    private boolean defaut;

    public static AvatarDto fromEntity(Avatar avatar) {
        if (avatar == null) return null;
        return AvatarDto.builder()
                .id(avatar.getId())
                .code(avatar.getCode())
                .nom(avatar.getNom())
                .rarete(avatar.getRarete().name())
                .prix(avatar.getPrix())
                .imageUrl(avatar.getImageUrl())
                .defaut(avatar.isDefaut())
                .build();
    }
}
