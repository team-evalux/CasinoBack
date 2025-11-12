package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.LeaderboardEntryDto;
import org.example.service.LeaderboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    /**
     * Public : retourne le top "limit" (défaut 50, borne 1..200).
     */
    @GetMapping
    public ResponseEntity<List<LeaderboardEntryDto>> getTop(
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit
    ) {
        List<LeaderboardEntryDto> top = leaderboardService.topByCredits(limit);
        return ResponseEntity.ok(top);
    }
}
