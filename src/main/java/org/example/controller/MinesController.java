package org.example.controller;

import org.example.dto.*;
import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.service.GameHistoryService;
import org.example.service.MinesService;
import org.example.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game/mines")
public class MinesController {

    @Autowired private UtilisateurRepository utilisateurRepo;
    @Autowired private WalletService walletService;
    @Autowired private MinesService minesService;
    @Autowired private GameHistoryService historyService;

    private static final int GRID = 25;

    // ==================== DÉMARRER PARTIE ====================
    @PostMapping("/start")
    @Transactional
    public ResponseEntity<?> start(@RequestBody MinesStartRequest req, Authentication auth) {
        if (req == null || req.montant <= 0)
            return ResponseEntity.badRequest().body(Map.of("error", "Montant invalide"));

        Utilisateur u = utilisateurRepo.findByEmail(auth.getName()).orElseThrow();

        try {
            walletService.debiter(u, req.montant);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solde insuffisant"));
        }

        var round = minesService.start(u, req.montant, req.mines);
        Wallet w = walletService.getWalletParUtilisateur(u);

        double nextMult = minesService.multiplierFor(round.mines, 1);
        MinesStartResponse resp = new MinesStartResponse(
                round.id, GRID, round.mines, GRID - round.mines, 0, nextMult, w.getSolde());
        return ResponseEntity.ok(resp);
    }

    // ==================== CLIQUER SUR UNE CASE ====================
    @PostMapping("/pick")
    @Transactional
    public ResponseEntity<?> pick(@RequestBody MinesPickRequest req, Authentication auth) {
        Utilisateur u = utilisateurRepo.findByEmail(auth.getName()).orElseThrow();

        try {
            // (optionnel) guard basique si session disparue
            var active = minesService.getActiveFor(u);
            if (active == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Session invalide ou expirée. Relancez une partie."));
            }

            var pr = minesService.pick(u, req.sessionId, req.index);

            // 💣 Si c’est une bombe → partie perdue → on enregistre
            if (pr.bomb) {
                String outcome = String.format(
                        "mines=%d,safe=%d,bomb=true,index=%d",
                        pr.mines, pr.safeCount, pr.index
                );

                long mise = pr.mise; // ✅ fourni par le service (robuste)
                historyService.record(
                        u,
                        "mines",
                        outcome,
                        mise,
                        0L,
                        0
                );
            }

            MinesPickResponse resp = new MinesPickResponse(
                    pr.bomb, pr.index, pr.safeCount, pr.mines,
                    pr.currentMultiplier, pr.potentialPayout, pr.finished, pr.bombsToReveal);
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    // ==================== CASHOUT ====================
    @PostMapping("/cashout")
    @Transactional
    public ResponseEntity<?> cashout(@RequestBody MinesCashoutRequest req, Authentication auth) {
        Utilisateur u = utilisateurRepo.findByEmail(auth.getName()).orElseThrow();

        try {
            // On récupère la round active AVANT l'encaissement pour avoir la mise (et mines)
            var active = minesService.getActiveFor(u);
            if (active == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Session invalide ou expirée. Relancez une partie."));
            }
            long mise = active.mise;

            var cr = minesService.cashout(u, req.sessionId);
            if (cr.payout > 0) walletService.crediter(u, cr.payout);

            // 🧾 Enregistre l’historique (outcome clair)
            String outcome = String.format(
                    "mines=%d,safe=%d,bomb=false,multiplier=%.2f",
                    cr.mines, cr.safeCount, cr.multiplier
            );

            historyService.record(
                    u,
                    "mines",
                    outcome,
                    mise,
                    cr.payout,
                    (int) Math.floor(cr.multiplier)
            );

            Wallet w = walletService.getWalletParUtilisateur(u);
            MinesCashoutResponse resp = new MinesCashoutResponse(
                    true, cr.safeCount, cr.multiplier, cr.payout, w.getSolde(), cr.bombs);
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    // ==================== RESET ====================
    @Transactional
    @DeleteMapping("/reset")
    public ResponseEntity<?> reset(Authentication auth) {
        String email = auth.getName();
        minesService.resetSession(email);
        return ResponseEntity.noContent().build();
    }

    // ==================== RESUME ====================
    @GetMapping("/resume")
    @Transactional(readOnly = true)
    public ResponseEntity<?> resume(Authentication auth) {
        Utilisateur u = utilisateurRepo.findByEmail(auth.getName()).orElseThrow();
        var active = minesService.getActiveFor(u);
        if (active == null) {
            return ResponseEntity.ok(Map.of("active", false));
        }

        return ResponseEntity.ok(Map.of(
                "active", true,
                "sessionId", active.id,
                "mines", active.mines,
                "safeCount", active.safes.size(),
                "revealed", active.safes,
                "mise", active.mise
        ));
    }

}
