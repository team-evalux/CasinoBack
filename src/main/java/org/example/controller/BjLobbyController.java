package org.example.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.model.blackjack.BjTable;
import org.example.service.BjTableService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
            // Map.of(...) n'accepte pas de null → utilisons une HashMap/LinkedHashMap
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id",        t.getId());
            row.put("maxSeats",  t.getMaxSeats());
            row.put("isPrivate", t.isPrivate());
            row.put("phase",     t.getPhase() != null ? t.getPhase().name() : "WAITING_FOR_PLAYERS");
            out.add(row);
        }
        return ResponseEntity.ok(out);
    }

    // src/main/java/org/example/controller/BjLobbyController.java (méthode create)
    @PostMapping("/table")
    public ResponseEntity<?> create(@RequestBody CreateReq req) {
        BjTable t = service.createTable(Boolean.TRUE.equals(req.privateTable),
                req.code, req.maxSeats != null ? req.maxSeats : 5);

        Map<String,Object> out = new java.util.HashMap<>();
        out.put("id", t.getId());
        out.put("private", t.isPrivate());
        if (t.getCode() != null) out.put("code", t.getCode());
        return ResponseEntity.ok(out);
    }


    @Data
    public static class CreateReq {
        private Boolean privateTable;
        private Integer maxSeats;
        private String  code; // optionnel
    }
}
