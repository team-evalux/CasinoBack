// src/main/java/org/example/service/GameHistoryService.java
package org.example.service;

import org.example.model.GameHistory;
import org.example.model.Utilisateur;
import org.example.repo.GameHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service // Bean Spring : service métier pour la gestion des historiques de jeux
public class GameHistoryService {

    @Autowired // Injection du repository JPA qui accède à la table game_history
    private GameHistoryRepository repo;

    /**
     * Retourne les 'limit' derniers historiques (toutes catégories de jeux confondues)
     * pour un utilisateur donné, triés du plus récent au plus ancien.
     * - Si limit <= 0, on force une limite par défaut de 15.
     * - Utilise PageRequest pour ne récupérer que la première page (page 0) de 'limit' éléments.
     */
    public List<GameHistory> recentForUser(Utilisateur u, int limit) {
        if (limit <= 0) limit = 15; // valeur par défaut si mauvaise limite fournie
        return repo.findByUtilisateurOrderByCreatedAtDesc(u, PageRequest.of(0, limit)); // requête paginée + tri DESC
    }

    /**
     * Retourne les 'limit' derniers historiques pour un utilisateur ET un jeu précis.
     * - Filtre par nom de jeu (champ 'game').
     * - Tri décroissant sur la date de création (createdAt).
     * - Limite paginée.
     */
    public List<GameHistory> recentForUserByGame(Utilisateur u, String game, int limit) {
        if (limit <= 0) limit = 15; // valeur par défaut
        return repo.findByUtilisateurAndGameOrderByCreatedAtDesc(u, game, PageRequest.of(0, limit)); // filtre + tri DESC + pagination
    }

    /**
     * Enregistre un nouvel historique de partie.
     * - Crée un GameHistory et remplit tous les champs utiles (utilisateur, type de jeu, outcome, montants, multiplicateur).
     * - Ne renseigne pas 'createdAt' explicitement : c'est le @PrePersist dans l'entité qui s'en charge.
     * - Sauvegarde via le repository et retourne l'entité persistée (avec son id / createdAt).
     */
    public GameHistory record(Utilisateur u, String game, String outcome, long montantJoue, long montantGagne, Integer multiplier) {
        GameHistory h = new GameHistory(); // nouvelle instance d'historique
        h.setUtilisateur(u);               // joueur concerné
        h.setGame(game);                   // type de jeu (ex: "roulette", "slot", "blackjack")
        h.setOutcome(outcome);             // résultat/outcome libre (ex: "WIN", "LOSE", détail JSON éventuel, etc.)
        h.setMontantJoue(montantJoue);     // mise jouée
        h.setMontantGagne(montantGagne);   // gain obtenu
        h.setMultiplier(multiplier);       // multiplicateur (si pertinent), peut être null
        return repo.save(h);               // persiste et renvoie l'entité sauvegardée
    }
}
