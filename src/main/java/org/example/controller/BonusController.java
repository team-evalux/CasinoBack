// src/main/java/org/example/controller/BonusController.java
package org.example.controller;

import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.service.BonusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bonus")
public class BonusController {

    @Autowired
    private BonusService bonusService;

    @Autowired
    private UtilisateurRepository utilisateurRepo;

    @PostMapping("/claim")
    public ResponseEntity<?> claim(Authentication authentication) {
        String email = authentication.getName();
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
        try {
            Wallet w = bonusService.claimDailyBonus(u);
            return ResponseEntity.ok(Map.of(
                    "amount", 1000,
                    "solde", w.getSolde()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
