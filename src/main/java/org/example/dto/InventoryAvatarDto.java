package org.example.dto;

import lombok.*;
import org.example.model.UtilisateurAvatar;

import java.time.format.DateTimeFormatter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAvatarDto {

    private Long id;               // id UtilisateurAvatar
    private Long avatarId;
    private String code;
    private String nom;
    private String rarete;
    private String imageUrl;
    private boolean equipe;
    private String dateAcquisition; // ISO string côté front

    public static InventoryAvatarDto fromEntity(UtilisateurAvatar ua) {
        if (ua == null) return null;
        return InventoryAvatarDto.builder()
                .id(ua.getId())
                .avatarId(ua.getAvatar().getId())
                .code(ua.getAvatar().getCode())
                .nom(ua.getAvatar().getNom())
                .rarete(ua.getAvatar().getRarete().name())
                .imageUrl(ua.getAvatar().getImageUrl())
                .equipe(ua.isEquipe())
                .dateAcquisition(
                        ua.getDateAcquisition() != null
                                ? ua.getDateAcquisition().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                : null
                )
                .build();
    }
}
