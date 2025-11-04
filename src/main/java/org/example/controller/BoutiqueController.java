package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.model.*;
import org.example.service.BoutiqueService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/boutique")
@RequiredArgsConstructor
public class BoutiqueController {

    private final BoutiqueService boutiqueService;

    @GetMapping("/items")
    public List<Item> getAllItems() {
        return boutiqueService.getAllItems();
    }

    @GetMapping("/collection/{userId}")
    public List<UserItem> getUserCollection(@PathVariable Long userId) {
        return boutiqueService.getUserCollection(userId);
    }

    @PostMapping("/acheter")
    public String acheterItem(@RequestParam Long userId, @RequestParam Long itemId) {
        return boutiqueService.acheterItem(userId, itemId);
    }

    @PostMapping("/avatar")
    public void setAvatar(@RequestParam Long userId, @RequestParam Long userItemId) {
        boutiqueService.setAvatar(userId, userItemId);
    }

    @GetMapping("/avatar/{userId}")
    public UserItem getAvatarActif(@PathVariable Long userId) {
        return boutiqueService.getAvatarActif(userId);
    }
}
