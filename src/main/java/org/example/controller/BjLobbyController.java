// src/main/java/org/example/controller/BjLobbyController.java
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
        for (BjTable t : service.listPublicTables()) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id",        t.getId());
            row.put("maxSeats",  t.getMaxSeats());
            row.put("isPrivate", t.isPrivate());
            row.put("phase",     t.getPhase() != null ? t.getPhase().name() : "WAITING_FOR_PLAYERS");
            out.add(row);
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping("/table")
    public ResponseEntity<?> create(@RequestBody CreateReq req, Principal principal) {
        String creator = principal != null ? principal.getName() : null;
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
        out.put("private", t.isPrivate());
        out.put("name", t.getName());
        out.put("minBet", t.getMinBet());
        out.put("maxBet", t.getMaxBet());
        if (t.getCode() != null) out.put("code", t.getCode());
        return ResponseEntity.ok(out);
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
