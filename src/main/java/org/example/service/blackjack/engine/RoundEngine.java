// src/main/java/org/example/service/blackjack/engine/RoundEngine.java
package org.example.service.blackjack.engine;

import lombok.RequiredArgsConstructor;
import org.example.model.blackjack.*;
import org.example.model.blackjack.rules.DealingRules;
import org.example.repo.UtilisateurRepository;
import org.example.service.WalletService;
import org.example.service.blackjack.access.AccessService;
import org.example.service.blackjack.registry.TableRegistry;
import org.example.service.blackjack.util.Locks;
import org.example.service.blackjack.util.Payloads;
import org.example.service.blackjack.util.Timeouts;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RoundEngine {
    private final TableRegistry registry;
    private final Payloads payloads;
    private final PayoutService payouts;
    private final WalletService wallet;
    private final UtilisateurRepository users;
    private final Timeouts timeouts;
    private final Locks locks;
    private final AccessService access;   // <-- IMPORTANT

    private static final long BETTING_MS = 10_000, TURN_MS = 20_000, RESULT_MS = 10_000;

    public void startBetting(BjTable t) {
        t.setPhase(TablePhase.BETTING);
        t.setPhaseDeadlineEpochMs(now()+BETTING_MS);
        broadcastState(t);
        timeouts.schedule(t.getId(), "betting", BETTING_MS, () -> {
            synchronized (this) { lockBetsAndDeal(t); }
        });
    }

    public void lockBetsAndDeal(BjTable t) {
        boolean any = false;
        for (Seat s : t.getSeats().values()) {
            if (s.getStatus()!=SeatStatus.EMPTY && s.getHand().getBet() > 0) {
                any = true;
                try {
                    Long uid = s.getUserId();
                    if (uid != null) {
                        users.findById(uid).ifPresent(u -> wallet.debiter(u, s.getHand().getBet()));
                    } else {
                        users.findByEmail(s.getEmail()).ifPresent(u -> wallet.debiter(u, s.getHand().getBet()));
                    }
                } catch (Exception ex) {
                    s.getHand().setBet(0);
                }
            }
        }
        if (!any) { startBetting(t); return; }

        DealingRules.dealInitial(t);

        t.setPhase(TablePhase.PLAYING);
        t.setCurrentSeatIndex(firstActiveSeat(t));
        scheduleTurnTimeout(t);

        // HAND_START pour afficher la première carte du croupier + mains joueurs
        access.broadcastToTable(t, "HAND_START", Map.of(
                "dealerUp", t.getDealer().getCards().isEmpty()?null:t.getDealer().getCards().get(0),
                "deadline", t.getPhaseDeadlineEpochMs(),
                "players", payloads.seatsPayload(t)
        ));

        if (t.getCurrentSeatIndex() == null) dealerTurn(t);
    }

    public void scheduleTurnTimeout(BjTable t) {
        Integer idx = t.getCurrentSeatIndex(); if (idx == null) return;
        t.setPhaseDeadlineEpochMs(now()+TURN_MS);
        access.broadcastToTable(t, "PLAYER_TURN", Map.of("seat", idx, "deadline", t.getPhaseDeadlineEpochMs()));
        timeouts.schedule(t.getId(), "turn-"+idx, TURN_MS, () -> {
            synchronized (this) {
                if (Objects.equals(idx, t.getCurrentSeatIndex()) && t.getPhase()==TablePhase.PLAYING) {
                    Seat s = t.getSeats().get(idx);
                    if (s != null && s.getHand().getBet()>0 && !s.getHand().isBusted() && !s.getHand().isStanding()) {
                        s.getHand().setStanding(true);
                        access.broadcastToTable(t, "ACTION_RESULT", Map.of("seat", idx, "action", "AUTO_STAND"));
                        nextTurnOrDealer(t);
                    }
                }
            }
        });
    }

    public void nextTurnOrDealer(BjTable t) {
        Integer idx = t.getCurrentSeatIndex();
        if (idx == null) { dealerTurn(t); return; }
        int start = idx + 1, n = t.getMaxSeats();
        for (int k = 0; k < n; k++) {
            int i = (start + k) % n;
            Seat s = t.getSeats().get(i);
            if (s != null && s.getHand().getBet()>0 && !s.getHand().isBusted() && !s.getHand().isStanding()) {
                t.setCurrentSeatIndex(i);
                scheduleTurnTimeout(t);
                return;
            }
        }
        dealerTurn(t);
    }

    public void dealerTurn(BjTable t) {
        t.setPhase(TablePhase.DEALER_TURN);
        t.setCurrentSeatIndex(null);
        t.setPhaseDeadlineEpochMs(0L);
        access.broadcastToTable(t, "DEALER_TURN_START", Map.of("dealer", t.getDealer()));

        Runnable draw = new Runnable() {
            @Override public void run() {
                synchronized (RoundEngine.this) {
                    if (t.getDealer().bestTotal() < 17) {
                        t.getDealer().add(t.getShoe().draw());
                        access.broadcastToTable(t, "DEALER_TURN_UPDATE", Map.of("dealer", t.getDealer()));
                        timeouts.schedule(t.getId(), "dealerDraw", 700, this);
                    } else {
                        access.broadcastToTable(t, "DEALER_TURN_END", Map.of("dealer", t.getDealer()));
                        doPayouts(t);
                    }
                }
            }
        };
        timeouts.schedule(t.getId(), "dealerDraw", 700, draw);
    }

    private void doPayouts(BjTable t) {
        var pay = payouts.computeAndPay(t);
        t.setPhase(TablePhase.PAYOUT);
        t.setPhaseDeadlineEpochMs(now()+RESULT_MS);
        access.broadcastToTable(t, "PAYOUTS", Map.of("payouts", pay));
        broadcastState(t);

        timeouts.schedule(t.getId(), "nextHand", RESULT_MS, () -> {
            synchronized (this) {
                for (Seat s : t.getSeats().values()) s.resetForNextHand();
                t.getDealer().getCards().clear();
                t.setPhase(TablePhase.BETTING);
                t.setPhaseDeadlineEpochMs(now()+BETTING_MS);
                broadcastState(t);
                startBetting(t);
            }
        });
    }

    public void broadcastState(BjTable t) {
        access.broadcastToTable(t, "TABLE_STATE", payloads.tableState(t, t.getPhaseDeadlineEpochMs()));
    }

    /** Utilisé par BjTableService après une action du joueur */
    public void onPlayerAction(BjTable t) {
        synchronized (locks.of(t.getId())) {
            nextTurnOrDealer(t);
            if (t.getPhase() == TablePhase.PLAYING && t.getCurrentSeatIndex() != null) {
                t.setPhaseDeadlineEpochMs(now() + TURN_MS);
                access.broadcastToTable(t, "PLAYER_TURN", Map.of(
                        "seat", t.getCurrentSeatIndex(),
                        "deadline", t.getPhaseDeadlineEpochMs()
                ));
            }
            broadcastState(t);
        }
    }

    private Integer firstActiveSeat(BjTable t) {
        for (var e : t.getSeats().entrySet()) {
            var s = e.getValue();
            if (s.getHand().getBet() > 0 && !s.getHand().isBusted() && !s.getHand().isStanding()) return e.getKey();
        }
        return null;
    }

    private long now(){ return System.currentTimeMillis(); }
}
