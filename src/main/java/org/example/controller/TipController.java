package org.example.controller;

import org.example.model.Utilisateur;
import org.example.repo.UtilisateurRepository;
import org.example.repo.WalletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tip")
public class TipController {

    @Autowired
    private UtilisateurRepository utilisateurRepo;
    @Autowired
    private WalletRepository walletRepo;

    @PostMapping
    @Transactional
    public ResponseEntity<?> tip(@RequestBody Map<String, Object> body, Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).body(Map.of("error", "Non connecté"));

        String emailDonneur = auth.getName();
        String pseudoReceveur = (String) body.get("pseudo");
        int montant = ((Number) body.get("montant")).intValue();

        if (montant <= 0 || montant > 10000)
            return ResponseEntity.badRequest().body(Map.of("error", "Montant invalide"));

        Utilisateur donneur = utilisateurRepo.findByEmail(emailDonneur).orElse(null);
        Utilisateur receveur = utilisateurRepo.findByPseudo(pseudoReceveur).orElse(null);

        if (donneur == null || receveur == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Utilisateur introuvable"));

        var walletDonneur = walletRepo.findByUtilisateur(donneur).orElse(null);
        var walletReceveur = walletRepo.findByUtilisateur(receveur).orElse(null);

        if (walletDonneur == null || walletReceveur == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Erreur portefeuille"));

        if (walletDonneur.getSolde() < montant)
            return ResponseEntity.badRequest().body(Map.of("error", "Solde insuffisant"));

        walletDonneur.setSolde(walletDonneur.getSolde() - montant);
        walletReceveur.setSolde(walletReceveur.getSolde() + montant);

        walletRepo.save(walletDonneur);
        walletRepo.save(walletReceveur);

        return ResponseEntity.ok(Map.of("success", "Tip envoyé à " + pseudoReceveur));
    }
}
