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

@RestController
@RequestMapping("/api/wallet")
public class WalletController {
    @Autowired
    private WalletService walletService;
    @Autowired
    private UtilisateurRepository utilisateurRepo;
    @Autowired
    private WalletSseService walletSseService;

    // obtenir solde (utilisateur connecté)
    @GetMapping("/me")
    public ResponseEntity<?> solde(Authentication authentication){
        String email = authentication.getName();
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
        Wallet w = walletService.getWalletParUtilisateur(u);
        return ResponseEntity.ok(w);
    }

    @PostMapping("/credit")
    public ResponseEntity<?> crediter(@RequestBody Map<String, Long> body, Authentication authentication){
        long montant = body.getOrDefault("montant", 0L);
        String email = authentication.getName();
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
        Wallet w = walletService.crediter(u, montant);
        return ResponseEntity.ok(w);
    }

    @PostMapping("/debit")
    public ResponseEntity<?> debiter(@RequestBody Map<String, Long> body, Authentication authentication){
        long montant = body.getOrDefault("montant", 0L);
        String email = authentication.getName();
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
        try {
            Wallet w = walletService.debiter(u, montant);
            return ResponseEntity.ok(w);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // SSE stream pour le wallet de l'utilisateur (token peut être passé en header ou en query param)
    @GetMapping("/stream")
    public SseEmitter stream(Authentication authentication) {
        String email = authentication.getName();
        return walletSseService.register(email);
    }
}
