// src/main/java/org/example/controller/PilefaceController.java
package org.example.controller;

import org.example.dto.CoinFlipRequest;
import org.example.dto.CoinFlipResponse;
import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.service.CoinFlipService;
import org.example.service.GameHistoryService;
import org.example.service.WalletService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class PilefaceController {

    private final WalletService walletService;
    private final UtilisateurRepository utilisateurRepo;
    private final CoinFlipService coinFlipService;
    private final String adminKey;

    private final GameHistoryService historyService;

    public PilefaceController(WalletService walletService,
                              UtilisateurRepository utilisateurRepo,
                              CoinFlipService coinFlipService,
                              GameHistoryService historyService,
                              @Value("${app.admin.key:changeme}") String adminKey) {
        this.walletService = walletService;
        this.utilisateurRepo = utilisateurRepo;
        this.coinFlipService = coinFlipService;
        this.historyService = historyService;
        this.adminKey = adminKey;
    }

    @PostMapping("/coinflip")
    public ResponseEntity<?> jouerCoinFlip(@RequestBody CoinFlipRequest req, Authentication authentication) {
        if (req == null || req.choix == null || (!req.choix.equals("pile") && !req.choix.equals("face"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Choix invalide (pile|face)"));
        }
        if (req.montant <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Montant invalide (>0)"));
        }

        String email = authentication.getName();
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();

        try {
            walletService.debiter(u, req.montant);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solde insuffisant"));
        }

        String outcome = coinFlipService.tirer();
        boolean win = outcome.equals(req.choix);

        long montantGagne = 0L;
        if (win) {
            montantGagne = req.montant * 2L;
            walletService.crediter(u, montantGagne);
        }

        historyService.record(u, "coinflip", "choice=" + req.choix + ",outcome=" + outcome, req.montant, montantGagne, win ? 2 : 0);

        Wallet w = walletService.getWalletParUtilisateur(u);

        CoinFlipResponse resp = new CoinFlipResponse(outcome, win, req.montant, montantGagne, w.getSolde());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/coinflip/bias")
    public ResponseEntity<?> getBias() {
        return ResponseEntity.ok(Map.of("probPile", coinFlipService.getProbPile()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/coinflip/bias")
    public ResponseEntity<?> setBias(@RequestBody Map<String, Object> body) {
        Object p = body.get("probPile");
        if (!(p instanceof Number)) {
            return ResponseEntity.badRequest().body(Map.of("error", "probPile manquant ou invalide"));
        }
        double prob = ((Number) p).doubleValue();
        if (prob < 0.0 || prob > 1.0) {
            return ResponseEntity.badRequest().body(Map.of("error", "probPile doit être entre 0.0 et 1.0"));
        }
        coinFlipService.setProbPile(prob);
        return ResponseEntity.ok(Map.of("probPile", coinFlipService.getProbPile()));
    }
}
