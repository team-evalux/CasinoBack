// src/main/java/org/example/controller/HistoryController.java
package org.example.controller;

import org.example.model.Utilisateur;
import org.example.repo.UtilisateurRepository;
import org.example.service.GameHistoryService;
import org.example.service.GameHistoryService.HistoryRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    @Autowired
    private GameHistoryService historyService;

    @Autowired
    private UtilisateurRepository utilisateurRepo;

    @GetMapping("/me")
    public ResponseEntity<?> myHistory(
            @RequestParam(required = false) String game,
            @RequestParam(required = false, defaultValue = "15") Integer limit,
            Authentication authentication) {

        Utilisateur u = utilisateurRepo.findByEmail(authentication.getName()).orElseThrow();

        List<HistoryRecord> list;
        if (game == null || game.isBlank()) {
            list = historyService.recentForUser(u, limit);
        } else {
            list = historyService.recentForUserByGame(u, game.trim(), limit);
        }

        return ResponseEntity.ok(list);
    }

    @GetMapping("/me/summary")
    public ResponseEntity<?> mySummary(
            @RequestParam(required = false, defaultValue = "15") Integer limit,
            Authentication authentication) {

        Utilisateur u = utilisateurRepo.findByEmail(authentication.getName()).orElseThrow();
        List<HistoryRecord> list = historyService.recentForUser(u, limit);

        return ResponseEntity.ok(Map.of("items", list));
    }

    @DeleteMapping("/me")
    public ResponseEntity<?> deleteMyHistory(Authentication authentication) {
        Utilisateur u = utilisateurRepo.findByEmail(authentication.getName()).orElseThrow();
        historyService.deleteAllForUser(u);
        return ResponseEntity.noContent().build();
    }
}
