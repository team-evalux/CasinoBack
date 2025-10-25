package org.example.service.blackjack.entry;

import lombok.RequiredArgsConstructor;
import org.example.model.Utilisateur;
import org.example.model.blackjack.*;
import org.example.repo.UtilisateurRepository;
import org.example.service.blackjack.access.AccessService;
import org.example.service.blackjack.registry.TableRegistry;
import org.example.service.blackjack.util.Payloads;
import org.example.service.blackjack.util.Locks;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class EntryService {
    private final TableRegistry registry;
    private final UtilisateurRepository users;
    private final AccessService access;
    private final Payloads payloads;
    private final Locks locks;
    private final SimpMessagingTemplate broker;

    // un joueur ne peut être inscrit que sur une table à la fois
    private final Map<String, Long> userTable = new ConcurrentHashMap<>();

    // --- timers de nettoyage déconnexion ---
    private static final long DISCONNECT_GRACE_MS = 120_000; // 2 minutes
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> disconnectTimers = new ConcurrentHashMap<>();

    // ------------------------------------------------------------
    public BjTable enter(String email, Long tableId, String code) {
        BjTable t = registry.get(tableId);

        if (t.isPrivate() && !access.authorizeEmailForTable(tableId, email, code)) {
            throw new IllegalStateException("Code d'accès invalide pour cette table privée");
        }

        synchronized (locks.of(tableId)) {
            if (Boolean.TRUE.equals(isFull(t))) {
                throw new IllegalStateException("Table pleine");
            }

            Long already = userTable.get(email);
            if (already != null && !already.equals(tableId)) {
                throw new IllegalStateException("Tu es déjà présent sur une autre table");
            }

            Integer idx = findSeatIndexByEmail(t, email);
            if (idx == null) {
                idx = firstEmpty(t).orElseThrow(() -> new IllegalStateException("Table pleine"));
                Seat s = Optional.ofNullable(t.getSeats().get(idx)).orElse(new Seat());
                Utilisateur u = users.findByEmail(email).orElseThrow();
                s.setUserId(u.getId());
                s.setEmail(email);
                s.setDisplayName( // ✅ plus de lookup lors du payload
                        (u.getPseudo() != null && !u.getPseudo().isBlank())
                                ? u.getPseudo()
                                : email.substring(0, Math.max(0, email.indexOf('@')) > 0 ? email.indexOf('@') : email.length())
                );
                s.setStatus(SeatStatus.SEATED);
                t.getSeats().put(idx, s);
                userTable.put(email, t.getId());
            } else {
                cancelDisconnectTimer(email);
                Seat seat = t.getSeats().get(idx);
                if (seat != null && seat.getStatus() == SeatStatus.DISCONNECTED) {
                    seat.setStatus(SeatStatus.SEATED);
                }
            }

            t.setLastActiveAt(Instant.now());
        }

        broadcastState(t);
        access.broadcastLobby();
        return t;
    }

    public void leave(String email, Long tableId) {
        BjTable t = registry.get(tableId);
        if (t == null) return;

        synchronized (locks.of(tableId)) {
            Integer idx = findSeatIndexByEmail(t, email);
            if (idx != null) {
                Seat seat = t.getSeats().get(idx);
                if (seat == null) return;

                if (t.getPhase() == TablePhase.PLAYING || t.getPhase() == TablePhase.DEALER_TURN) {
                    seat.setStatus(SeatStatus.DISCONNECTED);
                    scheduleDisconnectCleanup(t, idx, email);
                } else {
                    t.getSeats().put(idx, new Seat());
                    userTable.remove(email);
                    cancelDisconnectTimer(email);
                }
            }
        }
        broadcastState(t);
        access.broadcastLobby();
    }

    public Boolean isFull(BjTable t) {
        long occupied = t.getSeats().values().stream()
                .filter(s -> s != null && s.getStatus() != SeatStatus.EMPTY)
                .count();
        return occupied >= t.getMaxSeats();
    }

    private Integer findSeatIndexByEmail(BjTable t, String email) {
        for (var e : t.getSeats().entrySet()) {
            if (e.getValue() != null && email.equals(e.getValue().getEmail())) return e.getKey();
        }
        return null;
    }

    private Optional<Integer> firstEmpty(BjTable t) {
        return t.getSeats().entrySet().stream()
                .filter(e -> e.getValue() == null || e.getValue().getStatus() == SeatStatus.EMPTY)
                .map(Map.Entry::getKey).sorted().findFirst();
    }

    private void broadcastState(BjTable t) {
        var payload = payloads.tableState(t, t.getPhaseDeadlineEpochMs());
        access.broadcastToTable(t, "TABLE_STATE", payload);
    }

    // ========= déconnexion / reconnexion =========

    /** Appelé par WsDisconnectListener quand la session WS d’un user tombe. */
    public void markDisconnected(String email) {
        Long tableId = userTable.get(email);
        if (tableId == null) return;
        BjTable t = registry.get(tableId);
        if (t == null) return;

        synchronized (locks.of(tableId)) {
            Integer idx = findSeatIndexByEmail(t, email);
            if (idx == null) return;
            Seat s = t.getSeats().get(idx);
            if (s == null) return;

            if (s.getStatus() == SeatStatus.EMPTY) return;

            s.setStatus(SeatStatus.DISCONNECTED);
            scheduleDisconnectCleanup(t, idx, email);
        }
        broadcastState(t);
        access.broadcastLobby();
    }

    /** Appelé par WsDisconnectListener quand l’utilisateur se reconnecte. */
    public void markReconnected(String email) {
        cancelDisconnectTimer(email);

        Long tableId = userTable.get(email);
        if (tableId == null) return;
        BjTable t = registry.get(tableId);
        if (t == null) return;

        synchronized (locks.of(tableId)) {
            Integer idx = findSeatIndexByEmail(t, email);
            if (idx == null) return;
            Seat s = t.getSeats().get(idx);
            if (s != null && s.getStatus() == SeatStatus.DISCONNECTED) {
                s.setStatus(SeatStatus.SEATED);
            }
        }
        broadcastState(t);
        access.broadcastLobby();
    }

    private void cancelDisconnectTimer(String email) {
        ScheduledFuture<?> f = disconnectTimers.remove(email);
        if (f != null) f.cancel(false);
    }

    private void scheduleDisconnectCleanup(BjTable t, int seatIndex, String email) {
        cancelDisconnectTimer(email);

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            synchronized (locks.of(t.getId())) {
                Seat s = t.getSeats().get(seatIndex);
                if (s == null) return;
                if (!Objects.equals(email, s.getEmail())) return;
                if (s.getStatus() != SeatStatus.DISCONNECTED) return;

                // libère le siège seulement dans un état sûr (BETTING ou PAYOUT)
                if (t.getPhase() == TablePhase.BETTING || t.getPhase() == TablePhase.PAYOUT) {
                    t.getSeats().put(seatIndex, new Seat());
                    userTable.remove(email);
                    disconnectTimers.remove(email);
                    broadcastState(t);
                    access.broadcastLobby();
                } else {
                    disconnectTimers.remove(email);
                }
            }
        }, DISCONNECT_GRACE_MS, TimeUnit.MILLISECONDS);

        disconnectTimers.put(email, future);
    }

    /**
     * 🔑 Appelé quand une table est réellement fermée/supprimée.
     * Libère tous les joueurs de la map userTable pour éviter les “fantômes”.
     */
    public void onTableClosed(BjTable t) {
        if (t == null) return;
        for (Seat s : t.getSeats().values()) {
            if (s != null && s.getEmail() != null) {
                userTable.remove(s.getEmail());
                cancelDisconnectTimer(s.getEmail());
            }
        }
    }
}
