// src/main/java/org/example/controller/HistoryController.java
package org.example.controller;

import org.example.model.GameHistory;
import org.example.model.Utilisateur;
import org.example.repo.UtilisateurRepository;
import org.example.service.GameHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

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

        List<GameHistory> list;
        if (game == null || game.isBlank()) {
            list = historyService.recentForUser(u, limit);
        } else {
            list = historyService.recentForUserByGame(u, game.trim(), limit);
        }

        List<Map<String,Object>> out = list.stream().map(h -> {
            Map<String,Object> m = new HashMap<>();
            m.put("id", h.getId());
            m.put("game", h.getGame());
            m.put("outcome", h.getOutcome());
            m.put("montantJoue", h.getMontantJoue());
            m.put("montantGagne", h.getMontantGagne());
            m.put("multiplier", h.getMultiplier());
            m.put("createdAt", h.getCreatedAt());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(out);
    }

    @GetMapping("/me/summary")
    public ResponseEntity<?> mySummary(
            @RequestParam(required = false, defaultValue = "15") Integer limit,
            Authentication authentication) {

        Utilisateur u = utilisateurRepo.findByEmail(authentication.getName()).orElseThrow();
        List<GameHistory> list = historyService.recentForUser(u, limit);

        List<Map<String,Object>> items = list.stream().map(h -> {
            Map<String,Object> m = new HashMap<>();
            m.put("id", h.getId());
            m.put("game", h.getGame());
            m.put("outcome", h.getOutcome());
            m.put("montantJoue", h.getMontantJoue());
            m.put("montantGagne", h.getMontantGagne());
            m.put("multiplier", h.getMultiplier());
            m.put("createdAt", h.getCreatedAt());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("items", items));
    }
}
