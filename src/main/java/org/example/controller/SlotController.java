package org.example.controller;

import org.example.dto.SlotConfigRequest;
import org.example.dto.SlotConfigResponse;
import org.example.dto.SlotPlayRequest;
import org.example.dto.SlotPlayResponse;
import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.service.GameHistoryService;
import org.example.service.SlotService;
import org.example.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
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
        Integer requestedReels = req.reelsCount; // champ attendu dans SlotPlayRequest
        if (requestedReels != null && (requestedReels <= 0 || requestedReels > 10)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nombre de rouleaux demandé invalide (1..10)"));
        }

        String email = authentication.getName();
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();

        try {
            walletService.debiter(u, req.montant);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solde insuffisant"));
        }

        List<String> reels = slotService.spinForReels(requestedReels);
        long payout = slotService.computePayout(reels, req.montant);
        boolean win = payout > 0L;
        if (win) walletService.crediter(u, payout);

        int mult = (payout > 0 && req.montant > 0) ? (int) (payout / req.montant) : 0;

        historyService.record(
                u,
                "slots",
                String.join(",", reels) + (requestedReels != null ? ("|r:" + requestedReels) : ""),
                req.montant,
                payout,
                mult
        );

        Wallet w = walletService.getWalletParUtilisateur(u);
        SlotPlayResponse resp = new SlotPlayResponse(reels, req.montant, payout, win, w.getSolde());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/config")
    public ResponseEntity<?> config(@RequestParam(required = false) Integer reelsCount) {
        if (reelsCount != null) {
            SlotService.SlotRuntimeConfig cfg = slotService.getRuntimeConfigFor(reelsCount);
            if (cfg == null) return ResponseEntity.notFound().build();
            SlotConfigResponse resp = new SlotConfigResponse(
                    cfg.symbols, cfg.reelWeights, cfg.reelsCount, cfg.payouts, cfg.symbolValues
            );
            return ResponseEntity.ok(resp);
        } else {
            Map<Integer, SlotService.SlotRuntimeConfig> all = slotService.getAllRuntimeConfigs();
            LinkedHashMap<Integer, SlotConfigResponse> map = new LinkedHashMap<>();
            all.forEach((k, c) -> map.put(
                    k,
                    new SlotConfigResponse(c.symbols, c.reelWeights, c.reelsCount, c.payouts, c.symbolValues)
            ));
            return ResponseEntity.ok(map);
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody SlotConfigRequest req) {
        if (req == null) return ResponseEntity.badRequest().body(Map.of("error", "body manquant"));
        if (req.symbols == null || req.symbols.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "symbols manquant"));
        }
        if (req.reelsCount == null || req.reelsCount <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "reelsCount manquant ou invalide"));
        }

        slotService.updateConfigForReels(
                req.reelsCount, req.symbols, req.reelWeights, req.payouts, req.symbolValues
        );

        SlotService.SlotRuntimeConfig cfg = slotService.getRuntimeConfigFor(req.reelsCount);
        SlotConfigResponse resp = new SlotConfigResponse(
                cfg.symbols, cfg.reelWeights, cfg.reelsCount, cfg.payouts, cfg.symbolValues
        );
        return ResponseEntity.ok(resp);
    }
}
