package org.example.controller;

import org.example.model.Utilisateur;
import org.example.repo.UtilisateurRepository;
import org.example.service.GameHistoryService;
import org.example.service.UtilisateurService;
import org.example.service.WalletService;
import org.example.service.WalletSseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class UserController {

    @Autowired
    private UtilisateurRepository utilisateurRepo;

    @Autowired
    private WalletSseService walletSseService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private GameHistoryService historyService;

    /**
     * Supprime l'utilisateur connecté après confirmation d'e-mail.
     * Le front a déjà supprimé wallet + historique, mais on sécurise en les re-supprimant côté serveur.
     */
    @DeleteMapping("/me")
    @Transactional
    public ResponseEntity<?> supprimerMonCompte(@RequestBody Map<String, String> body,
                                                Authentication authentication) {
        String emailAuth = authentication.getName();
        String emailConfirm = body != null ? body.get("emailConfirm") : null;
        if (emailConfirm == null || !emailAuth.equalsIgnoreCase(emailConfirm)) {
            return ResponseEntity.status(403).body(Map.of("error", "Email de confirmation invalide"));
        }

        Utilisateur u = utilisateurRepo.findByEmail(emailAuth).orElseThrow();
        System.out.println("1");

        // 1) historique
        historyService.deleteAllForUser(u);
        System.out.println("2");
        // 2) wallet
        walletSseService.complete(u.getEmail());

        walletService.supprimerWallet(u);
        System.out.println("3");
        // 3) utilisateur
        utilisateurRepo.delete(u);
        System.out.println("4");
        // 204 No Content
        return ResponseEntity.noContent().build();
    }
}
