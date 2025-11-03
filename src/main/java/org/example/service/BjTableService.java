package org.example.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.example.dto.blackjack.*;
import org.example.model.blackjack.BjTable;
import org.example.model.blackjack.SeatStatus;
import org.example.model.blackjack.TablePhase;
import org.example.service.blackjack.access.AccessService;
import org.example.service.blackjack.action.ActionService;
import org.example.service.blackjack.betting.BettingService;
import org.example.service.blackjack.engine.RoundEngine;
import org.example.service.blackjack.entry.EntryService;
import org.example.service.blackjack.registry.TableRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BjTableService {

    private final TableRegistry registry;
    private final AccessService access;
    private final EntryService entry;
    private final BettingService betting;
    private final RoundEngine engine;
    private final ActionService actions;

    @PostConstruct
    private void loadTablesFromDb() {
        // Si TableRegistry restaure des tables au boot, tu peux réenregistrer le verrou créateur :
        for (BjTable t : registry.all()) {
            access.registerCreatorLock(t);
        }
    }

    public List<BjTable> listTables() { return new ArrayList<>(registry.all()); }
    public BjTable getTable(Long id) { return registry.get(id); }
    public List<BjTable> listPublicTables() { return registry.listPublic(); }

    public synchronized BjTable createTable(String creatorEmail, boolean isPrivate, String code, int maxSeats,
                                            String name, long minBet, long maxBet) {
        BjTable t = access.createTable(creatorEmail, isPrivate, code, maxSeats, name, minBet, maxBet);
        t.setPhase(TablePhase.BETTING);
        t.setPhaseDeadlineEpochMs(0L);
        engine.startBetting(t);
        return t;
    }

    /**
     * JOIN = entrer dans la table (auto-seat). Si tableId absent :
     * - on tente de rejoindre une table publique non pleine,
     * - sinon on en crée une nouvelle publique.
     */
    public synchronized BjTable joinOrCreate(String email, JoinOrCreateMsg msg) {
        // Création explicite
        if (Boolean.TRUE.equals(msg.getCreatePublic())) {
            BjTable t = access.createTable(email, false, null,
                    msg.getMaxSeats() != null ? msg.getMaxSeats() : 5,
                    msg.getName(), msg.getMinBet() != null ? msg.getMinBet() : 0L,
                    msg.getMaxBet() != null ? msg.getMaxBet() : 0L);
            entry.enter(email, t.getId(), null);
            if (t.getPhase() == TablePhase.BETTING && t.getPhaseDeadlineEpochMs() == 0) engine.startBetting(t);
            return t;
        }
        if (Boolean.TRUE.equals(msg.getCreatePrivate())) {
            if (msg.getCode() == null || msg.getCode().isBlank()) {
                throw new IllegalArgumentException("Code requis pour créer une table privée");
            }
            BjTable t = access.createTable(email, true, msg.getCode(),
                    msg.getMaxSeats() != null ? msg.getMaxSeats() : 5,
                    msg.getName(), msg.getMinBet() != null ? msg.getMinBet() : 0L,
                    msg.getMaxBet() != null ? msg.getMaxBet() : 0L);
            entry.enter(email, t.getId(), msg.getCode());
            if (t.getPhase() == TablePhase.BETTING && t.getPhaseDeadlineEpochMs() == 0) engine.startBetting(t);
            return t;
        }

        // Rejoindre existante
        if (msg.getTableId() != null) {
            Long tableId = Long.valueOf(msg.getTableId().toString());
            BjTable t = entry.enter(email, tableId, msg.getCode());
            if (t.getPhase() == TablePhase.BETTING && t.getPhaseDeadlineEpochMs() == 0) {
                engine.startBetting(t);
            }
            return t;
        }

        // Sinon : rejoindre ou créer une publique
        BjTable t = findPublicNotFull().orElseGet(() ->
                access.createTable(email, false, null, 5, null, 0L, 0L)
        );
        entry.enter(email, t.getId(), null);
        if (t.getPhase() == TablePhase.BETTING && t.getPhaseDeadlineEpochMs() == 0) engine.startBetting(t);
        return t;
    }

    public synchronized boolean authorizeEmailForTable(Long tableId, String email, String code) {
        return access.authorizeEmailForTable(tableId, email, code);
    }

    /** Compat: /bj/sit ne fait plus que "enter". seatIndex ignoré. */
    public synchronized void sit(String email, Long tableId, Integer seatIndex) {
        entry.enter(email, tableId, null);
    }

    public synchronized void leave(String email, Long tableId, Integer seatIndex) {
        entry.leave(email, tableId);
    }

    public synchronized void bet(String email, BetMsg msg) {
        betting.bet(email, msg);
        Long id = Objects.requireNonNull(msg.getTableId(), "tableId manquant");
        BjTable t = registry.get(id);
        engine.broadcastState(t);
    }

    public synchronized void action(String email, ActionMsg msg) {
        actions.apply(email, msg);
        Long id = Objects.requireNonNull(msg.getTableId(), "tableId manquant");
        BjTable t = registry.get(id);
        engine.onPlayerAction(t); // avance le tour / croupier et re-broadcast
    }

    /**
     * Ferme une table :
     * - vérifie l'autorisation,
     * - libère d'abord les verrous (entry + access),
     * - supprime ensuite du registry + DB,
     * - notifie la fermeture et rafraîchit le lobby (fait dans access.onTableClosed).
     */
    public synchronized void closeTable(Long tableId, String requesterEmail) {
        BjTable t = registry.get(tableId);
        boolean isAdmin = false; // selon SecurityContext si besoin
        access.ensureCloseAllowed(t, requesterEmail, isAdmin);
        if (t == null) return;

        if (t.getPhase() == TablePhase.BETTING) {
            // 1) Libère les joueurs (maps internes)
            entry.onTableClosed(t);

            // 2) Supprime d'abord du registry + DB (pour que la liste lobby soit déjà à jour)
            registry.remove(tableId);
            registry.deleteFromDb(tableId);

            // 3) Puis notifie la fermeture et broadcast le lobby (utilise maintenant un registry sans la table)
            access.onTableClosed(t, t.getCreatorEmail());
            return;
        }
        // En cours de main : on marque une fermeture différée
        t.setPendingClose(true);
    }

    @Scheduled(fixedRate = 600_000)
    public synchronized void nettoyerTablesInactives() {
        Instant now = Instant.now();
        List<Long> toRemove = new ArrayList<>();
        for (BjTable t : registry.all()) {
            boolean vide = t.getSeats().values().stream()
                    .allMatch(s -> s == null || s.getStatus() == SeatStatus.EMPTY);
            if (vide && Duration.between(t.getLastActiveAt(), now).toMillis() > 60_000) {
                toRemove.add(t.getId());
            }
        }
        for (Long id : toRemove) {
            BjTable t = registry.get(id);
            if (t == null) continue;

            // 1) libère les joueurs
            entry.onTableClosed(t);

            // 2) supprime d'abord
            registry.remove(id);
            registry.deleteFromDb(id);

            // 3) notifie fermeture + lobby (registry déjà à jour)
            access.onTableClosed(t, t.getCreatorEmail());
        }
    }

    public synchronized void markDisconnected(String email) {
        entry.markDisconnected(email);
    }

    public synchronized void markReconnected(String email) {
        entry.markReconnected(email);
    }

    // ---------- helpers ----------
    private Optional<BjTable> findPublicNotFull() {
        return registry.listPublic().stream()
                .filter(t -> !isFull(t))
                .findFirst();
    }

    private boolean isFull(BjTable t) {
        long occupied = t.getSeats().values().stream()
                .filter(s -> s != null && s.getStatus() != SeatStatus.EMPTY)
                .count();
        return occupied >= t.getMaxSeats();
    }
}
