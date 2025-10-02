// chemin du fichier : src/main/java/org/example/controller/HistoryController.java
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

// Annotation pour indiquer que cette classe expose des endpoints REST et retourne directement le corps.
@RestController
// Préfixe commun pour toutes les routes définies dans ce contrôleur (/api/history).
@RequestMapping("/api/history")
public class HistoryController {

    // Injection du service qui fournit les méthodes pour récupérer l'historique de jeu.
    @Autowired
    private GameHistoryService historyService;

    // Injection du repository Utilisateur pour résoudre l'utilisateur courant depuis son email.
    @Autowired
    private UtilisateurRepository utilisateurRepo;

    // Endpoint HTTP GET accessible à /api/history/me
    @GetMapping("/me")
    // Méthode qui renvoie l'historique d'un utilisateur connecté ; accepte un filtre 'game' et un 'limit'.
    public ResponseEntity<?> myHistory(
            // Paramètre optionnel 'game' pour filtrer par type de jeu (ex. "blackjack").
            @RequestParam(required = false) String game,
            // Paramètre optionnel 'limit' qui a une valeur par défaut de 15 si non fourni.
            @RequestParam(required = false, defaultValue = "15") Integer limit,
            // Spring injecte l'objet Authentication représentant la requête authentifiée.
            Authentication authentication) {

        // Récupère l'utilisateur dans la base en utilisant l'email (subject) fourni par Spring Security.
        Utilisateur u = utilisateurRepo.findByEmail(authentication.getName()).orElseThrow();

        // Déclare la liste qui contiendra les entrées d'historique à renvoyer.
        List<GameHistory> list;
        // Si aucun jeu spécifié, récupère l'historique récent global pour l'utilisateur.
        if (game == null || game.isBlank()) {
            list = historyService.recentForUser(u, limit);
        } else {
            // Sinon, récupère l'historique récent filtré par type de jeu (trim enlève les espaces).
            list = historyService.recentForUserByGame(u, game.trim(), limit);
        }

        // Transforme la liste d'entités GameHistory en liste de maps simples (DTO minimal pour la réponse).
        List<Map<String,Object>> out = list.stream().map(h -> {
            // Pour chaque élément crée une HashMap et y met des champs sélectionnés.
            Map<String,Object> m = new HashMap<>();
            // Ajoute l'identifiant de l'entrée d'historique.
            m.put("id", h.getId());
            // Ajoute le nom du jeu (ex. "BLACKJACK", "SLOTS", ...).
            m.put("game", h.getGame());
            // Ajoute le résultat/outcome (ex. "WIN", "LOSE", "PUSH").
            m.put("outcome", h.getOutcome());
            // Ajoute le montant joué pour cette entrée.
            m.put("montantJoue", h.getMontantJoue());
            // Ajoute le montant gagné (ou crédit reçu) pour cette entrée.
            m.put("montantGagne", h.getMontantGagne());
            // Ajoute le multiplicateur appliqué (si pertinent).
            m.put("multiplier", h.getMultiplier());
            // Ajoute la date/heure de création de l'entrée.
            m.put("createdAt", h.getCreatedAt());
            // Retourne la map pour le collecteur du stream.
            return m;
        }).collect(Collectors.toList());

        // Retourne la liste transformée comme corps de la réponse HTTP 200 OK.
        return ResponseEntity.ok(out);
    }

    // Endpoint HTTP GET accessible à /api/history/me/summary
    @GetMapping("/me/summary")
    // Méthode qui renvoie un résumé (wrapper) des entrées récentes ; accepte un paramètre 'limit'.
    public ResponseEntity<?> mySummary(
            // Paramètre optionnel 'limit' avec valeur par défaut 15.
            @RequestParam(required = false, defaultValue = "15") Integer limit,
            // Objet Authentication injecté par Spring Security pour identifier l'utilisateur courant.
            Authentication authentication) {

        // Récupère l'entité Utilisateur correspondant à l'authentification courante (email).
        Utilisateur u = utilisateurRepo.findByEmail(authentication.getName()).orElseThrow();
        // Récupère les entrées récentes pour l'utilisateur via le service.
        List<GameHistory> list = historyService.recentForUser(u, limit);

        // Transforme la liste en une liste d'objets simples (maps) pour le payload "items".
        List<Map<String,Object>> items = list.stream().map(h -> {
            // Pour chaque historique on crée une map et on y copie les champs d'intérêt.
            Map<String,Object> m = new HashMap<>();
            // Identifiant de l'historique.
            m.put("id", h.getId());
            // Nom/type du jeu.
            m.put("game", h.getGame());
            // Issue/outcome.
            m.put("outcome", h.getOutcome());
            // Montant misé.
            m.put("montantJoue", h.getMontantJoue());
            // Montant reçu/gagné.
            m.put("montantGagne", h.getMontantGagne());
            // Multiplicateur appliqué.
            m.put("multiplier", h.getMultiplier());
            // Date de création de l'entrée.
            m.put("createdAt", h.getCreatedAt());
            // Retourne la map pour le collecteur.
            return m;
        }).collect(Collectors.toList());

        // Renvoie un objet JSON contenant la clé "items" mappée sur la liste d'items.
        return ResponseEntity.ok(Map.of("items", items));
    }
}
