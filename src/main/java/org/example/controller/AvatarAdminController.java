package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.AvatarAdminRequest;
import org.example.dto.AvatarDto;
import org.example.model.Avatar;
import org.example.service.AvatarShopService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/avatars")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AvatarAdminController {

    private final AvatarShopService avatarShopService;

    @GetMapping
    public List<AvatarDto> listAll() {
        return avatarShopService.listAllForAdmin().stream()
                .map(AvatarDto::fromEntity)
                .toList();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody AvatarAdminRequest req) {
        try {
            Avatar avatar = avatarShopService.createAvatar(req);
            return ResponseEntity.ok(AvatarDto.fromEntity(avatar));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody AvatarAdminRequest req) {
        try {
            Avatar avatar = avatarShopService.updateAvatar(id, req);
            return ResponseEntity.ok(AvatarDto.fromEntity(avatar));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> disable(@PathVariable Long id) {
        try {
            avatarShopService.disableAvatar(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<?> setActive(@PathVariable Long id,
                                       @RequestBody Map<String, Boolean> body) {
        Boolean actif = body.get("actif");
        if (actif == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Champ 'actif' manquant"));
        }
        try {
            var avatar = avatarShopService.setAvatarActive(id, actif);
            return ResponseEntity.ok(AvatarDto.fromEntity(avatar));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


}
