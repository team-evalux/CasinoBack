package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.AvatarDto;
import org.example.service.AvatarShopService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/avatars")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarShopService avatarShopService;

    @GetMapping
    public List<AvatarDto> listeBoutique() {
        return avatarShopService.listeBoutique().stream()
                .map(AvatarDto::fromEntity)
                .toList();
    }
}
