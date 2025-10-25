// src/main/java/org/example/service/blackjack/access/AccessService.java
package org.example.service.blackjack.access;

import lombok.RequiredArgsConstructor;
import org.example.dto.blackjack.TableEvent;
import org.example.model.Utilisateur;
import org.example.model.blackjack.BjTable;
import org.example.repo.UtilisateurRepository;
import org.example.service.blackjack.registry.TableRegistry;
import org.example.service.blackjack.util.Payloads;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AccessService {
    private final TableRegistry registry;
    private final UtilisateurRepository utilisateurRepo;
    private final SimpMessagingTemplate broker;
    private final Payloads payloads;

    // email -> table créée
    private final Map<String, Long> createdBy = new ConcurrentHashMap<>();
    // tableId -> emails autorisés (accès privé)
    private final Map<Long, Set<String>> privateAccess = new ConcurrentHashMap<>();

    /* --- appelé au boot si tu restaures des tables --- */
    public void registerCreatorLock(BjTable t) {
        if (t.getCreatorEmail() != null) {
            createdBy.put(t.getCreatorEmail(), t.getId());
        }
    }

    public BjTable createTable(String creatorEmail, boolean isPrivate, String code,
                               int maxSeats, String name, long minBet, long maxBet) {
        if (creatorEmail != null && createdBy.containsKey(creatorEmail))
            throw new IllegalStateException("Vous possédez déjà une table. Fermez-la avant d'en créer une nouvelle.");

        if (isPrivate) {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("Code requis pour une table privée");
            }
            code = code.trim();
            if (code.length() > 32) code = code.substring(0, 32);
        }

        if (name != null) {
            name = name.trim();
            if (name.length() > 20) name = name.substring(0, 20);
        }
        if (name == null || name.isBlank()) {
            String pseudo = utilisateurRepo.findByEmail(creatorEmail)
                    .map(Utilisateur::getPseudo)
                    .orElseGet(() -> {
                        int at = creatorEmail != null ? creatorEmail.indexOf('@') : -1;
                        return at > 0 ? creatorEmail.substring(0, at) : "Inconnu";
                    });
            name = ("Table de " + pseudo);
            if (name.length() > 20) name = name.substring(0, 20);
        }

        var ent = new org.example.model.blackjack.BjTableEntity();
        ent.setPrivate(isPrivate);
        ent.setCode(code);
        ent.setMaxSeats(maxSeats);
        ent.setName(name);
        ent.setMinBet(minBet);
        ent.setMaxBet(maxBet);
        ent.setCreatorEmail(creatorEmail);
        ent.setCreatedAt(Instant.now());

        BjTable t = registry.createAndPersist(ent);

        // Le créateur est autorisé d’office à une table privée
        if (isPrivate && creatorEmail != null) {
            privateAccess.computeIfAbsent(t.getId(), k -> ConcurrentHashMap.newKeySet()).add(creatorEmail);
        }
        createdBy.put(creatorEmail, t.getId());
        broadcastLobby();
        return t;
    }

    public void ensureCloseAllowed(BjTable t, String requesterEmail, boolean isAdmin) {
        if (!Objects.equals(t.getCreatorEmail(), requesterEmail) && !isAdmin)
            throw new IllegalStateException("Seul le créateur ou un ADMIN peut fermer la table");
    }

    /** Vérifie STRICTEMENT le code pour une table privée et n’ajoute l’email qu’en cas de succès. */
    public boolean authorizeEmailForTable(Long tableId, String email, String code) {
        BjTable t = registry.get(tableId);
        if (t == null) return false;

        // Public -> toujours OK
        if (!t.isPrivate()) return true;

        // ✅ Déjà autorisé ? (créateur, siégeants actuels, privateAccess…)
        if (allowedFor(t).contains(email)) return true;

        // ✅ Code correct -> on autorise et on mémorise l’email
        if (code != null && Objects.equals(code, t.getCode())) {
            privateAccess
                    .computeIfAbsent(tableId, k -> ConcurrentHashMap.newKeySet())
                    .add(email);
            return true;
        }

        // ❌ sinon non
        return false;
    }

    /** Emails autorisés pour recevoir les événements de la table privée. */
    public Set<String> allowedFor(BjTable t) {
        Set<String> allowed = new HashSet<>();
        if (t.getCreatorEmail() != null) allowed.add(t.getCreatorEmail());
        t.getSeats().values().forEach(s -> { if (s != null && s.getEmail() != null) allowed.add(s.getEmail()); });
        var extra = privateAccess.get(t.getId());
        if (extra != null) allowed.addAll(extra);
        return allowed;
    }

    /** Diffuse un événement de table (TABLE_STATE, HAND_START, …) en respectant la confidentialité. */
    public void broadcastToTable(BjTable t, String type, Object payload) {
        var evt = org.example.dto.blackjack.TableEvent.builder()
                .type(type)
                .payload(payload)
                .build();

        if (!t.isPrivate()) {
            // public: topic global
            broker.convertAndSend("/topic/bj/table/" + t.getId(), evt);
        } else {
            // privé: strictement les autorisés
            Set<String> allowed = allowedFor(t);
            for (String em : allowed) {
                broker.convertAndSendToUser(em, "/queue/bj/table/" + t.getId(), evt);
            }
        }
    }

    public void onTableClosed(BjTable t, String ownerEmail) {
        var evt = org.example.dto.blackjack.TableEvent.builder()
                .type("TABLE_CLOSED").payload(Map.of("tableId", t.getId()))
                .build();

        if (!t.isPrivate()) {
            broker.convertAndSend("/topic/bj/table/" + t.getId(), evt);
        } else {
            for (String em : allowedFor(t)) {
                broker.convertAndSendToUser(em, "/queue/bj/table/" + t.getId(), evt);
            }
        }

        // 🔥 Nettoyage ACL et verrous créateur
        privateAccess.remove(t.getId());
        createdBy.entrySet().removeIf(e -> Objects.equals(e.getValue(), t.getId()));

        broadcastLobby();
    }

    public void broadcastLobby() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (BjTable t : registry.all()) {
            list.add(Map.of(
                    "id", t.getId(),
                    "maxSeats", t.getMaxSeats(),
                    "isPrivate", t.isPrivate(),
                    "phase", t.getPhase() != null ? t.getPhase().name() : "BETTING",
                    "name", t.getName(),
                    "minBet", t.getMinBet(),
                    "maxBet", t.getMaxBet(),
                    "creatorEmail", t.getCreatorEmail()
            ));
        }
        broker.convertAndSend("/topic/bj/lobby", list);
    }
}
