// src/main/java/org/example/service/RouletteService.java
package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.RouletteConfig;
import org.example.repo.RouletteConfigRepository;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service // Déclare cette classe comme un "bean" de type service géré par Spring
public class RouletteService {

    private final Random rand = new Random();
    // Générateur de nombres pseudo-aléatoires utilisé pour tirer les numéros.

    private Map<Integer, Integer> customWeights = null;
    // Carte (numéro de la roulette → poids).
    // Si null → tirage uniforme, sinon permet de "truquer" la roulette avec des probabilités personnalisées.

    private final ObjectMapper objectMapper = new ObjectMapper();
    // Utilisé pour transformer les objets Java ↔ JSON (sauvegarde/lecture en DB).

    private final RouletteConfigRepository repo;
    // Repository JPA pour accéder/sauvegarder la configuration en base de données.

    // Ensemble des numéros rouges (selon les règles officielles de la roulette européenne)
    private static final Set<Integer> RED_SET = Set.of(
            1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36
    );

    // Constructeur : Spring injectera automatiquement le repository
    public RouletteService(RouletteConfigRepository repo) {
        this.repo = repo;
    }

    @PostConstruct // Méthode appelée automatiquement après la création du bean Spring
    public void initFromDb() {
        List<RouletteConfig> all = repo.findAll(); // récupère toutes les configs en DB
        if (all.isEmpty()) {
            // Si aucune config trouvée, crée une nouvelle avec poids null (roulette équitable)
            repo.save(new RouletteConfig(null));
            this.customWeights = null;
        } else {
            String json = all.get(0).getWeightsJson(); // prend la première config
            if (json == null) {
                this.customWeights = null; // si pas de poids → roulette équitable
            } else {
                try {
                    // Conversion JSON (clé = string) en Map<String,Integer>
                    Map<String,Integer> tmp = objectMapper.readValue(json, new TypeReference<>(){});
                    Map<Integer,Integer> converted = new ConcurrentHashMap<>();
                    for (Map.Entry<String,Integer> e : tmp.entrySet()) {
                        // convertit les clés String en Integer (numéro roulette)
                        converted.put(Integer.valueOf(e.getKey()), e.getValue());
                    }
                    this.customWeights = converted;
                } catch (Exception ex) {
                    // en cas d'erreur → annule et garde roulette équitable
                    this.customWeights = null;
                }
            }
        }
    }

    // Définit des poids personnalisés pour truquer les probabilités de tirage
    public synchronized void setCustomWeights(Map<Integer, Integer> weights) {
        if (weights == null || weights.isEmpty()) {
            // Si aucun poids → reset à roulette équitable
            this.customWeights = null;
            persistWeightsJson(null);
            return;
        }
        Map<Integer, Integer> cleaned = new ConcurrentHashMap<>();
        for (Map.Entry<Integer, Integer> e : weights.entrySet()) {
            Integer k = e.getKey();
            Integer v = e.getValue();
            // On ignore les entrées invalides :
            if (k == null || v == null) continue; // clé ou valeur null
            if (k < 0 || k > 36) continue;        // numéro invalide (roulette = 0 → 36)
            if (v <= 0) continue;                 // poids doit être > 0
            cleaned.put(k, v);                    // ajout à la map nettoyée
        }
        this.customWeights = cleaned.isEmpty() ? null : cleaned;
        try {
            // Persiste en JSON la nouvelle config en base
            persistWeightsJson(this.customWeights == null ? null : objectMapper.writeValueAsString(this.customWeights));
        } catch (Exception ex) { /* ignore erreur */ }
    }

    // Récupère les poids personnalisés (copie défensive)
    public synchronized Map<Integer, Integer> getCustomWeights() {
        return customWeights == null ? null : new HashMap<>(customWeights);
    }

    // Réinitialise les poids (retour à roulette équitable)
    public synchronized void resetWeights() {
        this.customWeights = null;
        persistWeightsJson(null);
    }

    // Sauvegarde en base la config actuelle des poids
    private void persistWeightsJson(String json) {
        List<RouletteConfig> all = repo.findAll();
        RouletteConfig cfg;
        if (all.isEmpty()) {
            // Si aucune config en DB, en crée une nouvelle
            cfg = new RouletteConfig(json);
        } else {
            // Sinon met à jour la première existante
            cfg = all.get(0);
            cfg.setWeightsJson(json);
        }
        repo.save(cfg);
    }

    // ----- Tirage d’un numéro -----
    public int tirerNumero() {
        if (customWeights == null || customWeights.isEmpty()) {
            // Tirage équitable : un nombre aléatoire entre 0 et 36 inclus
            return rand.nextInt(37);
        }
        // Sinon tirage pondéré selon customWeights
        return weightedRandom(customWeights);
    }

    // Tirage pondéré (roulette truquée)
    private int weightedRandom(Map<Integer, Integer> weights) {
        // somme des poids
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) return rand.nextInt(37); // fallback équitable

        int r = rand.nextInt(total); // tire un nombre entre 0 et total-1
        for (Map.Entry<Integer, Integer> e : weights.entrySet()) {
            r -= e.getValue(); // décrémente par le poids du numéro
            if (r < 0) return e.getKey(); // quand ça passe en négatif → numéro choisi
        }
        return rand.nextInt(37); // fallback (ne devrait pas arriver)
    }

    // Détermine la couleur d’un numéro (green/red/black)
    public String couleurPour(int numero) {
        if (numero == 0) return "green"; // le 0 est toujours vert
        return RED_SET.contains(numero) ? "red" : "black";
    }

    // Vérifie si un pari est gagnant
    public boolean estGagnant(String betType, String betValue, int numero) {
        if (betType == null || betValue == null) return false;
        switch (betType) {
            case "straight":
                // Pari direct sur un numéro exact
                try { return Integer.parseInt(betValue) == numero; } catch (Exception e) { return false;}
            case "color":
                // Pari sur la couleur
                return betValue.equalsIgnoreCase(couleurPour(numero));
            case "parity":
                // Pair ou impair
                if (numero == 0) return false; // le 0 n’est ni pair ni impair
                if ("even".equalsIgnoreCase(betValue)) return numero % 2 == 0;
                if ("odd".equalsIgnoreCase(betValue)) return numero % 2 == 1;
                return false;
            case "range":
                // 1-18 (low) ou 19-36 (high)
                if ("low".equalsIgnoreCase(betValue)) return numero >= 1 && numero <= 18;
                if ("high".equalsIgnoreCase(betValue)) return numero >= 19 && numero <= 36;
                return false;
            case "dozen":
                // Douzaine 1-12, 13-24, 25-36
                if ("1".equals(betValue)) return numero >= 1 && numero <= 12;
                if ("2".equals(betValue)) return numero >= 13 && numero <= 24;
                if ("3".equals(betValue)) return numero >= 25 && numero <= 36;
                return false;
            default:
                // Type de pari inconnu
                return false;
        }
    }

    // Retourne le maultiplicateur des gains selon le type de pari
            public long payoutMultiplier(String betType) {
                if (betType == null) return 0L;
                switch (betType) {
                    case "straight": return 35L; // pari direct → 35x la mise
                    case "dozen": return 3L;     // pari sur une douzaine → 3x
                    case "color":
                    case "parity":
                    case "range":
                        return 2L;               // pari couleur/pair-impair/rnge → 2x
            default: return 0L;          // inconnu → rien
        }
    }
}
