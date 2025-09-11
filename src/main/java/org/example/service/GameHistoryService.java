// src/main/java/org/example/service/GameHistoryService.java
package org.example.service;

import org.example.model.GameHistory;
import org.example.model.Utilisateur;
import org.example.repo.GameHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GameHistoryService {

    @Autowired
    private GameHistoryRepository repo;

    public List<GameHistory> recentForUser(Utilisateur u, int limit) {
        if (limit <= 0) limit = 15;
        return repo.findByUtilisateurOrderByCreatedAtDesc(u, PageRequest.of(0, limit));
    }

    public List<GameHistory> recentForUserByGame(Utilisateur u, String game, int limit) {
        if (limit <= 0) limit = 15;
        return repo.findByUtilisateurAndGameOrderByCreatedAtDesc(u, game, PageRequest.of(0, limit));
    }

    public GameHistory record(Utilisateur u, String game, String outcome, long montantJoue, long montantGagne, Integer multiplier) {
        GameHistory h = new GameHistory();
        h.setUtilisateur(u);
        h.setGame(game);
        h.setOutcome(outcome);
        h.setMontantJoue(montantJoue);
        h.setMontantGagne(montantGagne);
        h.setMultiplier(multiplier);
        return repo.save(h);
    }
}
