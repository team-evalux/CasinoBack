package org.example.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.model.blackjack.BjTable;
import org.example.service.BjTableService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/bj")
@RequiredArgsConstructor
public class BjLobbyController {

    private final BjTableService service;

    @GetMapping("/tables")
    public ResponseEntity<?> list() {
        List<Map<String,Object>> out = new ArrayList<>();
        for (BjTable t : service.listTables()) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id",        t.getId());
            row.put("maxSeats",  t.getMaxSeats());
            row.put("isPrivate", t.isPrivate());
            row.put("phase",     t.getPhase() != null ? t.getPhase().name() : "WAITING_FOR_PLAYERS");
            row.put("name",      t.getName());
            row.put("minBet",    t.getMinBet());
            row.put("maxBet",    t.getMaxBet());
            out.add(row);
        }
        return ResponseEntity.ok(out);
    }

    @GetMapping("/table/{id}")
    public ResponseEntity<?> tableMeta(@PathVariable Long id, Principal principal) {
        BjTable t = service.getTable(id);
        if (t == null) return ResponseEntity.notFound().build();
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id", t.getId());
        out.put("maxSeats", t.getMaxSeats());
        out.put("isPrivate", t.isPrivate());
        out.put("name", t.getName());
        out.put("minBet", t.getMinBet());
        out.put("maxBet", t.getMaxBet());
        out.put("creatorEmail", t.getCreatorEmail());
        // si le créateur demande -> renvoyer le code (pour qu'il puisse le partager)
        if (t.isPrivate() && principal != null && Objects.equals(principal.getName(), t.getCreatorEmail())) {
            out.put("code", t.getCode());
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping("/table")
    public ResponseEntity<?> create(@RequestBody CreateReq req, Principal principal) {
        String creator = principal != null ? principal.getName() : null;
        try {
            BjTable t = service.createTable(
                    creator,
                    Boolean.TRUE.equals(req.privateTable),
                    req.getCode(),
                    req.getMaxSeats() != null ? req.getMaxSeats() : 5,
                    req.getName(),
                    req.getMinBet() != null ? req.getMinBet() : 0L,
                    req.getMaxBet() != null ? req.getMaxBet() : 0L
            );

            Map<String,Object> out = new java.util.HashMap<>();
            out.put("id", t.getId());
            out.put("isPrivate", t.isPrivate());
            out.put("name", t.getName());
            out.put("minBet", t.getMinBet());
            out.put("maxBet", t.getMaxBet());
            if (t.getCode() != null && creator != null && creator.equals(t.getCreatorEmail())) out.put("code", t.getCode());
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("error", "Erreur serveur lors de la création"));
        }
    }

    @DeleteMapping("/table/{id}")
    public ResponseEntity<?> close(@PathVariable Long id, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body("Non authentifié");
        service.closeTable(id, principal.getName());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @Data
    public static class CreateReq {
        private Boolean privateTable;
        private Integer maxSeats;
        private String  code;
        private String  name;
        private Long    minBet;
        private Long    maxBet;
    }
}
