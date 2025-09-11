// src/main/java/org/example/controller/RouletteController.java
package org.example.controller;

import org.example.dto.RouletteBetRequest;
import org.example.dto.RouletteBetResponse;
import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.service.RouletteService;
import org.example.service.WalletService;
import org.example.service.GameHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class RouletteController {

    @Autowired
    private WalletService walletService;
    @Autowired
    private UtilisateurRepository utilisateurRepo;
    @Autowired
    private RouletteService rouletteService;
    @Autowired
    private GameHistoryService historyService;

    @PostMapping("/roulette/probabilities")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> setProbabilities(@RequestBody Map<Integer, Integer> weights) {
        rouletteService.setCustomWeights(weights);
        return ResponseEntity.ok(Map.of("success", true, "weights", rouletteService.getCustomWeights()));
    }

    @GetMapping("/roulette/probabilities")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getProbabilities() {
        Map<Integer, Integer> w = rouletteService.getCustomWeights();
        if (w == null) return ResponseEntity.ok(Map.of("weights", null));
        return ResponseEntity.ok(Map.of("weights", w));
    }

    @DeleteMapping("/roulette/probabilities")
    public ResponseEntity<?> resetProbabilities() {
        rouletteService.resetWeights();
        return ResponseEntity.ok(Map.of("success", true, "message", "Probabilités réinitialisées"));
    }

    @PostMapping("/roulette")
    public ResponseEntity<?> jouerRoulette(@RequestBody RouletteBetRequest req, Authentication authentication) {
        if (req == null || req.betType == null || req.betValue == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Paramètres invalides"));
        }
        if (req.montant <= 0) return ResponseEntity.badRequest().body(Map.of("error","Montant invalide"));

        String email = authentication.getName();
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();

        try {
            walletService.debiter(u, req.montant);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solde insuffisant"));
        }

        int result = rouletteService.tirerNumero();
        String color = rouletteService.couleurPour(result);

        boolean win = rouletteService.estGagnant(req.betType, req.betValue, result);
        long montantGagne = 0L;
        Integer mult = 0;
        if (win) {
            long m = rouletteService.payoutMultiplier(req.betType);
            montantGagne = req.montant * m;
            walletService.crediter(u, montantGagne);
            mult = (int) m;
        }

        historyService.record(u, "roulette", "number=" + result + ",color=" + color, req.montant, montantGagne, mult);

        Wallet w = walletService.getWalletParUtilisateur(u);
        RouletteBetResponse resp = new RouletteBetResponse(result, color, win, req.montant, montantGagne, w.getSolde());
        return ResponseEntity.ok(resp);
    }
}
