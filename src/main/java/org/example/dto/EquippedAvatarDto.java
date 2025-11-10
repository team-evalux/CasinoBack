package org.example.dto;

import lombok.*;
import org.example.model.UtilisateurAvatar;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquippedAvatarDto {
    private Long avatarId;
    private String code;
    private String nom;
    private String rarete;
    private String imageUrl;

    public static EquippedAvatarDto fromEntity(UtilisateurAvatar ua) {
        if (ua == null) return null;
        return EquippedAvatarDto.builder()
                .avatarId(ua.getAvatar().getId())
                .code(ua.getAvatar().getCode())
                .nom(ua.getAvatar().getNom())
                .rarete(ua.getAvatar().getRarete().name())
                .imageUrl(ua.getAvatar().getImageUrl())
                .build();
    }
}
