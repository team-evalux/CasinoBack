package org.example.controller;

import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.service.WalletService;
import org.example.service.WalletSseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController // Déclare ce contrôleur comme une API REST (les méthodes renvoient JSON par défaut)
@RequestMapping("/api/wallet") // Toutes les routes de ce contrôleur commencent par /api/wallet
public class WalletController {

    @Autowired
    private WalletService walletService; // Service qui gère la logique métier du portefeuille

    @Autowired
    private UtilisateurRepository utilisateurRepo; // Pour retrouver l’utilisateur dans la DB

    @Autowired
    private WalletSseService walletSseService; // Service qui gère la communication SSE (push events)

    // --------------------------------------------------------------------
    // 📌 Endpoint : GET /api/wallet/me
    // Récupère le solde du portefeuille de l’utilisateur connecté
    @GetMapping("/me")
    public ResponseEntity<?> solde(Authentication authentication){
        String email = authentication.getName(); // Récupère l’email depuis le token JWT
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow(); // cherche l’utilisateur en DB
        Wallet w = walletService.getWalletParUtilisateur(u); // récupère ou crée le wallet
        return ResponseEntity.ok(w); // renvoie le wallet (JSON)
    }

    // --------------------------------------------------------------------
    // 📌 Endpoint : POST /api/wallet/credit
    // Crédite le wallet de l’utilisateur
    @PostMapping("/credit")
    public ResponseEntity<?> crediter(@RequestBody Map<String, Long> body, Authentication authentication){
        long montant = body.getOrDefault("montant", 0L); // récupère "montant" du JSON
        String email = authentication.getName(); // récupère l’email du user connecté
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow(); // utilisateur DB
        Wallet w = walletService.crediter(u, montant); // crédite le wallet
        return ResponseEntity.ok(w); // renvoie le nouveau solde
    }

    // --------------------------------------------------------------------
    // 📌 Endpoint : POST /api/wallet/debit
    // Débite le wallet si solde suffisant
    @PostMapping("/debit")
    public ResponseEntity<?> debiter(@RequestBody Map<String, Long> body, Authentication authentication){
        long montant = body.getOrDefault("montant", 0L); // récupère "montant"
        String email = authentication.getName();
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
        try {
            Wallet w = walletService.debiter(u, montant); // essaie de débiter
            return ResponseEntity.ok(w); // succès
        } catch (IllegalArgumentException e) {
            // si solde insuffisant ou autre erreur → 400 avec message
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // --------------------------------------------------------------------
    // 📌 Endpoint : GET /api/wallet/stream
    // Ouvre une connexion SSE pour envoyer des mises à jour de solde en temps réel
    @GetMapping("/stream")
    public SseEmitter stream(Authentication authentication) {
        String email = authentication.getName(); // récupère l’utilisateur connecté
        return walletSseService.register(email); // enregistre l’émetteur SSE
    }
}
