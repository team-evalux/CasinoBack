package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.EquippedAvatarDto;
import org.example.dto.InventoryAvatarDto;
import org.example.model.UtilisateurAvatar;
import org.example.service.AvatarShopService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final AvatarShopService avatarShopService;

    @GetMapping("/avatars")
    public List<InventoryAvatarDto> mesAvatars(Authentication authentication) {
        String email = authentication.getName();
        return avatarShopService.inventaire(email).stream()
                .map(InventoryAvatarDto::fromEntity)
                .toList();
    }

    @GetMapping("/avatars/equipped")
    public ResponseEntity<?> avatarEquipe(Authentication authentication) {
        String email = authentication.getName();
        UtilisateurAvatar ua = avatarShopService.avatarEquipe(email);
        return ResponseEntity.ok(EquippedAvatarDto.fromEntity(ua));
    }

    @PostMapping("/avatars/{id}/buy")
    public ResponseEntity<?> acheter(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        try {
            UtilisateurAvatar ua = avatarShopService.acheter(email, id);
            return ResponseEntity.ok(InventoryAvatarDto.fromEntity(ua));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/avatars/{id}/equip")
    public ResponseEntity<?> equiper(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        try {
            UtilisateurAvatar ua = avatarShopService.equiper(email, id);
            return ResponseEntity.ok(InventoryAvatarDto.fromEntity(ua));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
