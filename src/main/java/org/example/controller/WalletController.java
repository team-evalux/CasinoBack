package org.example.controller;

import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {
    @Autowired
    private WalletService walletService;
    @Autowired
    private UtilisateurRepository utilisateurRepo;

    // obtenir solde (utilisateur connecté)
    @GetMapping("/me")
    public ResponseEntity<?> solde(Authentication authentication){
        String email = authentication.getName();
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
        Wallet w = walletService.getWalletParUtilisateur(u);
        return ResponseEntity.ok(w);
    }
}

