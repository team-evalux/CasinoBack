package org.example.controller;

import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.model.TipDailyAggregate;
import org.example.repo.UtilisateurRepository;
import org.example.repo.WalletRepository;
import org.example.repo.TipDailyAggregateRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/tip")
public class TipController {

    private static final long DAILY_LIMIT = 10_000;

    @Autowired private UtilisateurRepository utilisateurRepo;
    @Autowired private WalletRepository walletRepo;
    @Autowired private TipDailyAggregateRepository tipRepo;

    /** Retourne combien un utilisateur peut encore recevoir aujourd’hui */
    @GetMapping("/max-receivable")
    public ResponseEntity<?> maxReceivable(@RequestParam String pseudo) {

        Utilisateur u = utilisateurRepo.findByPseudo(pseudo).orElse(null);
        if (u == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Utilisateur introuvable"));

        TipDailyAggregate agg = tipRepo.findByUtilisateurIdAndDate(u.getId(), LocalDate.now())
                .orElse(new TipDailyAggregate(u.getId(), LocalDate.now(), 0L));

        long remaining = Math.max(0, DAILY_LIMIT - agg.getTotalReceived());

        return ResponseEntity.ok(Map.of("maxReceivable", remaining));
    }


    /** Envoi d’un tip */
    @PostMapping
    @Transactional
    public ResponseEntity<?> tip(@RequestBody Map<String, Object> body, Authentication auth) {

        if (auth == null)
            return ResponseEntity.status(401).body(Map.of("error", "Non connecté"));

        String emailDonneur = auth.getName();
        String pseudoReceveur = (String) body.get("pseudo");
        int montant = ((Number) body.get("montant")).intValue();

        if (montant <= 0)
            return ResponseEntity.badRequest().body(Map.of("error", "Montant invalide"));

        Utilisateur donneur = utilisateurRepo.findByEmail(emailDonneur).orElse(null);
        Utilisateur receveur = utilisateurRepo.findByPseudo(pseudoReceveur).orElse(null);

        if (donneur == null || receveur == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Utilisateur introuvable"));

        // Interdiction auto-tip
        if (donneur.getId().equals(receveur.getId()))
            return ResponseEntity.badRequest().body(Map.of("error", "Impossible de s’auto-tipper"));

        Wallet wDonneur = walletRepo.findByUtilisateur(donneur).orElse(null);
        Wallet wReceveur = walletRepo.findByUtilisateur(receveur).orElse(null);

        if (wDonneur == null || wReceveur == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Erreur portefeuille"));

        if (wDonneur.getSolde() < montant)
            return ResponseEntity.badRequest().body(Map.of("error", "Solde insuffisant"));

        // Vérification du quota journée
        TipDailyAggregate agg = tipRepo.findByUtilisateurIdAndDate(receveur.getId(), LocalDate.now())
                .orElse(new TipDailyAggregate(receveur.getId(), LocalDate.now(), 0L));

        long remaining = DAILY_LIMIT - agg.getTotalReceived();

        if (remaining <= 0)
            return ResponseEntity.badRequest().body(Map.of("error", "Cet utilisateur a atteint la limite journalière"));

        if (montant > remaining)
            return ResponseEntity.badRequest().body(Map.of("error", "Ce joueur ne peut recevoir que " + remaining + " crédits aujourd’hui"));

        // appliquer la transaction
        wDonneur.setSolde(wDonneur.getSolde() - montant);
        wReceveur.setSolde(wReceveur.getSolde() + montant);
        walletRepo.save(wDonneur);
        walletRepo.save(wReceveur);

        // mise à jour aggregate
        agg.setTotalReceived(agg.getTotalReceived() + montant);
        tipRepo.save(agg);

        return ResponseEntity.ok(Map.of("success", "Tip envoyé à " + pseudoReceveur));
    }
}
