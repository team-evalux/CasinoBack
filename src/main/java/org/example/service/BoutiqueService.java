package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.model.*;
import org.example.repo.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BoutiqueService {

    private final ItemRepository itemRepo;
    private final UserItemRepository userItemRepo;
    private final UtilisateurRepository utilisateurRepo;
    private final WalletService walletService;

    public List<Item> getAllItems() {
        return itemRepo.findAll();
    }

    public List<UserItem> getUserCollection(Long userId) {
        return userItemRepo.findByUserId(userId);
    }

    public String acheterItem(Long userId, Long itemId) {
        Utilisateur user = utilisateurRepo.findById(userId).orElseThrow();
        Item item = itemRepo.findById(itemId).orElseThrow();

        if (walletService.getBalance(userId) < item.getPrix()) {
            throw new IllegalArgumentException("Solde insuffisant !");
        }

        walletService.debiter(user, item.getPrix());

        UserItem userItem = new UserItem();
        userItem.setUser(user);
        userItem.setItem(item);
        userItem.setActif(false);
        userItemRepo.save(userItem);

        return "Achat réussi : " + item.getNom();
    }

    public void setAvatar(Long userId, Long userItemId) {
        List<UserItem> items = userItemRepo.findByUserId(userId);
        for (UserItem ui : items) {
            ui.setActif(ui.getId().equals(userItemId));
        }
        userItemRepo.saveAll(items);
    }

    public UserItem getAvatarActif(Long userId) {
        return userItemRepo.findByUserId(userId)
                .stream()
                .filter(UserItem::isActif)
                .findFirst()
                .orElse(null);
    }
}
