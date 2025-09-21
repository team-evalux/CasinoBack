// src/main/java/org/example/service/BjTableService.java
package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.blackjack.*;
import org.example.model.Utilisateur;
import org.example.model.blackjack.*;
import org.example.repo.UtilisateurRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class BjTableService {

    private final WalletService walletService;
    private final UtilisateurRepository utilisateurRepo;
    private final SimpMessagingTemplate broker;

    private final Map<Long, BjTable> tables = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    // mapping email -> tableId (un utilisateur assis sur au plus une table)
    private final Map<String, Long> userTable = new ConcurrentHashMap<>();

    // timers pour nettoyage après déconnexion (email -> scheduledFuture)
    private final Map<String, ScheduledFuture<?>> disconnectTimers = new ConcurrentHashMap<>();

    // paramètres
    // paramètres
    private static final long BETTING_MS = 10_000;
    private static final long TURN_MS    = 20_000;
    private static final long RESULT_MS = 10_000; // <-- affiche les résultats pendant 10s
    private static final long DISCONNECT_GRACE_MS = 120_000; // 2 minutes de grâce


    // ------------------------------------------------------------------------
    // Helpers

    private Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length - 1; i += 2) {
            String k = (String) kv[i];
            Object v = kv[i + 1];
            out.put(k, v);
        }
        return out;
    }

    private void broadcast(BjTable t, String type, Object payload) {
        broker.convertAndSend("/topic/bj/table/" + t.getId(),
                TableEvent.builder().type(type).payload(payload).build());
    }

    private void broadcastState(BjTable t) {
        broadcast(t, "TABLE_STATE", m(
                "tableId",          t.getId(),
                "phase",            t.getPhase(),
                "deadline",         t.getPhaseDeadlineEpochMs(),
                "seats",            t.getSeats(),
                "dealer",           t.getDealer(),
                "currentSeatIndex", t.getCurrentSeatIndex(),
                "isPrivate",        t.isPrivate(),
                "code",             t.getCode(),
                "creatorEmail",     t.getCreatorEmail()
        ));
    }

    private void broadcastLobby() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (BjTable t : listPublicTables()) {
            list.add(m(
                    "id",        t.getId(),
                    "maxSeats",  t.getMaxSeats(),
                    "isPrivate", t.isPrivate(),
                    "phase",     t.getPhase() != null ? t.getPhase().name() : "WAITING_FOR_PLAYERS"
            ));
        }
        broker.convertAndSend("/topic/bj/lobby", list);
    }

    private BjTable mustTable(Long id) {
        BjTable t = tables.get(id);
        if (t == null) throw new IllegalArgumentException("Table inconnue");
        return t;
    }

    private Seat mustOwnedSeat(BjTable t, int seatIndex, String email) {
        Seat s = t.getSeats().get(seatIndex);
        if (s == null || s.getStatus() == SeatStatus.EMPTY) throw new IllegalStateException("Siège vide");
        if (!Objects.equals(s.getEmail(), email)) throw new IllegalStateException("Pas ton siège");
        return s;
    }

    private Integer firstActiveSeat(BjTable t) {
        for (Map.Entry<Integer, Seat> e : t.getSeats().entrySet()) {
            Seat s = e.getValue();
            if (s.getHand().getBet() > 0 && !s.getHand().isBusted() && !s.getHand().isStanding()) {
                return e.getKey();
            }
        }
        return null;
    }

    private void cancelDisconnectTimer(String email) {
        ScheduledFuture<?> f = disconnectTimers.remove(email);
        if (f != null) f.cancel(false);
    }

    private void scheduleDisconnectCleanup(BjTable t, int seatIndex, String email) {
        cancelDisconnectTimer(email);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            synchronized (BjTableService.this) {
                Seat s = t.getSeats().get(seatIndex);
                if (s == null) return;
                if (!Objects.equals(s.getEmail(), email)) return;
                if (s.getStatus() != SeatStatus.DISCONNECTED) return;

                // Ne supprimer le siège que si la table est dans un état sûr (pas mid-hand)
                if (t.getPhase() == TablePhase.WAITING_FOR_PLAYERS || t.getPhase() == TablePhase.PAYOUT) {
                    t.getSeats().put(seatIndex, new Seat());
                    userTable.remove(email);
                    disconnectTimers.remove(email);
                    broadcastState(t);
                    broadcastLobby();
                } else {
                    // si on est en pleine main, on laisse la place et on ne supprime pas tout de suite
                    // l'utilisateur restera DISCONNECTED ; on évite une suppression qui complexifierait la logique
                    disconnectTimers.remove(email);
                }
            }
        }, DISCONNECT_GRACE_MS, TimeUnit.MILLISECONDS);
        disconnectTimers.put(email, future);
    }

    // ------------------------------------------------------------------------
    // PUBLIC API

    /**
     * Crée une table et, si creatorEmail != null, assied automatiquement le créateur au siège 0.
     */
    public synchronized BjTable createTable(String creatorEmail, boolean isPrivate, String code, int maxSeats) {
        BjTable t = new BjTable(Math.max(2, Math.min(7, maxSeats)), isPrivate, code);
        t.setPhase(TablePhase.WAITING_FOR_PLAYERS);
        t.setPhaseDeadlineEpochMs(5L);
        t.setCurrentSeatIndex(null);
        t.setCreatorEmail(creatorEmail);
        t.setCreatedAt(Instant.now());

        tables.put(t.getId(), t);

        // si on a un créateur (endpoint sécurisé), on l'assoit automatiquement au siège 0
        if (creatorEmail != null) {
            utilisateurRepo.findByEmail(creatorEmail).ifPresent(u -> {
                Seat seat0 = t.getSeats().get(0);
                seat0.setUserId(u.getId());
                seat0.setEmail(creatorEmail);
                seat0.setStatus(SeatStatus.SEATED);
                userTable.put(creatorEmail, t.getId());
            });
        }

        broadcastState(t);
        broadcastLobby();
        return t;
    }

    public BjTable getTable(Long id) {
        return tables.get(id);
    }

    public List<BjTable> listPublicTables() {
        List<BjTable> out = new ArrayList<>();
        for (BjTable t : tables.values()) if (!t.isPrivate()) out.add(t);
        return out;
    }

    public synchronized BjTable joinOrCreate(String email, JoinOrCreateMsg msg) {
        if (Boolean.TRUE.equals(msg.isCreatePublic())) {
            return createTable(email, false, null, msg.getMaxSeats() != null ? msg.getMaxSeats() : 5);
        }
        if (Boolean.TRUE.equals(msg.isCreatePrivate())) {
            String c = (msg.getCode() != null && !msg.getCode().isBlank())
                    ? msg.getCode()
                    : UUID.randomUUID().toString().substring(0, 6);
            return createTable(email, true, c, msg.getMaxSeats() != null ? msg.getMaxSeats() : 5);
        }
        if (msg.getTableId() != null) {
            return mustTable(msg.getTableId());
        }
        return listPublicTables().stream().findFirst()
                .orElseGet(() -> createTable(email, false, null, 5));
    }

    public synchronized void sit(String email, Long tableId, int seatIndex) {
        // protection : un utilisateur ne peut être assis que sur une table
        Long already = userTable.get(email);
        if (already != null && !already.equals(tableId)) {
            throw new IllegalStateException("Tu es déjà assis sur une autre table");
        }

        BjTable t = mustTable(tableId);
        Seat seat = t.getSeats().get(seatIndex);
        if (seat == null) throw new IllegalArgumentException("Seat invalide");
        if (seat.getStatus() != SeatStatus.EMPTY && !Objects.equals(seat.getEmail(), email))
            throw new IllegalStateException("Seat occupé");

        // règle : sur une table publique on n'autorise pas de nouveaux joueurs en plein milieu d'une main
        if (!t.isPrivate() && (t.getPhase() == TablePhase.PLAYING || t.getPhase() == TablePhase.DEALER_TURN)) {
            throw new IllegalStateException("Impossible de rejoindre une table publique en cours de main. Attends la prochaine main.");
        }

        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
        seat.setUserId(u.getId());
        seat.setEmail(email);
        seat.setStatus(SeatStatus.SEATED);
        userTable.put(email, t.getId());
        cancelDisconnectTimer(email);

        if (t.getPhase() == TablePhase.WAITING_FOR_PLAYERS) {
            goBetting(t);
        } else {
            broadcastState(t);
        }
        broadcastLobby();
    }

    public synchronized void bet(String email, BetMsg msg) {
        BjTable t = mustTable(msg.getTableId());
        if (t.getPhase() != TablePhase.BETTING) {
            throw new IllegalStateException("Hors phase BETTING");
        }

        Integer idx = msg.getSeatIndex();
        if (idx == null) {
            idx = findSeatIndexByEmail(t, email);
            if (idx == null) {
                throw new IllegalStateException("Aucun siège pour cet utilisateur");
            }
        }

        Seat seat = mustOwnedSeat(t, idx, email);

        long amount = msg.getAmount();
        if (amount <= 0) throw new IllegalArgumentException("Mise invalide");

        seat.getHand().setBet(amount);

        // On ne débite qu'au moment du lockBetsAndDeal
        broadcast(t, "BET_UPDATE", Map.of(
                "seat", idx,
                "bet", amount
        ));
    }

    private Integer findSeatIndexByEmail(BjTable t, String email) {
        for (Map.Entry<Integer, Seat> e : t.getSeats().entrySet()) {
            Seat s = e.getValue();
            if (s != null && email.equals(s.getEmail())) {
                return e.getKey();
            }
        }
        return null;
    }

    public synchronized void action(String email, ActionMsg msg) {
        BjTable t = mustTable(msg.getTableId());
        if (t.getPhase() != TablePhase.PLAYING) throw new IllegalStateException("Hors phase PLAYING");
        if (!Objects.equals(t.getCurrentSeatIndex(), msg.getSeatIndex()))
            throw new IllegalStateException("Pas ton tour");

        Seat seat = mustOwnedSeat(t, msg.getSeatIndex(), email);

        switch (msg.getType()) {
            case HIT -> {
                seat.getHand().add(t.getShoe().draw());
                broadcast(t, "ACTION_RESULT", m("seat", msg.getSeatIndex(), "action", "HIT", "hand", seat.getHand()));
                if (seat.getHand().isBusted()) nextTurnOrDealer(t);
                else scheduleTurnTimeout(t);
            }
            case STAND -> {
                seat.getHand().setStanding(true);
                broadcast(t, "ACTION_RESULT", m("seat", msg.getSeatIndex(), "action", "STAND"));
                nextTurnOrDealer(t);
            }
            case DOUBLE -> {
                long add = seat.getHand().getBet();
                Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
                try {
                    walletService.debiter(u, add);
                } catch (Exception ex) {
                    broadcast(t, "ERROR", m("msg", "Solde insuffisant pour DOUBLE"));
                    return;
                }
                seat.getHand().setBet(seat.getHand().getBet() + add);
                seat.getHand().add(t.getShoe().draw());
                seat.getHand().setStanding(true);
                broadcast(t, "ACTION_RESULT", m("seat", msg.getSeatIndex(), "action", "DOUBLE", "hand", seat.getHand()));
                nextTurnOrDealer(t);
            }
        }
    }

    public synchronized void leave(String email, Long tableId, Integer seatIndex) {
        BjTable t = mustTable(tableId);
        if (seatIndex != null) {
            Seat seat = t.getSeats().get(seatIndex);
            if (seat != null && Objects.equals(seat.getEmail(), email)) {
                // si on est en cours de main on marque DISCONNECTED, sinon on libère le siège
                if (t.getPhase() == TablePhase.PLAYING || t.getPhase() == TablePhase.DEALER_TURN) {
                    seat.setStatus(SeatStatus.DISCONNECTED);
                    scheduleDisconnectCleanup(t, seatIndex, email);
                } else {
                    t.getSeats().put(seatIndex, new Seat());
                    userTable.remove(email);
                    cancelDisconnectTimer(email);
                }
            }
        }
        broadcastState(t);
        broadcastLobby();
    }

    public synchronized void markDisconnected(String email) {
        tables.values().forEach(table -> {
            table.getSeats().forEach((i, s) -> {
                if (email.equals(s.getEmail()) && s.getStatus() == SeatStatus.SEATED) {
                    s.setStatus(SeatStatus.DISCONNECTED);
                    scheduleDisconnectCleanup(table, i, email);
                }
            });
            broadcastState(table);
        });
        broadcastLobby();
    }

    public synchronized void markReconnected(String email) {
        // annule timer si existant et réassied si possible
        cancelDisconnectTimer(email);
        tables.values().forEach(table -> {
            table.getSeats().forEach((i, s) -> {
                if (email.equals(s.getEmail()) && s.getStatus() == SeatStatus.DISCONNECTED) {
                    s.setStatus(SeatStatus.SEATED);
                }
            });
            broadcastState(table);
        });
        broadcastLobby();
    }

    // ------------------------------------------------------------------------
    // LOGIQUE DE PHASES (inchangée sauf usage des nouvelles structures)

    private void goBetting(BjTable t) {
        t.setPhase(TablePhase.BETTING);
        t.setPhaseDeadlineEpochMs(Instant.now().toEpochMilli() + BETTING_MS);
        broadcastState(t);
        scheduler.schedule(() -> {
            synchronized (BjTableService.this) {
                try { lockBetsAndDeal(t); } catch (Exception ignored) {}
            }
        }, BETTING_MS, TimeUnit.MILLISECONDS);
    }

    private void lockBetsAndDeal(BjTable t) {
        // valider mises (débit)
        boolean aDesMises = false;
        for (Map.Entry<Integer, Seat> e : t.getSeats().entrySet()) {
            Seat s = e.getValue();
            if (s.getStatus() != SeatStatus.EMPTY && s.getHand().getBet() > 0) {
                aDesMises = true;
                try {
                    Utilisateur u = utilisateurRepo.findByEmail(s.getEmail()).orElseThrow();
                    walletService.debiter(u, s.getHand().getBet());
                } catch (Exception ex) {
                    s.getHand().setBet(0); // solde insuffisant -> annule la mise
                }
            }
        }
        if (!aDesMises) {
            t.setPhase(TablePhase.WAITING_FOR_PLAYERS);
            broadcastState(t);
            goBetting(t);
            return;
        }

        // reset dealer + distribuer 2 cartes
        t.getDealer().getCards().clear();
        t.getDealer().setStanding(false);
        t.getDealer().setBusted(false);
        t.getDealer().setBlackjack(false);

        for (Seat s : t.getSeats().values()) s.getHand().getCards().clear();

        for (int i = 0; i < 2; i++) {
            for (Seat s : t.getSeats().values()) {
                if (s.getHand().getBet() > 0) s.getHand().add(t.getShoe().draw());
            }
            t.getDealer().add(t.getShoe().draw());
        }

        t.setPhase(TablePhase.PLAYING);
        t.setCurrentSeatIndex(firstActiveSeat(t));
        scheduleTurnTimeout(t);

        broadcast(t, "HAND_START", m(
                "dealerUp", !t.getDealer().getCards().isEmpty() ? t.getDealer().getCards().get(0) : null,
                "deadline", t.getPhaseDeadlineEpochMs(),
                "players",  t.getSeats()
        ));

        if (t.getCurrentSeatIndex() == null) dealerTurn(t);
    }

    private void scheduleTurnTimeout(BjTable t) {
        Integer seatIdx = t.getCurrentSeatIndex();
        if (seatIdx == null) return;
        t.setPhaseDeadlineEpochMs(Instant.now().toEpochMilli() + TURN_MS);

        broadcast(t, "PLAYER_TURN", m("seat", seatIdx, "deadline", t.getPhaseDeadlineEpochMs()));

        scheduler.schedule(() -> {
            synchronized (BjTableService.this) {
                if (Objects.equals(seatIdx, t.getCurrentSeatIndex()) && t.getPhase() == TablePhase.PLAYING) {
                    Seat s = t.getSeats().get(seatIdx);
                    if (s != null && s.getHand().getBet() > 0 && !s.getHand().isBusted() && !s.getHand().isStanding()) {
                        s.getHand().setStanding(true);
                        broadcast(t, "ACTION_RESULT", m("seat", seatIdx, "action", "AUTO_STAND"));
                        nextTurnOrDealer(t);
                    }
                }
            }
        }, TURN_MS, TimeUnit.MILLISECONDS);
    }

    private void nextTurnOrDealer(BjTable t) {
        Integer idx = t.getCurrentSeatIndex();
        if (idx == null) { dealerTurn(t); return; }

        int start = idx + 1, n = t.getMaxSeats();
        for (int k = 0; k < n; k++) {
            int i = (start + k) % n;
            Seat s = t.getSeats().get(i);
            if (s != null && s.getHand().getBet() > 0 && !s.getHand().isBusted() && !s.getHand().isStanding()) {
                t.setCurrentSeatIndex(i);
                scheduleTurnTimeout(t);
                return;
            }
        }
        dealerTurn(t);
    }

    private void dealerTurn(BjTable t) {
        t.setPhase(TablePhase.DEALER_TURN);
        t.setCurrentSeatIndex(null);
        // optionnel : on peut définir un deadline si on veut l'afficher (ici 0 = pas de deadline pour croupier)
        t.setPhaseDeadlineEpochMs(0L);
        broadcast(t, "DEALER_TURN_START", m("dealer", t.getDealer()));

        // Runnable qui pioche progressivement et se re-schedule jusqu'à la condition d'arrêt
        Runnable drawTask = new Runnable() {
            @Override
            public void run() {
                synchronized (BjTableService.this) {
                    // si la condition est de tirer jusqu'à 17 (soft 17 reste), on continue
                    if (t.getDealer().bestTotal() < 17) {
                        t.getDealer().add(t.getShoe().draw());
                        // broadcast intermédiaire pour que le front affiche la carte piochée
                        broadcast(t, "DEALER_TURN_UPDATE", m("dealer", t.getDealer()));
                        // re-schedule une nouvelle itération (delay UX = 700ms)
                        scheduler.schedule(this, 700, TimeUnit.MILLISECONDS);
                    } else {
                        // fin du tour du croupier
                        broadcast(t, "DEALER_TURN_END", m("dealer", t.getDealer()));
                        // passe aux paiements
                        payouts(t);
                    }
                }
            }
        };

        // démarre la première itération avec un petit délai (donne le temps au front d'afficher)
        scheduler.schedule(drawTask, 700, TimeUnit.MILLISECONDS);
    }


    private void payouts(BjTable t) {
        // calcule et crédite d'abord les paiements
        int dealerTotal = t.getDealer().bestTotal();
        boolean dealerBust = dealerTotal > 21;

        List<Map<String, Object>> pay = new ArrayList<>();

        for (Map.Entry<Integer,Seat> e : t.getSeats().entrySet()) {
            Seat s = e.getValue();
            long bet = s.getHand().getBet();
            if (bet <= 0) continue;

            long credit = 0;
            int total = s.getHand().bestTotal();

            String outcome;

            if (s.getHand().isBusted()) {
                credit = 0;
                outcome = "LOSE";
            } else if (s.getHand().isBlackjack() && t.getDealer().isBlackjack()) {
                credit = bet;
                outcome = "PUSH";
            } else if (s.getHand().isBlackjack()) {
                credit = bet + (bet * 3) / 2; // 3:2
                outcome = "BLACKJACK";
            } else if (t.getDealer().isBlackjack()) {
                credit = 0;
                outcome = "LOSE";
            } else if (dealerBust || total > dealerTotal) {
                credit = bet * 2;
                outcome = "WIN";
            } else if (total == dealerTotal) {
                credit = bet;
                outcome = "PUSH";
            } else {
                credit = 0;
                outcome = "LOSE";
            }

            if (credit > 0) {
                Utilisateur u = utilisateurRepo.findByEmail(s.getEmail()).orElseThrow();
                walletService.crediter(u, credit);
            }

            pay.add(Map.of(
                    "seat",   e.getKey(),
                    "bet",    bet,
                    "credit", credit,
                    "total",  total,
                    "outcome", outcome
            ));
        }

        // on passe la table en phase PAYOUT et on lui donne un deadline (affichage résultats)
        t.setPhase(TablePhase.PAYOUT);
        t.setPhaseDeadlineEpochMs(Instant.now().toEpochMilli() + RESULT_MS);

        // envoie d'abord l'événement PAYOUTS (payload détaillé)
        broadcast(t, "PAYOUTS", Map.of("payouts", pay));

        // et envoie aussi un TABLE_STATE qui contient lastPayouts pour que les clients gardent l'état pendant RESULT_MS
        broadcast(t, "TABLE_STATE", m(
                "tableId",          t.getId(),
                "phase",            t.getPhase(),
                "deadline",         t.getPhaseDeadlineEpochMs(),
                "seats",            t.getSeats(),
                "dealer",           t.getDealer(),
                "currentSeatIndex", t.getCurrentSeatIndex(),
                "isPrivate",        t.isPrivate(),
                "code",             t.getCode(),
                "creatorEmail",     t.getCreatorEmail(),
                "lastPayouts",      pay
        ));

        // après RESULT_MS, on nettoie et on relance une nouvelle phase de mises
        scheduler.schedule(() -> {
            synchronized (BjTableService.this) {
                // reset main
                for (Seat s : t.getSeats().values()) s.resetForNextHand();
                t.getDealer().getCards().clear();

                // repasse en WAITING et broadcast normal
                t.setPhase(TablePhase.WAITING_FOR_PLAYERS);
                t.setPhaseDeadlineEpochMs(null);
                broadcastState(t);
                broadcastLobby();

                // lance la prochaine betting phase
                goBetting(t);
            }
        }, RESULT_MS, TimeUnit.MILLISECONDS);
    }



    // ------------------------------------------------------------------------
    // Admin / close table
    public synchronized void closeTable(Long tableId, String requesterEmail) {
        BjTable t = mustTable(tableId);
        boolean allowed = Objects.equals(t.getCreatorEmail(), requesterEmail);
        if (!allowed) {
            // admin override ?
            Utilisateur u = utilisateurRepo.findByEmail(requesterEmail).orElse(null);
            if (u != null && "ADMIN".equalsIgnoreCase(u.getRole())) allowed = true;
        }
        if (!allowed) throw new IllegalStateException("Seul le créateur ou un ADMIN peut fermer la table");

        // cleanup : retirer les mappings & timers pour les joueurs assis
        for (Seat s : t.getSeats().values()) {
            if (s.getEmail() != null) {
                userTable.remove(s.getEmail());
                cancelDisconnectTimer(s.getEmail());
            }
        }
        tables.remove(tableId);
        broadcastLobby();
    }
}
