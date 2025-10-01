package org.example.service;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component // Déclare un composant Spring utilisable comme un service singleton
public class WalletSseService {
    // Map email → liste de connexions SSE associées
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // Méthode pour enregistrer un client SSE sur un email
    public SseEmitter register(String email) {
        // Création d’un nouvel émetteur SSE avec timeout "infini"
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        // Ajoute cet émetteur à la liste correspondant à l’email
        emitters.computeIfAbsent(email, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Gestion de la déconnexion ou erreurs → supprime l’émetteur
        emitter.onCompletion(() -> removeEmitter(email, emitter));
        emitter.onTimeout(() -> removeEmitter(email, emitter));
        emitter.onError((e) -> removeEmitter(email, emitter));

        try {
            // Envoie un premier événement "connected" pour confirmer la connexion
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException ignored) {} // si ça échoue on ignore

        return emitter; // renvoie l’émetteur au contrôleur qui l’exposera
    }

    // Supprime un émetteur spécifique d’un utilisateur
    private void removeEmitter(String email, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(email);
        if (list != null) {
            list.remove(emitter); // enlève de la liste
            if (list.isEmpty()) emitters.remove(email); // si liste vide → supprime la clé
        }
    }

    // Envoie un événement "wallet-update" contenant le solde actuel
    public void sendBalanceUpdate(String email, long solde) {
        // Récupère la liste des clients connectés pour cet email
        List<SseEmitter> list = emitters.get(email);
        if (list == null) return; // aucun abonné

        // Parcourt tous les émetteurs (copie pour éviter les erreurs concurrentes)
        for (SseEmitter emitter : list.toArray(new SseEmitter[0])) {
            try {
                // Envoie l’événement JSON : {"solde": ...}
                emitter.send(SseEmitter.event()
                        .name("wallet-update")
                        .data(Map.of("solde", solde)));
            } catch (IOException e) {
                // En cas d’erreur → déconnecte l’émetteur
                removeEmitter(email, emitter);
            }
        }
    }
}
