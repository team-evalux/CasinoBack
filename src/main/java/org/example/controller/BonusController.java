package org.example.controller;

import org.example.dto.BonusStatusDTO;
import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.service.BonusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bonus")
public class BonusController {

    @Autowired
    private BonusService bonusService;

    @Autowired
    private UtilisateurRepository utilisateurRepo;

    @GetMapping("/status")
    public ResponseEntity<BonusStatusDTO> status(Authentication authentication) {
        String email = authentication.getName();
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
        return ResponseEntity.ok(bonusService.getStatus(u));
    }

    @PostMapping("/claim")
    public ResponseEntity<?> claim(Authentication authentication) {
        String email = authentication.getName();
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
        try {
            Wallet w = bonusService.claimDailyBonus(u);
            BonusStatusDTO dto = bonusService.getStatus(u);
            dto.setSolde(w.getSolde()); // on renvoie aussi le solde mis à jour
            return ResponseEntity.ok(dto);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }
}
