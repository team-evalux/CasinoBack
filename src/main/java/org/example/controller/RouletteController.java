package org.example.controller;

import org.example.dto.RouletteBetRequest;
import org.example.dto.RouletteBetResponse;
import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.service.RouletteService;
import org.example.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/roulette")
    public ResponseEntity<?> jouerRoulette(@RequestBody RouletteBetRequest req, Authentication authentication) {
        if (req == null || req.betType == null || req.betValue == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Paramètres invalides"));
        }
        if (req.montant <= 0) return ResponseEntity.badRequest().body(Map.of("error","Montant invalide"));

        String email = authentication.getName();
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();

        // 1) débiter la mise
        try {
            walletService.debiter(u, req.montant);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solde insuffisant"));
        }

        // 2) tirer numéro
        int result = rouletteService.tirerNumero();
        String color = rouletteService.couleurPour(result);

        // 3) test gagnant
        boolean win = rouletteService.estGagnant(req.betType, req.betValue, result);
        long montantGagne = 0L;
        if (win) {
            long mult = rouletteService.payoutMultiplier(req.betType);
            montantGagne = req.montant * mult; // retour total
            walletService.crediter(u, montantGagne);
        }

        Wallet w = walletService.getWalletParUtilisateur(u);
        RouletteBetResponse resp = new RouletteBetResponse(result, color, win, req.montant, montantGagne, w.getSolde());
        return ResponseEntity.ok(resp);
    }
}
