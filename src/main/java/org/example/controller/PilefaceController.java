package org.example.controller;

import org.example.dto.CoinFlipRequest;
import org.example.dto.CoinFlipResponse;
import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.service.CoinFlipService;
import org.example.service.WalletService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
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

    public PilefaceController(WalletService walletService,
                          UtilisateurRepository utilisateurRepo,
                          CoinFlipService coinFlipService,
                          @Value("${app.admin.key:changeme}") String adminKey) {
        this.walletService = walletService;
        this.utilisateurRepo = utilisateurRepo;
        this.coinFlipService = coinFlipService;
        this.adminKey = adminKey;
    }

    /**
     * Endpoint principal : joue une partie pile ou face.
     * Body: { "choix": "pile"|"face", "montant": 100 }
     */
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

        // débiter d'abord la mise (lance IllegalArgumentException si solde insuffisant)
        try {
            walletService.debiter(u, req.montant);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solde insuffisant"));
        }

        // tirer l'issue (pile/face) suivant la probabilité configurée
        String outcome = coinFlipService.tirer();
        boolean win = outcome.equals(req.choix);

        long montantGagne = 0L;
        if (win) {
            // payout = 2 * mise (comme demandé)
            montantGagne = req.montant * 2L;
            walletService.crediter(u, montantGagne);
        }

        // récupère l'état courant du wallet pour renvoyer solde
        Wallet w = walletService.getWalletParUtilisateur(u);

        CoinFlipResponse resp = new CoinFlipResponse(outcome, win, req.montant, montantGagne, w.getSolde());
        return ResponseEntity.ok(resp);
    }

    /**
     * GET current bias (probabilité de pile)
     */
    @GetMapping("/coinflip/bias")
    public ResponseEntity<?> getBias() {
        return ResponseEntity.ok(Map.of("probPile", coinFlipService.getProbPile()));
    }

    /**
     * SET bias (adminKey requis pour limiter l'accès)
     * Body: { "probPile": 0.6, "adminKey": "xxxx" }
     */
    @PostMapping("/coinflip/bias")
    public ResponseEntity<?> setBias(@RequestBody Map<String, Object> body) {
        Object key = body.get("adminKey");
        if (!(key instanceof String) || !key.equals(adminKey)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin key invalide"));
        }
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
