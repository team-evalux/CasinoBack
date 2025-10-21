// src/main/java/org/example/controller/SlotController.java
package org.example.controller;

import org.example.dto.SlotConfigRequest;
import org.example.dto.SlotConfigResponse;
import org.example.dto.SlotPlayRequest;
import org.example.dto.SlotPlayResponse;
import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.service.SlotService;
import org.example.service.WalletService;
import org.example.service.GameHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/game/slots")
public class SlotController {

    @Autowired
    private WalletService walletService;
    @Autowired
    private UtilisateurRepository utilisateurRepo;
    @Autowired
    private SlotService slotService;
    @Autowired
    private GameHistoryService historyService;

    @PostMapping("/play")
    @Transactional
    public ResponseEntity<?> play(@RequestBody SlotPlayRequest req, Authentication authentication) {
        if (req == null || req.montant <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Montant invalide"));
        }
        // validate requested reelsCount (avoid abus) : autoriser 1..10 par exemple
        Integer requestedReels = req.reelsCount;
        if (requestedReels != null) {
            if (requestedReels <= 0 || requestedReels > 10) {
                return ResponseEntity.badRequest().body(Map.of("error", "Nombre de rouleaux demandé invalide (1..10)"));
            }
        }

        String email = authentication.getName();
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();

        try {
            walletService.debiter(u, req.montant);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solde insuffisant"));
        }

        // spin en utilisant la valeur demandée sans sauvegarder la config globale
        List<String> reels = slotService.spinForReels(requestedReels);
        long payout = slotService.computePayout(reels, req.montant);
        boolean win = payout > 0L;
        if (win) walletService.crediter(u, payout);

        Integer mult = 0;
        if (payout > 0 && req.montant > 0) mult = (int)(payout / req.montant);

        historyService.record(u, "slots", String.join(",", reels) + (requestedReels != null ? ("|r:"+requestedReels) : ""), req.montant, payout, mult);

        Wallet w = walletService.getWalletParUtilisateur(u);
        SlotPlayResponse resp = new SlotPlayResponse(reels, req.montant, payout, win, w.getSolde());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/config")
    public ResponseEntity<?> config() {
        SlotConfigResponse resp = new SlotConfigResponse(
                slotService.getSymbols(),
                slotService.getReelWeights(),
                slotService.getReelsCount(),
                slotService.getPayouts()
        );
        return ResponseEntity.ok(resp);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody SlotConfigRequest req) {
        if (req == null) return ResponseEntity.badRequest().body(Map.of("error", "body manquant"));
        if (req.symbols == null || req.symbols.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "symbols manquant"));
        int reelsCount = (req.reelsCount != null && req.reelsCount > 0) ? req.reelsCount : slotService.getReelsCount();

        slotService.updateConfig(req.symbols, req.reelWeights, reelsCount, req.payouts);

        SlotConfigResponse resp = new SlotConfigResponse(
                slotService.getSymbols(),
                slotService.getReelWeights(),
                slotService.getReelsCount(),
                slotService.getPayouts()
        );
        return ResponseEntity.ok(resp);
    }
}
